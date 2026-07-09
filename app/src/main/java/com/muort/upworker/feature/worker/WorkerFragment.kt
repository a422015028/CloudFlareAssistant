package com.muort.upworker.feature.worker

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.WorkerScript
import com.muort.upworker.core.model.DEFAULT_COMPATIBILITY_DATE
import com.muort.upworker.core.repository.KvRepository
import com.muort.upworker.core.repository.R2Repository
import com.muort.upworker.core.repository.D1Repository
import com.muort.upworker.core.repository.AccountRepository
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
import com.muort.upworker.R
import com.muort.upworker.core.model.WorkerVersion
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.dropWhile
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
    
    @Inject
    lateinit var accountRepository: AccountRepository
    
    private var selectedFile: File? = null
    private lateinit var scriptsAdapter: WorkerScriptsAdapter
    
    // 缓存脚本大小
    private val scriptSizeCache = mutableMapOf<String, Long>()
    
    // 批量删除相关属性
    private var isSelectionMode = false
    private val selectedScripts = mutableSetOf<String>()
    
    // 版本历史对话框引用
    private var historyDialog: android.app.Dialog? = null
    
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
                
                // Auto-populate worker name from file name if empty
                // 格式：原文件名-4位随机字母 (如: test-hfdh)
                if (binding.workerNameEdit.text.isNullOrEmpty()) {
                    val baseName = fileName.substringBeforeLast(".")
                    val randomSuffix = generateRandomSuffix()
                    binding.workerNameEdit.setText("$baseName-$randomSuffix")
                }
            }
        }
    }

    private fun generateRandomSuffix(): String {
        val chars = ('a'..'z').toList()
        return (1..4).map { chars.random() }.joinToString("")
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
            onDeleteClick = { script ->
                showDeleteConfirmDialog(script)
            },
            onHistoryClick = { script ->
                showScriptHistoryDialog(script)
            },
            onEditClick = { script ->
                editScript(script)
            },
            onTriggerClick = { script ->
                showBuildTriggersDialog(script)
            },
            onLogsClick = { script ->
                showWorkerLogs(script)
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
            },
            onSelectionModeClick = { script, isSelected ->
                if (isSelected) {
                    selectedScripts.add(script.id)
                } else {
                    selectedScripts.remove(script.id)
                }
                updateSelectionUI()
            }
        )
        binding.scriptsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scriptsAdapter
        }
        
        // 部署卡片折叠功能
        val prefs = requireContext().getSharedPreferences("worker_prefs", android.content.Context.MODE_PRIVATE)
        var isExpanded = prefs.getBoolean("deploy_card_expanded", false)
        binding.deployCardContent.visibility = if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
        binding.deployCardArrow.rotation = if (isExpanded) 180f else 0f
        binding.deployCardHeader.setOnClickListener {
            isExpanded = !isExpanded
            binding.deployCardContent.visibility = if (isExpanded) android.view.View.VISIBLE else android.view.View.GONE
            binding.deployCardArrow.rotation = if (isExpanded) 180f else 0f
            prefs.edit().putBoolean("deploy_card_expanded", isExpanded).apply()
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
        
        // 添加多选模式切换和批量操作按钮
        setupBatchOperationUI()
    }
    
    private fun setupBatchOperationUI() {
        val toggleSelectionBtn = binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("toggleSelectionModeBtn", "id", requireContext().packageName)
        )
        
        val selectionActionsLayout = binding.root.findViewById<android.widget.LinearLayout>(
            resources.getIdentifier("selectionActionsLayout", "id", requireContext().packageName)
        )
        
        val selectionStatusText = binding.root.findViewById<android.widget.TextView>(
            resources.getIdentifier("selectionStatusText", "id", requireContext().packageName)
        )
        
        val selectAllBtn = binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("selectAllBtn", "id", requireContext().packageName)
        )
        
        val batchDeleteBtn = binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("batchDeleteBtn", "id", requireContext().packageName)
        )
        
        val cleanupBtn = binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("cleanupDeploymentsBtn", "id", requireContext().packageName)
        )
        
        toggleSelectionBtn?.text = if (isSelectionMode) "取消" else "管理脚本"
        selectionActionsLayout?.visibility = if (isSelectionMode) android.view.View.VISIBLE else android.view.View.GONE
        selectionStatusText?.text = "已选择 ${selectedScripts.size} 个脚本"
        batchDeleteBtn?.isEnabled = selectedScripts.isNotEmpty()
        
        toggleSelectionBtn?.setOnClickListener {
            toggleSelectionMode()
        }
        
        selectAllBtn?.setOnClickListener {
            selectAllScripts()
        }
        
        batchDeleteBtn?.setOnClickListener {
            if (selectedScripts.isNotEmpty()) {
                showBatchDeleteConfirmDialog()
            }
        }
        
        cleanupBtn?.setOnClickListener {
            showCleanupVersionsDialog()
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

        // 获取用户输入的兼容性日期，为空时使用默认值
        val customCompatibilityDate = binding.compatibilityDateEdit.text.toString().trim()
            .takeIf { it.isNotEmpty() } ?: DEFAULT_COMPATIBILITY_DATE
        
        // 直接从云端检查 Worker 是否存在，而不是依赖本地缓存列表
        // 这样即使本地列表为空（如刚打开 App），也能正确识别已存在的 Worker 并保留绑定
        // silent = true: 新脚本不存在时不显示错误提示
        viewModel.getWorkerSettings(account, workerName, silent = true) { result ->
            checkingDialog.dismiss()
            if (result is com.muort.upworker.core.model.Resource.Success) {
                viewModel.uploadWorkerScriptWithBindings(account, workerName, file, customCompatibilityDate)
            } else {
                viewModel.uploadWorkerScript(account, workerName, file, customCompatibilityDate)
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
    
    private fun showScriptHistoryDialog(script: WorkerScript) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("加载中...")
            .setMessage("正在获取版本历史")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        lifecycleScope.launch {
            val result = viewModel.fetchWorkerVersions(account, script.id)
            loadingDialog.dismiss()
            
            when (result) {
                is Resource.Success -> {
                    val versions = result.data
                    if (versions.isNotEmpty()) {
                        val runningVersionId = versions.firstOrNull()?.id
                        
                        val dialogView = layoutInflater.inflate(R.layout.dialog_worker_history, null)
                        val closeBtn = dialogView.findViewById<android.widget.Button>(R.id.closeBtn)
                        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.historyRecyclerView)
                        
                        val adapter = WorkerHistoryAdapter(
                            versions = versions,
                            runningVersionId = runningVersionId,
                            formatDate = { formatDate(it) },
                            onItemClick = { version ->
                                showVersionDetailDialog(script, version, runningVersionId == version.id)
                            },
                            onDeleteClick = { version ->
                                showDeleteVersionConfirmDialog(script, version)
                            }
                        )
                        recyclerView.apply {
                            this.adapter = adapter
                            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
                        }
                        
                        historyDialog?.dismiss()
                        historyDialog = MaterialAlertDialogBuilder(requireContext())
                            .setView(dialogView)
                            .create()
                        
                        closeBtn.setOnClickListener {
                            historyDialog?.dismiss()
                            historyDialog = null
                        }
                        
                        historyDialog?.show()
                    } else {
                        showToast("暂无版本历史")
                    }
                }
                is Resource.Error -> {
                    showToast("${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    private fun showDeleteVersionConfirmDialog(script: WorkerScript, version: WorkerVersion) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除版本")
            .setMessage("确定要删除版本 #${version.number} 吗？")
            .setPositiveButton("删除") { _, _ ->
                val account = accountViewModel.defaultAccount.value
                if (account != null) {
                    lifecycleScope.launch {
                        val result = viewModel.deleteWorkerVersion(account, script.id, version.id)
                        when (result) {
                            is Resource.Success -> {
                                showToast("删除成功")
                                historyDialog?.dismiss()
                                showScriptHistoryDialog(script)
                            }
                            is Resource.Error -> {
                                showToast("${result.message}")
                            }
                            is Resource.Loading -> {}
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private var triggersDialog: android.app.Dialog? = null

    private fun showWorkerLogs(script: WorkerScript) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }

        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("加载中...")
            .setMessage("正在创建日志通道")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            val result = viewModel.createTail(account, script.id)
            loadingDialog.dismiss()

            when (result) {
                is Resource.Success -> {
                    WorkerLogsActivity.start(requireContext(), script.id, result.data.url)
                }
                is Resource.Error -> {
                    showToast("${result.message}")
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun showBuildTriggersDialog(script: WorkerScript) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }

        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("加载中...")
            .setMessage("正在获取触发器列表")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            val result = viewModel.fetchSchedules(account, script.id)
            loadingDialog.dismiss()

            when (result) {
                is Resource.Success -> {
                    showTriggersDialog(script, result.data)
                }
                is Resource.Error -> {
                    showToast("${result.message}")
                    showTriggersDialog(script, emptyList())
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun showTriggersDialog(script: WorkerScript, schedules: List<com.muort.upworker.core.model.Schedule>) {
        triggersDialog?.dismiss()

        val dialogView = layoutInflater.inflate(R.layout.dialog_build_triggers, null)
        val addTriggerBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.addTriggerBtn)
        val closeBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeBtn)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.triggersRecyclerView)

        val adapter = BuildTriggersAdapter(
            schedules = schedules,
            formatDate = { formatDate(it) },
            onDeleteClick = { schedule ->
                showDeleteTriggerConfirmDialog(script, schedule)
            }
        )
        recyclerView.apply {
            this.adapter = adapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        }

        triggersDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        addTriggerBtn.setOnClickListener {
            showAddTriggerDialog(script)
        }

        closeBtn.setOnClickListener {
            triggersDialog?.dismiss()
            triggersDialog = null
        }

        triggersDialog?.show()
    }

    private fun showAddTriggerDialog(script: WorkerScript) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_trigger, null)
        val cronEditText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.cronEditText)
        val saveBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.saveBtn)
        val cancelBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.cancelBtn)

        val cronItems = listOf(
            dialogView.findViewById<android.view.View>(R.id.cronItem1) to "*/5 * * * *",
            dialogView.findViewById<android.view.View>(R.id.cronItem2) to "0 * * * *",
            dialogView.findViewById<android.view.View>(R.id.cronItem3) to "0 0 * * *",
            dialogView.findViewById<android.view.View>(R.id.cronItem4) to "0 0 * * 1",
            dialogView.findViewById<android.view.View>(R.id.cronItem5) to "0 12 * * *",
            dialogView.findViewById<android.view.View>(R.id.cronItem6) to "0 8,18 * * *",
            dialogView.findViewById<android.view.View>(R.id.cronItem7) to "0 0 1 * *",
            dialogView.findViewById<android.view.View>(R.id.cronItem8) to "0 0 * * 0",
            dialogView.findViewById<android.view.View>(R.id.cronItem9) to "*/30 * * * *",
            dialogView.findViewById<android.view.View>(R.id.cronItem10) to "0 0 15 * *"
        )

        cronItems.forEach { (view, cron) ->
            view.setOnClickListener {
                cronEditText.setText(cron)
            }
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        saveBtn.setOnClickListener {
            val cron = cronEditText.text?.toString()?.trim() ?: ""

            if (cron.isEmpty()) {
                showToast("请输入Cron表达式")
                return@setOnClickListener
            }

            val account = accountViewModel.defaultAccount.value
            if (account != null) {
                lifecycleScope.launch {
                    val fetchResult = viewModel.fetchSchedules(account, script.id)
                    val currentCronList = if (fetchResult is Resource.Success) {
                        fetchResult.data.map { it.cron }
                    } else {
                        emptyList()
                    }
                    
                    val newCronList = currentCronList + cron
                    val result = viewModel.updateSchedules(account, script.id, newCronList)
                    
                    when (result) {
                        is Resource.Success -> {
                            showToast("创建成功")
                            dialog.dismiss()
                            triggersDialog?.dismiss()
                            showBuildTriggersDialog(script)
                        }
                        is Resource.Error -> {
                            showToast("${result.message}")
                        }
                        is Resource.Loading -> {}
                    }
                }
            }
        }

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDeleteTriggerConfirmDialog(script: WorkerScript, schedule: com.muort.upworker.core.model.Schedule) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除触发器")
            .setMessage("确定要删除触发器 \"${schedule.cron}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                val account = accountViewModel.defaultAccount.value
                if (account != null) {
                    lifecycleScope.launch {
                        val fetchResult = viewModel.fetchSchedules(account, script.id)
                        if (fetchResult is Resource.Success) {
                            val currentCronList = fetchResult.data.map { it.cron }.toMutableList()
                            currentCronList.remove(schedule.cron)
                            
                            val result = viewModel.updateSchedules(account, script.id, currentCronList)
                            when (result) {
                                is Resource.Success -> {
                                    showToast("删除成功")
                                    triggersDialog?.dismiss()
                                    showBuildTriggersDialog(script)
                                }
                                is Resource.Error -> {
                                    showToast("${result.message}")
                                }
                                is Resource.Loading -> {}
                            }
                        } else {
                            showToast("获取触发器列表失败")
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showVersionDetailDialog(script: WorkerScript, version: WorkerVersion, isRunning: Boolean) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }

        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("加载中...")
            .setMessage("正在获取详情信息")
            .setCancelable(false)
            .create()
        loadingDialog.show()

        lifecycleScope.launch {
            val accountInfoResult = accountRepository.fetchAccountsFromApi(account)

            val accountName = when (accountInfoResult) {
                is Resource.Success -> {
                    accountInfoResult.data.firstOrNull { it.id == account.accountId }?.name ?: account.name
                }
                else -> account.name
            }

            val emailMatch = Regex("([^@]+)@").find(accountName)
            val emailPrefix = (emailMatch?.groupValues?.get(1) ?: account.name).lowercase()

            // 并行获取部署记录
            val deploymentsResult = viewModel.listWorkerDeployments(account, script.id)

            loadingDialog.dismiss()

            val dialogView = layoutInflater.inflate(R.layout.dialog_worker_version_detail, null)
            val titleText = dialogView.findViewById<android.widget.TextView>(R.id.titleText)
            val versionNumberText = dialogView.findViewById<android.widget.TextView>(R.id.versionNumberText)
            val versionIdText = dialogView.findViewById<android.widget.TextView>(R.id.versionIdText)
            val createTimeText = dialogView.findViewById<android.widget.TextView>(R.id.createTimeText)
            val sourceText = dialogView.findViewById<android.widget.TextView>(R.id.sourceText)
            val urlText = dialogView.findViewById<android.widget.TextView>(R.id.urlText)
            val authorText = dialogView.findViewById<android.widget.TextView>(R.id.authorText)
            val statusBadge = dialogView.findViewById<android.widget.LinearLayout>(R.id.statusBadge)
            val authorIdText = dialogView.findViewById<android.widget.TextView>(R.id.authorIdText)
            val hasPreviewText = dialogView.findViewById<android.widget.TextView>(R.id.hasPreviewText)
            val deploymentMessageText = dialogView.findViewById<android.widget.TextView>(R.id.deploymentMessageText)
            val triggeredByText = dialogView.findViewById<android.widget.TextView>(R.id.triggeredByText)
            val deploymentInfoSection = dialogView.findViewById<android.widget.LinearLayout>(R.id.deploymentInfoSection)
            val deploymentIdText = dialogView.findViewById<android.widget.TextView>(R.id.deploymentIdText)
            val deploymentCreatedText = dialogView.findViewById<android.widget.TextView>(R.id.deploymentCreatedText)
            val deploymentSourceText = dialogView.findViewById<android.widget.TextView>(R.id.deploymentSourceText)
            val deploymentStrategyText = dialogView.findViewById<android.widget.TextView>(R.id.deploymentStrategyText)
            val deploymentPercentageText = dialogView.findViewById<android.widget.TextView>(R.id.deploymentPercentageText)
            val deploymentAuthorText = dialogView.findViewById<android.widget.TextView>(R.id.deploymentAuthorText)
            val deleteBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.deleteBtn)
            val accessBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.accessBtn)
            val closeBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeBtn)

            titleText.text = "${script.id} - 版本详情"
            versionNumberText.text = "#${version.number}"
            versionIdText.text = version.id
            createTimeText.text = formatDate(version.metadata?.createdOn)
            sourceText.text = version.metadata?.source ?: "未知"
            urlText.text = "https://${script.id}.${emailPrefix}.workers.dev"
            authorText.text = version.metadata?.authorEmail ?: "未知"
            authorIdText.text = version.metadata?.authorId ?: "未知"
            hasPreviewText.text = version.metadata?.hasPreview?.let { if (it) "是" else "否" } ?: "未知"

            // 版本注解（workers/message, workers/triggered_by）
            val versionAnnotations = version.annotations
            deploymentMessageText.text = versionAnnotations?.get("workers/message") ?: "无"
            triggeredByText.text = versionAnnotations?.get("workers/triggered_by") ?: "无"

            // 查找包含当前版本的部署
            var matchedDeployment: com.muort.upworker.core.model.WorkerDeployment? = null
            var versionPercentage: Int? = null
            if (deploymentsResult is Resource.Success) {
                val deployments = deploymentsResult.data
                for (deployment in deployments) {
                    val versionInfo = deployment.versions?.find { it.versionId == version.id }
                    if (versionInfo != null) {
                        matchedDeployment = deployment
                        versionPercentage = versionInfo.percentage
                        break
                    }
                }
            }

            // 填充部署信息
            if (matchedDeployment != null) {
                deploymentInfoSection.visibility = android.view.View.VISIBLE
                deploymentIdText.text = matchedDeployment.id
                deploymentCreatedText.text = formatDate(matchedDeployment.createdOn)
                deploymentSourceText.text = matchedDeployment.source ?: "未知"
                deploymentStrategyText.text = matchedDeployment.strategy ?: "未知"
                deploymentPercentageText.text = versionPercentage?.let { "$it%" } ?: "未知"
                deploymentAuthorText.text = matchedDeployment.authorEmail ?: "未知"

                // 部署注解覆盖版本注解（如果存在）
                matchedDeployment.annotations?.get("workers/message")?.let {
                    deploymentMessageText.text = it
                }
                matchedDeployment.annotations?.get("workers/triggered_by")?.let {
                    triggeredByText.text = it
                }
            } else {
                deploymentInfoSection.visibility = android.view.View.GONE
            }

            if (isRunning) {
                val statusIcon = android.widget.ImageView(requireContext()).apply {
                    setImageResource(R.drawable.ic_running)
                    layoutParams = android.widget.LinearLayout.LayoutParams(14, 14)
                }
                val statusText = android.widget.TextView(requireContext()).apply {
                    text = "运行中"
                    textSize = 11f
                    setTextColor(resources.getColor(R.color.red_500, requireContext().theme))
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = 4 }
                }
                statusBadge.addView(statusIcon)
                statusBadge.addView(statusText)
            } else {
                statusBadge.visibility = android.view.View.GONE
            }

            accessBtn.visibility = if (isRunning) android.view.View.VISIBLE else android.view.View.GONE
            deleteBtn.visibility = android.view.View.VISIBLE

            accessBtn.setOnClickListener {
                val url = "https://${script.id}.${emailPrefix}.workers.dev"
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                requireContext().startActivity(intent)
            }

            deleteBtn.setOnClickListener {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("删除版本")
                    .setMessage("确定要删除版本 #${version.number} 吗？")
                    .setPositiveButton("删除") { _, _ ->
                        lifecycleScope.launch {
                            val result = viewModel.deleteWorkerVersion(account, script.id, version.id)
                            when (result) {
                                is Resource.Success -> {
                                    showToast("删除成功")
                                    showScriptHistoryDialog(script)
                                }
                                is Resource.Error -> {
                                    showToast("${result.message}")
                                }
                                is Resource.Loading -> {}
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }

            val dialog = MaterialAlertDialogBuilder(requireContext())
                .setView(dialogView)
                .create()

            closeBtn.setOnClickListener {
                dialog.dismiss()
            }

            dialog.show()
        }
    }
    
    private fun editScript(script: WorkerScript) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        val action = WorkerFragmentDirections.actionWorkerToScriptEditor(
            accountEmail = account.accountId,
            scriptName = script.id
        )
        findNavController().navigate(action)
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
    
    private fun formatSize(size: Long): String {
        return when {
            size < 1024 -> "${size}B"
            size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
            size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
            else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
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
    
    // ==================== Batch Delete Functions ====================
    
    private fun toggleSelectionMode() {
        isSelectionMode = !isSelectionMode
        selectedScripts.clear()
        scriptsAdapter.setSelectionMode(isSelectionMode)
        updateSelectionUI()
    }
    
    private fun selectAllScripts() {
        scriptsAdapter.getAllScripts().forEach { script ->
            selectedScripts.add(script.id)
        }
        scriptsAdapter.selectAll()
        updateSelectionUI()
    }
    
    private fun updateSelectionUI() {
        val selectionActionsLayout = binding.root.findViewById<android.widget.LinearLayout>(
            resources.getIdentifier("selectionActionsLayout", "id", requireContext().packageName)
        )
        
        val toggleSelectionBtn = binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("toggleSelectionModeBtn", "id", requireContext().packageName)
        )
        
        val selectionStatusText = binding.root.findViewById<android.widget.TextView>(
            resources.getIdentifier("selectionStatusText", "id", requireContext().packageName)
        )
        
        val batchDeleteBtn = binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("batchDeleteBtn", "id", requireContext().packageName)
        )
        
        toggleSelectionBtn?.text = if (isSelectionMode) "取消" else "管理脚本"
        selectionActionsLayout?.visibility = if (isSelectionMode) android.view.View.VISIBLE else android.view.View.GONE
        selectionStatusText?.text = "已选择 ${selectedScripts.size} 个脚本"
        batchDeleteBtn?.isEnabled = selectedScripts.isNotEmpty()
    }
    
    private fun showBatchDeleteConfirmDialog() {
        val message = if (selectedScripts.size == 1) {
            "确定要删除 1 个脚本吗？\n\n${selectedScripts.first()}\n\n此操作无法撤销。"
        } else {
            "确定要删除 ${selectedScripts.size} 个脚本吗？此操作无法撤销。"
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("批量删除脚本")
            .setMessage(message)
            .setPositiveButton("删除") { _, _ ->
                performBatchDelete()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun performBatchDelete() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        val scriptsToDelete = selectedScripts.toList()
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除中...")
            .setMessage("正在删除 ${scriptsToDelete.size} 个脚本")
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        var deletedCount = 0
        var failedCount = 0
        
        lifecycleScope.launch {
            scriptsToDelete.forEach { scriptName ->
                try {
                    viewModel.deleteWorkerScript(account, scriptName)
                    deletedCount++
                } catch (e: Exception) {
                    failedCount++
                    Timber.e(e, "Failed to delete script: $scriptName")
                }
            }
            
            progressDialog.dismiss()
            
            selectedScripts.clear()
            isSelectionMode = false
            scriptsAdapter.setSelectionMode(false)
            updateSelectionUI()
            
            val message = if (failedCount == 0) {
                "成功删除 $deletedCount 个脚本"
            } else {
                "删除了 $deletedCount 个脚本，$failedCount 个失败"
            }
            showToast(message)
            
            // 刷新列表
            loadScripts()
        }
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
                                // 上传成功后删除临时文件
                                selectedFile?.let { file ->
                                    if (file.exists()) {
                                        file.delete()
                                    }
                                }
                                selectedFile = null
                                viewModel.resetUploadState()
                            }
                            is UploadState.Error -> {
                                binding.uploadProgress.visibility = View.GONE
                                binding.uploadBtn.isEnabled = true
                                // 上传失败后也删除临时文件
                                selectedFile?.let { file ->
                                    if (file.exists()) {
                                        file.delete()
                                    }
                                }
                                selectedFile = null
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
    
    private fun showCleanupVersionsDialog() {
        val scripts = viewModel.scripts.value
        
        if (scripts.isEmpty()) {
            showToast("暂无脚本")
            return
        }
        
        val dialogBinding = com.muort.upworker.databinding.DialogCleanupDeploymentsBinding.inflate(layoutInflater)
        
        val scriptNames = scripts.map { it.id }
        val spinnerAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, scriptNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.projectSpinner.adapter = spinnerAdapter
        
        dialogBinding.cleanupModeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            dialogBinding.singleProjectContainer.visibility = 
                if (checkedId == com.muort.upworker.R.id.cleanupSingleProjectRadio) android.view.View.VISIBLE else android.view.View.GONE
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清理旧版本")
            .setView(dialogBinding.root)
            .setPositiveButton("开始清理") { _, _ ->
                val retainCount = dialogBinding.retainCountEdit.text.toString().trim().toIntOrNull() ?: 10
                
                if (dialogBinding.cleanupAllProjectsRadio.isChecked) {
                    showCleanupConfirmDialog(true, null, retainCount)
                } else {
                    val selectedScriptName = dialogBinding.projectSpinner.selectedItem?.toString()
                    if (selectedScriptName.isNullOrEmpty()) {
                        showToast("请选择脚本")
                        return@setPositiveButton
                    }
                    showCleanupConfirmDialog(false, selectedScriptName, retainCount)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showCleanupConfirmDialog(isAllScripts: Boolean, scriptName: String?, retainCount: Int) {
        val account = accountViewModel.defaultAccount.value ?: return
        
        val title = if (isAllScripts) "清理所有脚本的旧版本" else "清理脚本 \"$scriptName\" 的旧版本"
        val message = if (isAllScripts) {
            "将清理账号下所有 Worker 脚本的旧版本，每个脚本保留最新 $retainCount 个版本。\n\n此操作不可撤销，确定继续吗？"
        } else {
            "将清理脚本 \"$scriptName\" 的旧版本，保留最新 $retainCount 个版本。\n\n此操作不可撤销，确定继续吗？"
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定清理") { dialog, _ ->
                dialog.dismiss()
                
                val loadingDialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle("正在清理")
                    .setMessage("正在清理旧版本，请稍候...")
                    .setCancelable(false)
                    .show()
                
                if (isAllScripts) {
                    viewModel.cleanupVersionsForAllScripts(account, retainCount)
                } else {
                    scriptName?.let {
                        viewModel.cleanupVersionsForSingleScript(account, it, retainCount)
                    }
                }
                
                viewLifecycleOwner.lifecycleScope.launch {
                    viewModel.loadingState.dropWhile { !it }.first { !it }
                    loadingDialog.dismiss()
                    val results = viewModel.cleanupResults.value
                    if (results.isNotEmpty()) {
                        showCleanupResultsDialog(results)
                        viewModel.clearCleanupResults()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showCleanupResultsDialog(results: List<WorkerCleanupResult>) {
        val totalDeleted = results.sumOf { it.deletedCount }
        val totalScripts = results.size
        
        val resultBuilder = StringBuilder()
        resultBuilder.append("清理结果：\n\n")
        
        results.forEach { result ->
            if (result.success) {
                val status = if (result.deletedCount > 0) {
                    "成功清理 ${result.deletedCount} 个旧版本"
                } else {
                    "无需清理（当前 ${result.totalVersions} 个版本 ≤ 保留数量）"
                }
                resultBuilder.append("• ${result.scriptName}: $status\n")
            } else {
                resultBuilder.append("• ${result.scriptName}: 失败 - ${result.errorMessage}\n")
            }
        }
        
        resultBuilder.append("\n总计：处理 $totalScripts 个脚本，成功清理 $totalDeleted 个旧版本")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清理完成")
            .setMessage(resultBuilder.toString())
            .setPositiveButton("关闭", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class WorkerScriptsAdapter(
    private val scriptSizeCache: Map<String, Long>,
    private val onDeleteClick: (WorkerScript) -> Unit,
    private val onHistoryClick: (WorkerScript) -> Unit,
    private val onEditClick: (WorkerScript) -> Unit,
    private val onTriggerClick: (WorkerScript) -> Unit,
    private val onLogsClick: (WorkerScript) -> Unit,
    private val onConfigKvClick: (WorkerScript) -> Unit,
    private val onConfigR2Click: (WorkerScript) -> Unit,
    private val onConfigD1Click: (WorkerScript) -> Unit,
    private val onConfigVariablesClick: (WorkerScript) -> Unit,
    private val onConfigSecretsClick: (WorkerScript) -> Unit,
    private val onSelectionModeClick: (WorkerScript, Boolean) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<WorkerScriptsAdapter.ScriptViewHolder>() {
    
    private var scripts = listOf<WorkerScript>()
    private var selectionMode = false
    private val selectedItems = mutableSetOf<String>()
    
    fun submitList(newScripts: List<WorkerScript>) {
        scripts = newScripts
        notifyDataSetChanged()
    }
    
    fun setSelectionMode(enabled: Boolean) {
        selectionMode = enabled
        selectedItems.clear()
        notifyDataSetChanged()
    }
    
    fun getAllScripts(): List<WorkerScript> = scripts
    
    fun selectAll() {
        selectedItems.clear()
        scripts.forEach { selectedItems.add(it.id) }
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
            
            // 添加多选模式支持 - 通过改变卡片背景色表示选中状态
            if (selectionMode) {
                binding.deleteBtn.visibility = android.view.View.GONE
                binding.historyBtn.visibility = android.view.View.GONE
                binding.editBtn.visibility = android.view.View.GONE
                binding.triggerBtn.visibility = android.view.View.GONE
                binding.logsBtn.visibility = android.view.View.GONE
                
                val isSelected = selectedItems.contains(script.id)
                updateSelectionUI(binding.root, isSelected)
                
                binding.root.setOnClickListener {
                    val newSelected = !selectedItems.contains(script.id)
                    if (newSelected) {
                        selectedItems.add(script.id)
                    } else {
                        selectedItems.remove(script.id)
                    }
                    updateSelectionUI(binding.root, newSelected)
                    onSelectionModeClick(script, newSelected)
                }
            } else {
                binding.deleteBtn.visibility = android.view.View.VISIBLE
                binding.historyBtn.visibility = android.view.View.VISIBLE
                binding.editBtn.visibility = android.view.View.VISIBLE
                binding.triggerBtn.visibility = android.view.View.VISIBLE
                binding.logsBtn.visibility = android.view.View.VISIBLE
                updateSelectionUI(binding.root, false)
                binding.root.setOnClickListener(null)
            }
            
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
            
            binding.logsBtn.setOnClickListener {
                onLogsClick(script)
            }
            
            binding.triggerBtn.setOnClickListener {
                onTriggerClick(script)
            }
            
            binding.historyBtn.setOnClickListener {
                onHistoryClick(script)
            }
            
            binding.editBtn.setOnClickListener {
                onEditClick(script)
            }
            
            binding.deleteBtn.setOnClickListener {
                onDeleteClick(script)
            }
        }
        
        private fun updateSelectionUI(view: android.view.View, isSelected: Boolean) {
            if (isSelected) {
                view.setAlpha(0.8f)
                val color = view.context.getColor(android.R.color.darker_gray)
                view.setBackgroundColor(color)
            } else {
                view.setAlpha(1.0f)
                view.setBackgroundColor(android.R.color.transparent)
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
