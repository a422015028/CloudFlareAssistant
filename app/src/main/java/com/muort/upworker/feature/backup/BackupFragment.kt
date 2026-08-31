package com.muort.upworker.feature.backup

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.muort.upworker.R
import com.muort.upworker.core.model.StorageType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BackupFragment : Fragment() {

    private val viewModel: BackupViewModel by viewModels()

    private lateinit var storageTypeToggleGroup: MaterialButtonToggleGroup
    private lateinit var webDavTypeButton: MaterialButton
    private lateinit var r2TypeButton: MaterialButton
    private lateinit var localTypeButton: MaterialButton
    private lateinit var webDavConfigLayout: View
    private lateinit var r2ConfigLayout: View
    private lateinit var localConfigLayout: View

    private lateinit var urlInput: TextInputEditText
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var backupPathInput: TextInputEditText
    private lateinit var webDavBackupPasswordInput: TextInputEditText
    private lateinit var autoBackupSwitch: SwitchMaterial
    private lateinit var testConnectionButton: MaterialButton
    private lateinit var saveConfigButton: MaterialButton

    private lateinit var accountInput: AutoCompleteTextView
    private lateinit var bucketInput: AutoCompleteTextView
    private lateinit var r2BackupPathInput: TextInputEditText
    private lateinit var r2BackupPasswordInput: TextInputEditText
    private lateinit var r2AutoBackupSwitch: SwitchMaterial
    private lateinit var loadBucketsButton: MaterialButton
    private lateinit var saveR2ConfigButton: MaterialButton

    private lateinit var localDirText: TextView
    private lateinit var chooseDirButton: MaterialButton
    private lateinit var importBackupButton: MaterialButton
    private lateinit var localBackupPasswordInput: TextInputEditText
    private lateinit var localAutoBackupSwitch: SwitchMaterial
    private lateinit var saveLocalConfigButton: MaterialButton

    private lateinit var backupButton: MaterialButton
    private lateinit var loadFilesButton: MaterialButton
    private lateinit var backupFilesRecyclerView: RecyclerView
    private lateinit var progressIndicator: LinearProgressIndicator

    private lateinit var backupFilesAdapter: BackupFilesAdapter
    private lateinit var accountAdapter: ArrayAdapter<String>
    private lateinit var bucketAdapter: ArrayAdapter<String>

    private var selectedAccountId: Long = 0L
    private var currentR2Config: com.muort.upworker.core.model.R2BackupConfig? = null
    private var loadedAccounts: List<com.muort.upworker.core.model.Account>? = null
    private var lastLoadedBucketAccountId: Long = 0L

    private val directoryPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            viewModel.setLocalBackupDirectory(uri)
            updateLocalDirDisplay(uri)
        }
    }

    private val importFilePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            importBackupFromUri(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_backup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initViews(view)
        setupRecyclerView()
        setupListeners()
        observeViewModel()
    }

    private fun initViews(view: View) {
        storageTypeToggleGroup = view.findViewById(R.id.storageTypeToggleGroup)
        webDavTypeButton = view.findViewById(R.id.webDavTypeButton)
        r2TypeButton = view.findViewById(R.id.r2TypeButton)
        localTypeButton = view.findViewById(R.id.localTypeButton)
        webDavConfigLayout = view.findViewById(R.id.webDavConfigLayout)
        r2ConfigLayout = view.findViewById(R.id.r2ConfigLayout)
        localConfigLayout = view.findViewById(R.id.localConfigLayout)

        urlInput = view.findViewById(R.id.urlInput)
        usernameInput = view.findViewById(R.id.usernameInput)
        passwordInput = view.findViewById(R.id.passwordInput)
        backupPathInput = view.findViewById(R.id.backupPathInput)
        webDavBackupPasswordInput = view.findViewById(R.id.webDavBackupPasswordInput)
        autoBackupSwitch = view.findViewById(R.id.autoBackupSwitch)
        testConnectionButton = view.findViewById(R.id.testConnectionButton)
        saveConfigButton = view.findViewById(R.id.saveConfigButton)

        accountInput = view.findViewById(R.id.accountInput)
        bucketInput = view.findViewById(R.id.bucketInput)
        r2BackupPathInput = view.findViewById(R.id.r2BackupPathInput)
        r2BackupPasswordInput = view.findViewById(R.id.r2BackupPasswordInput)
        r2AutoBackupSwitch = view.findViewById(R.id.r2AutoBackupSwitch)
        loadBucketsButton = view.findViewById(R.id.loadBucketsButton)
        saveR2ConfigButton = view.findViewById(R.id.saveR2ConfigButton)

        localDirText = view.findViewById(R.id.localDirText)
        chooseDirButton = view.findViewById(R.id.chooseDirButton)
        importBackupButton = view.findViewById(R.id.importBackupButton)
        localBackupPasswordInput = view.findViewById(R.id.localBackupPasswordInput)
        localAutoBackupSwitch = view.findViewById(R.id.localAutoBackupSwitch)
        saveLocalConfigButton = view.findViewById(R.id.saveLocalConfigButton)

        // 默认显示应用私有目录
        localDirText.text = getString(R.string.backup_current_dir)

        backupButton = view.findViewById(R.id.backupButton)
        loadFilesButton = view.findViewById(R.id.loadFilesButton)
        backupFilesRecyclerView = view.findViewById(R.id.backupFilesRecyclerView)
        progressIndicator = view.findViewById(R.id.progressIndicator)

        accountAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())
        bucketAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, mutableListOf())

        accountInput.setAdapter(accountAdapter)
        bucketInput.setAdapter(bucketAdapter)
    }

    private fun setupRecyclerView() {
        backupFilesAdapter = BackupFilesAdapter(
            onRestoreClick = { fileName ->
                restoreBackup(fileName)
            },
            onDeleteClick = { fileName ->
                showDeleteConfirmDialog(fileName)
            }
        )

        backupFilesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = backupFilesAdapter
        }
    }

    private fun setupListeners() {
        storageTypeToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.webDavTypeButton -> {
                        viewModel.selectStorageType(StorageType.WEBDAV)
                        webDavConfigLayout.visibility = View.VISIBLE
                        r2ConfigLayout.visibility = View.GONE
                        localConfigLayout.visibility = View.GONE
                    }
                    R.id.r2TypeButton -> {
                        viewModel.selectStorageType(StorageType.R2)
                        webDavConfigLayout.visibility = View.GONE
                        r2ConfigLayout.visibility = View.VISIBLE
                        localConfigLayout.visibility = View.GONE
                    }
                    R.id.localTypeButton -> {
                        viewModel.selectStorageType(StorageType.LOCAL)
                        webDavConfigLayout.visibility = View.GONE
                        r2ConfigLayout.visibility = View.GONE
                        localConfigLayout.visibility = View.VISIBLE
                        viewModel.loadLocalBackupFiles()
                    }
                }
            }
        }

        localTypeButton.isChecked = true

        // 初始自动加载本地备份列表
        viewModel.loadLocalBackupFiles()

        testConnectionButton.setOnClickListener {
            val url = urlInput.text?.toString() ?: ""
            val username = usernameInput.text?.toString() ?: ""
            val password = passwordInput.text?.toString() ?: ""

            if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.backup_webdav_config_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.testConnection(url, username, password)
        }

        saveConfigButton.setOnClickListener {
            val url = urlInput.text?.toString() ?: ""
            val username = usernameInput.text?.toString() ?: ""
            val password = passwordInput.text?.toString() ?: ""
            val backupPath = backupPathInput.text?.toString() ?: ""
            val autoBackup = autoBackupSwitch.isChecked
            val backupPassword = webDavBackupPasswordInput.text?.toString()

            if (url.isEmpty() || username.isEmpty() || password.isEmpty() || backupPath.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.backup_webdav_config_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.saveWebDavConfig(url, username, password, backupPath, autoBackup, backupPassword)
        }

        accountInput.setOnItemClickListener { _, _, position, _ ->
            val accountName = accountAdapter.getItem(position) ?: return@setOnItemClickListener
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.accounts.collectLatest { accountsResource ->
                    if (accountsResource is com.muort.upworker.core.model.Resource.Success) {
                        val account = accountsResource.data.find { it.name == accountName }
                        account?.let {
                            val newAccountId = it.id
                            selectedAccountId = newAccountId
                            if (newAccountId != lastLoadedBucketAccountId) {
                                viewModel.loadBucketsForAccount(newAccountId)
                                lastLoadedBucketAccountId = newAccountId
                            }
                        }
                    }
                }
            }
        }

        loadBucketsButton.setOnClickListener {
            if (selectedAccountId == 0L) {
                Toast.makeText(requireContext(), getString(R.string.msg_please_select_account_first), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.loadBucketsForAccount(selectedAccountId)
        }

        saveR2ConfigButton.setOnClickListener {
            val bucketName = bucketInput.text?.toString() ?: ""
            val backupPath = r2BackupPathInput.text?.toString() ?: ""
            val autoBackup = r2AutoBackupSwitch.isChecked
            val backupPassword = r2BackupPasswordInput.text?.toString()

            if (selectedAccountId == 0L || bucketName.isEmpty() || backupPath.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.backup_r2_config_required), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.saveR2BackupConfig(selectedAccountId, bucketName, backupPath, autoBackup, backupPassword)
        }

        // 本地备份 - 选择目录
        chooseDirButton.setOnClickListener {
            openDirectoryPicker()
        }

        importBackupButton.setOnClickListener {
            importFilePickerLauncher.launch(
                arrayOf("application/json", "application/octet-stream", "*/*")
            )
        }

        saveLocalConfigButton.setOnClickListener {
            val autoBackup = localAutoBackupSwitch.isChecked
            val backupPassword = localBackupPasswordInput.text?.toString()
            viewModel.saveLocalBackupConfig(autoBackup, backupPassword)
        }

        backupButton.setOnClickListener {
            val password = getCurrentBackupPassword()
            when (viewModel.selectedStorageType.value) {
                StorageType.WEBDAV -> viewModel.backupAccounts(password)
                StorageType.R2 -> viewModel.backupAccountsToR2(password)
                StorageType.LOCAL -> viewModel.backupAccountsLocal(password)
            }
        }

        loadFilesButton.setOnClickListener {
            when (viewModel.selectedStorageType.value) {
                StorageType.WEBDAV -> viewModel.loadBackupFiles()
                StorageType.R2 -> viewModel.loadR2BackupFiles()
                StorageType.LOCAL -> viewModel.loadLocalBackupFiles()
            }
        }
    }

    private fun openDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        try {
            directoryPickerLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.backup_cannot_open_dir_picker), Toast.LENGTH_SHORT).show()
        }
    }

    private var pendingImportFileName: String? = null
    private var pendingImportContent: String? = null

    private fun importBackupFromUri(uri: Uri) {
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "backup_file"
        val content = try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().use { it.readText() }
            } ?: return
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.msg_file_read_failed, e.message), Toast.LENGTH_SHORT).show()
            return
        }

        val isEncrypted = fileName.endsWith(".enc") ||
                (content.startsWith("{").not() && content.isNotBlank())

        val configPassword = getCurrentBackupPassword()

        if (isEncrypted && configPassword.isNullOrBlank()) {
            // 加密文件且没配置密码 → 弹窗输入
            pendingImportFileName = fileName
            pendingImportContent = content
            showPasswordDialog(
                title = getString(R.string.backup_import_title),
                message = getString(R.string.backup_file_encrypted_hint),
                hintText = getString(R.string.backup_password),
                allowEmpty = false
            ) { password ->
                showRestoreConfirmDialogWithContent(fileName, password, content)
                pendingImportFileName = null
                pendingImportContent = null
            }
        } else {
            // 明文或有配置密码 → 直接确认恢复
            showRestoreConfirmDialogWithContent(fileName, configPassword, content)
        }
    }

    private fun showRestoreConfirmDialogWithContent(
        fileName: String,
        password: String?,
        content: String
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_confirm_import_title)
            .setMessage(getString(R.string.backup_confirm_import_message, fileName))
            .setPositiveButton(R.string.backup_action_import) { _, _ ->
                viewModel.importBackupFromContent(content, password, fileName)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateLocalDirDisplay(uri: Uri?) {
        if (uri == null) {
            localDirText.text = getString(R.string.backup_current_dir)
            return
        }
        try {
            val docFile = DocumentFile.fromTreeUri(requireContext(), uri)
            val displayName = docFile?.name ?: uri.lastPathSegment ?: getString(R.string.backup_selected)
            localDirText.text = getString(R.string.backup_current_dir_format, displayName)
        } catch (e: Exception) {
            localDirText.text = getString(R.string.backup_selected_directory)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.webDavConfig.collectLatest { config ->
                config?.let {
                    urlInput.setText(it.url)
                    usernameInput.setText(it.username)
                    passwordInput.setText(it.password)
                    backupPathInput.setText(it.backupPath)
                    webDavBackupPasswordInput.setText(it.backupPassword ?: "")
                    autoBackupSwitch.isChecked = it.autoBackup
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.localBackupConfig.collectLatest { config ->
                config?.let {
                    localAutoBackupSwitch.isChecked = it.autoBackup
                    localBackupPasswordInput.setText(it.backupPassword ?: "")
                    if (!it.directoryUri.isNullOrBlank()) {
                        updateLocalDirDisplay(Uri.parse(it.directoryUri))
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.availableBuckets.collectLatest { buckets ->
                bucketAdapter.clear()
                bucketAdapter.addAll(buckets)
                bucketAdapter.notifyDataSetChanged()

                currentR2Config?.let { config ->
                    if (buckets.contains(config.bucketName)) {
                        bucketInput.setText(config.bucketName, false)
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.r2BackupConfig.collectLatest { config ->
                config?.let {
                    selectedAccountId = it.accountId
                    r2BackupPathInput.setText(it.backupPath)
                    r2BackupPasswordInput.setText(it.backupPassword ?: "")
                    r2AutoBackupSwitch.isChecked = it.autoBackup
                    currentR2Config = it

                    loadedAccounts?.let { accounts ->
                        val account = accounts.find { acc -> acc.id == it.accountId }
                        account?.let { acc ->
                            accountInput.setText(acc.name, false)
                            selectedAccountId = acc.id
                            bucketInput.setText(it.bucketName, false)
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.accounts.collectLatest { accountsResource ->
                if (accountsResource is com.muort.upworker.core.model.Resource.Success) {
                    val accountNames = accountsResource.data.map { it.name }
                    accountAdapter.clear()
                    accountAdapter.addAll(accountNames)
                    accountAdapter.notifyDataSetChanged()

                    loadedAccounts = accountsResource.data

                    currentR2Config?.let { config ->
                        val account = accountsResource.data.find { acc -> acc.id == config.accountId }
                        account?.let { acc ->
                            accountInput.setText(acc.name, false)
                            selectedAccountId = acc.id
                            bucketInput.setText(config.bucketName, false)
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.loadingState.collectLatest { isLoading ->
                progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE

                testConnectionButton.isEnabled = !isLoading
                saveConfigButton.isEnabled = !isLoading
                loadBucketsButton.isEnabled = !isLoading
                saveR2ConfigButton.isEnabled = !isLoading
                saveLocalConfigButton.isEnabled = !isLoading
                chooseDirButton.isEnabled = !isLoading
                backupButton.isEnabled = !isLoading
                loadFilesButton.isEnabled = !isLoading
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.backupFiles.collectLatest { files ->
                backupFilesAdapter.submitList(files)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.message.collectLatest { message ->
                if (message != com.muort.upworker.core.model.UiMessage.Empty) {
                    Toast.makeText(requireContext(), message.asString(requireContext()), Toast.LENGTH_LONG).show()
                    viewModel.clearMessage()
                }
            }
        }
    }

    /**
     * 显示密码输入对话框
     */
    private fun showPasswordDialog(
        title: String,
        message: String,
        hintText: String,
        allowEmpty: Boolean = false,
        onConfirm: (String?) -> Unit
    ) {
        val passwordInput = TextInputLayout(requireContext()).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            setPaddingRelative(32, 8, 32, 8)
        }

        val editText = TextInputEditText(requireContext()).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = hintText
            setSingleLine()
        }

        passwordInput.addView(editText)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setView(passwordInput)
            .setPositiveButton(R.string.confirm, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.show()

        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.setOnClickListener {
            val password = editText.text?.toString() ?: ""
            if (password.isEmpty() && !allowEmpty) {
                editText.error = getString(R.string.backup_password_required_hint)
                return@setOnClickListener
            }
            val pwd = password.ifBlank { null }
            dialog.dismiss()
            onConfirm(pwd)
        }
    }

    /**
     * 获取当前存储类型配置的备份密码
     */
    private fun getCurrentBackupPassword(): String? {
        return when (viewModel.selectedStorageType.value) {
            StorageType.WEBDAV -> webDavBackupPasswordInput.text?.toString()?.takeIf { it.isNotBlank() }
            StorageType.R2 -> r2BackupPasswordInput.text?.toString()?.takeIf { it.isNotBlank() }
            StorageType.LOCAL -> localBackupPasswordInput.text?.toString()?.takeIf { it.isNotBlank() }
        }
    }

    private var pendingRestoreFileName: String? = null

    private fun restoreBackup(fileName: String) {
        val isEncrypted = fileName.endsWith(".enc")
        val configPassword = getCurrentBackupPassword()

        // 加密文件且配置里有密码 → 先尝试用配置密码
        if (isEncrypted && !configPassword.isNullOrBlank()) {
            showRestoreConfirmDialog(fileName, configPassword)
            return
        }

        // 加密文件但没配置密码 → 弹窗让用户输入
        if (isEncrypted) {
            pendingRestoreFileName = fileName
            showPasswordDialog(
                title = getString(R.string.backup_restore),
                message = getString(R.string.backup_file_encrypted_hint),
                hintText = getString(R.string.backup_password),
                allowEmpty = false
            ) { password ->
                pendingRestoreFileName = null
                showRestoreConfirmDialog(fileName, password)
            }
            return
        }

        // 明文备份 → 直接恢复
        showRestoreConfirmDialog(fileName, null)
    }

    private fun showRestoreConfirmDialog(fileName: String, password: String?) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_confirm_restore_title)
            .setMessage(getString(R.string.backup_confirm_restore_message, fileName))
            .setPositiveButton(R.string.backup_restore) { _, _ ->
                when (viewModel.selectedStorageType.value) {
                    StorageType.WEBDAV -> viewModel.restoreAccounts(fileName, password)
                    StorageType.R2 -> viewModel.restoreAccountsFromR2(fileName, password)
                    StorageType.LOCAL -> viewModel.restoreAccountsLocal(fileName, password)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirmDialog(fileName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.backup_confirm_delete_title)
            .setMessage(getString(R.string.backup_confirm_delete_message, fileName))
            .setPositiveButton(R.string.delete) { _, _ ->
                when (viewModel.selectedStorageType.value) {
                    StorageType.WEBDAV -> viewModel.deleteBackupFile(fileName)
                    StorageType.R2 -> viewModel.deleteR2BackupFile(fileName)
                    StorageType.LOCAL -> viewModel.deleteLocalBackupFile(fileName)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
