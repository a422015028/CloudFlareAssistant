package com.muort.upworker.feature.worker

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.KvNamespace
import com.muort.upworker.core.model.R2Bucket
import com.muort.upworker.core.model.WorkerScript
import com.muort.upworker.core.repository.KvRepository
import com.muort.upworker.core.repository.R2Repository
import com.muort.upworker.core.repository.D1Repository
import timber.log.Timber
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.DialogAddSecretBinding
import com.muort.upworker.databinding.DialogAddVariableBinding
import com.muort.upworker.databinding.DialogKvBindingBinding
import com.muort.upworker.databinding.DialogR2BindingBinding
import com.muort.upworker.databinding.FragmentWorkerBinding
import com.muort.upworker.databinding.ItemKvBindingBinding
import com.muort.upworker.databinding.ItemR2BindingBinding
import com.muort.upworker.databinding.ItemSecretBinding
import com.muort.upworker.databinding.ItemVariableBinding
import com.muort.upworker.databinding.ItemWorkerScriptBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

// Data class for D1 binding display
data class D1BindingItem(
    val bindingName: String,
    val databaseId: String,
    val databaseName: String
)

@AndroidEntryPoint
class WorkerFragment : Fragment() {
    
    private var _binding: FragmentWorkerBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: WorkerViewModel by viewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    
    @Inject
    lateinit var kvRepository: KvRepository
    
    @Inject
    lateinit var r2Repository: R2Repository
    
    @Inject
    lateinit var d1Repository: D1Repository
    
    private var selectedFile: File? = null
    private lateinit var scriptsAdapter: WorkerScriptsAdapter
    private var scriptViewDialog: AlertDialog? = null
    
    // 缓存脚本大小
    private val scriptSizeCache = mutableMapOf<String, Long>()
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                var fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "script.js"
                // 尝试从 ContentResolver 获取真实文件名，以兼容更多文件管理器
                try {
                    requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex >= 0) {
                            cursor.getString(nameIndex)?.let { fileName = it }
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to resolve file name")
                }

                val tempFile = File(requireContext().cacheDir, fileName)
                
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                
                selectedFile = tempFile
                binding.filePathEdit.setText(tempFile.absolutePath)
                
