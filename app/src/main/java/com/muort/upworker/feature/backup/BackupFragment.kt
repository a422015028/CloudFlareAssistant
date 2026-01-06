package com.muort.upworker.feature.backup

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
    private lateinit var webDavConfigLayout: View
    private lateinit var r2ConfigLayout: View
    
    private lateinit var urlInput: TextInputEditText
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var backupPathInput: TextInputEditText
    private lateinit var autoBackupSwitch: SwitchMaterial
    private lateinit var testConnectionButton: MaterialButton
    private lateinit var saveConfigButton: MaterialButton
    
    private lateinit var accountInput: AutoCompleteTextView
    private lateinit var bucketInput: AutoCompleteTextView
    private lateinit var r2BackupPathInput: TextInputEditText
    private lateinit var r2AutoBackupSwitch: SwitchMaterial
    private lateinit var loadBucketsButton: MaterialButton
    private lateinit var saveR2ConfigButton: MaterialButton
    
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
        webDavConfigLayout = view.findViewById(R.id.webDavConfigLayout)
        r2ConfigLayout = view.findViewById(R.id.r2ConfigLayout)
        
        urlInput = view.findViewById(R.id.urlInput)
        usernameInput = view.findViewById(R.id.usernameInput)
        passwordInput = view.findViewById(R.id.passwordInput)
        backupPathInput = view.findViewById(R.id.backupPathInput)
        autoBackupSwitch = view.findViewById(R.id.autoBackupSwitch)
        testConnectionButton = view.findViewById(R.id.testConnectionButton)
        saveConfigButton = view.findViewById(R.id.saveConfigButton)
        
        accountInput = view.findViewById(R.id.accountInput)
        bucketInput = view.findViewById(R.id.bucketInput)
        r2BackupPathInput = view.findViewById(R.id.r2BackupPathInput)
        r2AutoBackupSwitch = view.findViewById(R.id.r2AutoBackupSwitch)
        loadBucketsButton = view.findViewById(R.id.loadBucketsButton)
        saveR2ConfigButton = view.findViewById(R.id.saveR2ConfigButton)
        
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
                showRestoreConfirmDialog(fileName)
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
                    }
                    R.id.r2TypeButton -> {
                        viewModel.selectStorageType(StorageType.R2)
                        webDavConfigLayout.visibility = View.GONE
                        r2ConfigLayout.visibility = View.VISIBLE
                    }
                }
            }
        }
        
        webDavTypeButton.isChecked = true
        
        testConnectionButton.setOnClickListener {
            val url = urlInput.text?.toString() ?: ""
            val username = usernameInput.text?.toString() ?: ""
            val password = passwordInput.text?.toString() ?: ""
            
            if (url.isEmpty() || username.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "请填写完整的WebDAV配置", Toast.LENGTH_SHORT).show()
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
            
            if (url.isEmpty() || username.isEmpty() || password.isEmpty() || backupPath.isEmpty()) {
                Toast.makeText(requireContext(), "请填写完整的WebDAV配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            viewModel.saveWebDavConfig(url, username, password, backupPath, autoBackup)
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
                Toast.makeText(requireContext(), "请先选择账号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewModel.loadBucketsForAccount(selectedAccountId)
        }
        
        saveR2ConfigButton.setOnClickListener {
            val bucketName = bucketInput.text?.toString() ?: ""
            val backupPath = r2BackupPathInput.text?.toString() ?: ""
            val autoBackup = r2AutoBackupSwitch.isChecked
            
            if (selectedAccountId == 0L || bucketName.isEmpty() || backupPath.isEmpty()) {
                Toast.makeText(requireContext(), "请填写完整的R2配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            viewModel.saveR2BackupConfig(selectedAccountId, bucketName, backupPath, autoBackup)
        }
        
        backupButton.setOnClickListener {
            when (viewModel.selectedStorageType.value) {
                StorageType.WEBDAV -> viewModel.backupAccounts()
                StorageType.R2 -> viewModel.backupAccountsToR2()
            }
        }
        
        loadFilesButton.setOnClickListener {
            when (viewModel.selectedStorageType.value) {
                StorageType.WEBDAV -> viewModel.loadBackupFiles()
                StorageType.R2 -> viewModel.loadR2BackupFiles()
            }
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
                    autoBackupSwitch.isChecked = it.autoBackup
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
                message?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                    viewModel.clearMessage()
                }
            }
        }
    }
    
    private fun showRestoreConfirmDialog(fileName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认恢复")
            .setMessage("确定要从备份文件 $fileName 恢复账号吗？这将导入备份中的所有账号。")
            .setPositiveButton("恢复") { _, _ ->
                when (viewModel.selectedStorageType.value) {
                    StorageType.WEBDAV -> viewModel.restoreAccounts(fileName)
                    StorageType.R2 -> viewModel.restoreAccountsFromR2(fileName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteConfirmDialog(fileName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除备份文件 $fileName 吗？")
            .setPositiveButton("删除") { _, _ ->
                when (viewModel.selectedStorageType.value) {
                    StorageType.WEBDAV -> viewModel.deleteBackupFile(fileName)
                    StorageType.R2 -> viewModel.deleteR2BackupFile(fileName)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
