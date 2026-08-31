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
import com.muort.upworker.core.repository.ZoneRepository
import com.muort.upworker.databinding.DialogR2InputBinding
import com.muort.upworker.databinding.DialogR2UploadBinding
import com.muort.upworker.databinding.FragmentR2Binding
import com.muort.upworker.databinding.ItemR2BucketBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.io.File

@AndroidEntryPoint
class R2Fragment : Fragment() {

        private lateinit var bucketAdapter: BucketAdapter
        private lateinit var objectAdapter: ObjectAdapter
    
    /**
     * 文件名缩短显示，保留后缀，超长用 ... 省略
     */
    private fun shortenFileName(name: String, maxLen: Int = 24): String {
        // 边界处理
        if (maxLen <= 3) return ".".repeat(maxLen)
        if (name.length <= maxLen) return name
        val ellipsis = "..."
        // 分离文件名和扩展名
        val dotIdx = name.lastIndexOf('.')
        val (fileNamePart, extension) = if (dotIdx > 0 && dotIdx < name.length - 1) {
            name.substring(0, dotIdx) to name.substring(dotIdx)
        } else {
            name to ""
        }
        return if (extension.isEmpty()) {
            // 没有扩展名
            val nameChars = maxOf(1, maxLen - ellipsis.length)
            "${fileNamePart.take(nameChars)}$ellipsis"
        } else {
            // 有扩展名
            // 尝试最优方案：显示尽可能多的文件名 + 完整扩展名
            val availableForName = maxLen - extension.length - ellipsis.length
            when {
                availableForName >= 1 ->
                    "${fileNamePart.take(availableForName)}$ellipsis$extension"
                availableForName == 0 ->
                    "$ellipsis$extension"
                else -> {
                    // 计算还能显示多少扩展名
                    val availableForExt = maxLen - ellipsis.length
                    when {
                        availableForExt >= extension.length ->
                            "$ellipsis$extension"  // 意外情况
                        availableForExt > 0 ->
                            "$ellipsis${extension.take(availableForExt)}"
                        else ->
                            ellipsis.take(maxLen)  // 保护
                    }
                }
            }
        }
    }
    private var _binding: FragmentR2Binding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val r2ViewModel: R2ViewModel by viewModels()

    @Inject
    lateinit var zoneRepository: ZoneRepository
    
