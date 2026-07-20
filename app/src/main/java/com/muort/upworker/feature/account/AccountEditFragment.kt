package com.muort.upworker.feature.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.muort.upworker.core.model.AuthType
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.FragmentAccountEditBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccountEditFragment : Fragment(), ApiTokenPermissionSheet.Listener {
    
    private var _binding: FragmentAccountEditBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: AccountViewModel by activityViewModels()
    private val args: AccountEditFragmentArgs by navArgs()
    
    // 当前选择的认证类型
    private var selectedAuthType: AuthType = AuthType.TOKEN
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountEditBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAuthTypeDropdown()
        setupFetchAccountIdButton()
        setupPermissionButton()
        loadAccountIfEditing()
        setupSaveButton()
        setupZoneButtons()
        observeViewModel()
    }
    
    /**
     * 设置自动获取 Account ID 按钮
     */
    private fun setupFetchAccountIdButton() {
        binding.fetchAccountIdBtn.setOnClickListener {
            val token = binding.tokenEditText.text.toString().trim()
            val email = binding.emailEditText.text.toString().trim()
            val globalApiKey = binding.globalApiKeyEditText.text.toString().trim()
            
            // 检查是否有可用的认证凭据
            val hasTokenCredentials = token.isNotEmpty()
            val hasGlobalKeyCredentials = email.isNotEmpty() && globalApiKey.isNotEmpty()
            
            if (!hasTokenCredentials && !hasGlobalKeyCredentials) {
                showToast("请先填写 API Token 或 Global API Key 凭据")
                return@setOnClickListener
            }
            
            // 使用当前选中的认证方式，或使用有凭据的那种
            val authTypeToUse = when {
                selectedAuthType == AuthType.TOKEN && hasTokenCredentials -> AuthType.TOKEN
                selectedAuthType == AuthType.GLOBAL_API_KEY && hasGlobalKeyCredentials -> AuthType.GLOBAL_API_KEY
                hasTokenCredentials -> AuthType.TOKEN
                hasGlobalKeyCredentials -> AuthType.GLOBAL_API_KEY
                else -> {
                    showToast("请先填写认证凭据")
                    return@setOnClickListener
                }
            }
            
            val tempAccount = com.muort.upworker.core.model.Account(
                id = 0,
                name = "临时",
                accountId = "",
                token = if (authTypeToUse == AuthType.TOKEN) token else "",
                email = if (authTypeToUse == AuthType.GLOBAL_API_KEY) email else null,
                globalApiKey = if (authTypeToUse == AuthType.GLOBAL_API_KEY) globalApiKey else null,
                authType = authTypeToUse.name
            )
            
            viewModel.fetchAccountsFromApi(tempAccount) { accounts ->
                if (accounts.size == 1) {
                    // 只有一个账号，自动填充
                    binding.accountIdEditText.setText(accounts[0].id)
                    if (binding.nameEditText.text.toString().isBlank()) {
                        binding.nameEditText.setText(accounts[0].name)
                    }
                    showToast("已自动填充 Account ID")
                } else {
                    // 多个账号，弹出选择对话框
                    showAccountSelectionDialog(accounts)
                }
            }
        }
    }
    
    /**
     * 显示账号选择对话框
     */
    private fun showAccountSelectionDialog(accounts: List<com.muort.upworker.core.model.AccountInfo>) {
        val items = accounts.map { "${it.name} (${it.id})" }.toTypedArray()
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择账号")
            .setItems(items) { _, which ->
                val selected = accounts[which]
                binding.accountIdEditText.setText(selected.id)
                if (binding.nameEditText.text.toString().isBlank()) {
                    binding.nameEditText.setText(selected.name)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 设置 “权限” 按钮：打开 API Token 权限管理弹窗
     * 可查看当前 Token 权限，或创建一个权限受控的新 Token 并回填
     */
    private fun setupPermissionButton() {
        binding.tokenPermissionBtn.setOnClickListener {
            val token = binding.tokenEditText.text.toString().trim()
            val email = binding.emailEditText.text.toString().trim()
            val globalApiKey = binding.globalApiKeyEditText.text.toString().trim()
            val accountId = binding.accountIdEditText.text.toString().trim()
            val name = binding.nameEditText.text.toString().trim().ifBlank { "临时" }

            // 决定可用认证方式（优先当前选中，否则用已填写的凭据）
            val hasToken = token.isNotEmpty()
            val hasGlobal = email.isNotEmpty() && globalApiKey.isNotEmpty()
            val authTypeToUse = when {
                selectedAuthType == AuthType.TOKEN && hasToken -> AuthType.TOKEN
                selectedAuthType == AuthType.GLOBAL_API_KEY && hasGlobal -> AuthType.GLOBAL_API_KEY
                hasToken -> AuthType.TOKEN
                hasGlobal -> AuthType.GLOBAL_API_KEY
                else -> {
                    showToast("请先填写 API Token 或 Global API Key 凭据")
                    return@setOnClickListener
                }
            }

            val tempAccount = com.muort.upworker.core.model.Account(
                id = 0,
                name = name,
                accountId = accountId,
                token = if (authTypeToUse == AuthType.TOKEN) token else "",
                email = if (authTypeToUse == AuthType.GLOBAL_API_KEY) email else null,
                globalApiKey = if (authTypeToUse == AuthType.GLOBAL_API_KEY) globalApiKey else null,
                authType = authTypeToUse.name
            )

            ApiTokenPermissionSheet.newInstance(tempAccount)
                .show(childFragmentManager, "ApiTokenPermissionSheet")
        }
    }

    /** ApiTokenPermissionSheet.Listener：回填创建的新 Token */
    override fun onFillApiToken(value: String) {
        binding.tokenEditText.setText(value)
        // 切换为 API Token 认证方式
        selectedAuthType = AuthType.TOKEN
        binding.authTypeRadioGroup.check(com.muort.upworker.R.id.radioToken)
        showToast("已填入新创建的 API Token")
    }
    
    /**
     * 设置认证类型选择
     */
    private fun setupAuthTypeDropdown() {
        // 设置 RadioGroup 选中监听，只更新 selectedAuthType（不切换显示/隐藏）
        binding.authTypeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedAuthType = when (checkedId) {
                com.muort.upworker.R.id.radioGlobalApiKey -> AuthType.GLOBAL_API_KEY
                else -> AuthType.TOKEN
            }
        }
    }
    
    private fun loadAccountIfEditing() {
        if (args.accountId != -1L) {
            // Editing existing account
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.accounts.collect { accounts ->
                    val account = accounts.find { it.id == args.accountId }
                    account?.let {
                        binding.nameEditText.setText(it.name)
                        binding.accountIdEditText.setText(it.accountId)
                        binding.r2AccessKeyIdEditText.setText(it.r2AccessKeyId ?: "")
                        binding.r2SecretAccessKeyEditText.setText(it.r2SecretAccessKey ?: "")
                        binding.defaultSwitch.isChecked = it.isDefault
                        
                        // 设置认证类型（当前使用的认证方式）
                        selectedAuthType = it.getAuthTypeEnum()
                        val radioId = when (selectedAuthType) {
                            AuthType.TOKEN -> com.muort.upworker.R.id.radioToken
                            AuthType.GLOBAL_API_KEY -> com.muort.upworker.R.id.radioGlobalApiKey
                        }
                        binding.authTypeRadioGroup.check(radioId)
                        
                        // 同时填充所有认证凭据（不再互斥）
                        binding.tokenEditText.setText(it.token)
                        binding.emailEditText.setText(it.email ?: "")
                        binding.globalApiKeyEditText.setText(it.globalApiKey ?: "")
                        
                        // Load zones for this account
                        viewModel.loadZonesForAccount(it.id)
                    }
                }
            }
        }
    }
    
    private fun setupZoneButtons() {
        binding.manageZonesBtn.setOnClickListener {
            if (args.accountId == -1L) {
                showToast("请先保存账号")
                return@setOnClickListener
            }

            showZoneManagementDialog()
        }
    }
    
    private fun setupSaveButton() {
        binding.saveBtn.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val accountId = binding.accountIdEditText.text.toString().trim()
            val token = binding.tokenEditText.text.toString().trim()
            val email = binding.emailEditText.text.toString().trim()
            val globalApiKey = binding.globalApiKeyEditText.text.toString().trim()
            val r2AccessKeyId = binding.r2AccessKeyIdEditText.text.toString().trim()
            val r2SecretAccessKey = binding.r2SecretAccessKeyEditText.text.toString().trim()
            val isDefault = binding.defaultSwitch.isChecked
            
            // Get selected zone ID from ViewModel (backward compatibility with old zoneId field)
            val selectedZoneId = if (args.accountId != -1L) {
                viewModel.getSelectedZoneId(args.accountId)
            } else null
            
            if (name.isEmpty()) {
                showToast("请输入账号名称")
                return@setOnClickListener
            }
            
            if (accountId.isEmpty()) {
                showToast("请输入 Account ID")
                return@setOnClickListener
            }
            
            // 根据认证类型验证凭据
            when (selectedAuthType) {
                AuthType.TOKEN -> {
                    if (token.isEmpty()) {
                        showToast("请输入 API Token")
                        return@setOnClickListener
                    }
                }
                AuthType.GLOBAL_API_KEY -> {
                    if (email.isEmpty()) {
                        showToast("请输入 Cloudflare 邮箱")
                        return@setOnClickListener
                    }
                    if (globalApiKey.isEmpty()) {
                        showToast("请输入 Global API Key")
                        return@setOnClickListener
                    }
                }
            }
            
            if (args.accountId == -1L) {
                // Add new account
                viewModel.addAccount(
                    name, 
                    accountId, 
                    token.ifEmpty { "" },
                    selectedZoneId,
                    isDefault,
                    r2AccessKeyId.ifEmpty { null },
                    r2SecretAccessKey.ifEmpty { null },
                    email.ifEmpty { null },
                    globalApiKey.ifEmpty { null },
                    selectedAuthType.name
                )
            } else {
                // Update existing account
                viewLifecycleOwner.lifecycleScope.launch {
                    val accounts = viewModel.accounts.value
                    val existingAccount = accounts.find { it.id == args.accountId }
                    existingAccount?.let { account ->
                        viewModel.updateAccount(
                            account.copy(
                                name = name,
                                accountId = accountId,
                                token = token.ifEmpty { "" },
                                zoneId = selectedZoneId,
                                isDefault = isDefault,
                                r2AccessKeyId = r2AccessKeyId.ifEmpty { null },
                                r2SecretAccessKey = r2SecretAccessKey.ifEmpty { null },
                                email = email.ifEmpty { null },
                                globalApiKey = globalApiKey.ifEmpty { null },
                                authType = selectedAuthType.name
                            )
                        )
                    }
                }
            }
            
            findNavController().navigateUp()
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.message.collect { message ->
                    showToast(message)
                }
            }
        }
    }
    
    private fun showZoneManagementDialog() {
        val zones = viewModel.zones.value
        val selectedZone = viewModel.selectedZone.value
        
        if (zones.isEmpty()) {
            showToast("暂无域名，请先从API获取")
            return
        }
        
        val items = zones.map { zone ->
            val status = if (zone.status == "active") "✓" else "○"
            val selected = if (zone.id == selectedZone?.id) " [已选择]" else ""
            "$status ${zone.name}$selected"
        }.toTypedArray()
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择域名")
            .setSingleChoiceItems(items, zones.indexOfFirst { it.id == selectedZone?.id }) { dialog, which ->
                val zone = zones[which]
                viewModel.selectZone(args.accountId, zone.id)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
