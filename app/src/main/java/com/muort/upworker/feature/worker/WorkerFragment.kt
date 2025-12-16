package com.muort.upworker.feature.worker

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.muort.upworker.core.model.KvNamespace
import com.muort.upworker.core.model.WorkerScript
import com.muort.upworker.core.repository.KvRepository
import timber.log.Timber
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.DialogKvBindingBinding
import com.muort.upworker.databinding.FragmentWorkerBinding
import com.muort.upworker.databinding.ItemKvBindingBinding
import com.muort.upworker.databinding.ItemWorkerScriptBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class WorkerFragment : Fragment() {
    
    private var _binding: FragmentWorkerBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: WorkerViewModel by viewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    
    @Inject
    lateinit var kvRepository: KvRepository
    
    private var selectedFile: File? = null
    private lateinit var scriptsAdapter: WorkerScriptsAdapter
    private lateinit var kvBindingsAdapter: KvBindingsAdapter
    private var scriptViewDialog: AlertDialog? = null
    
    // Store KV bindings as (binding_name, namespace_id) pairs
    private val kvBindings = mutableListOf<Pair<String, String>>()
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val fileName = uri.lastPathSegment?.substringAfterLast("/") ?: "script.js"
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
            onViewClick = { script ->
                viewScriptContent(script)
            },
            onDeleteClick = { script ->
                showDeleteConfirmDialog(script)
            },
            onConfigKvClick = { script ->
                showConfigKvBindingsDialog(script)
            }
        )
        binding.scriptsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = scriptsAdapter
        }
        
        kvBindingsAdapter = KvBindingsAdapter(
            onDeleteClick = { position ->
                kvBindings.removeAt(position)
                updateKvBindingsUI()
            }
        )
        binding.kvBindingsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = kvBindingsAdapter
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
        
        binding.addKvBindingBtn.setOnClickListener {
            showAddKvBindingDialog()
        }
        
        binding.uploadBtn.setOnClickListener {
            uploadWorker()
        }
        
        binding.refreshBtn.setOnClickListener {
            loadScripts()
        }
        
        updateKvBindingsUI()
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
        
        // Upload with or without KV bindings
        if (kvBindings.isEmpty()) {
            timber.log.Timber.d("Uploading worker without KV bindings")
            viewModel.uploadWorkerScript(account, workerName, file)
        } else {
            timber.log.Timber.d("Uploading worker with ${kvBindings.size} KV bindings: $kvBindings")
            viewModel.uploadWorkerScriptWithKvBindings(account, workerName, file, kvBindings)
        }
    }
    
    private fun showAddKvBindingDialog() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        // Load KV namespaces
        viewLifecycleOwner.lifecycleScope.launch {
            val result = kvRepository.listNamespaces(account)
            
            if (result is com.muort.upworker.core.model.Resource.Success) {
                val namespaces = result.data
                
                if (namespaces.isEmpty()) {
                    showToast("暂无 KV 命名空间，请先创建")
                    return@launch
                }
                
                // Show dialog
                val dialogBinding = DialogKvBindingBinding.inflate(layoutInflater)
                
                // Setup spinner
                val namespaceNames = namespaces.map { "${it.title} (${it.id})" }
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
                            val binding = Pair(bindingName, namespace.id)
                            kvBindings.add(binding)
                            timber.log.Timber.d("Added KV binding: $bindingName -> ${namespace.id}, Total bindings: ${kvBindings.size}")
                            updateKvBindingsUI()
                            showToast("KV 绑定已添加: $bindingName")
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else if (result is com.muort.upworker.core.model.Resource.Error) {
                showToast("加载 KV 命名空间失败: ${result.message}")
            }
        }
    }
    
    private fun updateKvBindingsUI() {
        if (kvBindings.isEmpty()) {
            binding.noBindingsText.visibility = View.VISIBLE
            binding.kvBindingsRecyclerView.visibility = View.GONE
        } else {
            binding.noBindingsText.visibility = View.GONE
            binding.kvBindingsRecyclerView.visibility = View.VISIBLE
            kvBindingsAdapter.submitList(kvBindings)
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
                            tempKvBindings.add(Pair(binding.name, binding.namespaceId))
                            Timber.d("Loaded existing KV binding: ${binding.name} -> ${binding.namespaceId}")
                        }
                    }
                }
                
                // Setup adapter with lateinit reference
                lateinit var tempAdapter: KvBindingsAdapter
            tempAdapter = KvBindingsAdapter(
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
                        if (tempKvBindings.isEmpty()) {
                            showToast("请至少添加一个 KV 绑定")
                            return@setPositiveButton
                        }
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
                val namespaceNames = namespaces.map { "${it.title} (${it.id})" }
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
    
    private fun loadScripts() {
        val account = accountViewModel.defaultAccount.value
        if (account != null) {
            viewModel.loadWorkerScripts(account)
        }
    }
    
    private fun viewScriptContent(script: WorkerScript) {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("加载中...")
            .setMessage("正在获取脚本内容")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        viewModel.getWorkerScript(account, script.id) { content ->
            loadingDialog.dismiss()
            
            val scrollView = NestedScrollView(requireContext()).apply {
                setPadding(48, 24, 48, 24)
            }
            
            val editText = EditText(requireContext()).apply {
                setText(content)
                textSize = 12f
                typeface = Typeface.MONOSPACE
                inputType = InputType.TYPE_CLASS_TEXT or 
                           InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                           InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                isSingleLine = false
                setHorizontallyScrolling(false)
                minLines = 20
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
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
                }
                .setNegativeButton("关闭", null)
                .setNeutralButton("复制") { _, _ ->
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Worker Script", editText.text.toString())
                    clipboard.setPrimaryClip(clip)
                    showToast("已复制到剪贴板")
                }
                .create()
            
            scriptViewDialog?.show()
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
            viewModel.uploadWorkerScript(account, script.id, tempFile)
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
    
    override fun onDestroyView() {
        super.onDestroyView()
        scriptViewDialog?.dismiss()
        scriptViewDialog = null
        _binding = null
    }
}

class WorkerScriptsAdapter(
    private val onViewClick: (WorkerScript) -> Unit,
    private val onDeleteClick: (WorkerScript) -> Unit,
    private val onConfigKvClick: (WorkerScript) -> Unit
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
            binding.scriptSizeText.text = "创建于 $dateText"
            
            binding.configKvBtn.setOnClickListener {
                onConfigKvClick(script)
            }
            
            binding.viewBtn.setOnClickListener {
                onViewClick(script)
            }
            
            binding.deleteBtn.setOnClickListener {
                onDeleteClick(script)
            }
        }
        
        private fun formatDate(dateString: String?): String {
            if (dateString == null) return "未知日期"
            
            return try {
                val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
                val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                val date = inputFormat.parse(dateString)
                date?.let { outputFormat.format(it) } ?: dateString
            } catch (e: Exception) {
                dateString.substringBefore('T')
            }
        }
    }
}

class KvBindingsAdapter(
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
            binding.namespaceIdText.text = "Namespace ID: ${kvBinding.second}"
            
            binding.deleteBindingBtn.setOnClickListener {
                onDeleteClick(position)
            }
        }
    }
}
