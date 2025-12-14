package com.muort.upworker.feature.backup

import android.os.Bundle
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
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BackupFragment : Fragment() {
    
    private val viewModel: BackupViewModel by viewModels()
    
    private lateinit var urlInput: TextInputEditText
    private lateinit var usernameInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var backupPathInput: TextInputEditText
    private lateinit var autoBackupSwitch: SwitchMaterial
    private lateinit var testConnectionButton: MaterialButton
    private lateinit var saveConfigButton: MaterialButton
    private lateinit var backupButton: MaterialButton
    private lateinit var loadFilesButton: MaterialButton
    private lateinit var backupFilesRecyclerView: RecyclerView
    private lateinit var progressIndicator: LinearProgressIndicator
    
    private lateinit var backupFilesAdapter: BackupFilesAdapter
    
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
        urlInput = view.findViewById(R.id.urlInput)
        usernameInput = view.findViewById(R.id.usernameInput)
        passwordInput = view.findViewById(R.id.passwordInput)
        backupPathInput = view.findViewById(R.id.backupPathInput)
        autoBackupSwitch = view.findViewById(R.id.autoBackupSwitch)
        testConnectionButton = view.findViewById(R.id.testConnectionButton)
        saveConfigButton = view.findViewById(R.id.saveConfigButton)
        backupButton = view.findViewById(R.id.backupButton)
        loadFilesButton = view.findViewById(R.id.loadFilesButton)
        backupFilesRecyclerView = view.findViewById(R.id.backupFilesRecyclerView)
        progressIndicator = view.findViewById(R.id.progressIndicator)
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
        
        backupButton.setOnClickListener {
            viewModel.backupAccounts()
        }
        
        loadFilesButton.setOnClickListener {
            viewModel.loadBackupFiles()
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
            viewModel.loadingState.collectLatest { isLoading ->
                progressIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
                
                // 禁用按钮
                testConnectionButton.isEnabled = !isLoading
                saveConfigButton.isEnabled = !isLoading
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
                viewModel.restoreAccounts(fileName)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteConfirmDialog(fileName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("确认删除")
            .setMessage("确定要删除备份文件 $fileName 吗？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteBackupFile(fileName)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
