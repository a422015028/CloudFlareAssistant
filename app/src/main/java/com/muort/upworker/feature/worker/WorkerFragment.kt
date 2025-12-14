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
import com.muort.upworker.core.model.WorkerScript
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.FragmentWorkerBinding
import com.muort.upworker.databinding.ItemWorkerScriptBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

@AndroidEntryPoint
class WorkerFragment : Fragment() {
    
    private var _binding: FragmentWorkerBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: WorkerViewModel by viewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    
    private var selectedFile: File? = null
    private lateinit var scriptsAdapter: WorkerScriptsAdapter
    private var scriptViewDialog: AlertDialog? = null
    
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
        
        viewModel.uploadWorkerScript(account, workerName, file)
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
    private val onDeleteClick: (WorkerScript) -> Unit
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
