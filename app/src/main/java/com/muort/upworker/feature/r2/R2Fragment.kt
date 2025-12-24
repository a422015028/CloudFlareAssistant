package com.muort.upworker.feature.r2

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import android.widget.Toast
import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.R2Bucket
import com.muort.upworker.core.model.R2CustomDomain
import com.muort.upworker.core.model.R2Object
import com.muort.upworker.databinding.DialogR2InputBinding
import com.muort.upworker.databinding.FragmentR2Binding
import com.muort.upworker.databinding.ItemR2BucketBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class R2Fragment : Fragment() {
    
    private var _binding: FragmentR2Binding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val r2ViewModel: R2ViewModel by viewModels()
    
    private lateinit var bucketAdapter: BucketAdapter
    private lateinit var objectAdapter: ObjectAdapter
    private var currentBucket: R2Bucket? = null
    private var downloadData: ByteArray? = null
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                uploadFile(uri)
            }
        }
    }
    
    private val fileSaverLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                saveFile(uri)
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentR2Binding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAdapter()
        setupObjectAdapter()
        setupClickListeners()
        observeViewModel()
        // 初始对象标题
        binding.objectTitleText.text = "对象"
        accountViewModel.defaultAccount.value?.let { account ->
            r2ViewModel.loadBuckets(account)
        }
    }
    
    private fun setupAdapter() {
        bucketAdapter = BucketAdapter(
            onBucketClick = { bucket ->
                r2ViewModel.selectBucket(bucket)
                accountViewModel.defaultAccount.value?.let { account ->
                    r2ViewModel.loadObjects(account, bucket.name)
                    r2ViewModel.loadCustomDomains(account, bucket.name)
                }
                // 设置右侧对象标题为当前存储桶名
                binding.objectTitleText.text = "对象（${bucket.name}）"
                // 清空对象列表等待新数据
                objectAdapter.submitList(emptyList())
            },
            onDeleteClick = { bucket ->
                showDeleteBucketDialog(bucket)
            },
            onManageDomainsClick = { bucket ->
                showCustomDomainsDialog(bucket)
            }
        )
        binding.bucketRecyclerView.adapter = bucketAdapter
    }

    private fun setupObjectAdapter() {
        objectAdapter = ObjectAdapter()
        objectAdapter.setOnObjectClickListener { obj ->
            val bucket = r2ViewModel.selectedBucket.value
            val account = accountViewModel.defaultAccount.value
            if (bucket != null && account != null) {
                showObjectDetailsDialog(account, bucket, obj, r2ViewModel.customDomains.value)
            }
        }
        binding.objectRecyclerView.adapter = objectAdapter
    }
    
    private fun setupClickListeners() {
        binding.fabAddBucket.setOnClickListener {
            showAddBucketDialog()
        }
        binding.fabAddObject.setOnClickListener {
            // 仅允许在选中存储桶时上传
            val bucket = r2ViewModel.selectedBucket.value
            if (bucket != null) {
                selectFileToUpload(bucket)
            } else {
                Snackbar.make(binding.root, "请先选择存储桶", Snackbar.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    r2ViewModel.buckets.collect { buckets ->
                        bucketAdapter.submitList(buckets)
                        binding.emptyText.visibility = 
                            if (buckets.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    r2ViewModel.objects.collect { objects ->
                        objectAdapter.submitList(objects)
                        binding.objectEmptyText.visibility =
                            if (objects.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    r2ViewModel.selectedBucket.collect { bucket ->
                        // 选中存储桶时显示fabAddObject，否则隐藏
                        binding.fabAddObject.visibility = if (bucket != null) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    r2ViewModel.loadingState.collect { isLoading ->
                        binding.progressBar.visibility = 
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    r2ViewModel.message.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }
                
                launch {
                    accountViewModel.defaultAccount.collect { account ->
                        if (account != null) {
                            r2ViewModel.loadBuckets(account)
                        }
                    }
                }
            }
        }
    }
    
    private fun showAddBucketDialog() {
        val dialogBinding = DialogR2InputBinding.inflate(layoutInflater)
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("创建") { _, _ ->
                val name = dialogBinding.bucketName.text.toString()
                val location = dialogBinding.bucketLocation.text.toString()
                    .takeIf { it.isNotBlank() }
                accountViewModel.defaultAccount.value?.let { account ->
                    r2ViewModel.createBucket(account, name, location)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteBucketDialog(bucket: R2Bucket) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除存储桶")
            .setMessage("确定要删除存储桶 \"${bucket.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    r2ViewModel.deleteBucket(account, bucket.name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showObjectsDialog(bucket: R2Bucket) {
        accountViewModel.defaultAccount.value?.let { account ->
            // Show loading dialog first
            val loadingDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("${bucket.name}")
                .setMessage("加载中...")
                .setCancelable(true)
                .create()
            loadingDialog.show()
            
            // Load and wait for completion
            viewLifecycleOwner.lifecycleScope.launch {
                // Start loading objects and custom domains
                r2ViewModel.loadObjects(account, bucket.name)
                r2ViewModel.loadCustomDomains(account, bucket.name)
                
                // Wait for loading to start
                r2ViewModel.loadingState.first { it }
                // Wait for loading to complete
                r2ViewModel.loadingState.first { !it }
                
                loadingDialog.dismiss()
                
                // Show the objects list dialog
                showObjectsListDialog(account, bucket)
            }
        } ?: showToast("账号信息不可用")
    }
    
    private fun showObjectsListDialog(account: Account, bucket: R2Bucket) {
            
        val objects = r2ViewModel.objects.value
        val customDomains = r2ViewModel.customDomains.value
        
        val items = if (objects.isEmpty()) {
            arrayOf("暂无对象", "上传文件")
        } else {
            objects.map { obj ->
                val size = formatFileSize(obj.size ?: 0)
                "${obj.key} ($size)"
            }.toTypedArray() + "上传文件"
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${bucket.name} - 对象列表")
            .setItems(items) { _, which ->
                if (objects.isEmpty()) {
                    if (which == 1) {
                        selectFileToUpload(bucket)
                    }
                } else {
                    if (which < objects.size) {
                        showObjectDetailsDialog(account, bucket, objects[which], customDomains)
                    } else {
                        selectFileToUpload(bucket)
                    }
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun showObjectDetailsDialog(account: Account, bucket: R2Bucket, obj: R2Object, customDomains: List<R2CustomDomain>) {
        // R2 public URL format: https://pub-<hash>.r2.dev/<object-key>
        // Note: The actual URL format depends on whether public access is enabled for the bucket
        // For now we show the standard format, but it requires the bucket to have public access configured
        val accountHash = account.accountId.take(16) // Use first 16 chars of account ID as approximation
        val defaultUrl = "https://pub-${accountHash}.r2.dev/${bucket.name}/${obj.key}"
        
        val customUrl = customDomains.firstOrNull()?.let { domain ->
            "https://${domain.domain}/${obj.key}"
        }
        
        timber.log.Timber.d("Showing object details: customDomains size=${customDomains.size}, customUrl=$customUrl")
        
        val options = mutableListOf<String>()
        if (customUrl != null) {
            options.add("复制自定义域 URL")
            options.add("复制默认 URL")
        } else {
            options.add("复制 URL")
        }
        options.add("下载")
        options.add("删除")
        
        timber.log.Timber.d("Dialog options: ${options.joinToString()}")
        
        val title = buildString {
            append(obj.key)
            append("\n")
            append("大小: ${formatFileSize(obj.size ?: 0)}")
        }
        
        val message = buildString {
            if (customUrl != null) {
                append("自定义域 URL:\n$customUrl")
                append("\n\n默认 URL:\n$defaultUrl")
            } else {
                append("URL:\n$defaultUrl")
                append("\n\n注意: 需要为存储桶配置公开访问才能使用此 URL")
            }
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("返回", null)
            .setNeutralButton(if (customUrl != null) "复制自定义域 URL" else "复制 URL") { _, _ ->
                copyToClipboard(customUrl ?: defaultUrl, if (customUrl != null) "自定义域 URL 已复制" else "URL 已复制")
            }
            .setNegativeButton("更多") { _, _ ->
                // Show more options
                showObjectActionsDialog(account, bucket, obj, customUrl, defaultUrl)
            }
            .show()
    }
    
    private fun showObjectActionsDialog(account: Account, bucket: R2Bucket, obj: R2Object, customUrl: String?, defaultUrl: String) {
        val options = mutableListOf<String>()
        if (customUrl != null) {
            options.add("复制自定义域 URL")
            options.add("复制默认 URL")
        } else {
            options.add("复制 URL")
        }
        options.add("删除")

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("操作")
            .setItems(options.toTypedArray()) { _, which ->
                var index = 0
                when {
                    customUrl != null && which == index++ -> {
                        copyToClipboard(customUrl, "自定义域 URL 已复制")
                    }
                    which == index++ -> {
                        copyToClipboard(if (customUrl != null) defaultUrl else defaultUrl, if (customUrl != null) "默认 URL 已复制" else "URL 已复制")
                    }
                    which == index -> {
                        showDeleteObjectDialog(bucket, obj)
                    }
                }
            }
            .setNegativeButton("返回") { _, _ ->
                // 返回对象详情弹窗
                showObjectDetailsDialog(account, bucket, obj, r2ViewModel.customDomains.value)
            }
            .show()
    }
    
    private fun copyToClipboard(text: String, message: String) {
        val clipboard = requireContext().getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("R2 URL", text)
        clipboard.setPrimaryClip(clip)
        showToast(message)
    }
    
    private fun showDeleteObjectDialog(bucket: R2Bucket, obj: R2Object) {
        accountViewModel.defaultAccount.value?.let { account ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("删除对象")
                .setMessage("确定要删除对象 ${obj.key} 吗？")
                .setPositiveButton("删除") { _, _ ->
                    r2ViewModel.deleteObject(account, bucket.name, obj.key)
                    // Note: After deletion, the list will be refreshed automatically
                    // and the dialog will be dismissed
                }
                .setNegativeButton("取消") { _, _ ->
                    showObjectsListDialog(account, bucket)
                }
                .show()
        }
    }
    
    private fun selectFileToUpload(bucket: R2Bucket) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        currentBucket = bucket
        filePickerLauncher.launch(intent)
    }
    
    private fun downloadObject(bucket: R2Bucket, obj: R2Object) {
        accountViewModel.defaultAccount.value?.let { account ->
            r2ViewModel.downloadObject(account, bucket.name, obj.key) { data ->
                if (data != null) {
                    // Save to downloads folder
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        type = "*/*"
                        putExtra(Intent.EXTRA_TITLE, obj.key.substringAfterLast('/'))
                    }
                    downloadData = data
                    fileSaverLauncher.launch(intent)
                }
            }
        }
    }
    
    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            size < 1024 * 1024 * 1024 -> "${size / (1024 * 1024)} MB"
            else -> "${size / (1024 * 1024 * 1024)} GB"
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    
    private fun uploadFile(uri: Uri) {
        val bucket = currentBucket ?: return
        val account = accountViewModel.defaultAccount.value ?: return
        
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Snackbar.make(binding.root, "无法读取文件", Snackbar.LENGTH_SHORT).show()
                return
            }
            
            // Get safe filename - extract just the filename from path
            val originalName = uri.lastPathSegment ?: "upload"
            // Extract filename from path (handle both / and \ separators)
            val fileName = originalName.substringAfterLast('/').substringAfterLast('\\')
            val safeFileName = fileName
                .replace(":", "_")
                .replace("*", "_")
                .replace("?", "_")
                .replace("\"", "_")
                .replace("<", "_")
                .replace(">", "_")
                .replace("|", "_")
                .takeIf { it.isNotBlank() } ?: "upload"
            
            val file = java.io.File(requireContext().cacheDir, "upload_${System.currentTimeMillis()}_$safeFileName")
            inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            r2ViewModel.uploadObject(account, bucket.name, safeFileName, file)
            
            // Clean up temp file after upload
            file.deleteOnExit()
            
        } catch (e: Exception) {
            Snackbar.make(binding.root, "文件读取失败: ${e.message}", Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun saveFile(uri: Uri) {
        val data = downloadData ?: return
        
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                output.write(data)
            }
            Snackbar.make(binding.root, "File saved successfully", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "Failed to save file: ${e.message}", Snackbar.LENGTH_SHORT).show()
        } finally {
            downloadData = null
        }
    }
    
    // ==================== Custom Domains ====================
    
    private fun showCustomDomainsDialog(bucket: R2Bucket) {
        accountViewModel.defaultAccount.value?.let { account ->
            // Show loading dialog first
            val bucketName = bucket.name
            val loadingDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("自定义域 - $bucketName")
                .setMessage("加载中...")
                .setCancelable(true)
                .create()
            loadingDialog.show()
            
            // Load and wait for completion
            viewLifecycleOwner.lifecycleScope.launch {
                // Start loading
                r2ViewModel.loadCustomDomains(account, bucket.name)
                
                // Wait for loading to start (loading = true)
                r2ViewModel.loadingState.first { it }
                // Then wait for loading to complete (loading = false)
                r2ViewModel.loadingState.first { !it }
                
                loadingDialog.dismiss()
                
                val domains = r2ViewModel.customDomains.value
                
                val items = if (domains.isEmpty()) {
                    arrayOf("暂无自定义域")
                } else {
                    domains.map { domain ->
                        "${domain.domain} (${domain.statusText})"
                    }.toTypedArray()
                }
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("自定义域 - $bucketName")
                    .setItems(items) { _, which ->
                        if (!domains.isEmpty() && which < domains.size) {
                            showDeleteCustomDomainDialog(account, bucket, domains[which])
                        }
                    }
                    .setPositiveButton("添加域名") { _, _ ->
                        showAddCustomDomainDialog(account, bucket)
                    }
                    .setNegativeButton("返回", null)
                    .show()
            }
        }
    }
    
    private fun showAddCustomDomainDialog(account: Account, bucket: R2Bucket) {
        if (account.zoneId.isNullOrEmpty()) {
            showToast("该账号未配置 Zone ID，无法添加自定义域")
            showCustomDomainsDialog(bucket)
            return
        }
        
        val input = android.widget.EditText(requireContext()).apply {
            hint = "example.com"
            inputType = android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        
        val container = android.widget.FrameLayout(requireContext()).apply {
            val params = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
            )
            params.leftMargin = resources.getDimensionPixelSize(com.google.android.material.R.dimen.design_bottom_sheet_peek_height_min) / 2
            params.rightMargin = params.leftMargin
            addView(input, params)
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加自定义域")
            .setMessage("输入要绑定的域名：")
            .setView(container)
            .setPositiveButton("添加") { _, _ ->
                val domain = input.text.toString().trim()
                if (domain.isNotEmpty()) {
                    r2ViewModel.createCustomDomain(account, bucket.name, domain)
                    showCustomDomainsDialog(bucket)
                } else {
                    showToast("请输入域名")
                    showCustomDomainsDialog(bucket)
                }
            }
            .setNegativeButton("返回") { _, _ ->
                showCustomDomainsDialog(bucket)
            }
            .show()
    }
    
    private fun showDeleteCustomDomainDialog(account: Account, bucket: R2Bucket, domain: R2CustomDomain) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除自定义域")
            .setMessage("确定要删除域名 \"${domain.domain}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                r2ViewModel.deleteCustomDomain(account, bucket.name, domain.domain)
                showCustomDomainsDialog(bucket)
            }
            .setNegativeButton("返回") { _, _ ->
                showCustomDomainsDialog(bucket)
            }
            .show()
    }
    

    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private class BucketAdapter(
        private val onBucketClick: (R2Bucket) -> Unit,
        private val onDeleteClick: (R2Bucket) -> Unit,
        private val onManageDomainsClick: (R2Bucket) -> Unit
    ) : RecyclerView.Adapter<BucketAdapter.ViewHolder>() {
        
        private var buckets = listOf<R2Bucket>()
        
        fun submitList(newList: List<R2Bucket>) {
            buckets = newList
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemR2BucketBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(buckets[position])
        }
        
        override fun getItemCount() = buckets.size
        
        inner class ViewHolder(
            private val binding: ItemR2BucketBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(bucket: R2Bucket) {
                binding.bucketNameText.text = bucket.name
                binding.bucketLocationText.text = bucket.location?.let { "位置: $it" } ?: "默认位置"
                
                binding.root.setOnClickListener {
                    onBucketClick(bucket)
                }
                
                binding.bucketMenuButton.setOnClickListener { view ->
                    PopupMenu(view.context, view).apply {
                        inflate(R.menu.menu_r2_bucket)
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_manage_domains -> {
                                    onManageDomainsClick(bucket)
                                    true
                                }
                                R.id.action_delete -> {
                                    onDeleteClick(bucket)
                                    true
                                }
                                else -> false
                            }
                        }
                        show()
                    }
                }
            }
        }
    }
}