    private var currentBucket: R2Bucket? = null
    private var currentDownloadObject: Pair<R2Bucket, R2Object>? = null  // 存储待下载的对象
    private var isLoadingCustomDomains = false
    private var pendingUploadUri: Uri? = null
    private var uploadDialogBinding: DialogR2UploadBinding? = null
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                pendingUploadUri = uri
                // 更新对话框中的文件名显示
                updateSelectedFileName(uri)
            }
        }
    }
    
    private val fileSaverLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                downloadFileToUri(uri)
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
        binding.objectTitleText.text = getString(R.string.r2_object)
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
                binding.objectTitleText.text = getString(R.string.r2_object_title_bucket, bucket.name)
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
        objectAdapter.setOnDownloadClickListener { obj ->
            val bucket = r2ViewModel.selectedBucket.value
            if (bucket != null) {
                downloadObject(bucket, obj)
            }
        }
        objectAdapter.setOnDeleteClickListener { obj ->
            val bucket = r2ViewModel.selectedBucket.value
            if (bucket != null) {
                showDeleteObjectDialog(bucket, obj)
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
                showUploadDialog(bucket)
            } else {
                Snackbar.make(binding.root, getString(R.string.msg_please_select_bucket_first), Snackbar.LENGTH_SHORT).show()
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
                    r2ViewModel.objectsLoadingState.collect { isLoading ->
                        binding.objectProgressBar.visibility = 
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    r2ViewModel.message.collect { message ->
                        Snackbar.make(binding.root, message.asString(requireContext()), Snackbar.LENGTH_SHORT).show()
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
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val name = dialogBinding.bucketName.text.toString()
                val location = dialogBinding.bucketLocation.text.toString()
                    .takeIf { it.isNotBlank() }
                accountViewModel.defaultAccount.value?.let { account ->
                    r2ViewModel.createBucket(account, name, location)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showDeleteBucketDialog(bucket: R2Bucket) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.r2_delete_bucket_confirm, bucket.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    r2ViewModel.deleteBucket(account, bucket.name)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showObjectsDialog(bucket: R2Bucket) {
        accountViewModel.defaultAccount.value?.let { account ->
            // Show loading dialog first
            val loadingDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("${bucket.name}")
                .setMessage(R.string.dialog_loading_ellipsis)
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
        } ?: showToast(getString(R.string.msg_account_info_unavailable))
    }
    
    private fun showObjectsListDialog(account: Account, bucket: R2Bucket) {
            
        val objects = r2ViewModel.objects.value
        val customDomains = r2ViewModel.customDomains.value
        
        val items = if (objects.isEmpty()) {
            arrayOf(getString(R.string.r2_no_objects), getString(R.string.r2_upload_file))
        } else {
            objects.map { obj ->
                val size = formatFileSize(obj.size ?: 0)
                "${obj.key} ($size)"
            }.toTypedArray() + getString(R.string.r2_upload_file)
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.r2_bucket_objects_list, bucket.name))
            .setItems(items) { _, which ->
                if (objects.isEmpty()) {
                    if (which == 1) {
                        showUploadDialog(bucket)
                    }
                } else {
                    if (which < objects.size) {
                        showObjectDetailsDialog(account, bucket, objects[which], customDomains)
                    } else {
                        showUploadDialog(bucket)
                    }
                }
            }
            .setNegativeButton(R.string.dialog_close, null)
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
            options.add(getString(R.string.r2_copy_custom_url))
            options.add(getString(R.string.r2_copy_default_url))
        } else {
            options.add(getString(R.string.r2_copy_url))
        }
        options.add(getString(R.string.r2_download))
        options.add(getString(R.string.delete))
        
        timber.log.Timber.d("Dialog options: ${options.joinToString()}")
        
        val title = buildString {
            append(shortenFileName(obj.key))
            append("\n")
            append(getString(R.string.r2_object_size, formatFileSize(obj.size ?: 0)))
        }

        // ...existing code...
        
        val message = buildString {
            if (customUrl != null) {
                append(getString(R.string.r2_custom_url_label, customUrl))
                append(getString(R.string.r2_default_url_label, defaultUrl))
            } else {
                append("URL:\n$defaultUrl")
                append(getString(R.string.r2_public_access_note))
            }
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_back, null)
            .setNeutralButton(if (customUrl != null) R.string.r2_copy_custom_url else R.string.r2_copy_url) { _, _ ->
                copyToClipboard(customUrl ?: defaultUrl, getString(if (customUrl != null) R.string.r2_custom_url_copied else R.string.r2_url_copied))
            }
            .setNegativeButton(R.string.dialog_more) { _, _ ->
                // Show more options
                showObjectActionsDialog(account, bucket, obj, customUrl, defaultUrl)
            }
            .show()
    }
    
    private fun showObjectActionsDialog(account: Account, bucket: R2Bucket, obj: R2Object, customUrl: String?, defaultUrl: String) {
        val options = mutableListOf<String>()
        if (customUrl != null) {
            options.add(getString(R.string.r2_copy_custom_url))
            options.add(getString(R.string.r2_copy_default_url))
        } else {
            options.add(getString(R.string.r2_copy_url))
        }
        options.add(getString(R.string.delete))

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_operation)
            .setItems(options.toTypedArray()) { _, which ->
                var index = 0
                when {
                    customUrl != null && which == index++ -> {
                        copyToClipboard(customUrl, getString(R.string.r2_custom_url_copied))
                    }
                    which == index++ -> {
                        copyToClipboard(if (customUrl != null) defaultUrl else defaultUrl, getString(if (customUrl != null) R.string.r2_default_url_copied else R.string.r2_url_copied))
                    }
                    which == index -> {
                        showDeleteObjectDialog(bucket, obj)
                    }
                }
            }
            .setNegativeButton(R.string.dialog_back) { _, _ ->
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
                .setTitle(R.string.r2_delete_object_title)
                .setMessage(getString(R.string.r2_delete_object_message, obj.key))
                .setPositiveButton(R.string.delete) { _, _ ->
                    r2ViewModel.deleteObject(account, bucket.name, obj.key)
                    // Note: After deletion, the list will be refreshed automatically
                    // and the dialog will be dismissed
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
    
    private fun selectFileToUpload() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }

    /**
     * 从 URI 提取用于显示的简短文件名
     */
    private fun extractDisplayFileName(uri: Uri): String {
        val originalName = uri.lastPathSegment ?: "upload"
        val fileName = originalName.substringAfterLast('/').substringAfterLast('\\')
        return shortenFileName(fileName, 40)
    }

    /**
     * 更新对话框中已选择文件的显示文本
     */
    private fun updateSelectedFileName(uri: Uri) {
        val db = uploadDialogBinding ?: return
        db.tvSelectedFile.setTextColor(com.google.android.material.R.attr.colorOnSurface)
        try {
            val typedValue = android.util.TypedValue()
            requireContext().theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            db.tvSelectedFile.setTextColor(typedValue.data)
        } catch (_: Exception) {
            // fall back to default text color
        }
        db.tvSelectedFile.text = extractDisplayFileName(uri)
    }

    /**
     * 显示上传文件对话框（路径前缀输入 + 选择文件 + 取消/上传）
     */
    private fun showUploadDialog(bucket: R2Bucket) {
        currentBucket = bucket
        pendingUploadUri = null

        val dialogBinding = DialogR2UploadBinding.inflate(layoutInflater)
        uploadDialogBinding = dialogBinding
        // 默认路径前缀为 /
        dialogBinding.pathPrefixEditText.setText("/")

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setCancelable(true)

        val dialog = builder.create()

        // 关闭按钮
        dialogBinding.btnClose.setOnClickListener {
            dialog.dismiss()
        }
        // 取消按钮
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        // 选择文件按钮
        dialogBinding.btnSelectFile.setOnClickListener {
            selectFileToUpload()
        }
        // 上传按钮
        dialogBinding.btnUpload.setOnClickListener {
            val uri = pendingUploadUri
            if (uri == null) {
                Snackbar.make(binding.root, R.string.r2_msg_please_select_file, Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val prefix = dialogBinding.pathPrefixEditText.text?.toString()?.trim() ?: "/"
            dialog.dismiss()
            uploadFile(uri, prefix)
        }

        dialog.setOnDismissListener {
            uploadDialogBinding = null
            pendingUploadUri = null
        }
        dialog.show()
    }
    
    private fun downloadObject(bucket: R2Bucket, obj: R2Object) {
        // 先选择保存位置，再进行流式下载（避免大文件 OOM）
        currentDownloadObject = bucket to obj
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_TITLE, obj.key.substringAfterLast('/'))
        }
        fileSaverLauncher.launch(intent)
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

    
    private fun uploadFile(uri: Uri, pathPrefix: String = "/") {
        val bucket = currentBucket ?: return
        val account = accountViewModel.defaultAccount.value ?: return

        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Snackbar.make(binding.root, R.string.msg_cannot_read_file, Snackbar.LENGTH_SHORT).show()
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

            // 归一化路径前缀：去掉开头的 "/"，若不为空且不以 "/" 结尾则追加 "/"
            var normalizedPrefix = pathPrefix.trimStart('/')
            if (normalizedPrefix.isNotEmpty() && !normalizedPrefix.endsWith("/")) {
                normalizedPrefix += "/"
            }
            val objectKey = normalizedPrefix + safeFileName
            
            val file = java.io.File(requireContext().cacheDir, "upload_${System.currentTimeMillis()}_$safeFileName")
            inputStream.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            r2ViewModel.uploadObject(account, bucket.name, objectKey, file) { _ ->
                // 上传完成后立即删除临时文件
                if (file.exists()) {
                    file.delete()
                }
            }
            
        } catch (e: Exception) {
            Snackbar.make(binding.root, getString(R.string.msg_file_read_failed, e.message ?: "null"), Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun downloadFileToUri(uri: Uri) {
        val (bucket, obj) = currentDownloadObject ?: return
        val account = accountViewModel.defaultAccount.value ?: return
        
        try {
            // 创建临时文件用于流式下载
            val tempFile = File.createTempFile("r2_download_", ".tmp", requireContext().cacheDir)
            
            // 流式下载到临时文件，完成后复制到目标 URI
            r2ViewModel.downloadObjectToFile(account, bucket.name, obj.key, tempFile) { success ->
                if (success && tempFile.exists()) {
                    try {
                        requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                            tempFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        Snackbar.make(binding.root, R.string.msg_file_saved_success, Snackbar.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Snackbar.make(binding.root, getString(R.string.msg_file_save_failed, e.message ?: "null"), Snackbar.LENGTH_SHORT).show()
                    }
                }
                // 清理临时文件
                tempFile.delete()
                currentDownloadObject = null
            }
        } catch (e: Exception) {
            Snackbar.make(binding.root, getString(R.string.msg_download_failed, e.message ?: "null"), Snackbar.LENGTH_SHORT).show()
            currentDownloadObject = null
        }
    }
    
    // ==================== Custom Domains ====================
    
    private fun showCustomDomainsDialog(bucket: R2Bucket) {
        accountViewModel.defaultAccount.value?.let { account ->
            // Show loading dialog first
            val bucketName = bucket.name
            val loadingDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.r2_custom_domain_bucket, bucketName))
                .setMessage(R.string.dialog_loading_ellipsis)
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
                    arrayOf(getString(R.string.r2_no_custom_domains))
                } else {
                    domains.map { domain ->
                        "${domain.domain} (${domain.getStatusText(requireContext())})"
                    }.toTypedArray()
                }
                
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.r2_custom_domain_bucket, bucketName))
                    .setItems(items) { _, which ->
                        if (!domains.isEmpty() && which < domains.size) {
                            showDeleteCustomDomainDialog(account, bucket, domains[which])
                        }
                    }
                    .setPositiveButton(R.string.domain_add_title) { _, _ ->
                        showAddCustomDomainDialog(account, bucket)
                    }
                    .setNegativeButton(R.string.dialog_back, null)
                    .show()
            }
        }
    }
    
    private fun showAddCustomDomainDialog(account: Account, bucket: R2Bucket) {
        val context = requireContext()
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val density = context.resources.displayMetrics.density
            setPadding(
                (density * 24).toInt(),
                (density * 16).toInt(),
                (density * 24).toInt(),
                (density * 8).toInt()
            )
        }
        val density = context.resources.displayMetrics.density

        // ==================== Zone Exposed Dropdown Menu（与 Worker / Pages / Route 添加域名同套样式） ====================
        val zoneLayout = com.google.android.material.textfield.TextInputLayout(context).apply {
            hint = getString(R.string.worker_route_select_zone)
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_DROPDOWN_MENU
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (density * 8).toInt() }
        }
        val zoneAuto = com.google.android.material.textfield.MaterialAutoCompleteTextView(zoneLayout.context).apply {
            keyListener = null
            isCursorVisible = false
            val padStart  = (density * 16).toInt()
            val padTop    = (density * 14).toInt()
            val padEnd    = (density * 56).toInt()
            val padBottom = (density * 14).toInt()
            setPaddingRelative(padStart, padTop, padEnd, padBottom)
            minHeight = (density * 52).toInt()
        }
        zoneLayout.addView(zoneAuto)
        container.addView(zoneLayout)

        val inputLayout = com.google.android.material.textfield.TextInputLayout(context).apply {
            hint = getString(R.string.pages_domain_input_hint)
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val editText = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            val padStart  = (density * 16).toInt()
            val padTop    = (density * 14).toInt()
            val padEnd    = (density * 16).toInt()
            val padBottom = (density * 14).toInt()
            setPaddingRelative(padStart, padTop, padEnd, padBottom)
            minHeight = (density * 52).toInt()
        }
        inputLayout.addView(editText)
        container.addView(inputLayout)

        val zones = mutableListOf<com.muort.upworker.core.model.Zone>()

        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.r2_add_custom_domain_title)
            .setView(container)
            .setPositiveButton(R.string.add, null)
            .setNegativeButton(R.string.dialog_back, null)
            .create()

        dialog.setOnShowListener { iface ->
            val dlg = iface as androidx.appcompat.app.AlertDialog

            // 打开即加载 Zone 列表
            viewLifecycleOwner.lifecycleScope.launch {
                val loadingAdapter = android.widget.ArrayAdapter<String>(
                    context,
                    android.R.layout.simple_dropdown_item_1line,
                    listOf(getString(R.string.worker_route_zone_loading))
                )
                zoneAuto.setAdapter(loadingAdapter)
                when (val res = zoneRepository.fetchAndSaveZones(account)) {
                    is com.muort.upworker.core.model.Resource.Success -> {
                        zones.clear()
                        val sorted = res.data.sortedBy { it.name }
                        zones.addAll(sorted)
                        if (zones.isEmpty()) {
                            val emptyAdapter = android.widget.ArrayAdapter<String>(
                                context,
                                android.R.layout.simple_dropdown_item_1line,
                                listOf(getString(R.string.worker_route_zone_empty))
                            )
                            zoneAuto.setAdapter(emptyAdapter)
                        } else {
                            val adapter = android.widget.ArrayAdapter<String>(
                                context,
                                android.R.layout.simple_spinner_dropdown_item,
                                zones.map { it.name }
                            )
                            zoneAuto.setAdapter(adapter)
                            zoneAuto.setOnItemClickListener { _, _, position, _ ->
                                if (position in zones.indices) {
                                    zoneLayout.error = null
                                    // 添加域名 → 自动填入通配子域名 *.zone.name
                                    val hostname = "*.${zones[position].name}"
                                    editText.setText(hostname)
                                    editText.requestFocus()
                                    editText.setSelection(hostname.length)
                                }
                            }
                        }
                    }
                    is com.muort.upworker.core.model.Resource.Error -> {
                        showToast(
                            getString(R.string.worker_route_zone_load_failed, res.message)
                        )
                    }
                    else -> {}
                }
            }

            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val domain = editText.text?.toString()?.trim()?.lowercase().orEmpty()
                if (domain.isEmpty()) {
                    inputLayout.error = getString(R.string.pages_domain_cannot_be_empty)
                    editText.requestFocus()
                    return@setOnClickListener
                }
                inputLayout.error = null
                r2ViewModel.createCustomDomain(account, bucket.name, domain)
                showCustomDomainsDialog(bucket)
                dlg.dismiss()
            }
            dlg.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                showCustomDomainsDialog(bucket)
                dlg.dismiss()
            }
        }
        dialog.show()
    }

    private fun showDeleteCustomDomainDialog(account: Account, bucket: R2Bucket, domain: R2CustomDomain) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.r2_delete_custom_domain_title)
            .setMessage(getString(R.string.r2_delete_custom_domain_message, domain.domain))
            .setPositiveButton(R.string.delete) { _, _ ->
                r2ViewModel.deleteCustomDomain(account, bucket.name, domain.domain)
                showCustomDomainsDialog(bucket)
            }
            .setNegativeButton(R.string.dialog_back) { _, _ ->
                showCustomDomainsDialog(bucket)
            }
            .show()
    }
    

    
    override fun onDestroyView() {
        uploadDialogBinding = null
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
                binding.bucketLocationText.text = bucket.location?.let { binding.root.context.getString(R.string.r2_bucket_location_template, it) } ?: binding.root.context.getString(R.string.r2_bucket_location_default)
                
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