                val workerName = Regex("""(.+?)(?:-Worker|\.worker)?\.js""", RegexOption.IGNORE_CASE)
                    .find(fileName)?.groupValues?.get(1)
                workerName?.let { binding.workerNameEdit.setText(it) }
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkerBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupUI()
        observeViewModels()
        loadScripts()
    }
    
    private fun setupUI() {
        scriptsAdapter = WorkerScriptsAdapter(
            scriptSizeCache,
            onViewClick = { script ->
                viewScriptContent(script)
            },
            onDeleteClick = { script ->
                showDeleteConfirmDialog(script)
            },
            onConfigKvClick = { script ->
                showConfigKvBindingsDialog(script)
            },
            onConfigR2Click = { script ->
                showConfigR2BindingsDialog(script)
            },
            onConfigD1Click = { script ->
                showConfigD1BindingsDialog(script)
            },
            onConfigVariablesClick = { script ->
                showConfigVariablesDialog(script)
            },
            onConfigSecretsClick = { script ->
                showConfigSecretsDialog(script)
            }
        )
        binding.scriptsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scriptsAdapter
        }
        
        binding.selectFileBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/javascript",
                    "text/javascript",
                    "text/plain"
                ))
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(intent)
        }
        
        binding.filePathEdit.setOnClickListener {
            binding.selectFileBtn.performClick()
        }
        
        binding.uploadBtn.setOnClickListener {
            uploadWorker()
        }
        
        binding.refreshBtn.setOnClickListener {
            loadScripts()
        }
    }
    
    private fun uploadWorker() {
        val workerName = binding.workerNameEdit.text.toString().trim()
        val file = selectedFile

        if (workerName.isEmpty()) {
            showToast("请输入 Worker 名称")
            return
        }

        if (file == null || !file.exists()) {
            showToast("请选择脚本文件")
            return
        }

        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }

        // 显示检查状态的 Loading
        val checkingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在准备...")
            .setMessage("正在检查 Worker 状态")
            .setCancelable(false)
            .create()
        checkingDialog.show()

        // 直接从云端检查 Worker 是否存在，而不是依赖本地缓存列表
        // 这样即使本地列表为空（如刚打开 App），也能正确识别已存在的 Worker 并保留绑定
        viewModel.getWorkerSettings(account, workerName) { result ->
            checkingDialog.dismiss()
            if (result is com.muort.upworker.core.model.Resource.Success) {
                viewModel.uploadWorkerScriptWithBindings(account, workerName, file)
            } else {
                viewModel.uploadWorkerScript(account, workerName, file)
            }
        }
    }
    
    private fun showConfigKvBindingsDialog(script: WorkerScript) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("加载中...")
            .setMessage("正在获取当前 KV 绑定配置")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // First, fetch current settings to get existing bindings
        viewLifecycleOwner.lifecycleScope.launch {
            // 先查出所有命名空间
            val namespaces = run {
                val result = kvRepository.listNamespaces(account)
                if (result is com.muort.upworker.core.model.Resource.Success) result.data else emptyList()
            }
            viewModel.getWorkerSettings(account, script.id) { settingsResult ->
                loadingDialog.dismiss()

                val dialogBinding = com.muort.upworker.databinding.DialogScriptKvBindingsBinding.inflate(layoutInflater)

                // Setup title
                dialogBinding.scriptNameText.text = "脚本名称: ${script.id}"

                // Temporary list for this dialog - initialize with existing bindings
                val tempKvBindings = mutableListOf<Pair<String, String>>()
                // Load existing KV bindings from settings
                if (settingsResult is com.muort.upworker.core.model.Resource.Success) {
                    settingsResult.data.bindings?.forEach { binding ->
                        if (binding.type == "kv_namespace" && binding.namespaceId != null) {
                            val ns = namespaces.find { it.id == binding.namespaceId }
                            val nsTitle = ns?.title ?: binding.namespaceId
                            tempKvBindings.add(Pair(binding.name, nsTitle))
                            Timber.d("Loaded existing KV binding: ${binding.name} -> $nsTitle")
                        }
                    }
                }

                // Setup adapter with lateinit reference
                lateinit var tempAdapter: KvBindingsAdapter
            tempAdapter = KvBindingsAdapter(
                namespaces = namespaces,
                onDeleteClick = { position ->
                    tempKvBindings.removeAt(position)
                    updateDialogBindingsUI(dialogBinding, tempAdapter, tempKvBindings)
                }
            )
            dialogBinding.bindingsRecyclerView.apply {
                layoutManager = LinearLayoutManager(requireContext())
                adapter = tempAdapter
            }
            
            // Add binding button
            dialogBinding.addBindingBtn.setOnClickListener {
                showAddKvBindingDialogForScript(tempKvBindings) {
                    updateDialogBindingsUI(dialogBinding, tempAdapter, tempKvBindings)
                }
            }
            
                updateDialogBindingsUI(dialogBinding, tempAdapter, tempKvBindings)
                
                // Show dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton("应用配置") { _, _ ->
                        // Allow empty bindings (remove all bindings)
                        applyKvBindingsToScript(script, tempKvBindings)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }
    
    private fun updateDialogBindingsUI(
        dialogBinding: com.muort.upworker.databinding.DialogScriptKvBindingsBinding,
        adapter: KvBindingsAdapter,
        bindings: List<Pair<String, String>>
    ) {
        if (bindings.isEmpty()) {
            dialogBinding.noBindingsText.visibility = View.VISIBLE
            dialogBinding.bindingsRecyclerView.visibility = View.GONE
        } else {
            dialogBinding.noBindingsText.visibility = View.GONE
            dialogBinding.bindingsRecyclerView.visibility = View.VISIBLE
            adapter.submitList(bindings)
        }
    }
    
    private fun showAddKvBindingDialogForScript(
        tempBindings: MutableList<Pair<String, String>>,
        onAdded: () -> Unit
    ) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            val result = kvRepository.listNamespaces(account)
            
            if (result is com.muort.upworker.core.model.Resource.Success) {
                val namespaces = result.data
                
                if (namespaces.isEmpty()) {
                    showToast("暂无 KV 命名空间，请先创建")
                    return@launch
                }
                
                val dialogBinding = DialogKvBindingBinding.inflate(layoutInflater)
                
                // Setup spinner
                val namespaceNames = namespaces.map { it.title }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, namespaceNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                dialogBinding.namespaceSpinner.adapter = adapter
                
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton("添加") { _, _ ->
                        val bindingName = dialogBinding.bindingNameEdit.text.toString().trim()
                        val selectedIndex = dialogBinding.namespaceSpinner.selectedItemPosition
                        
                        if (bindingName.isEmpty()) {
                            showToast("请输入绑定名称")
                            return@setPositiveButton
                        }
                        
                        if (selectedIndex >= 0 && selectedIndex < namespaces.size) {
                            val namespace = namespaces[selectedIndex]
                            tempBindings.add(Pair(bindingName, namespace.id))
                            onAdded()
                            showToast("KV 绑定已添加")
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else if (result is com.muort.upworker.core.model.Resource.Error) {
                showToast("加载 KV 命名空间失败: ${result.message}")
            }
        }
    }
    
    private fun applyKvBindingsToScript(script: WorkerScript, bindings: List<Pair<String, String>>) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        Timber.d("Applying ${bindings.size} KV bindings to script '${script.id}'")
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在更新...")
            .setMessage("正在更新 KV 绑定配置（不重新上传脚本代码）")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Use the new method that only updates bindings without re-uploading script
        viewModel.updateWorkerKvBindings(account, script.id, bindings)
        
        // Dismiss loading dialog after a short delay to show the message
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            loadingDialog.dismiss()
            showToast("KV 绑定配置已更新")
        }
    }
    
    // ==================== R2 Bindings Configuration ====================
    
    private fun showConfigR2BindingsDialog(script: WorkerScript) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在加载...")
            .setMessage("正在获取当前 R2 绑定配置")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // First, fetch current settings to get existing bindings
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getWorkerSettings(account, script.id) { settingsResult ->
                loadingDialog.dismiss()
                
                val dialogBinding = com.muort.upworker.databinding.DialogScriptR2BindingsBinding.inflate(layoutInflater)
                
                // Setup title
                dialogBinding.scriptNameText.text = "脚本名称: ${script.id}"
                
                // Temporary list for this dialog - initialize with existing bindings
                val tempR2Bindings = mutableListOf<Pair<String, String>>()
                
                // Load existing R2 bindings from settings
                if (settingsResult is com.muort.upworker.core.model.Resource.Success) {
                    settingsResult.data.bindings?.forEach { binding ->
                        if (binding.type == "r2_bucket" && binding.bucketName != null) {
                            tempR2Bindings.add(Pair(binding.name, binding.bucketName))
                            Timber.d("Loaded existing R2 binding: ${binding.name} -> ${binding.bucketName}")
                        }
                    }
                }
                
                // Setup adapter with lateinit reference
                lateinit var tempAdapter: R2BindingsAdapter
                tempAdapter = R2BindingsAdapter(
                    onDeleteClick = { position ->
                        tempR2Bindings.removeAt(position)
                        updateDialogR2BindingsUI(dialogBinding, tempAdapter, tempR2Bindings)
                    }
                )
                dialogBinding.bindingsRecyclerView.apply {
                    layoutManager = LinearLayoutManager(requireContext())
                    adapter = tempAdapter
                }
                
                // Add binding button
                dialogBinding.addBindingBtn.setOnClickListener {
                    showAddR2BindingDialogForScript(tempR2Bindings) {
                        updateDialogR2BindingsUI(dialogBinding, tempAdapter, tempR2Bindings)
                    }
                }
                
                updateDialogR2BindingsUI(dialogBinding, tempAdapter, tempR2Bindings)
                
                // Show dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton("应用配置") { _, _ ->
                        // Allow empty bindings (remove all bindings)
                        applyR2BindingsToScript(script, tempR2Bindings)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }
    
    private fun updateDialogR2BindingsUI(
        dialogBinding: com.muort.upworker.databinding.DialogScriptR2BindingsBinding,
        adapter: R2BindingsAdapter,
        bindings: List<Pair<String, String>>
    ) {
        if (bindings.isEmpty()) {
            dialogBinding.noBindingsText.visibility = View.VISIBLE
            dialogBinding.bindingsRecyclerView.visibility = View.GONE
        } else {
            dialogBinding.noBindingsText.visibility = View.GONE
            dialogBinding.bindingsRecyclerView.visibility = View.VISIBLE
            adapter.submitList(bindings)
        }
    }
    
    private fun showAddR2BindingDialogForScript(
        tempBindings: MutableList<Pair<String, String>>,
        onAdded: () -> Unit
    ) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            val result = r2Repository.listBuckets(account)
            
            if (result is com.muort.upworker.core.model.Resource.Success) {
                val buckets = result.data
                
                if (buckets.isEmpty()) {
                    showToast("暂无 R2 存储桶，请先创建")
                    return@launch
                }
                
                val dialogBinding = DialogR2BindingBinding.inflate(layoutInflater)
                
                // Setup spinner
                val bucketNames = buckets.map { 
                    val location = it.location?.uppercase() ?: "自动选择"
                    "${it.name} ($location)"
                }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, bucketNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                dialogBinding.bucketSpinner.adapter = adapter
                
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton("添加") { _, _ ->
                        val bindingName = dialogBinding.bindingNameEdit.text.toString().trim()
                        val selectedIndex = dialogBinding.bucketSpinner.selectedItemPosition
                        
                        if (bindingName.isEmpty()) {
                            showToast("请输入绑定名称")
                            return@setPositiveButton
                        }
                        
                        if (selectedIndex >= 0 && selectedIndex < buckets.size) {
                            val bucket = buckets[selectedIndex]
                            tempBindings.add(Pair(bindingName, bucket.name))
                            onAdded()
                            showToast("R2 绑定已添加")
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else if (result is com.muort.upworker.core.model.Resource.Error) {
                showToast("加载 R2 存储桶失败: ${result.message}")
            }
        }
    }
    
    private fun applyR2BindingsToScript(script: WorkerScript, bindings: List<Pair<String, String>>) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        Timber.d("Applying ${bindings.size} R2 bindings to script '${script.id}'")
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在更新...")
            .setMessage("正在更新 R2 绑定配置（不重新上传脚本代码）")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Use the new method that only updates bindings without re-uploading script
        viewModel.updateWorkerR2Bindings(account, script.id, bindings)
        
        // Dismiss loading dialog after a short delay to show the message
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            loadingDialog.dismiss()
            showToast("R2 绑定配置已更新")
        }
    }
    
    // ==================== D1 Bindings Configuration ====================
    
    private fun showConfigD1BindingsDialog(script: WorkerScript) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在加载...")
            .setMessage("正在获取当前 D1 绑定配置")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // First, fetch current settings to get existing bindings
        viewLifecycleOwner.lifecycleScope.launch {
            // First get D1 databases for name resolution
            val databasesResult = d1Repository.listDatabases(account)
            val databaseIdToName = if (databasesResult is com.muort.upworker.core.model.Resource.Success) {
                databasesResult.data.associate { it.uuid to it.name }
            } else {
                emptyMap()
            }
            
            viewModel.getWorkerSettings(account, script.id) { settingsResult ->
                loadingDialog.dismiss()
                
                val dialogBinding = com.muort.upworker.databinding.DialogScriptD1BindingsBinding.inflate(layoutInflater)
                
                // Setup title
                dialogBinding.scriptNameText.text = "脚本名称: ${script.id}"
                
                // Temporary list for this dialog - initialize with existing bindings
                val tempD1Bindings = mutableListOf<D1BindingItem>()
                
                // Load existing D1 bindings from settings
                if (settingsResult is com.muort.upworker.core.model.Resource.Success) {
                    settingsResult.data.bindings?.forEach { binding ->
                        if (binding.type == "d1" && binding.databaseId != null) {
                            val databaseName = databaseIdToName[binding.databaseId] ?: binding.databaseId
                            tempD1Bindings.add(D1BindingItem(binding.name, binding.databaseId, databaseName))
                            Timber.d("Loaded existing D1 binding: ${binding.name} -> ${databaseName} (${binding.databaseId})")
                        }
                    }
                }
                
                // Setup adapter with lateinit reference
                lateinit var tempAdapter: D1BindingsAdapter
                tempAdapter = D1BindingsAdapter(
                    onDeleteClick = { position ->
                        tempD1Bindings.removeAt(position)
                        updateDialogD1BindingsUI(dialogBinding, tempAdapter, tempD1Bindings)
                    }
                )
                dialogBinding.bindingsRecyclerView.apply {
                    layoutManager = LinearLayoutManager(requireContext())
                    adapter = tempAdapter
                }
                
                // Add binding button
                dialogBinding.addBindingBtn.setOnClickListener {
                    showAddD1BindingDialogForScript(tempD1Bindings) {
                        updateDialogD1BindingsUI(dialogBinding, tempAdapter, tempD1Bindings)
                    }
                }
                
                updateDialogD1BindingsUI(dialogBinding, tempAdapter, tempD1Bindings)
                
                // Show dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton("应用配置") { _, _ ->
                        // Allow empty bindings (remove all bindings)
                        applyD1BindingsToScript(script, tempD1Bindings)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }
    
    private fun updateDialogD1BindingsUI(
        dialogBinding: com.muort.upworker.databinding.DialogScriptD1BindingsBinding,
        adapter: D1BindingsAdapter,
        bindings: List<D1BindingItem>
    ) {
        if (bindings.isEmpty()) {
            dialogBinding.noBindingsText.visibility = View.VISIBLE
            dialogBinding.bindingsRecyclerView.visibility = View.GONE
        } else {
            dialogBinding.noBindingsText.visibility = View.GONE
            dialogBinding.bindingsRecyclerView.visibility = View.VISIBLE
            adapter.submitList(bindings)
        }
    }
    
    private fun showAddD1BindingDialogForScript(
        tempBindings: MutableList<D1BindingItem>,
        onAdded: () -> Unit
    ) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        viewLifecycleOwner.lifecycleScope.launch {
            val result = d1Repository.listDatabases(account)
            
            if (result is com.muort.upworker.core.model.Resource.Success<List<com.muort.upworker.core.model.D1Database>>) {
                val databases = result.data
                
                if (databases.isEmpty()) {
                    showToast("暂无 D1 数据库，请先创建")
                    return@launch
                }
                
                val dialogBinding = com.muort.upworker.databinding.DialogD1BindingBinding.inflate(layoutInflater)
                
                // Setup spinner
                val databaseNames = mutableListOf<String>()
                databases.forEach { database: com.muort.upworker.core.model.D1Database ->
                    databaseNames.add("${database.name} (${database.uuid})")
                }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, databaseNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                dialogBinding.databaseSpinner.adapter = adapter
                
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton("添加") { _, _ ->
                        val bindingName = dialogBinding.bindingNameEdit.text.toString().trim()
                        val selectedIndex = dialogBinding.databaseSpinner.selectedItemPosition
                        
                        if (bindingName.isEmpty()) {
                            showToast("请输入绑定名称")
                            return@setPositiveButton
                        }
                        
                        if (selectedIndex >= 0 && selectedIndex < databases.size) {
                            val database = databases[selectedIndex]
                            tempBindings.add(D1BindingItem(bindingName, database.uuid, database.name))
                            onAdded()
                            showToast("D1 绑定已添加")
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else if (result is com.muort.upworker.core.model.Resource.Error) {
                showToast("加载 D1 数据库失败: ${result.message}")
            }
        }
    }
    
    private fun applyD1BindingsToScript(script: WorkerScript, bindings: List<D1BindingItem>) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        Timber.d("Applying ${bindings.size} D1 bindings to script '${script.id}'")
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在更新...")
            .setMessage("正在更新 D1 绑定配置（不重新上传脚本代码）")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Use the new method that only updates bindings without re-uploading script
        val bindingPairs = bindings.map { Pair(it.bindingName, it.databaseId) }
        viewModel.updateWorkerD1Bindings(account, script.id, bindingPairs)
        
        // Dismiss loading dialog after a short delay to show the message
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            loadingDialog.dismiss()
            showToast("D1 绑定配置已更新")
        }
    }
    
    // ==================== Variables Configuration ====================
    
    private fun showConfigVariablesDialog(script: WorkerScript) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在加载...")
            .setMessage("正在获取当前环境变量配置")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Fetch current settings to get existing variables
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getWorkerSettings(account, script.id) { settingsResult ->
                loadingDialog.dismiss()
                
                val dialogBinding = com.muort.upworker.databinding.DialogScriptVariablesBinding.inflate(layoutInflater)
                
                // Setup title
                dialogBinding.scriptNameText.text = "脚本名称: ${script.id}"
                
                // Temporary list for variables (name, value, type)
                val tempVariables = mutableListOf<Triple<String, String, String>>()
                
                // Load existing variables from settings
                if (settingsResult is com.muort.upworker.core.model.Resource.Success) {
                    settingsResult.data.bindings?.forEach { binding ->
                        if (binding.type == "plain_text" || binding.type == "json") {
                            // Plain text and JSON bindings are environment variables
                            val value = binding.getValue() ?: ""
                            Timber.d("Loaded existing variable: ${binding.name} (${binding.type}), text field: '${binding.text}', json field: '${binding.json}', value: '$value'")
                            tempVariables.add(Triple(binding.name, value, binding.type))
                        }
                    }
                }
                
                // Setup adapter
                lateinit var tempAdapter: VariablesAdapter
                tempAdapter = VariablesAdapter(
                    onEditClick = { position ->
                        showEditVariableDialog(tempVariables, position) {
                            updateDialogVariablesUI(dialogBinding, tempAdapter, tempVariables)
                        }
                    },
                    onDeleteClick = { position ->
                        tempVariables.removeAt(position)
                        updateDialogVariablesUI(dialogBinding, tempAdapter, tempVariables)
                    }
                )
                dialogBinding.variablesRecyclerView.apply {
                    layoutManager = LinearLayoutManager(requireContext())
                    adapter = tempAdapter
                }
                
                // Add variable button
                dialogBinding.addVariableBtn.setOnClickListener {
                    showAddVariableDialog(tempVariables) {
                        updateDialogVariablesUI(dialogBinding, tempAdapter, tempVariables)
                    }
                }
                
                updateDialogVariablesUI(dialogBinding, tempAdapter, tempVariables)
                
                // Show dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton("应用配置") { _, _ ->
                        applyVariablesToScript(script, tempVariables)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }
    
    private fun updateDialogVariablesUI(
        dialogBinding: com.muort.upworker.databinding.DialogScriptVariablesBinding,
        adapter: VariablesAdapter,
        variables: List<Triple<String, String, String>>
    ) {
        if (variables.isEmpty()) {
            dialogBinding.noVariablesText.visibility = View.VISIBLE
            dialogBinding.variablesRecyclerView.visibility = View.GONE
        } else {
            dialogBinding.noVariablesText.visibility = View.GONE
            dialogBinding.variablesRecyclerView.visibility = View.VISIBLE
            adapter.submitList(variables)
        }
    }
    
    private fun showAddVariableDialog(
        tempVariables: MutableList<Triple<String, String, String>>,
        onAdded: () -> Unit
    ) {
        val dialogBinding = DialogAddVariableBinding.inflate(layoutInflater)
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("添加") { _, _ ->
                val name = dialogBinding.variableNameEdit.text.toString().trim()
                val value = dialogBinding.variableValueEdit.text.toString().trim()
                val type = if (dialogBinding.typeJsonRadio.isChecked) "json" else "plain_text"
                
                if (name.isEmpty()) {
                    showToast("请输入变量名称")
                    return@setPositiveButton
                }
                
                if (value.isEmpty()) {
                    showToast("请输入变量值")
                    return@setPositiveButton
                }
                
                // Validate JSON format if JSON type is selected
                if (type == "json") {
                    try {
                        com.google.gson.JsonParser.parseString(value)
                    } catch (e: Exception) {
                        showToast("JSON 格式无效: ${e.message}")
                        return@setPositiveButton
                    }
                }
                
                tempVariables.add(Triple(name, value, type))
                onAdded()
                showToast("环境变量已添加")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showEditVariableDialog(
        tempVariables: MutableList<Triple<String, String, String>>,
        position: Int,
        onEdited: () -> Unit
    ) {
        val variable = tempVariables[position]
        val dialogBinding = DialogAddVariableBinding.inflate(layoutInflater)
        
        // Pre-fill existing values
        dialogBinding.variableNameEdit.setText(variable.first)
        dialogBinding.variableValueEdit.setText(variable.second)
        if (variable.third == "json") {
            dialogBinding.typeJsonRadio.isChecked = true
        } else {
            dialogBinding.typeTextRadio.isChecked = true
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("编辑环境变量")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val name = dialogBinding.variableNameEdit.text.toString().trim()
                val value = dialogBinding.variableValueEdit.text.toString().trim()
                val type = if (dialogBinding.typeJsonRadio.isChecked) "json" else "plain_text"
                
                if (name.isEmpty()) {
                    showToast("请输入变量名称")
                    return@setPositiveButton
                }
                
                if (value.isEmpty()) {
                    showToast("请输入变量值")
                    return@setPositiveButton
                }
                
                // Validate JSON format if JSON type is selected
                if (type == "json") {
                    try {
                        com.google.gson.JsonParser.parseString(value)
                    } catch (e: Exception) {
                        showToast("JSON 格式无效: ${e.message}")
                        return@setPositiveButton
                    }
                }
                
                tempVariables[position] = Triple(name, value, type)
                onEdited()
                showToast("环境变量已更新")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun applyVariablesToScript(script: WorkerScript, variables: List<Triple<String, String, String>>) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        Timber.d("Applying ${variables.size} variables to script '${script.id}'")
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在更新...")
            .setMessage("正在更新环境变量配置")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        viewModel.updateWorkerVariables(account, script.id, variables)
        
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            loadingDialog.dismiss()
            showToast("环境变量配置已更新")
        }
    }
    
    // ==================== Secrets Configuration ====================
    
    private fun showConfigSecretsDialog(script: WorkerScript) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在加载...")
            .setMessage("正在获取当前机密变量配置")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Fetch current settings to get existing secrets
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getWorkerSettings(account, script.id) { settingsResult ->
                loadingDialog.dismiss()
                
                val dialogBinding = com.muort.upworker.databinding.DialogScriptSecretsBinding.inflate(layoutInflater)
                
                // Setup title
                dialogBinding.scriptNameText.text = "脚本名称: ${script.id}"
                
                // Temporary list for secrets (name only, values are not readable)
                val tempSecrets = mutableListOf<Pair<String, String>>()
                
                // Load existing secrets from settings
                if (settingsResult is com.muort.upworker.core.model.Resource.Success) {
                    settingsResult.data.bindings?.forEach { binding ->
                        if (binding.type == "secret_text") {
                            // Secret bindings - values are not returned by API
                            tempSecrets.add(Pair(binding.name, ""))
                            Timber.d("Loaded existing secret: ${binding.name}")
                        }
                    }
                }
                
                // Setup adapter
                lateinit var tempAdapter: SecretsAdapter
                tempAdapter = SecretsAdapter(
                    onEditClick = { position ->
                        showEditSecretDialog(tempSecrets, position) {
                            updateDialogSecretsUI(dialogBinding, tempAdapter, tempSecrets)
                        }
                    },
                    onDeleteClick = { position ->
                        tempSecrets.removeAt(position)
                        updateDialogSecretsUI(dialogBinding, tempAdapter, tempSecrets)
                    }
                )
                dialogBinding.secretsRecyclerView.apply {
                    layoutManager = LinearLayoutManager(requireContext())
                    adapter = tempAdapter
                }
                
                // Add secret button
                dialogBinding.addSecretBtn.setOnClickListener {
                    showAddSecretDialog(tempSecrets) {
                        updateDialogSecretsUI(dialogBinding, tempAdapter, tempSecrets)
                    }
                }
                
                updateDialogSecretsUI(dialogBinding, tempAdapter, tempSecrets)
                
                // Show dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton("应用配置") { _, _ ->
                        applySecretsToScript(script, tempSecrets)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }
    
    private fun updateDialogSecretsUI(
        dialogBinding: com.muort.upworker.databinding.DialogScriptSecretsBinding,
        adapter: SecretsAdapter,
        secrets: List<Pair<String, String>>
    ) {
        if (secrets.isEmpty()) {
            dialogBinding.noSecretsText.visibility = View.VISIBLE
            dialogBinding.secretsRecyclerView.visibility = View.GONE
        } else {
            dialogBinding.noSecretsText.visibility = View.GONE
            dialogBinding.secretsRecyclerView.visibility = View.VISIBLE
            adapter.submitList(secrets)
        }
    }
    
    private fun showAddSecretDialog(
        tempSecrets: MutableList<Pair<String, String>>,
        onAdded: () -> Unit
    ) {
        val dialogBinding = DialogAddSecretBinding.inflate(layoutInflater)
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("添加") { _, _ ->
                val name = dialogBinding.secretNameEdit.text.toString().trim()
                val value = dialogBinding.secretValueEdit.text.toString().trim()
                
                if (name.isEmpty()) {
                    showToast("请输入机密名称")
                    return@setPositiveButton
                }
                
                if (value.isEmpty()) {
                    showToast("请输入机密值")
                    return@setPositiveButton
                }
                
                tempSecrets.add(Pair(name, value))
                onAdded()
                showToast("机密变量已添加")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showEditSecretDialog(
        tempSecrets: MutableList<Pair<String, String>>,
        position: Int,
        onEdited: () -> Unit
    ) {
        val secret = tempSecrets[position]
        val dialogBinding = DialogAddSecretBinding.inflate(layoutInflater)
        
        // Pre-fill existing name (value cannot be retrieved)
        dialogBinding.secretNameEdit.setText(secret.first)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("编辑机密变量")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val name = dialogBinding.secretNameEdit.text.toString().trim()
                val value = dialogBinding.secretValueEdit.text.toString().trim()
                
                if (name.isEmpty()) {
                    showToast("请输入机密名称")
                    return@setPositiveButton
                }
                
                if (value.isEmpty()) {
                    showToast("请输入机密值")
                    return@setPositiveButton
                }
                
                tempSecrets[position] = Pair(name, value)
                onEdited()
                showToast("机密变量已更新")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun applySecretsToScript(script: WorkerScript, secrets: List<Pair<String, String>>) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        Timber.d("Applying ${secrets.size} secrets to script '${script.id}'")
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在更新...")
            .setMessage("正在更新机密变量配置")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        viewModel.updateWorkerSecrets(account, script.id, secrets)
        
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            loadingDialog.dismiss()
            showToast("机密变量配置已更新")
        }
    }
    
    private fun loadScripts() {
        val account = accountViewModel.defaultAccount.value
        if (account != null) {
            viewModel.loadWorkerScripts(account)
            // 加载完成后自动获取所有脚本大小
            loadScriptSizes(account)
        }
    }
    
    private fun loadScriptSizes(account: Account) {
        lifecycleScope.launch {
            // 等待脚本列表加载完成
            viewModel.scripts.collect { scripts ->
                if (scripts.isEmpty()) return@collect
                
                // 并发获取所有脚本的大小
                scripts.forEach { script ->
                    // 跳过已缓存的
                    if (scriptSizeCache.containsKey(script.id)) return@forEach
                    
                    launch {
                        try {
                            viewModel.getWorkerScript(account, script.id, silent = true) { content ->
                                scriptSizeCache[script.id] = content.length.toLong()
                                // 更新UI
                                scriptsAdapter.notifyDataSetChanged()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to get script size for ${script.id}")
                        }
                    }
                }
                
                // 只执行一次
                return@collect
            }
        }
    }
    
    private fun viewScriptContent(script: WorkerScript) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        // 导航到脚本编辑器
        val action = WorkerFragmentDirections.actionWorkerToScriptEditor(
            accountEmail = account.accountId,
            scriptName = script.id
        )
        findNavController().navigate(action)
    }
    
    private fun showScriptInEditText(script: WorkerScript, content: String) {
        val scrollView = NestedScrollView(requireContext()).apply {
            setPadding(48, 24, 48, 24)
        }
        
        val editText = EditText(requireContext()).apply {
            post {
                setText(content)
            }
            textSize = 12f
            typeface = Typeface.MONOSPACE
            inputType = InputType.TYPE_CLASS_TEXT or 
                       InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                       InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            isSingleLine = false
            setHorizontallyScrolling(false)
            minLines = 20
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setTextIsSelectable(true)
        }
        
        scrollView.addView(editText)
        
        scriptViewDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(script.id)
            .setView(scrollView)
            .setPositiveButton("保存") { _, _ ->
                val newContent = editText.text.toString()
                if (newContent != content) {
                    saveScriptContent(script, newContent)
                } else {
                    showToast("内容未修改")
                }
                editText.setText("")
            }
            .setNegativeButton("关闭") { _, _ ->
                editText.setText("")
            }
            .setNeutralButton("复制") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Worker Script", editText.text.toString())
                clipboard.setPrimaryClip(clip)
                showToast("已复制到剪贴板")
            }
            .setOnDismissListener {
                editText.setText("")
                scriptViewDialog = null
            }
            .create()
        
        scriptViewDialog?.show()
    }
    
    private fun showScriptInWebView(script: WorkerScript, content: String) {
        // 获取当前主题的Dialog背景色
        val typedValue = android.util.TypedValue()
        val theme = requireContext().theme
        
        // 获取Dialog的实际背景色 - 使用android.R.attr.colorBackground更准确
        theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        val bgColor = typedValue.data
        val backgroundColor = String.format("#%06X", 0xFFFFFF and bgColor)
        
        // 获取文字颜色
        theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
        val txtColor = typedValue.data
        val textColor = String.format("#%06X", 0xFFFFFF and txtColor)
        
        val webView = WebView(requireContext()).apply {
            // 设置透明背景，让HTML背景色生效
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            
            settings.apply {
                javaScriptEnabled = false
                defaultTextEncodingName = "utf-8"
                textZoom = 85
                // 禁用所有不必要的功能以提高性能
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = false
                useWideViewPort = false
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            
            // HTML转义处理，防止内容被解析
            val escapedContent = content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;")
            
            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body {
                            margin: 0;
                            padding: 12px;
                            font-family: 'Courier New', monospace;
                            font-size: 11px;
                            line-height: 1.4;
                            word-wrap: break-word;
                            white-space: pre-wrap;
                            background: $backgroundColor;
                            color: $textColor;
                        }
                        * {
                            -webkit-user-select: text;
                            user-select: text;
                        }
                    </style>
                </head>
                <body>$escapedContent</body>
                </html>
            """.trimIndent()
            
            loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        }
        
        // 格式化文件大小显示
        val sizeText = formatScriptSize(content.length.toLong())
        
        scriptViewDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("${script.id} (只读 - $sizeText)")
            .setView(webView)
            .setNegativeButton("关闭", null)
            .setNeutralButton("复制") { _, _ ->
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Worker Script", content)
                clipboard.setPrimaryClip(clip)
                showToast("已复制到剪贴板")
            }
            .setPositiveButton("编辑") { _, _ ->
                // 为大脚本提供导出和编辑选项
                showEditOptionsDialog(script, content)
            }
            .setOnDismissListener {
                webView.destroy()
                scriptViewDialog = null
            }
            .create()
        
        scriptViewDialog?.show()
    }
    
    private fun formatScriptSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
        }
    }
    
    private fun showEditOptionsDialog(script: WorkerScript, content: String) {
        // 超过200KB的脚本强制使用导出功能，避免卡死
        if (content.length > 200_000) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("脚本过大")
                .setMessage("该脚本(${content.length}字符)过大，应用内编辑会导致卡顿。\n\n将自动导出到文件供外部编辑器使用。")
                .setPositiveButton("导出") { _, _ ->
                    exportScriptToFile(script, content)
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("编辑脚本")
            .setMessage("该脚本(${content.length}字符)，请选择编辑方式：")
            .setPositiveButton("导出到文件") { _, _ ->
                exportScriptToFile(script, content)
            }
            .setNeutralButton("应用内编辑") { _, _ ->
                // 使用优化的方式编辑
                showScriptInOptimizedEditText(script, content)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun exportScriptToFile(script: WorkerScript, content: String) {
        try {
            val fileName = "${script.id}.js"
            val file = File(requireContext().getExternalFilesDir(null), fileName)
            file.writeText(content)
            
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("导出成功")
                .setMessage("脚本已导出到：\n${file.absolutePath}\n\n你可以用任何文本编辑器打开编辑，编辑完成后重新上传。")
                .setPositiveButton("确定", null)
                .setNeutralButton("分享文件") { _, _ ->
                    shareScriptFile(file)
                }
                .show()
        } catch (e: Exception) {
            Timber.e(e, "导出脚本失败")
            showToast("导出失败: ${e.message}")
        }
    }
    
    private fun shareScriptFile(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/javascript"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "分享脚本文件"))
        } catch (e: Exception) {
            Timber.e(e, "分享文件失败")
            showToast("分享失败: ${e.message}")
        }
    }
    
    private fun showScriptInOptimizedEditText(script: WorkerScript, content: String) {
        // 使用优化的EditText，减少内存占用
        lifecycleScope.launch {
            try {
                val loadingDialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle("加载中...")
                    .setMessage("正在准备编辑器")
                    .setCancelable(false)
                    .create()
                loadingDialog.show()
                
                // 延迟加载，给UI线程时间响应
                kotlinx.coroutines.delay(100)
                
                val scrollView = NestedScrollView(requireContext()).apply {
                    setPadding(24, 12, 24, 12)
                }
                
                val editText = EditText(requireContext()).apply {
                    // 分批设置文本，减少卡顿
                    hint = "加载中..."
                    textSize = 10f  // 较小的字体减少渲染压力
                    typeface = Typeface.MONOSPACE
                    inputType = InputType.TYPE_CLASS_TEXT or 
                               InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                               InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                    isSingleLine = false
                    setHorizontallyScrolling(false)  // 禁用水平滚动，启用自动换行
                    minLines = 10
                    maxLines = 30  // 限制最大行数
                    gravity = android.view.Gravity.TOP or android.view.Gravity.START
                    setTextIsSelectable(true)
                    
                    // 延迟设置内容
                    postDelayed({
                        setText(content)
                        hint = ""
                    }, 200)
                }
                
                scrollView.addView(editText)
                loadingDialog.dismiss()
                
                scriptViewDialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle("${script.id} (编辑)")
                    .setView(scrollView)
                    .setPositiveButton("保存") { _, _ ->
                        val newContent = editText.text.toString()
                        if (newContent != content && newContent.isNotEmpty()) {
                            saveScriptContent(script, newContent)
                        } else {
                            showToast("内容未修改")
                        }
                        editText.setText("")
                    }
                    .setNegativeButton("取消") { _, _ ->
                        editText.setText("")
                    }
                    .setOnDismissListener {
                        editText.setText("")
                        scriptViewDialog = null
                    }
                    .create()
                
                scriptViewDialog?.show()
                
            } catch (e: OutOfMemoryError) {
                Timber.e(e, "内存不足")
                showToast("内存不足，无法编辑。请使用导出功能。")
            } catch (e: Exception) {
                Timber.e(e, "编辑器加载失败")
                showToast("加载失败: ${e.message}")
            }
        }
    }
    
    private fun saveScriptContent(script: WorkerScript, newContent: String) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        val tempFile = File(requireContext().cacheDir, "${script.id}-edited.js")
        try {
            tempFile.writeText(newContent)
            // 使用自动保留绑定的上传方法
            viewModel.uploadWorkerScriptWithBindings(account, script.id, tempFile)
            showToast("正在保存...")
            tempFile.deleteOnExit()
        } catch (e: Exception) {
            showToast("保存失败: ${e.message}")
        }
    }
    
    private fun showDeleteConfirmDialog(script: WorkerScript) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除脚本")
            .setMessage("确定要删除 Worker 脚本 \"${script.id}\" 吗？此操作无法撤销。")
            .setPositiveButton("删除") { _, _ ->
                val account = accountViewModel.defaultAccount.value
                if (account != null) {
                    viewModel.deleteWorkerScript(account, script.id)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun observeViewModels() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uploadState.collect { state ->
                        when (state) {
                            is UploadState.Idle -> {
                                binding.uploadProgress.visibility = View.GONE
                                binding.uploadBtn.isEnabled = true
                            }
                            is UploadState.Uploading -> {
                                binding.uploadProgress.visibility = View.VISIBLE
                                binding.uploadBtn.isEnabled = false
                            }
                            is UploadState.Success -> {
                                binding.uploadProgress.visibility = View.GONE
                                binding.uploadBtn.isEnabled = true
                                binding.workerNameEdit.text?.clear()
                                binding.filePathEdit.text?.clear()
                                selectedFile = null
                                viewModel.resetUploadState()
                            }
                            is UploadState.Error -> {
                                binding.uploadProgress.visibility = View.GONE
                                binding.uploadBtn.isEnabled = true
                                viewModel.resetUploadState()
                            }
                        }
                    }
                }
                
                launch {
                    viewModel.scripts.collect { scripts ->
                        if (scripts.isEmpty()) {
                            binding.emptyText.visibility = View.VISIBLE
                            binding.scriptsRecyclerView.visibility = View.GONE
                        } else {
                            binding.emptyText.visibility = View.GONE
                            binding.scriptsRecyclerView.visibility = View.VISIBLE
                            scriptsAdapter.submitList(scripts)
                        }
                        // 更新 Worker 名称下拉框
                        updateWorkerNameAutoComplete(scripts)
                    }
                }
                
                launch {
                    viewModel.message.collect { message ->
                        showToast(message)
                    }
                }
                
                launch {
                    accountViewModel.defaultAccount.collect { account ->
                        if (account != null) {
                            loadScripts()
                        }
                    }
                }
            }
        }
    }
    
    private fun updateWorkerNameAutoComplete(scripts: List<WorkerScript>) {
        val scriptNames = scripts.map { it.id }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            scriptNames
        )
        binding.workerNameEdit.setAdapter(adapter)
        
        // 设置点击下拉图标时显示所有选项
        binding.workerNameEdit.setOnClickListener {
            binding.workerNameEdit.showDropDown()
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        scriptViewDialog?.dismiss()
        scriptViewDialog = null
        _binding = null
    }
}

class WorkerScriptsAdapter(
    private val scriptSizeCache: Map<String, Long>,
    private val onViewClick: (WorkerScript) -> Unit,
    private val onDeleteClick: (WorkerScript) -> Unit,
    private val onConfigKvClick: (WorkerScript) -> Unit,
    private val onConfigR2Click: (WorkerScript) -> Unit,
    private val onConfigD1Click: (WorkerScript) -> Unit,
    private val onConfigVariablesClick: (WorkerScript) -> Unit,
    private val onConfigSecretsClick: (WorkerScript) -> Unit
) : RecyclerView.Adapter<WorkerScriptsAdapter.ScriptViewHolder>() {
    
    private var scripts = listOf<WorkerScript>()
    
    fun submitList(newScripts: List<WorkerScript>) {
        scripts = newScripts
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScriptViewHolder {
        val binding = ItemWorkerScriptBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ScriptViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ScriptViewHolder, position: Int) {
        holder.bind(scripts[position])
    }
    
    override fun getItemCount() = scripts.size
    
    inner class ScriptViewHolder(
        private val binding: ItemWorkerScriptBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(script: WorkerScript) {
            binding.scriptNameText.text = script.id
            
            val dateText = formatDate(script.createdOn)
            // 优先使用缓存的大小，其次是API返回的size
            val size = scriptSizeCache[script.id] ?: script.size
            val sizeText = formatSize(size)
            binding.scriptSizeText.text = "$sizeText \u2022 $dateText"
            
            binding.configKvBtn.setOnClickListener {
                onConfigKvClick(script)
            }
            
            binding.configR2Btn.setOnClickListener {
                onConfigR2Click(script)
            }
            
            binding.configD1Btn.setOnClickListener {
                onConfigD1Click(script)
            }
            
            binding.configVariablesBtn.setOnClickListener {
                onConfigVariablesClick(script)
            }
            
            binding.configSecretsBtn.setOnClickListener {
                onConfigSecretsClick(script)
            }
            
            binding.viewBtn.setOnClickListener {
                onViewClick(script)
            }
            
            binding.deleteBtn.setOnClickListener {
                onDeleteClick(script)
            }
        }
        
        private fun formatSize(size: Long?): String {
            if (size == null || size <= 0) return "未知大小"
            
            return when {
                size < 1024 -> "${size}B"
                size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
                size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
                else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
            }
        }
        
        private fun formatDate(dateString: String?): String {
            if (dateString == null) return "未知日期"
            
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                
                val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                outputFormat.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
                
                val date = inputFormat.parse(dateString)
                date?.let { outputFormat.format(it) } ?: dateString
            } catch (e: Exception) {
                dateString.substringBefore('T')
            }
        }
    }
}

class KvBindingsAdapter(
    private val namespaces: List<KvNamespace>,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<KvBindingsAdapter.BindingViewHolder>() {
    
    private var bindings = listOf<Pair<String, String>>()
    
    fun submitList(newBindings: List<Pair<String, String>>) {
        bindings = newBindings
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        val binding = ItemKvBindingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BindingViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        holder.bind(bindings[position], position)
    }
    
    override fun getItemCount() = bindings.size
    
    inner class BindingViewHolder(
        private val binding: ItemKvBindingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(kvBinding: Pair<String, String>, position: Int) {
            binding.bindingNameText.text = kvBinding.first
            // Convert namespace ID to title for display
            val namespaceId = kvBinding.second
            val namespace = namespaces.find { it.id == namespaceId }
            val displayText = namespace?.title ?: namespaceId
            binding.namespaceIdText.text = displayText
            
            binding.deleteBindingBtn.setOnClickListener {
                onDeleteClick(position)
            }
        }
    }
}

class R2BindingsAdapter(
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<R2BindingsAdapter.BindingViewHolder>() {
    
    private var bindings = listOf<Pair<String, String>>()
    
    fun submitList(newBindings: List<Pair<String, String>>) {
        bindings = newBindings
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        val binding = ItemR2BindingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BindingViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        holder.bind(bindings[position], position)
    }
    
    override fun getItemCount() = bindings.size
    
    inner class BindingViewHolder(
        private val binding: ItemR2BindingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(r2Binding: Pair<String, String>, position: Int) {
            binding.bindingNameText.text = r2Binding.first
            binding.bucketNameText.text = "Bucket: ${r2Binding.second}"
            
            binding.deleteBindingBtn.setOnClickListener {
                onDeleteClick(position)
            }
        }
    }
}

class VariablesAdapter(
    private val onEditClick: (Int) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<VariablesAdapter.VariableViewHolder>() {
    
    private var variables = listOf<Triple<String, String, String>>()
    
    fun submitList(newVariables: List<Triple<String, String, String>>) {
        variables = newVariables
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VariableViewHolder {
        val binding = ItemVariableBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VariableViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: VariableViewHolder, position: Int) {
        holder.bind(variables[position], position)
    }
    
    override fun getItemCount() = variables.size
    
    inner class VariableViewHolder(
        private val binding: ItemVariableBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(variable: Triple<String, String, String>, position: Int) {
            binding.variableNameText.text = variable.first
            binding.variableValueText.text = variable.second
            binding.variableTypeText.text = if (variable.third == "json") "[JSON]" else "[文本]"
            
            binding.editVariableBtn.setOnClickListener {
                onEditClick(position)
            }
            
            binding.deleteVariableBtn.setOnClickListener {
                onDeleteClick(position)
            }
        }
    }
}

class SecretsAdapter(
    private val onEditClick: (Int) -> Unit,
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<SecretsAdapter.SecretViewHolder>() {
    
    private var secrets = listOf<Pair<String, String>>()
    
    fun submitList(newSecrets: List<Pair<String, String>>) {
        secrets = newSecrets
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SecretViewHolder {
        val binding = ItemSecretBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SecretViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: SecretViewHolder, position: Int) {
        holder.bind(secrets[position], position)
    }
    
    override fun getItemCount() = secrets.size
    
    inner class SecretViewHolder(
        private val binding: ItemSecretBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(secret: Pair<String, String>, position: Int) {
            binding.secretNameText.text = secret.first
            
            binding.editSecretBtn.setOnClickListener {
                onEditClick(position)
            }
            
            binding.deleteSecretBtn.setOnClickListener {
                onDeleteClick(position)
            }
        }
    }
}

class D1BindingsAdapter(
    private val onDeleteClick: (Int) -> Unit
) : RecyclerView.Adapter<D1BindingsAdapter.BindingViewHolder>() {
    
    private var bindings = listOf<D1BindingItem>()
    
    fun submitList(newBindings: List<D1BindingItem>) {
        bindings = newBindings
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        val binding = com.muort.upworker.databinding.ItemD1BindingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BindingViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        holder.bind(bindings[position], position)
    }
    
    override fun getItemCount() = bindings.size
    
    inner class BindingViewHolder(
        private val binding: com.muort.upworker.databinding.ItemD1BindingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(d1Binding: D1BindingItem, position: Int) {
            binding.bindingNameText.text = d1Binding.bindingName
            binding.databaseNameText.text = d1Binding.databaseName
            
            binding.deleteBindingBtn.setOnClickListener {
                onDeleteClick(position)
            }
        }
    }
}
