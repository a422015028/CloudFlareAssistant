package com.muort.upworker.feature.pages

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import androidx.core.content.ContextCompat
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.DnsRecordRequest
import com.muort.upworker.core.model.PagesDeployment
import com.muort.upworker.core.model.PagesDeploymentLogLine
import com.muort.upworker.core.model.PagesDeploymentLogs
import com.muort.upworker.core.model.PagesDomain
import com.muort.upworker.core.model.DEFAULT_COMPATIBILITY_DATE
import com.muort.upworker.core.model.PagesProject
import com.muort.upworker.core.model.PagesProjectDetail
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.TailResult
import com.muort.upworker.feature.pages.CleanupResult
import com.muort.upworker.core.repository.KvRepository
import com.muort.upworker.core.repository.R2Repository
import com.muort.upworker.core.repository.D1Repository
import com.muort.upworker.core.util.RemoteFileResolver
import com.muort.upworker.core.util.hasSupportedExtension
import com.muort.upworker.core.util.isRemoteUrl
import com.muort.upworker.databinding.DialogPagesInputBinding
import com.muort.upworker.databinding.DialogPagesRuntimeSettingsBinding
import com.muort.upworker.core.model.Placement
import com.muort.upworker.databinding.FragmentPagesBinding
import com.muort.upworker.feature.attachDatePicker
import com.muort.upworker.feature.attachInlineFlagSuggestions
import com.muort.upworker.feature.bindPlacement
import com.muort.upworker.databinding.ItemPagesProjectBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class PagesFragment : Fragment() {
    
    private var _binding: FragmentPagesBinding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val pagesViewModel: PagesViewModel by viewModels()
    
    @Inject
    lateinit var kvRepository: KvRepository
    
    @Inject
    lateinit var r2Repository: R2Repository
    
    @Inject
    lateinit var d1Repository: D1Repository

    @Inject
    lateinit var workerRepository: com.muort.upworker.core.repository.WorkerRepository
    
    @Inject
    lateinit var dnsRepository: com.muort.upworker.core.repository.DnsRepository

    @Inject
    lateinit var zoneRepository: com.muort.upworker.core.repository.ZoneRepository
    
    private lateinit var projectAdapter: ProjectAdapter
    
    private var selectedFile: File? = null
    
    // 批量删除相关属性
    private var isSelectionMode = false
    private val selectedProjects = mutableSetOf<String>()
    
    // 部署记录对话框引用
    private var deploymentsDialog: android.app.Dialog? = null
    
    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleFileSelected(uri)
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPagesBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAdapter()
        setupClickListeners()
        observeViewModel()
        
        accountViewModel.defaultAccount.value?.let { account ->
            pagesViewModel.loadProjects(account)
        }
    }
    
    private fun handleFileSelected(uri: Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val fileName = getFileNameFromUri(uri)
            
            if (fileName != null && inputStream != null) {
                // Create temp file in cache directory
                val cacheFile = File(requireContext().cacheDir, fileName)
                cacheFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                inputStream.close()
                
                selectedFile = cacheFile
                binding.filePathEdit.setText(cacheFile.absolutePath)
                
                // Auto-populate project name from file name if empty
                // 格式：原文件名-4位随机字母 (如: test-hfdh)
                if (binding.projectNameEdit.text.isNullOrEmpty()) {
                    val baseName = fileName.substringBeforeLast(".")
                    val randomSuffix = generateRandomSuffix()
                    val projectName = "$baseName-$randomSuffix"
                    binding.projectNameEdit.setText(projectName)
                }
                
                Timber.d("File selected: ${cacheFile.name}, size: ${cacheFile.length()} bytes")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to handle selected file")
            showToast(getString(R.string.msg_file_process_failed, e.message ?: "null"))
        }
    }
    
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex)
                }
            }
        }
        return fileName
    }

    private fun generateRandomSuffix(): String {
        val chars = ('a'..'z').toList()
        return (1..4).map { chars.random() }.joinToString("")
    }
    
    private fun showToast(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    private fun setupAdapter() {
        projectAdapter = ProjectAdapter(
            onProjectClick = { _ ->
                // Click on card - no action for now
            },
            onDeleteClick = { project ->
                showDeleteProjectDialog(project)
            },
            onConfigEnvClick = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showConfigDialog(account, project, "production", "env")
                }
            },
            onConfigSecretClick = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showConfigDialog(account, project, "production", "secret")
                }
            },
            onConfigKvClick = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showConfigDialog(account, project, "production", "kv")
                }
            },
            onConfigD1Click = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showConfigDialog(account, project, "production", "d1")
                }
            },
            onConfigServiceClick = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showConfigDialog(account, project, "production", "services")
                }
            },
            onConfigR2Click = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showConfigDialog(account, project, "production", "r2")
                }
            },
            onViewDeploymentsClick = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showDeploymentsDialogWithLoading(account, project)
                }
            },
            onViewDomainsClick = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showDomainsDialog(account, project)
                }
            },
            onAddDomainClick = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showAddDomainDialog(account, project)
                }
            },
            onRuntimeSettingsClick = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showPagesRuntimeSettingsDialog(account, project)
                }
            },
            onLogsClick = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showProjectLiveLogs(account, project)
                }
            },
            onSelectionModeClick = { project, isSelected ->
                if (isSelected) {
                    selectedProjects.add(project.name)
                } else {
                    selectedProjects.remove(project.name)
                }
                updateSelectionUI()
            }
        )
        binding.projectRecyclerView.adapter = projectAdapter
        
        // Setup batch operation buttons
        setupBatchOperationUI()
    }
    
    private fun setupBatchOperationUI() {
        val selectionActionsLayout = binding.root.findViewById<android.widget.LinearLayout>(
            resources.getIdentifier("pagesSelectionActionsLayout", "id", requireContext().packageName)
        )
        
        val toggleSelectionBtn = binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("pagesToggleSelectionModeBtn", "id", requireContext().packageName)
        )
        
        val selectionStatusText = binding.root.findViewById<android.widget.TextView>(
            resources.getIdentifier("pagesSelectionStatusText", "id", requireContext().packageName)
        )
        
        val selectAllBtn = binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("pagesSelectAllBtn", "id", requireContext().packageName)
        )
        
        val batchDeleteBtn = binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("pagesBatchDeleteBtn", "id", requireContext().packageName)
        )
        
        toggleSelectionBtn?.text = if (isSelectionMode) getString(R.string.cancel) else getString(R.string.pages_manage_projects)
        selectionActionsLayout?.visibility = if (isSelectionMode) android.view.View.VISIBLE else android.view.View.GONE
        selectionStatusText?.text = resources.getQuantityString(R.plurals.pages_selected_projects, selectedProjects.size, selectedProjects.size)
        batchDeleteBtn?.isEnabled = selectedProjects.isNotEmpty()
        
        toggleSelectionBtn?.setOnClickListener {
            toggleSelectionMode()
        }
        
        selectAllBtn?.setOnClickListener {
            selectAllProjects()
        }
        
        batchDeleteBtn?.setOnClickListener {
            if (selectedProjects.isNotEmpty()) {
                showBatchDeleteConfirmDialog()
            }
        }
    }
    
    // ==================== Pages 运行时设置 ====================

    private fun showPagesRuntimeSettingsDialog(account: Account, project: PagesProject) {
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_loading)
            .setMessage(R.string.pages_fetching_runtime_settings)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        pagesViewModel.getProjectDetail(account, project.name) { result ->
            requireActivity().runOnUiThread {
                loadingDialog.dismiss()
                if (result !is Resource.Success) {
                    val msg = (result as? Resource.Error)?.message ?: getString(R.string.msg_unknown_error)
                    showToast(getString(R.string.pages_get_settings_failed, msg))
                    return@runOnUiThread
                }
                showPagesRuntimeSettingsForm(account, project, result.data)
            }
        }
    }

    private fun showPagesRuntimeSettingsForm(
        account: Account,
        project: PagesProject,
        detail: PagesProjectDetail
    ) {
        val dialogBinding = DialogPagesRuntimeSettingsBinding.inflate(layoutInflater)

        dialogBinding.projectNameText.text = getString(R.string.pages_project_name_label, project.name)

        // 读取当前生产环境配置（回显）
        val envConfig = detail.deploymentConfigs?.production
        dialogBinding.compatibilityDateInput.setText(envConfig?.compatibilityDate ?: "2025-01-01")
        if (!envConfig?.compatibilityFlags.isNullOrEmpty()) {
            dialogBinding.compatibilityFlagsInput.setText(envConfig?.compatibilityFlags?.joinToString("\n"))
        }
        dialogBinding.placementModeGroup.bindPlacement(null, null, envConfig?.placement?.mode)
        dialogBinding.dateInputLayout.attachDatePicker(this, dialogBinding.compatibilityDateInput)
        dialogBinding.compatibilityFlagsInput.attachInlineFlagSuggestions()

        // 环境选择
        dialogBinding.chipProduction.isChecked = true
        var selectedEnv = "production"
        dialogBinding.chipProduction.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedEnv = "production"
                applyEnvConfig(dialogBinding, detail, "production")
            }
        }
        dialogBinding.chipPreview.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedEnv = "preview"
                applyEnvConfig(dialogBinding, detail, "preview")
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.worker_runtime_settings)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val compatibilityDate = dialogBinding.compatibilityDateInput.text.toString().trim()
                    .takeIf { it.isNotEmpty() } ?: "2025-01-01"
                val compatibilityFlags = dialogBinding.compatibilityFlagsInput.text.toString().trim()
                    .split(Regex("[,\\n]"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                val placement = if (dialogBinding.placementModeGroup.checkedRadioButtonId == R.id.placementSmart) Placement(mode = "smart") else null

                // 保存时：环境选择仅用于回显提示，Cloudflare Pages API 会同时更新生产+预览
                val envLabel = if (selectedEnv == "production") getString(R.string.pages_generic_project_env_production) else getString(R.string.pages_generic_project_env_preview)
                showToast(getString(R.string.pages_updating_runtime_settings, envLabel))

                pagesViewModel.updateRuntimeSettings(
                    account = account,
                    projectName = project.name,
                    compatibilityDate = compatibilityDate,
                    compatibilityFlags = compatibilityFlags,
                    placement = placement
                ) { saveResult ->
                    requireActivity().runOnUiThread {
                        when (saveResult) {
                            is Resource.Success -> showToast(getString(R.string.pages_runtime_settings_updated))
                            is Resource.Error -> showToast(getString(R.string.msg_update_failed, saveResult.message))
                            else -> {}
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applyEnvConfig(
        dialogBinding: DialogPagesRuntimeSettingsBinding,
        detail: PagesProjectDetail,
        env: String
    ) {
        val envConfig = if (env == "production") detail.deploymentConfigs?.production else detail.deploymentConfigs?.preview
        dialogBinding.compatibilityDateInput.setText(envConfig?.compatibilityDate ?: "")
        dialogBinding.compatibilityFlagsInput.setText(envConfig?.compatibilityFlags?.joinToString("\n") ?: "")
        dialogBinding.placementModeGroup.bindPlacement(null, null, envConfig?.placement?.mode)
    }

    private fun showProjectManagementDialog(account: Account, project: PagesProject) {
        val options = arrayOf(
            getString(R.string.pages_menu_view_deployments),
            getString(R.string.pages_menu_env_vars_prod),
            getString(R.string.pages_menu_env_vars_preview),
            getString(R.string.pages_menu_secrets_prod),
            getString(R.string.pages_menu_secrets_preview),
            getString(R.string.pages_menu_kv_bindings),
            getString(R.string.pages_menu_r2_bindings),
            getString(R.string.pages_menu_d1_bindings),
            getString(R.string.pages_menu_service_bindings),
            getString(R.string.pages_menu_runtime_settings)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.pages_project_management, project.name))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showDeploymentsDialogWithLoading(account, project)
                    1 -> showConfigDialog(account, project, "production", "env")
                    2 -> showConfigDialog(account, project, "preview", "env")
                    3 -> showConfigDialog(account, project, "production", "secret")
                    4 -> showConfigDialog(account, project, "preview", "secret")
                    5 -> showConfigDialog(account, project, "production", "kv")
                    6 -> showConfigDialog(account, project, "production", "r2")
                    7 -> showConfigDialog(account, project, "production", "d1")
                    8 -> showConfigDialog(account, project, "production", "services")
                    9 -> showPagesRuntimeSettingsDialog(account, project)
                }
            }
            .setNegativeButton(R.string.dialog_close, null)
            .show()
    }

    private fun showConfigDialog(account: Account, project: PagesProject, environment: String, configType: String) {
        when (configType) {
            "env" -> showVariablesDialog(account, project, environment)
            "secret" -> showSecretsDialog(account, project, environment)
            "kv" -> showKvBindingsDialog(account, project, environment)
            "r2" -> showR2BindingsDialog(account, project, environment)
            "d1" -> showD1BindingsDialog(account, project, environment)
            "services" -> showServiceBindingsDialog(account, project, environment)
        }
    }
    
    private fun showVariablesDialog(account: Account, project: PagesProject, environment: String) {
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_loading_ellipsis)
            .setMessage(R.string.worker_env_fetching_vars)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        // Fetch current project detail to get existing variables
        viewLifecycleOwner.lifecycleScope.launch {
            pagesViewModel.getProjectDetail(account, project.name) { projectResult ->
                loadingDialog.dismiss()

                val dialogBinding = com.muort.upworker.databinding.DialogPagesVariablesBinding.inflate(layoutInflater)

                // Setup title
                dialogBinding.titleText.text = getString(R.string.pages_configure_env_vars)
                val env = if (environment == "production") getString(R.string.pages_generic_project_env_production) else getString(R.string.pages_generic_project_env_preview)
                dialogBinding.projectNameText.text = getString(R.string.pages_project_name_with_env, project.name, env)
                dialogBinding.listTitleText.text = getString(R.string.pages_variables_list_title)
                dialogBinding.noVariablesText.text = getString(R.string.pages_no_variables)

                // Triple<名称, 值, 类型>: 环境变量对话框只管理 plain_text 类型
                val tempVariables = mutableListOf<Triple<String, String, String>>()
                val originalVariables = mutableListOf<Triple<String, String, String>>()

                // Load existing plain_text variables from project settings
                if (projectResult is Resource.Success) {
                    val envConfig = if (environment == "production") {
                        projectResult.data.deploymentConfigs?.production
                    } else {
                        projectResult.data.deploymentConfigs?.preview
                    }
                    envConfig?.envVars?.forEach { (varName, varValue) ->
                        val type = varValue.type ?: "plain_text"
                        if (type == "plain_text") {
                            val value = varValue.value ?: ""
                            val variable = Triple(varName, value, type)
                            tempVariables.add(variable)
                            originalVariables.add(variable)
                            Timber.d("Loaded existing variable: $varName, value=$value")
                        }
                    }
                }

                // Setup adapter
                lateinit var tempAdapter: PagesVariablesAndSecretsAdapter
                tempAdapter = PagesVariablesAndSecretsAdapter(
                    onEditClick = { variable ->
                        showEditVariableOrSecretDialogForPages(tempVariables, variable) {
                            tempAdapter.submitList(tempVariables.toList())
                            updateVariablesDialogUI(dialogBinding, tempAdapter, tempVariables)
                        }
                    },
                    onDeleteClick = { variable ->
                        tempVariables.remove(variable)
                        tempAdapter.submitList(tempVariables.toList())
                        updateVariablesDialogUI(dialogBinding, tempAdapter, tempVariables)
                    }
                )
                dialogBinding.variablesRecyclerView.apply {
                    layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                    adapter = tempAdapter
                }

                // Add variable button
                dialogBinding.addVariableBtn.apply {
                    text = getString(R.string.pages_add_variable)
                    setOnClickListener {
                        showAddVariableOrSecretDialogForPages(tempVariables, isSecret = false) {
                            tempAdapter.submitList(tempVariables.toList())
                            updateVariablesDialogUI(dialogBinding, tempAdapter, tempVariables)
                        }
                    }
                }

                updateVariablesDialogUI(dialogBinding, tempAdapter, tempVariables)

                // Show dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.dialog_apply_config) { _, _ ->
                        applyVariablesToPages(account, project, environment, originalVariables, tempVariables)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun showSecretsDialog(account: Account, project: PagesProject, environment: String) {
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_loading_ellipsis)
            .setMessage(R.string.worker_secret_fetching_vars)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        // Fetch current project detail to get existing secrets
        viewLifecycleOwner.lifecycleScope.launch {
            pagesViewModel.getProjectDetail(account, project.name) { projectResult ->
                loadingDialog.dismiss()

                val dialogBinding = com.muort.upworker.databinding.DialogPagesVariablesBinding.inflate(layoutInflater)

                // Setup title
                dialogBinding.titleText.text = getString(R.string.pages_configure_secrets)
                val env = if (environment == "production") getString(R.string.pages_generic_project_env_production) else getString(R.string.pages_generic_project_env_preview)
                dialogBinding.projectNameText.text = getString(R.string.pages_project_name_with_env, project.name, env)
                dialogBinding.listTitleText.text = getString(R.string.pages_secrets_list_title)
                dialogBinding.noVariablesText.text = getString(R.string.pages_no_secrets)

                // Triple<名称, 值, 类型>: 机密对话框只管理 secret_text 类型
                // 机密加密保存无法获取，加载时值设为空字符串
                val tempSecrets = mutableListOf<Triple<String, String, String>>()
                val originalSecrets = mutableListOf<Triple<String, String, String>>()

                // Load existing secret_text from project settings
                if (projectResult is Resource.Success) {
                    val envConfig = if (environment == "production") {
                        projectResult.data.deploymentConfigs?.production
                    } else {
                        projectResult.data.deploymentConfigs?.preview
                    }
                    envConfig?.envVars?.forEach { (varName, varValue) ->
                        val type = varValue.type ?: "plain_text"
                        if (type == "secret_text") {
                            // Secret values are encrypted and cannot be retrieved
                            val secret = Triple(varName, "", type)
                            tempSecrets.add(secret)
                            originalSecrets.add(secret)
                            Timber.d("Loaded existing secret: $varName")
                        }
                    }
                }

                // Setup adapter
                lateinit var tempAdapter: PagesVariablesAndSecretsAdapter
                tempAdapter = PagesVariablesAndSecretsAdapter(
                    onEditClick = { variable ->
                        showEditVariableOrSecretDialogForPages(tempSecrets, variable) {
                            tempAdapter.submitList(tempSecrets.toList())
                            updateVariablesDialogUI(dialogBinding, tempAdapter, tempSecrets)
                        }
                    },
                    onDeleteClick = { variable ->
                        tempSecrets.remove(variable)
                        tempAdapter.submitList(tempSecrets.toList())
                        updateVariablesDialogUI(dialogBinding, tempAdapter, tempSecrets)
                    }
                )
                dialogBinding.variablesRecyclerView.apply {
                    layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                    adapter = tempAdapter
                }

                // Add secret button
                dialogBinding.addVariableBtn.apply {
                    text = getString(R.string.pages_add_secret)
                    setOnClickListener {
                        showAddVariableOrSecretDialogForPages(tempSecrets, isSecret = true) {
                            tempAdapter.submitList(tempSecrets.toList())
                            updateVariablesDialogUI(dialogBinding, tempAdapter, tempSecrets)
                        }
                    }
                }

                updateVariablesDialogUI(dialogBinding, tempAdapter, tempSecrets)

                // Show dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.dialog_apply_config) { _, _ ->
                        applySecretsToPages(account, project, environment, originalSecrets, tempSecrets)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }
    
    private fun updateVariablesDialogUI(
        dialogBinding: com.muort.upworker.databinding.DialogPagesVariablesBinding,
        adapter: PagesVariablesAndSecretsAdapter,
        variables: List<Triple<String, String, String>>
    ) {
        if (variables.isEmpty()) {
            dialogBinding.noVariablesText.visibility = View.VISIBLE
            dialogBinding.variablesRecyclerView.visibility = View.GONE
        } else {
            dialogBinding.noVariablesText.visibility = View.GONE
            dialogBinding.variablesRecyclerView.visibility = View.VISIBLE
            adapter.submitList(variables.toList())
        }
    }
    
    private fun showAddVariableOrSecretDialogForPages(
        tempVariables: MutableList<Triple<String, String, String>>,
        isSecret: Boolean = false,
        onAdded: () -> Unit
    ) {
        val dialogBinding = com.muort.upworker.databinding.DialogAddPagesVariableBinding.inflate(layoutInflater)

        val type = if (isSecret) "secret_text" else "plain_text"
        val label = if (isSecret) getString(R.string.pages_label_secret) else getString(R.string.pages_label_variable)

        dialogBinding.dialogTitleText.text = getString(R.string.pages_add_dialog_title, label)
        dialogBinding.variableTypeText.text = if (isSecret) "secret" else "txt"

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.add) { _, _ ->
                val name = dialogBinding.variableNameEdit.text.toString().trim()
                val value = dialogBinding.variableValueEdit.text.toString().trim()

                if (name.isEmpty()) {
                    showToast(getString(R.string.pages_please_enter_name_template, label))
                    return@setPositiveButton
                }

                if (value.isEmpty()) {
                    showToast(getString(R.string.pages_please_enter_value_template, label))
                    return@setPositiveButton
                }

                tempVariables.add(Triple(name, value, type))
                onAdded()
                showToast(if (isSecret) getString(R.string.pages_generic_secret_added) else getString(R.string.pages_generic_env_added))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showEditVariableOrSecretDialogForPages(
        tempVariables: MutableList<Triple<String, String, String>>,
        variable: Triple<String, String, String>,
        onEdited: () -> Unit
    ) {
        val dialogBinding = com.muort.upworker.databinding.DialogAddPagesVariableBinding.inflate(layoutInflater)

        val isSecret = variable.third == "secret_text"
        val label = if (isSecret) getString(R.string.pages_label_secret) else getString(R.string.pages_label_variable)

        dialogBinding.dialogTitleText.text = getString(R.string.pages_generic_edit_template, label)
        dialogBinding.variableTypeText.text = if (isSecret) "secret" else "txt"

        // Pre-fill with existing values
        dialogBinding.variableNameEdit.setText(variable.first)
        dialogBinding.variableNameEdit.isEnabled = false  // Can't change name

        // For secrets, don't show the old value (it's encrypted)
        if (!isSecret) {
            dialogBinding.variableValueEdit.setText(variable.second)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val newValue = dialogBinding.variableValueEdit.text.toString().trim()

                if (newValue.isEmpty()) {
                    showToast(getString(R.string.pages_please_enter_value))
                    return@setPositiveButton
                }

                // Find and update the variable
                val index = tempVariables.indexOf(variable)
                if (index >= 0) {
                    tempVariables[index] = Triple(variable.first, newValue, variable.third)
                    onEdited()
                    showToast(if (isSecret) getString(R.string.pages_generic_secret_updated) else getString(R.string.pages_generic_env_updated))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun applyVariablesToPages(
        account: Account,
        project: PagesProject,
        environment: String,
        originalVariables: List<Triple<String, String, String>>,
        newVariables: List<Triple<String, String, String>>
    ) {
        Timber.d("Applying ${newVariables.size} environment variables to Pages project '${project.name}' ($environment)")

        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_updating)
            .setMessage(R.string.pages_generic_updating_env_vars)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        // Convert to Map format for API (all plain_text, values are directly available)
        val variablesMap: MutableMap<String, Pair<String, String>?> = newVariables.associate { (name, value, type) ->
            name to (type to value)
        }.toMutableMap()

        // Add deleted variables with null values
        val newVariableNames = newVariables.map { it.first }.toSet()
        originalVariables.forEach { (name, _, _) ->
            if (name !in newVariableNames) {
                variablesMap[name] = null
                Timber.d("Marking variable for deletion: $name")
            }
        }

        pagesViewModel.updateEnvironmentVariables(account, project.name, environment, variablesMap)

        // Dismiss loading dialog after a short delay
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            loadingDialog.dismiss()
            showToast(getString(R.string.pages_generic_env_vars_updated))
        }
    }

    private fun applySecretsToPages(
        account: Account,
        project: PagesProject,
        environment: String,
        originalSecrets: List<Triple<String, String, String>>,
        newSecrets: List<Triple<String, String, String>>
    ) {
        Timber.d("Applying ${newSecrets.size} secrets to Pages project '${project.name}' ($environment)")

        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_updating)
            .setMessage(R.string.pages_generic_updating_secrets)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        // 机密加密保存无法获取，加载时值设为空字符串。
        // 对于值为空的项（表示用户未编辑的现有机密），跳过不发送，
        // 否则会用空字符串覆盖原机密值。只有用户新增/编辑过（值非空）的机密才发送。
        val secretsMap: MutableMap<String, Pair<String, String>?> = mutableMapOf()
        newSecrets.forEach { (name, value, type) ->
            if (value.isEmpty()) {
                Timber.d("Skipping unedited secret (empty value): $name")
                return@forEach
            }
            secretsMap[name] = (type to value)
        }

        // Add deleted secrets with null values
        val newSecretNames = newSecrets.map { it.first }.toSet()
        originalSecrets.forEach { (name, _, _) ->
            if (name !in newSecretNames) {
                secretsMap[name] = null
                Timber.d("Marking secret for deletion: $name")
            }
        }

        pagesViewModel.updateEnvironmentVariables(account, project.name, environment, secretsMap)

        // Dismiss loading dialog after a short delay
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            loadingDialog.dismiss()
            showToast(getString(R.string.pages_generic_secrets_updated))
        }
    }
    
    private fun showKvBindingsDialog(account: Account, project: PagesProject, environment: String) {
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_loading_ellipsis)
            .setMessage(R.string.pages_kv_fetching_bindings)
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Fetch current project detail to get existing bindings
        // Fetch all namespaces first
        lifecycleScope.launch {
            val namespacesResult = kvRepository.listNamespaces(account)
            val namespaces = if (namespacesResult is Resource.Success) namespacesResult.data else emptyList()
            // Fetch current project detail to get existing bindings
            pagesViewModel.getProjectDetail(account, project.name) { projectResult ->
                loadingDialog.dismiss()
                
                val dialogBinding = com.muort.upworker.databinding.DialogPagesKvBindingsBinding.inflate(layoutInflater)
                
                // Setup title
                val envLabel = if (environment == "production") getString(R.string.pages_generic_project_env_production) else getString(R.string.pages_generic_project_env_preview)
                dialogBinding.projectNameText.text = getString(R.string.pages_generic_project_name_with_env_template, project.name, envLabel)
                
                // Temporary list for this dialog - initialize with existing bindings
                val tempKvBindings = mutableListOf<Pair<String, String>>()
                val originalKvBindings = mutableListOf<Pair<String, String>>()
                
                // Load existing KV bindings from project settings
                if (projectResult is Resource.Success) {
                    val envConfig = if (environment == "production") {
                        projectResult.data.deploymentConfigs?.production
                    } else {
                        projectResult.data.deploymentConfigs?.preview
                    }
                    envConfig?.kvNamespaces?.forEach { (bindingName, kvBinding) ->
                        val ns = namespaces.find { it.id == kvBinding.namespaceId }
                        val nsTitle = ns?.title ?: kvBinding.namespaceId
                        val binding = Pair(bindingName, nsTitle)
                        tempKvBindings.add(binding)
                        originalKvBindings.add(binding)
                        Timber.d("Loaded existing KV binding: $bindingName -> $nsTitle")
                    }
                }
                
                // Setup adapter
                lateinit var tempAdapter: PagesKvBindingsAdapter
                tempAdapter = PagesKvBindingsAdapter(
                    onDeleteClick = { binding ->
                        tempKvBindings.remove(binding)
                        tempAdapter.submitList(tempKvBindings.toList())
                        updateKvDialogBindingsUI(dialogBinding, tempAdapter, tempKvBindings)
                    }
                )
                dialogBinding.bindingsRecyclerView.apply {
                    layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                    adapter = tempAdapter
                }
                
                // Add binding button
                dialogBinding.addBindingBtn.setOnClickListener {
                    showAddKvBindingDialogForPages(account, tempKvBindings) {
                        updateKvDialogBindingsUI(dialogBinding, tempAdapter, tempKvBindings)
                    }
                }
                
                updateKvDialogBindingsUI(dialogBinding, tempAdapter, tempKvBindings)
                
                // Show dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.dialog_apply_config) { _, _ ->
                        applyKvBindingsToPages(account, project, environment, originalKvBindings, tempKvBindings)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }
    
    private fun updateKvDialogBindingsUI(
        dialogBinding: com.muort.upworker.databinding.DialogPagesKvBindingsBinding,
        adapter: PagesKvBindingsAdapter,
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
    
    private fun showAddKvBindingDialogForPages(
        account: Account,
        tempBindings: MutableList<Pair<String, String>>,
        onAdded: () -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = kvRepository.listNamespaces(account)
            
            if (result is Resource.Success) {
                val namespaces = result.data
                
                if (namespaces.isEmpty()) {
                    showToast(getString(R.string.pages_kv_no_namespaces))
                    return@launch
                }
                
                val dialogBinding = com.muort.upworker.databinding.DialogKvBindingBinding.inflate(layoutInflater)
                
                // Setup spinner
                val namespaceNames = namespaces.map { it.title }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, namespaceNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                dialogBinding.namespaceSpinner.adapter = adapter
                
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.add) { _, _ ->
                        val bindingName = dialogBinding.bindingNameEdit.text.toString().trim()
                        val selectedIndex = dialogBinding.namespaceSpinner.selectedItemPosition
                        
                        if (bindingName.isEmpty()) {
                            showToast(getString(R.string.pages_generic_please_enter_binding_name))
                            return@setPositiveButton
                        }
                        
                        if (selectedIndex >= 0 && selectedIndex < namespaces.size) {
                            val namespace = namespaces[selectedIndex]
                            tempBindings.add(Pair(bindingName, namespace.id))
                            onAdded()
                            showToast(getString(R.string.pages_kv_binding_added))
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else if (result is Resource.Error) {
                showToast(getString(R.string.pages_kv_load_namespaces_failed_template, result.message))
            }
        }
    }
    
    private fun applyKvBindingsToPages(
        account: Account,
        project: PagesProject,
        environment: String,
        originalBindings: List<Pair<String, String>>,
        newBindings: List<Pair<String, String>>
    ) {
        Timber.d("Applying ${newBindings.size} KV bindings to Pages project '${project.name}' ($environment)")
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_updating)
            .setMessage(R.string.pages_kv_updating_bindings)
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Convert to Map format for API
        // Include all new bindings with their values
        val bindingsMap = newBindings.associate { it.first to it.second as String? }.toMutableMap()
        
        // Add deleted bindings with null values
        val newBindingNames = newBindings.map { it.first }.toSet()
        originalBindings.forEach { (name, _) ->
            if (name !in newBindingNames) {
                bindingsMap[name] = null
                Timber.d("Marking KV binding for deletion: $name")
            }
        }
        
        pagesViewModel.updateKvBindings(account, project.name, environment, bindingsMap)
        
        // Dismiss loading dialog after a short delay
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            loadingDialog.dismiss()
            showToast(getString(R.string.pages_kv_bindings_updated))
        }
    }
    
    private fun showR2BindingsDialog(account: Account, project: PagesProject, environment: String) {
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_loading_ellipsis)
            .setMessage(R.string.pages_r2_fetching_bindings)
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Fetch current project detail to get existing bindings
        viewLifecycleOwner.lifecycleScope.launch {
            pagesViewModel.getProjectDetail(account, project.name) { projectResult ->
                loadingDialog.dismiss()
                
                val dialogBinding = com.muort.upworker.databinding.DialogPagesR2BindingsBinding.inflate(layoutInflater)
                
                // Setup title
                val envLabel = if (environment == "production") getString(R.string.pages_generic_project_env_production) else getString(R.string.pages_generic_project_env_preview)
                dialogBinding.projectNameText.text = getString(R.string.pages_generic_project_name_with_env_template, project.name, envLabel)
                
                // Temporary list for this dialog - initialize with existing bindings
                val tempR2Bindings = mutableListOf<Pair<String, String>>()
                val originalR2Bindings = mutableListOf<Pair<String, String>>()
                
                // Load existing R2 bindings from project settings
                if (projectResult is Resource.Success) {
                    val envConfig = if (environment == "production") {
                        projectResult.data.deploymentConfigs?.production
                    } else {
                        projectResult.data.deploymentConfigs?.preview
                    }
                    envConfig?.r2Buckets?.forEach { (bindingName, r2Binding) ->
                        val binding = Pair(bindingName, r2Binding.name)
                        tempR2Bindings.add(binding)
                        originalR2Bindings.add(binding)
                        Timber.d("Loaded existing R2 binding: $bindingName -> ${r2Binding.name}")
                    }
                }
                
                // Setup adapter
                lateinit var tempAdapter: PagesR2BindingsAdapter
                tempAdapter = PagesR2BindingsAdapter(
                    onDeleteClick = { binding ->
                        tempR2Bindings.remove(binding)
                        tempAdapter.submitList(tempR2Bindings.toList())
                        updateR2DialogBindingsUI(dialogBinding, tempAdapter, tempR2Bindings)
                    }
                )
                dialogBinding.bindingsRecyclerView.apply {
                    layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                    adapter = tempAdapter
                }
                
                // Add binding button
                dialogBinding.addBindingBtn.setOnClickListener {
                    showAddR2BindingDialogForPages(account, tempR2Bindings) {
                        updateR2DialogBindingsUI(dialogBinding, tempAdapter, tempR2Bindings)
                    }
                }
                
                updateR2DialogBindingsUI(dialogBinding, tempAdapter, tempR2Bindings)
                
                // Show dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.dialog_apply_config) { _, _ ->
                        applyR2BindingsToPages(account, project, environment, originalR2Bindings, tempR2Bindings)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }
    
    private fun updateR2DialogBindingsUI(
        dialogBinding: com.muort.upworker.databinding.DialogPagesR2BindingsBinding,
        adapter: PagesR2BindingsAdapter,
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
    
    private fun showAddR2BindingDialogForPages(
        account: Account,
        tempBindings: MutableList<Pair<String, String>>,
        onAdded: () -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = r2Repository.listBuckets(account)
            
            if (result is Resource.Success) {
                val buckets = result.data
                
                if (buckets.isEmpty()) {
                    showToast(getString(R.string.pages_r2_no_buckets))
                    return@launch
                }
                
                val dialogBinding = com.muort.upworker.databinding.DialogR2BindingBinding.inflate(layoutInflater)
                
                // Setup spinner
                val bucketNames = buckets.map { "${it.name}" }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, bucketNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                dialogBinding.bucketSpinner.adapter = adapter
                
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.add) { _, _ ->
                        val bindingName = dialogBinding.bindingNameEdit.text.toString().trim()
                        val selectedIndex = dialogBinding.bucketSpinner.selectedItemPosition
                        
                        if (bindingName.isEmpty()) {
                            showToast(getString(R.string.pages_generic_please_enter_binding_name))
                            return@setPositiveButton
                        }
                        
                        if (selectedIndex >= 0 && selectedIndex < buckets.size) {
                            val bucket = buckets[selectedIndex]
                            tempBindings.add(Pair(bindingName, bucket.name))
                            onAdded()
                            showToast(getString(R.string.pages_r2_binding_added))
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else if (result is Resource.Error) {
                showToast(getString(R.string.pages_r2_load_buckets_failed_template, result.message))
            }
        }
    }
    
    private fun applyR2BindingsToPages(
        account: Account,
        project: PagesProject,
        environment: String,
        originalBindings: List<Pair<String, String>>,
        newBindings: List<Pair<String, String>>
    ) {
        Timber.d("Applying ${newBindings.size} R2 bindings to Pages project '${project.name}' ($environment)")
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_updating)
            .setMessage(R.string.pages_r2_updating_bindings)
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Convert to Map format for API
        // Include all new bindings with their values
        val bindingsMap = newBindings.associate { it.first to it.second as String? }.toMutableMap()
        
        // Add deleted bindings with null values
        val newBindingNames = newBindings.map { it.first }.toSet()
        originalBindings.forEach { (name, _) ->
            if (name !in newBindingNames) {
                bindingsMap[name] = null
                Timber.d("Marking R2 binding for deletion: $name")
            }
        }
        
        pagesViewModel.updateR2Bindings(account, project.name, environment, bindingsMap)
        
        // Dismiss loading dialog after a short delay
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            loadingDialog.dismiss()
            showToast(getString(R.string.pages_r2_bindings_updated))
        }
    }
    

    private fun showD1BindingsDialog(account: Account, project: PagesProject, environment: String) {
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_loading_ellipsis)
            .setMessage(R.string.pages_d1_fetching_bindings)
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Fetch current project detail to get existing bindings
        viewLifecycleOwner.lifecycleScope.launch {
            pagesViewModel.getProjectDetail(account, project.name) { projectResult ->
                loadingDialog.dismiss()
                
                val dialogBinding = com.muort.upworker.databinding.DialogPagesD1BindingsBinding.inflate(layoutInflater)
                
                // Setup title
                val envLabel = if (environment == "production") getString(R.string.pages_generic_project_env_production) else getString(R.string.pages_generic_project_env_preview)
                dialogBinding.projectNameText.text = getString(R.string.pages_generic_project_name_with_env_template, project.name, envLabel)
                
                // Temporary list for this dialog - initialize with existing bindings
                val tempD1Bindings = mutableListOf<Pair<String, String>>()
                val originalD1Bindings = mutableListOf<Pair<String, String>>()
                
                // Load existing D1 bindings from project settings
                if (projectResult is Resource.Success) {
                    val envConfig = if (environment == "production") {
                        projectResult.data.deploymentConfigs?.production
                    } else {
                        projectResult.data.deploymentConfigs?.preview
                    }
                    envConfig?.d1Databases?.forEach { (bindingName, d1Binding) ->
                        val binding = Pair(bindingName, d1Binding.id)
                        tempD1Bindings.add(binding)
                        originalD1Bindings.add(binding)
                        Timber.d("Loaded existing D1 binding: $bindingName -> ${d1Binding.id}")
                    }
                }
                
                // Setup adapter
                lateinit var tempAdapter: PagesD1BindingsAdapter
                tempAdapter = PagesD1BindingsAdapter(
                    onDeleteClick = { binding ->
                        tempD1Bindings.remove(binding)
                        tempAdapter.submitList(tempD1Bindings.toList())
                        updateD1DialogBindingsUI(dialogBinding, tempAdapter, tempD1Bindings)
                    }
                )
                dialogBinding.bindingsRecyclerView.apply {
                    layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                    adapter = tempAdapter
                }
                
                // Add binding button
                dialogBinding.addBindingBtn.setOnClickListener {
                    showAddD1BindingDialogForPages(account, tempD1Bindings) {
                        updateD1DialogBindingsUI(dialogBinding, tempAdapter, tempD1Bindings)
                    }
                }
                
                updateD1DialogBindingsUI(dialogBinding, tempAdapter, tempD1Bindings)
                
                // Show dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.dialog_apply_config) { _, _ ->
                        applyD1BindingsToPages(account, project, environment, originalD1Bindings, tempD1Bindings)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }
    
    private fun updateD1DialogBindingsUI(
        dialogBinding: com.muort.upworker.databinding.DialogPagesD1BindingsBinding,
        adapter: PagesD1BindingsAdapter,
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
    
    private fun showAddD1BindingDialogForPages(
        account: Account,
        tempBindings: MutableList<Pair<String, String>>,
        onAdded: () -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = d1Repository.listDatabases(account)
            
            if (result is Resource.Success) {
                val databases = result.data
                
                if (databases.isEmpty()) {
                    showToast(getString(R.string.pages_d1_no_databases))
                    return@launch
                }
                
                val dialogBinding = com.muort.upworker.databinding.DialogPagesD1BindingBinding.inflate(layoutInflater)
                
                // Setup spinner
                val databaseNames = databases.map { "${it.name}" }
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, databaseNames)
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                dialogBinding.databaseSpinner.adapter = adapter
                
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.add) { _, _ ->
                        val bindingName = dialogBinding.bindingNameEdit.text.toString().trim()
                        val selectedIndex = dialogBinding.databaseSpinner.selectedItemPosition
                        
                        if (bindingName.isEmpty()) {
                            showToast(getString(R.string.pages_generic_please_enter_binding_name))
                            return@setPositiveButton
                        }
                        
                        if (selectedIndex >= 0 && selectedIndex < databases.size) {
                            val database = databases[selectedIndex]
                            tempBindings.add(Pair(bindingName, database.uuid))
                            onAdded()
                            showToast(getString(R.string.pages_d1_binding_added))
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else if (result is Resource.Error) {
                showToast(getString(R.string.pages_d1_load_databases_failed_template, result.message))
            }
        }
    }
    
    private fun applyD1BindingsToPages(
        account: Account,
        project: PagesProject,
        environment: String,
        originalBindings: List<Pair<String, String>>,
        newBindings: List<Pair<String, String>>
    ) {
        Timber.d("Applying ${newBindings.size} D1 bindings to Pages project '${project.name}' ($environment)")
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_updating)
            .setMessage(R.string.pages_d1_updating_bindings)
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Convert to Map format for API
        // Include all new bindings with their values
        val bindingsMap = newBindings.associate { it.first to it.second as String? }.toMutableMap()
        
        // Add deleted bindings with null values
        val newBindingNames = newBindings.map { it.first }.toSet()
        originalBindings.forEach { (name, _) ->
            if (name !in newBindingNames) {
                bindingsMap[name] = null
                Timber.d("Marking D1 binding for deletion: $name")
            }
        }
        
        pagesViewModel.updateD1Bindings(account, project.name, environment, bindingsMap)

        // Dismiss loading dialog after a short delay
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            loadingDialog.dismiss()
            showToast(getString(R.string.pages_d1_bindings_updated))
        }
    }

    private fun showServiceBindingsDialog(account: Account, project: PagesProject, environment: String) {
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_loading_ellipsis)
            .setMessage(R.string.pages_service_fetching_bindings)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        // Fetch current project detail to get existing bindings
        viewLifecycleOwner.lifecycleScope.launch {
            pagesViewModel.getProjectDetail(account, project.name) { projectResult ->
                loadingDialog.dismiss()

                val dialogBinding = com.muort.upworker.databinding.DialogPagesServicesBinding.inflate(layoutInflater)

                // Setup title
                val envLabel = if (environment == "production") getString(R.string.pages_generic_project_env_production) else getString(R.string.pages_generic_project_env_preview)
                dialogBinding.projectNameText.text = getString(R.string.pages_generic_project_name_with_env_template, project.name, envLabel)

                // Temporary list for this dialog - Triple: (bindingName, serviceName, serviceEnv)
                val tempServiceBindings = mutableListOf<Triple<String, String, String>>()
                val originalBindingNames = mutableListOf<String>()

                // Load existing service bindings from project settings
                if (projectResult is Resource.Success) {
                    val envConfig = if (environment == "production") {
                        projectResult.data.deploymentConfigs?.production
                    } else {
                        projectResult.data.deploymentConfigs?.preview
                    }
                    envConfig?.services?.forEach { (bindingName, svc) ->
                        tempServiceBindings.add(Triple(bindingName, svc.service, svc.environment))
                        originalBindingNames.add(bindingName)
                        Timber.d("Loaded existing service binding: $bindingName -> ${svc.service}")
                    }
                }

                // Setup adapter
                lateinit var tempAdapter: PagesServiceBindingsAdapter
                tempAdapter = PagesServiceBindingsAdapter(
                    onDeleteClick = { binding ->
                        tempServiceBindings.remove(binding)
                        tempAdapter.submitList(tempServiceBindings.toList())
                        updateServicesDialogBindingsUI(dialogBinding, tempAdapter, tempServiceBindings)
                    }
                )
                dialogBinding.bindingsRecyclerView.apply {
                    layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())
                    adapter = tempAdapter
                }

                // Add binding button
                dialogBinding.addBindingBtn.setOnClickListener {
                    showAddServiceBindingDialogForPages(account, project, tempServiceBindings) {
                        updateServicesDialogBindingsUI(dialogBinding, tempAdapter, tempServiceBindings)
                    }
                }

                updateServicesDialogBindingsUI(dialogBinding, tempAdapter, tempServiceBindings)

                // Show dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.dialog_apply_config) { _, _ ->
                        applyServiceBindingsToPages(account, project, environment, originalBindingNames, tempServiceBindings)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        }
    }

    private fun updateServicesDialogBindingsUI(
        dialogBinding: com.muort.upworker.databinding.DialogPagesServicesBinding,
        adapter: PagesServiceBindingsAdapter,
        bindings: List<Triple<String, String, String>>
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

    private fun showAddServiceBindingDialogForPages(
        account: Account,
        project: PagesProject,
        tempBindings: MutableList<Triple<String, String, String>>,
        onAdded: () -> Unit
    ) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = workerRepository.listWorkerScripts(account)

            if (result is Resource.Success) {
                val workers = result.data.filter { it.id != project.name }

                if (workers.isEmpty()) {
                    showToast(getString(R.string.pages_service_no_workers))
                    return@launch
                }

                val dialogBinding = com.muort.upworker.databinding.DialogPagesServiceBindingBinding.inflate(layoutInflater)

                // Setup worker spinner
                val workerNames = workers.map { it.id }
                val workerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, workerNames)
                workerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                dialogBinding.workerSpinner.adapter = workerAdapter

                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton(R.string.add) { _, _ ->
                        val bindingName = dialogBinding.bindingNameEdit.text.toString().trim()
                        val selectedIndex = dialogBinding.workerSpinner.selectedItemPosition

                        if (bindingName.isEmpty()) {
                            showToast(getString(R.string.pages_generic_please_enter_binding_name))
                            return@setPositiveButton
                        }

                        if (selectedIndex >= 0 && selectedIndex < workers.size) {
                            val worker = workers[selectedIndex]
                            tempBindings.add(Triple(bindingName, worker.id, "production"))
                            onAdded()
                            showToast(getString(R.string.pages_service_binding_added))
                        }
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            } else if (result is Resource.Error) {
                showToast(getString(R.string.pages_service_load_workers_failed_template, result.message))
            }
        }
    }

    private fun applyServiceBindingsToPages(
        account: Account,
        project: PagesProject,
        environment: String,
        originalBindingNames: List<String>,
        newBindings: List<Triple<String, String, String>>
    ) {
        Timber.d("Applying ${newBindings.size} service bindings to Pages project '${project.name}' ($environment)")

        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_updating)
            .setMessage(R.string.pages_service_updating_bindings)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        // Convert to Map format for API: binding name -> (service, serviceEnv), null to delete
        val bindingsMap = newBindings.associate { it.first to Pair(it.second, it.third) as Pair<String, String>? }.toMutableMap()

        // Add deleted bindings with null values
        val newBindingNames = newBindings.map { it.first }.toSet()
        originalBindingNames.forEach { name ->
            if (name !in newBindingNames) {
                bindingsMap[name] = null
                Timber.d("Marking service binding for deletion: $name")
            }
        }

        pagesViewModel.updateServiceBindings(account, project.name, environment, bindingsMap)

        // Dismiss loading dialog after a short delay
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            loadingDialog.dismiss()
            showToast(getString(R.string.pages_service_bindings_updated))
        }
    }
    

    private var isDeployCardExpanded = false
    private val prefs by lazy { requireContext().getSharedPreferences("pages_prefs", android.content.Context.MODE_PRIVATE) }

    private fun setupClickListeners() {
        // 恢复卡片展开状态
        isDeployCardExpanded = prefs.getBoolean("deploy_card_expanded", false)
        binding.deployCardContent.visibility = if (isDeployCardExpanded) android.view.View.VISIBLE else android.view.View.GONE
        binding.deployCardArrow.rotation = if (isDeployCardExpanded) 180f else 0f
        
        // Deploy card expand/collapse
        binding.deployCardHeader.setOnClickListener {
            isDeployCardExpanded = !isDeployCardExpanded
            binding.deployCardContent.visibility = if (isDeployCardExpanded) android.view.View.VISIBLE else android.view.View.GONE
            binding.deployCardArrow.rotation = if (isDeployCardExpanded) 180f else 0f
            prefs.edit().putBoolean("deploy_card_expanded", isDeployCardExpanded).apply()
        }
        
        // File selection
        binding.selectFileBtn.setOnClickListener {
            selectFile()
        }
        
        // Create project button
        binding.createProjectBtn.setOnClickListener {
            showCreateProjectDialog()
        }
        
        // Deploy button
        binding.deployBtn.setOnClickListener {
            deployProject()
        }
        
        // Refresh button
        binding.refreshBtn.setOnClickListener {
            accountViewModel.defaultAccount.value?.let { account ->
                pagesViewModel.loadProjects(account)
            }
        }
        
        // Cleanup deployments button
        binding.pagesCleanupDeploymentsBtn.setOnClickListener {
            showCleanupDeploymentsDialog()
        }

        // 部署卡片：快速添加常用兼容性标志（点击输入框右侧下拉箭头直接追加）
        binding.compatibilityFlagsEdit.attachInlineFlagSuggestions()
    }
    
    private fun selectFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/javascript",
                "text/html",
                "text/javascript",
                "text/plain"
            ))
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        filePickerLauncher.launch(intent)
    }
    
    private fun deployProject() {
        val projectName = binding.projectNameEdit.text.toString().trim()
        val branch = binding.branchEdit.text.toString().trim()
        // 优先使用输入框中用户手动填写/修改的完整路径；
        // 若输入框为空，再回退到通过文件选择器已缓存的文件
        val filePathInput = binding.filePathEdit.text.toString().trim()
        val selectedOrNull = if (filePathInput.isNotEmpty()) {
            filePathInput
        } else {
            selectedFile?.absolutePath
        }

        when {
            projectName.isEmpty() -> {
                showToast(getString(R.string.pages_deploy_please_enter_project_name))
                return
            }
            branch.isEmpty() -> {
                showToast(getString(R.string.pages_deploy_please_enter_branch_name))
                return
            }
            selectedOrNull == null -> {
                showToast(getString(R.string.pages_deploy_please_select_file))
                return
            }
        }

        val customCompatibilityDate = binding.compatibilityDateEdit.text.toString().trim()
            .takeIf { it.isNotEmpty() }

        val customCompatibilityFlags = binding.compatibilityFlagsEdit.text.toString().trim()
            .takeIf { it.isNotEmpty() }
            ?.split(Regex("[,\\n]"))
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }

        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast(getString(R.string.msg_please_select_account_first))
            return
        }

        // —— 智能判断：如果输入的是 http(s):// URL → 先下载临时文件，再进入部署流程
        val selected: String = selectedOrNull ?: return
        if (isRemoteUrl(selected)) {
            if (!hasSupportedExtension(selected)) {
                showToast(getString(R.string.remote_download_unsupported_type))
                return
            }
            // 复用 deploy 的 uploadProgress + 禁用 deployBtn / createProjectBtn
            binding.uploadProgress.visibility = View.VISIBLE
            binding.uploadProgress.isIndeterminate = true
            binding.deployBtn.isEnabled = false
            binding.createProjectBtn.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val result = RemoteFileResolver.resolve(
                    context = requireContext().applicationContext,
                    url = selected,
                    onProgress = { bytes, total, _ ->
                        launch(Dispatchers.Main.immediate) {
                            if (total != null && total > 0L) {
                                binding.uploadProgress.isIndeterminate = false
                                binding.uploadProgress.max = 10000
                                binding.uploadProgress.progress =
                                    ((bytes.toDouble() / total.toDouble()) * 10000).toInt()
                            } else {
                                binding.uploadProgress.isIndeterminate = true
                            }
                        }
                    }
                )
                binding.uploadProgress.isIndeterminate = true
                when {
                    result.isSuccess -> {
                        val remoteFile = result.getOrThrow()
                        // 本地文件校验：扩展名、大小（RemoteFileResolver 内部其实已校验，但此处保持统一的中文提示文案）
                        val err = validateLocalPagesFileOrShowError(remoteFile)
                        binding.uploadProgress.visibility = View.GONE
                        binding.deployBtn.isEnabled = true
                        binding.createProjectBtn.isEnabled = true
                        if (err != null) {
                            showToast(err)
                            if (remoteFile.exists()) remoteFile.delete()
                            return@launch
                        }
                        // 输入框文本替换为本地路径，保持"输入框优先"一致
                        binding.filePathEdit.setText(remoteFile.absolutePath)
                        selectedFile = remoteFile
                        Timber.d(
                            "Deploying remote project: $projectName, branch: $branch, " +
                                    "downloaded file: ${remoteFile.name}, size=${remoteFile.length()}"
                        )
                        showDeployLogsDialog(
                            account = account,
                            projectName = projectName,
                            branch = branch,
                            file = remoteFile,
                            customCompatibilityDate = customCompatibilityDate,
                            customCompatibilityFlags = customCompatibilityFlags
                        )
                    }
                    else -> {
                        binding.uploadProgress.visibility = View.GONE
                        binding.deployBtn.isEnabled = true
                        binding.createProjectBtn.isEnabled = true
                        val msg = (result.exceptionOrNull()?.message
                            ?: getString(R.string.remote_download_unsupported_url))
                        showToast(getString(R.string.msg_download_failed, msg))
                    }
                }
            }
            return
        }

        // —— 本地路径分支
        val file = File(selectedOrNull)
        val localErr = validateLocalPagesFileOrShowError(file)
        if (localErr != null) {
            showToast(localErr)
            return
        }

        Timber.d("Deploying project: $projectName, branch: $branch, file: ${file.name}, compatibilityDate: $customCompatibilityDate, compatibilityFlags: $customCompatibilityFlags")

        showDeployLogsDialog(
            account = account,
            projectName = projectName,
            branch = branch,
            file = file,
            customCompatibilityDate = customCompatibilityDate,
            customCompatibilityFlags = customCompatibilityFlags
        )
    }

    /**
     * 对本地/下载完成后的 Pages 部署文件执行扩展名 + 存在性 + 大小校验。
     * 若校验失败返回对应中文提示；全部通过返回 null。
     */
    private fun validateLocalPagesFileOrShowError(file: File): String? {
        return when {
            !file.exists() -> getString(R.string.pages_deploy_file_not_exists)
            !file.name.endsWith(".zip", ignoreCase = true) &&
                    !file.name.endsWith(".js", ignoreCase = true) &&
                    !file.name.endsWith(".htm", ignoreCase = true) &&
                    !file.name.endsWith(".html", ignoreCase = true) ->
                getString(R.string.pages_deploy_unsupported_file_type)
            file.length() > 25 * 1024 * 1024 -> getString(R.string.pages_deploy_file_size_exceeded)
            else -> null
        }
    }

    /**
     * 显示部署日志对话框，实时显示部署过程的详细日志。
     * 部署完成前禁用关闭按钮；完成后状态徽章变色。
     */
    private fun showDeployLogsDialog(
        account: Account,
        projectName: String,
        branch: String,
        file: java.io.File,
        customCompatibilityDate: String?,
        customCompatibilityFlags: List<String>?
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_deploy_logs, null)
        val titleText = dialogView.findViewById<android.widget.TextView>(R.id.titleText)
        val projectNameText = dialogView.findViewById<android.widget.TextView>(R.id.projectNameText)
        val statusBadge = dialogView.findViewById<android.widget.LinearLayout>(R.id.statusBadge)
        val statusProgress = dialogView.findViewById<android.widget.ProgressBar>(R.id.statusProgress)
        val statusText = dialogView.findViewById<android.widget.TextView>(R.id.statusText)
        val logScrollView = dialogView.findViewById<android.widget.ScrollView>(R.id.logScrollView)
        val logContent = dialogView.findViewById<android.widget.TextView>(R.id.logContent)
        val copyBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.copyBtn)
        val closeBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeBtn)

        titleText.text = getString(R.string.pages_deploy_title_logs)
        projectNameText.text = getString(R.string.pages_deploy_project_label_template, projectName)
        logContent.text = ""

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.setCanceledOnTouchOutside(false)

        // 实时日志收集（用于一键复制）
        val logBuilder = StringBuilder()
        fun appendLog(line: String) {
            logBuilder.append(line).append('\n')
            // 在主线程追加并自动滚动到底部
            viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                logContent.text = logBuilder.toString()
                logScrollView.post { logScrollView.fullScroll(android.view.View.FOCUS_DOWN) }
            }
        }

        // 复制日志按钮
        copyBtn.setOnClickListener {
            val logs = logBuilder.toString()
            if (logs.isEmpty()) {
                showToast(getString(R.string.pages_deploy_no_logs))
                return@setOnClickListener
            }
            val clipboard = requireContext()
                .getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("deploy_logs", logs)
            clipboard.setPrimaryClip(clip)
            showToast(getString(R.string.msg_logs_copied))
        }

        // 关闭按钮（部署完成前禁用，完成后启用）
        closeBtn.setOnClickListener {
            // 部署完成后清空输入框
            binding.projectNameEdit.text?.clear()
            binding.filePathEdit.text?.clear()
            selectedFile = null
            dialog.dismiss()
        }

        // 显示进度条
        binding.uploadProgress.visibility = View.VISIBLE
        binding.deployBtn.isEnabled = false

        dialog.show()

        // 启动部署
        pagesViewModel.createDeploymentWithLogs(
            account = account,
            projectName = projectName,
            branch = branch,
            file = file,
            customCompatibilityDate = customCompatibilityDate,
            customCompatibilityFlags = customCompatibilityFlags,
            onLog = { line -> appendLog(line) },
            onComplete = { success, errorMessage ->
                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    // 隐藏进度条
                    binding.uploadProgress.visibility = View.GONE
                    binding.deployBtn.isEnabled = true

                    // 隐藏旋转进度指示器
                    statusProgress.visibility = View.GONE

                    // 更新状态徽章
                    val bgRes = if (success) R.drawable.bg_status_badge_success else R.drawable.bg_status_badge_error
                    statusBadge.setBackgroundResource(bgRes)
                    statusText.text = if (success) getString(R.string.pages_deploy_status_success) else getString(R.string.pages_deploy_status_failed)
                    val colorRes = if (success) R.color.green_500 else R.color.red_500
                    statusText.setTextColor(resources.getColor(colorRes, requireContext().theme))
                    statusProgress.indeterminateTintList = android.content.res.ColorStateList.valueOf(
                        resources.getColor(colorRes, requireContext().theme)
                    )

                    // 失败时追加错误信息
                    if (!success && errorMessage != null) {
                        appendLog(getString(R.string.pages_deploy_error_detail_template, errorMessage))
                    }

                    // 启用关闭按钮
                    closeBtn.isEnabled = true
                }
            }
        )
    }
    
    private fun showCreateProjectDialog() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast(getString(R.string.msg_please_select_account_first))
            return
        }
        
        val dialogBinding = com.muort.upworker.databinding.DialogPagesCreateProjectBinding.inflate(layoutInflater)
        dialogBinding.compatibilityDateInput.setText(DEFAULT_COMPATIBILITY_DATE)
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val projectName = dialogBinding.projectNameInput.text.toString().trim()
                val productionBranch = dialogBinding.productionBranchInput.text.toString().trim()
                val buildCommand = dialogBinding.buildCommandInput.text.toString().trim().takeIf { it.isNotEmpty() }
                val destinationDir = dialogBinding.destinationDirInput.text.toString().trim().takeIf { it.isNotEmpty() }
                val rootDir = dialogBinding.rootDirInput.text.toString().trim().takeIf { it.isNotEmpty() }
                val buildCaching = dialogBinding.buildCachingCheck.isChecked.takeIf { it }
                val compatibilityDate = dialogBinding.compatibilityDateInput.text.toString().trim().takeIf { it.isNotEmpty() }
                
                if (projectName.isEmpty()) {
                    showToast(getString(R.string.pages_create_please_enter_project_name))
                    return@setPositiveButton
                }
                
                if (productionBranch.isEmpty()) {
                    showToast(getString(R.string.pages_create_please_enter_production_branch))
                    return@setPositiveButton
                }
                
                pagesViewModel.createProject(
                    account = account,
                    name = projectName,
                    productionBranch = productionBranch,
                    buildCommand = buildCommand,
                    destinationDir = destinationDir,
                    rootDir = rootDir,
                    buildCaching = buildCaching,
                    compatibilityDate = compatibilityDate
                )
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    pagesViewModel.projects.collect { projects ->
                        projectAdapter.submitList(projects)
                        binding.emptyText.visibility = 
                            if (projects.isEmpty()) View.VISIBLE else View.GONE
                        
                        // 更新项目名称自动完成列表
                        updateProjectNameAutoComplete(projects)
                    }
                }
                
                launch {
                    pagesViewModel.loadingState.collect { isLoading ->
                        binding.progressBar.visibility = 
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    pagesViewModel.message.collect { message ->
                        val msgStr = message.asString(requireContext())
                        Snackbar.make(binding.root, msgStr, Snackbar.LENGTH_SHORT).show()
                        if (msgStr == getString(R.string.vm_msg_pages_deployment_created)) {
                            binding.projectNameEdit.text?.clear()
                            binding.filePathEdit.text?.clear()
                            selectedFile = null
                        }
                    }
                }
                
                launch {
                    accountViewModel.defaultAccount.collect { account ->
                        if (account != null) {
                            pagesViewModel.loadProjects(account)
                        }
                    }
                }
            }
        }
    }
    
    private fun updateProjectNameAutoComplete(projects: List<PagesProject>) {
        val projectNames = projects.map { it.name }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            projectNames
        )
        binding.projectNameEdit.setAdapter(adapter)
        
        // 设置点击下拉图标时显示所有选项
        binding.projectNameEdit.setOnClickListener {
            binding.projectNameEdit.showDropDown()
        }
    }
    
    private fun showAddProjectDialog() {
        val dialogBinding = DialogPagesInputBinding.inflate(layoutInflater)
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val name = dialogBinding.projectName.text.toString()
                val branch = dialogBinding.productionBranch.text.toString()
                accountViewModel.defaultAccount.value?.let { account ->
                    pagesViewModel.createProject(account, name, branch)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showDeleteProjectDialog(project: PagesProject) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pages_delete_project_title)
            .setMessage(getString(R.string.pages_delete_project_confirm_template, project.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    pagesViewModel.deleteProject(account, project.name)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    // ==================== Batch Delete Functions ====================
    
    private fun toggleSelectionMode() {
        isSelectionMode = !isSelectionMode
        selectedProjects.clear()
        projectAdapter.setSelectionMode(isSelectionMode)
        updateSelectionUI()
    }
    
    private fun selectAllProjects() {
        projectAdapter.getAllProjects().forEach { project ->
            selectedProjects.add(project.name)
        }
        projectAdapter.selectAll()
        updateSelectionUI()
    }
    
    private fun updateSelectionUI() {
        val selectionActionsLayout = binding.root.findViewById<android.widget.LinearLayout>(
            resources.getIdentifier("pagesSelectionActionsLayout", "id", requireContext().packageName)
        )
        
        val toggleSelectionBtn = binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("pagesToggleSelectionModeBtn", "id", requireContext().packageName)
        )
        
        val selectionStatusText = binding.root.findViewById<android.widget.TextView>(
            resources.getIdentifier("pagesSelectionStatusText", "id", requireContext().packageName)
        )
        
        val batchDeleteBtn = binding.root.findViewById<android.widget.Button>(
            resources.getIdentifier("pagesBatchDeleteBtn", "id", requireContext().packageName)
        )
        
        toggleSelectionBtn?.text = if (isSelectionMode) getString(R.string.cancel) else getString(R.string.pages_manage_projects)
        selectionActionsLayout?.visibility = if (isSelectionMode) android.view.View.VISIBLE else android.view.View.GONE
        selectionStatusText?.text = resources.getQuantityString(R.plurals.pages_selected_projects, selectedProjects.size, selectedProjects.size)
        batchDeleteBtn?.isEnabled = selectedProjects.isNotEmpty()
    }
    
    private fun showBatchDeleteConfirmDialog() {
        val message = if (selectedProjects.size == 1) {
            getString(R.string.pages_batch_delete_confirm_single_template, selectedProjects.first())
        } else {
            getString(R.string.pages_batch_delete_confirm_multi_template, selectedProjects.size)
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pages_batch_delete_project_title)
            .setMessage(message)
            .setPositiveButton(R.string.delete) { _, _ ->
                performBatchDelete()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun performBatchDelete() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast(getString(R.string.msg_please_select_account_first))
            return
        }
        
        val projectsToDelete = selectedProjects.toList()
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pages_generic_deleting)
            .setMessage(getString(R.string.pages_batch_deleting_message_template, projectsToDelete.size))
            .setCancelable(false)
            .create()
        progressDialog.show()
        
        var deletedCount = 0
        var failedCount = 0
        
        lifecycleScope.launch {
            projectsToDelete.forEach { projectName ->
                try {
                    pagesViewModel.deleteProject(account, projectName)
                    deletedCount++
                } catch (e: Exception) {
                    failedCount++
                    Timber.e(e, "Failed to delete project: $projectName")
                }
            }
            
            progressDialog.dismiss()
            
            selectedProjects.clear()
            isSelectionMode = false
            projectAdapter.setSelectionMode(false)
            updateSelectionUI()
            
            val message = if (failedCount == 0) {
                getString(R.string.pages_batch_delete_success_template, deletedCount)
            } else {
                getString(R.string.pages_batch_delete_mixed_template, deletedCount, failedCount)
            }
            showToast(message)
            
            // 刷新列表
            accountViewModel.defaultAccount.value?.let { acc ->
                pagesViewModel.loadProjects(acc)
            }
        }
    }
    
    private fun showDeploymentsDialogWithLoading(account: com.muort.upworker.core.model.Account, project: PagesProject) {
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.pages_deployments_title_template, project.name))
            .setMessage(R.string.dialog_loading_ellipsis)
            .setCancelable(true)
            .create()
        loadingDialog.show()
        
        viewLifecycleOwner.lifecycleScope.launch {
            pagesViewModel.selectProject(project)
            pagesViewModel.loadDeployments(account, project.name)
            
            pagesViewModel.loadingState.first { it }
            pagesViewModel.loadingState.first { !it }
            
            var runningDeploymentId: String? = null
            
            val projectDetailResult = pagesViewModel.getProjectDetailSuspend(account, project.name)
            if (projectDetailResult is Resource.Success) {
                val detail = projectDetailResult.data
                runningDeploymentId = detail.canonicalDeployment?.id ?: detail.previewDeployment?.id
            }
            
            loadingDialog.dismiss()
            showDeploymentsDialog(project, runningDeploymentId)
        }
    }
    
    private fun showDeploymentsDialog(project: PagesProject, runningDeploymentId: String?) {
        val deployments = pagesViewModel.deployments.value
        
        if (deployments.isEmpty()) {
            showToast(getString(R.string.pages_deploy_no_records))
            return
        }
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_pages_deployments, null)
        val closeBtn = dialogView.findViewById<android.widget.Button>(R.id.closeBtn)
        val recyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.deploymentsRecyclerView)
        
        val adapter = PagesDeploymentsAdapter(
            deployments = deployments,
            runningDeploymentId = runningDeploymentId,
            formatDate = { formatDeploymentDate(it) },
            onItemClick = { deployment ->
                showDeploymentDetailDialog(project, deployment, runningDeploymentId == deployment.id)
            },
            onRollbackClick = { deployment ->
                showRollbackDeploymentConfirmDialog(project, deployment)
            },
            onRetryClick = { deployment ->
                showRetryDeploymentConfirmDialog(project, deployment)
            }
        )
        
        recyclerView.apply {
            this.adapter = adapter
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
        }
        
        deploymentsDialog?.dismiss()
        deploymentsDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()
        
        closeBtn.setOnClickListener {
            deploymentsDialog?.dismiss()
            deploymentsDialog = null
        }
        
        deploymentsDialog?.show()
    }
    
    private fun showDeploymentDetailDialog(project: PagesProject, deployment: PagesDeployment, isRunning: Boolean) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pages_deployment_detail, null)
        val titleText = dialogView.findViewById<android.widget.TextView>(R.id.titleText)
        val deploymentIdText = dialogView.findViewById<android.widget.TextView>(R.id.deploymentIdText)
        val shortIdText = dialogView.findViewById<android.widget.TextView>(R.id.shortIdText)
        val projectNameText = dialogView.findViewById<android.widget.TextView>(R.id.projectNameText)
        val environmentText = dialogView.findViewById<android.widget.TextView>(R.id.environmentText)
        val urlText = dialogView.findViewById<android.widget.TextView>(R.id.urlText)
        val statusBadge = dialogView.findViewById<android.widget.LinearLayout>(R.id.statusBadge)
        val stageNameText = dialogView.findViewById<android.widget.TextView>(R.id.stageNameText)
        val createTimeText = dialogView.findViewById<android.widget.TextView>(R.id.createTimeText)
        val modifiedTimeText = dialogView.findViewById<android.widget.TextView>(R.id.modifiedTimeText)
        val triggerTypeText = dialogView.findViewById<android.widget.TextView>(R.id.triggerTypeText)
        val branchText = dialogView.findViewById<android.widget.TextView>(R.id.branchText)
        val commitHashText = dialogView.findViewById<android.widget.TextView>(R.id.commitHashText)
        val commitMessageText = dialogView.findViewById<android.widget.TextView>(R.id.commitMessageText)
        val projectIdText = dialogView.findViewById<android.widget.TextView>(R.id.projectIdText)
        val aliasesText = dialogView.findViewById<android.widget.TextView>(R.id.aliasesText)
        val isSkippedText = dialogView.findViewById<android.widget.TextView>(R.id.isSkippedText)
        val usesFunctionsText = dialogView.findViewById<android.widget.TextView>(R.id.usesFunctionsText)
        val commitDirtyText = dialogView.findViewById<android.widget.TextView>(R.id.commitDirtyText)
        val buildCommandText = dialogView.findViewById<android.widget.TextView>(R.id.buildCommandText)
        val destinationDirText = dialogView.findViewById<android.widget.TextView>(R.id.destinationDirText)
        val rootDirText = dialogView.findViewById<android.widget.TextView>(R.id.rootDirText)
        val sourceTypeText = dialogView.findViewById<android.widget.TextView>(R.id.sourceTypeText)
        val repoInfoText = dialogView.findViewById<android.widget.TextView>(R.id.repoInfoText)
        val deleteBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.deleteBtn)
        val accessBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.accessBtn)
        val liveLogsBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.liveLogsBtn)
        val closeBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeBtn)

        titleText.text = getString(R.string.pages_deploy_detail_title_template, project.name)
        deploymentIdText.text = deployment.id
        shortIdText.text = deployment.shortId ?: getString(R.string.status_unknown)
        projectNameText.text = deployment.projectName ?: project.name
        environmentText.text = deployment.environment ?: getString(R.string.status_unknown)
        urlText.text = deployment.url ?: ""
        stageNameText.text = deployment.latestStage?.name ?: getString(R.string.status_unknown)
        createTimeText.text = formatDeploymentDate(deployment.createdOn)
        modifiedTimeText.text = formatDeploymentDate(deployment.modifiedOn)
        triggerTypeText.text = deployment.deploymentTrigger?.type ?: getString(R.string.status_unknown)
        branchText.text = deployment.deploymentTrigger?.metadata?.branch ?: getString(R.string.status_unknown)
        commitHashText.text = deployment.deploymentTrigger?.metadata?.commitHash ?: getString(R.string.status_unknown)
        commitMessageText.text = deployment.deploymentTrigger?.metadata?.commitMessage ?: getString(R.string.status_unknown)
        projectIdText.text = deployment.projectId ?: getString(R.string.status_unknown)
        aliasesText.text = deployment.aliases?.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: getString(R.string.status_none)
        isSkippedText.text = deployment.isSkipped?.let { if (it) getString(R.string.pages_detail_yes) else getString(R.string.pages_detail_no) } ?: getString(R.string.status_unknown)
        usesFunctionsText.text = deployment.usesFunctions?.let { if (it) getString(R.string.pages_detail_yes) else getString(R.string.pages_detail_no) } ?: getString(R.string.status_unknown)
        commitDirtyText.text = deployment.deploymentTrigger?.metadata?.commitDirty?.let { if (it) getString(R.string.pages_detail_yes) else getString(R.string.pages_detail_no) } ?: getString(R.string.status_unknown)
        buildCommandText.text = deployment.buildConfig?.buildCommand ?: getString(R.string.status_none)
        destinationDirText.text = deployment.buildConfig?.destinationDir ?: getString(R.string.status_none)
        rootDirText.text = deployment.buildConfig?.rootDir ?: getString(R.string.status_none)
        sourceTypeText.text = deployment.source?.type ?: getString(R.string.status_none)
        repoInfoText.text = deployment.source?.config?.let { cfg ->
            val owner = cfg.owner ?: ""
            val repo = cfg.repoName ?: ""
            if (owner.isEmpty() && repo.isEmpty()) getString(R.string.status_none) else "$owner/$repo"
        } ?: getString(R.string.status_none)

        // 阶段详情
        val stageNameDetailText = dialogView.findViewById<android.widget.TextView>(R.id.stageNameDetailText)
        val stageStatusText = dialogView.findViewById<android.widget.TextView>(R.id.stageStatusText)
        val stageStartedText = dialogView.findViewById<android.widget.TextView>(R.id.stageStartedText)
        val stageEndedText = dialogView.findViewById<android.widget.TextView>(R.id.stageEndedText)
        val stagesContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.stagesContainer)

        stageNameDetailText.text = deployment.latestStage?.name ?: getString(R.string.status_unknown)
        stageStatusText.text = deployment.latestStage?.status ?: getString(R.string.status_unknown)
        stageStartedText.text = formatDeploymentDate(deployment.latestStage?.startedOn)
        stageEndedText.text = formatDeploymentDate(deployment.latestStage?.endedOn)

        // 所有阶段列表
        deployment.stages?.takeIf { it.isNotEmpty() }?.let { stageList ->
            for (stage in stageList) {
                val stageRow = android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 4
                    }
                }
                val nameTv = android.widget.TextView(requireContext()).apply {
                    text = stage.name ?: ""
                    textSize = 14f
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val statusTv = android.widget.TextView(requireContext()).apply {
                    text = stage.status ?: ""
                    textSize = 14f
                    setTextColor(
                        when (stage.status) {
                            "success" -> android.graphics.Color.parseColor("#22c55e")
                            "failure" -> android.graphics.Color.parseColor("#ef4444")
                            "active" -> android.graphics.Color.parseColor("#f59e0b")
                            "canceled" -> android.graphics.Color.parseColor("#6b7280")
                            else -> resources.getColor(android.R.color.tab_indicator_text, requireContext().theme)
                        }
                    )
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                stageRow.addView(nameTv)
                stageRow.addView(statusTv)
                stagesContainer.addView(stageRow)
            }
        }

        // 构建配置补充字段
        val buildCachingText = dialogView.findViewById<android.widget.TextView>(R.id.buildCachingText)
        val webAnalyticsTagText = dialogView.findViewById<android.widget.TextView>(R.id.webAnalyticsTagText)
        val webAnalyticsTokenText = dialogView.findViewById<android.widget.TextView>(R.id.webAnalyticsTokenText)

        buildCachingText.text = deployment.buildConfig?.buildCaching?.let { if (it) getString(R.string.pages_detail_enabled) else getString(R.string.pages_detail_disabled) } ?: getString(R.string.status_unknown)
        webAnalyticsTagText.text = deployment.buildConfig?.webAnalyticsTag ?: getString(R.string.status_none)
        webAnalyticsTokenText.text = deployment.buildConfig?.webAnalyticsToken ?: getString(R.string.status_none)

        // 源码配置补充字段
        val ownerIdText = dialogView.findViewById<android.widget.TextView>(R.id.ownerIdText)
        val repoIdText = dialogView.findViewById<android.widget.TextView>(R.id.repoIdText)
        val productionBranchText = dialogView.findViewById<android.widget.TextView>(R.id.productionBranchText)
        val deploymentsEnabledText = dialogView.findViewById<android.widget.TextView>(R.id.deploymentsEnabledText)
        val prodDeploymentsText = dialogView.findViewById<android.widget.TextView>(R.id.prodDeploymentsText)
        val prCommentsText = dialogView.findViewById<android.widget.TextView>(R.id.prCommentsText)
        val previewDeploySettingText = dialogView.findViewById<android.widget.TextView>(R.id.previewDeploySettingText)
        val pathExcludesSection = dialogView.findViewById<android.widget.LinearLayout>(R.id.pathExcludesSection)
        val pathExcludesText = dialogView.findViewById<android.widget.TextView>(R.id.pathExcludesText)
        val pathIncludesSection = dialogView.findViewById<android.widget.LinearLayout>(R.id.pathIncludesSection)
        val pathIncludesText = dialogView.findViewById<android.widget.TextView>(R.id.pathIncludesText)
        val previewBranchExcludesSection = dialogView.findViewById<android.widget.LinearLayout>(R.id.previewBranchExcludesSection)
        val previewBranchExcludesText = dialogView.findViewById<android.widget.TextView>(R.id.previewBranchExcludesText)
        val previewBranchIncludesSection = dialogView.findViewById<android.widget.LinearLayout>(R.id.previewBranchIncludesSection)
        val previewBranchIncludesText = dialogView.findViewById<android.widget.TextView>(R.id.previewBranchIncludesText)

        val srcCfg = deployment.source?.config
        ownerIdText.text = srcCfg?.ownerId ?: getString(R.string.status_unknown)
        repoIdText.text = srcCfg?.repoId ?: getString(R.string.status_unknown)
        productionBranchText.text = srcCfg?.productionBranch ?: getString(R.string.status_unknown)
        deploymentsEnabledText.text = srcCfg?.deploymentsEnabled?.let { if (it) getString(R.string.pages_detail_enabled) else getString(R.string.pages_detail_disabled) } ?: getString(R.string.status_unknown)
        prodDeploymentsText.text = srcCfg?.productionDeploymentsEnabled?.let { if (it) getString(R.string.pages_detail_enabled) else getString(R.string.pages_detail_disabled) } ?: getString(R.string.status_unknown)
        prCommentsText.text = srcCfg?.prCommentsEnabled?.let { if (it) getString(R.string.pages_detail_enabled) else getString(R.string.pages_detail_disabled) } ?: getString(R.string.status_unknown)
        previewDeploySettingText.text = srcCfg?.previewDeploymentSetting ?: getString(R.string.status_unknown)

        pathExcludesSection.visibility = if (srcCfg?.pathExcludes?.isNotEmpty() == true)
            android.view.View.VISIBLE else android.view.View.GONE
        pathExcludesText.text = srcCfg?.pathExcludes?.joinToString("\n") ?: ""

        pathIncludesSection.visibility = if (srcCfg?.pathIncludes?.isNotEmpty() == true)
            android.view.View.VISIBLE else android.view.View.GONE
        pathIncludesText.text = srcCfg?.pathIncludes?.joinToString("\n") ?: ""

        previewBranchExcludesSection.visibility = if (srcCfg?.previewBranchExcludes?.isNotEmpty() == true)
            android.view.View.VISIBLE else android.view.View.GONE
        previewBranchExcludesText.text = srcCfg?.previewBranchExcludes?.joinToString("\n") ?: ""

        previewBranchIncludesSection.visibility = if (srcCfg?.previewBranchIncludes?.isNotEmpty() == true)
            android.view.View.VISIBLE else android.view.View.GONE
        previewBranchIncludesText.text = srcCfg?.previewBranchIncludes?.joinToString("\n") ?: ""

        // 环境变量
        val envVarsSection = dialogView.findViewById<android.widget.LinearLayout>(R.id.envVarsSection)
        val envVarsContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.envVarsContainer)

        val envVars = deployment.envVars
        if (envVars != null && envVars.isNotEmpty()) {
            envVarsSection.visibility = android.view.View.VISIBLE
            for ((key, value) in envVars) {
                val envRow = android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = 8
                    }
                    setBackgroundResource(R.drawable.bg_list_item_border)
                    setPadding(12, 8, 12, 8)
                }
                val keyTv = android.widget.TextView(requireContext()).apply {
                    text = key
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                val typeTv = android.widget.TextView(requireContext()).apply {
                    text = if (value.type == "secret_text") getString(R.string.pages_detail_type_encrypted) else getString(R.string.pages_detail_type_plaintext)
                    textSize = 12f
                    setTextColor(resources.getColor(R.color.red_500, requireContext().theme))
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 2 }
                }
                val valueTv = android.widget.TextView(requireContext()).apply {
                    text = if (value.type == "secret_text") "••••••••" else (value.value ?: "")
                    textSize = 13f
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 4 }
                }
                envRow.addView(keyTv)
                envRow.addView(typeTv)
                envRow.addView(valueTv)
                envVarsContainer.addView(envRow)
            }
        } else {
            envVarsSection.visibility = android.view.View.GONE
        }

        if (isRunning) {
            val statusIcon = android.widget.ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_running)
                layoutParams = android.widget.LinearLayout.LayoutParams(14, 14)
            }
            val statusText = android.widget.TextView(requireContext()).apply {
                text = getString(R.string.status_active)
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
            val url = deployment.url ?: ""
            if (url.isNotEmpty()) {
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                requireContext().startActivity(intent)
            }
        }

        deleteBtn.setOnClickListener {
            showDeleteDeploymentConfirmDialog(project, deployment)
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }

        liveLogsBtn.setOnClickListener {
            accountViewModel.defaultAccount.value?.let { acc ->
                showProjectLogsDialog(acc, project, deployment)
            }
        }

        dialog.show()
    }

    private fun showProjectLogsDialog(
        account: com.muort.upworker.core.model.Account,
        project: PagesProject,
        deployment: PagesDeployment
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pages_logs, null)
        val titleText = dialogView.findViewById<android.widget.TextView>(R.id.titleText)
        val deploymentSelectorLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.deploymentSelectorLayout)
        val logContent = dialogView.findViewById<android.widget.TextView>(R.id.logContent)
        val logInfoText = dialogView.findViewById<android.widget.TextView>(R.id.logInfoText)
        val closeBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeBtn)

        titleText.text = getString(R.string.pages_build_logs_title_template, project.name)
        deploymentSelectorLayout.visibility = android.view.View.GONE
        logContent.text = getString(R.string.dialog_loading_ellipsis)

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        loadDeploymentLogs(account, project.name, deployment.id, logContent, logInfoText)
    }

    /**
     * 项目卡片"日志"入口：打开当前生产/预览部署的实时日志（与官网 Logs 一致）
     */
    private fun showProjectLiveLogs(account: com.muort.upworker.core.model.Account, project: PagesProject) {
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pages_live_logs_title)
            .setMessage(R.string.pages_creating_log_channel)
            .setCancelable(false)
            .create()
        loadingDialog.show()

        viewLifecycleOwner.lifecycleScope.launch {
            // 获取项目详情以定位当前生产/预览部署
            var deploymentId: String? = null
            val detailResult = pagesViewModel.getProjectDetailSuspend(account, project.name)
            if (detailResult is Resource.Success) {
                deploymentId = detailResult.data.canonicalDeployment?.id
                    ?: detailResult.data.previewDeployment?.id
            }
            if (deploymentId == null) {
                loadingDialog.dismiss()
                showToast(getString(R.string.pages_no_available_deployment))
                return@launch
            }

            val result = pagesViewModel.createDeploymentTail(account, project.name, deploymentId)
            when (result) {
                is Resource.Success<*> -> {
                    val tail = result.data as? TailResult
                    loadingDialog.dismiss()
                    if (tail?.url.isNullOrEmpty()) {
                        showToast(getString(R.string.pages_log_channel_no_wss))
                    } else {
                        PagesLogsActivity.start(requireContext(), project.name, tail!!.url)
                    }
                }
                is Resource.Error -> {
                    loadingDialog.dismiss()
                    showToast(getString(R.string.pages_log_channel_failed_template, result.message))
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun loadDeploymentLogs(
        account: com.muort.upworker.core.model.Account,
        projectName: String,
        deploymentId: String,
        logContent: android.widget.TextView,
        logInfoText: android.widget.TextView
    ) {
        lifecycleScope.launch {
            val result = pagesViewModel.getDeploymentLogs(account, projectName, deploymentId)
            when (result) {
                is Resource.Success<*> -> {
                    val logs = result.data as? PagesDeploymentLogs
                    val lines: List<PagesDeploymentLogLine> = logs?.data ?: emptyList()
                    if (lines.isEmpty()) {
                        logContent.text = getString(R.string.pages_deploy_no_logs)
                    } else {
                        val logText = lines.joinToString("\n") { line -> line.line ?: "" }
                        logContent.text = logText
                    }
                    val total = logs?.total ?: lines.size
                    val containerLogs = if (logs?.includesContainerLogs == true)
                        getString(R.string.pages_deploy_logs_include_container)
                    else
                        getString(R.string.pages_deploy_logs_build_only)
                    logInfoText.text = getString(R.string.pages_deploy_logs_info_template, total, containerLogs)
                }
                is Resource.Error -> {
                    logContent.text = getString(R.string.pages_deploy_load_logs_failed_template, result.message)
                    logInfoText.text = ""
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    private fun showAddDomainDialog(account: Account, project: PagesProject) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (context.resources.displayMetrics.density * 24).toInt(),
                (context.resources.displayMetrics.density * 16).toInt(),
                (context.resources.displayMetrics.density * 24).toInt(),
                (context.resources.displayMetrics.density * 8).toInt()
            )
        }
        val inputLayout = com.google.android.material.textfield.TextInputLayout(context).apply {
            hint = getString(R.string.pages_domain_input_hint)
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val editText = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        inputLayout.addView(editText)
        container.addView(inputLayout)

        MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.pages_domain_add_title_template, project.name))
            .setView(container)
            .setPositiveButton(R.string.add) { _, _ ->
                val hostname = editText.text?.toString()?.trim().orEmpty()
                if (hostname.isEmpty()) {
                    Snackbar.make(binding.root, getString(R.string.pages_domain_cannot_be_empty), Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val subdomain = "${project.name}.pages.dev"
                pagesViewModel.addCustomDomain(account, project.name, hostname) { result ->
                    if (result is Resource.Success) {
                        // 添加成功后直接自动配置 DNS，不弹窗询问
                        autoConfigureDnsForDomain(account, result.data, subdomain)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun autoConfigureDnsForDomain(account: Account, domain: PagesDomain, subdomain: String) {
        val validation = domain.validationData
        val recordType = when (validation?.method) {
            "txt" -> "TXT"
            else -> "CNAME"
        }
        val recordName = validation?.txtName?.takeIf { it.isNotEmpty() } ?: domain.name
        val recordValue = validation?.txtValue?.takeIf { it.isNotEmpty() } ?: subdomain

        viewLifecycleOwner.lifecycleScope.launch {
            // 根据 hostname 自动匹配 zone
            val zone = zoneRepository.findZoneByHostname(account.id, domain.name)
            if (zone == null) {
                Snackbar.make(
                    binding.root,
                    getString(R.string.pages_domain_no_zone_found),
                    Snackbar.LENGTH_LONG
                ).show()
                return@launch
            }

            Snackbar.make(binding.root, getString(R.string.pages_domain_auto_configuring_dns), Snackbar.LENGTH_SHORT).show()
            val dnsRequest = DnsRecordRequest(
                type = recordType,
                name = recordName,
                content = recordValue,
                proxied = true,
                ttl = 1
            )
            when (val result = dnsRepository.createDnsRecord(account, zone.id, dnsRequest)) {
                is Resource.Success -> {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.pages_domain_added_and_dns_ok),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                is Resource.Error -> {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.pages_domain_added_but_dns_failed_template, result.message, recordType),
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                is Resource.Loading -> {}
            }
        }
    }

    private fun showDomainsDialog(account: com.muort.upworker.core.model.Account, project: PagesProject) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pages_domains, null)
        val titleText = dialogView.findViewById<android.widget.TextView>(R.id.titleText)
        val loadingProgress = dialogView.findViewById<android.widget.ProgressBar>(R.id.loadingProgress)
        val domainsRecyclerView = dialogView.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.domainsRecyclerView)
        val closeBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeBtn)

        titleText.text = getString(R.string.pages_domain_list_title_template, project.name)
        loadingProgress.visibility = android.view.View.VISIBLE
        domainsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(requireContext())

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }

        val previewDomain = "${project.name}.pages.dev"

        fun loadDomains() {
            loadingProgress.visibility = android.view.View.VISIBLE
            lifecycleScope.launch {
                val result = pagesViewModel.listDomainsSuspend(account, project.name)
                loadingProgress.visibility = android.view.View.GONE
                if (result is Resource.Success<*>) {
                    @Suppress("UNCHECKED_CAST")
                    val domains = result.data as List<PagesDomain>
                    domainsRecyclerView.adapter = DomainAdapter(previewDomain, domains) { domain ->
                        confirmDeleteDomain(account, project, domain) {
                            loadDomains()
                        }
                    }
                } else {
                    domainsRecyclerView.adapter = DomainAdapter(previewDomain, emptyList()) { domain ->
                        confirmDeleteDomain(account, project, domain) {
                            loadDomains()
                        }
                    }
                }
            }
        }

        loadDomains()
        dialog.show()
    }

    private fun confirmDeleteDomain(
        account: Account,
        project: PagesProject,
        domain: PagesDomain,
        onDeleted: () -> Unit
    ) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pages_domain_delete_title)
            .setMessage(getString(R.string.pages_domain_delete_confirm_template, domain.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val result = pagesViewModel.deleteDomainSuspend(account, project.name, domain.name)) {
                        is Resource.Success -> {
                            Snackbar.make(binding.root, getString(R.string.pages_domain_delete_success), Snackbar.LENGTH_SHORT).show()
                            onDeleted()
                        }
                        is Resource.Error -> {
                            Snackbar.make(binding.root, getString(R.string.pages_domain_delete_failed_template, result.message), Snackbar.LENGTH_LONG).show()
                        }
                        is Resource.Loading -> {}
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private class DomainAdapter(
        private val previewDomain: String,
        private val customDomains: List<PagesDomain>,
        private val onDeleteClick: (PagesDomain) -> Unit
    ) : RecyclerView.Adapter<DomainAdapter.ViewHolder>() {

        private val PREVIEW_TYPE = 0
        private val CUSTOM_TYPE = 1

        override fun getItemViewType(position: Int): Int {
            return if (position == 0) PREVIEW_TYPE else CUSTOM_TYPE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_pages_domain, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            if (position == 0) {
                holder.bindPreview(previewDomain)
            } else {
                holder.bindCustom(customDomains[position - 1])
            }
        }

        override fun getItemCount() = 1 + customDomains.size

        inner class ViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
            private val nameText = itemView.findViewById<android.widget.TextView>(R.id.domainNameText)
            private val statusText = itemView.findViewById<android.widget.TextView>(R.id.domainStatusText)
            private val infoText = itemView.findViewById<android.widget.TextView>(R.id.domainInfoText)
            private val errorText = itemView.findViewById<android.widget.TextView>(R.id.domainErrorText)
            private val deleteBtn = itemView.findViewById<android.widget.ImageButton>(R.id.deleteDomainBtn)

            fun bindPreview(domain: String) {
                nameText.text = domain
                nameText.setOnClickListener {
                    copyToClipboard("https://$domain", itemView.context.getString(R.string.pages_domain_preview_copied))
                }
                statusText.text = itemView.context.getString(R.string.pages_domain_status_preview)
                statusText.setBackgroundColor(
                    ContextCompat.getColor(itemView.context, R.color.blue)
                )
                statusText.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.white)
                )
                infoText.text = itemView.context.getString(R.string.pages_domain_pages_default)
                errorText.visibility = android.view.View.GONE
                // 预览域名不可删除
                deleteBtn.visibility = android.view.View.GONE
            }

            fun bindCustom(domain: PagesDomain) {
                nameText.text = domain.name
                nameText.setOnClickListener {
                    copyToClipboard("https://${domain.name}", itemView.context.getString(R.string.pages_domain_custom_copied))
                }
                statusText.text = domain.status ?: itemView.context.getString(R.string.status_unknown)

                val statusColor = when (domain.status) {
                    "active" -> ContextCompat.getColor(itemView.context, R.color.status_success)
                    "pending", "initializing" -> ContextCompat.getColor(itemView.context, R.color.status_warning)
                    "error", "blocked" -> ContextCompat.getColor(itemView.context, R.color.status_error)
                    "deactivated" -> ContextCompat.getColor(itemView.context, R.color.status_neutral)
                    else -> ContextCompat.getColor(itemView.context, R.color.status_neutral)
                }
                statusText.setBackgroundColor(statusColor)
                statusText.setTextColor(
                    ContextCompat.getColor(itemView.context, R.color.white)
                )

                val method = domain.validationData?.method ?: itemView.context.getString(R.string.status_unknown)
                val createdDate = domain.createdOn?.substringBefore('T') ?: itemView.context.getString(R.string.pages_detail_unknown_time)
                infoText.text = itemView.context.getString(R.string.pages_domain_validation_info_template, method, createdDate)

                val validationError = domain.validationData?.errorMessage
                val verificationError = domain.verificationData?.errorMessage
                val errorStatus = domain.verificationData?.status
                val errorToShow = validationError ?: verificationError
                if (errorToShow != null || errorStatus == "error" || errorStatus == "blocked") {
                    errorText.text = errorToShow ?: itemView.context.getString(R.string.pages_domain_status_error_template, errorStatus ?: itemView.context.getString(R.string.status_unknown))
                    errorText.visibility = android.view.View.VISIBLE
                } else {
                    errorText.visibility = android.view.View.GONE
                }
                // 自定义域名显示删除按钮
                deleteBtn.visibility = android.view.View.VISIBLE
                deleteBtn.setOnClickListener {
                    onDeleteClick(domain)
                }
            }

            private fun copyToClipboard(text: String, message: String) {
                val clipboard = itemView.context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("url", text)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(itemView.context, message, android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun showDeleteDeploymentConfirmDialog(project: PagesProject, deployment: PagesDeployment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pages_deployment_delete_title)
            .setMessage(getString(R.string.pages_deployment_delete_confirm_template, deployment.shortId ?: deployment.id))
            .setPositiveButton(R.string.delete) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        pagesViewModel.deleteDeployment(account, project.name, deployment.id)
                        deploymentsDialog?.dismiss()
                        showDeploymentsDialogWithLoading(account, project)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRollbackDeploymentConfirmDialog(project: PagesProject, deployment: PagesDeployment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pages_deployment_rollback_title)
            .setMessage(getString(R.string.pages_deployment_rollback_confirm_template, deployment.shortId ?: deployment.id))
            .setPositiveButton(getString(R.string.pages_rollback_deployment_btn)) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        pagesViewModel.rollbackDeployment(account, project.name, deployment.id)
                        deploymentsDialog?.dismiss()
                        showDeploymentsDialogWithLoading(account, project)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showRetryDeploymentConfirmDialog(project: PagesProject, deployment: PagesDeployment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pages_deployment_retry_title)
            .setMessage(getString(R.string.pages_deployment_retry_confirm_template, deployment.shortId ?: deployment.id))
            .setPositiveButton(getString(R.string.pages_generic_redeploy)) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        pagesViewModel.retryDeployment(account, project.name, deployment.id)
                        deploymentsDialog?.dismiss()
                        showDeploymentsDialogWithLoading(account, project)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun formatDeploymentDate(dateString: String?): String {
        if (dateString == null) return getString(R.string.status_unknown)
        
        return try {
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
            inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
            
            val outputFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
            outputFormat.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
            
            // 处理可能的毫秒部分
            val cleanDateString = dateString.substringBefore('Z').substringBefore('+')
            val date = inputFormat.parse(cleanDateString)
            date?.let { outputFormat.format(it) } ?: dateString
        } catch (e: Exception) {
            dateString.substringBefore('T').replace('-', '/')
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private class ProjectAdapter(
        private val onProjectClick: (PagesProject) -> Unit,
        private val onDeleteClick: (PagesProject) -> Unit,
        private val onConfigEnvClick: (PagesProject) -> Unit,
        private val onConfigSecretClick: (PagesProject) -> Unit,
        private val onConfigKvClick: (PagesProject) -> Unit,
        private val onConfigD1Click: (PagesProject) -> Unit,
        private val onConfigServiceClick: (PagesProject) -> Unit,
        private val onConfigR2Click: (PagesProject) -> Unit,
        private val onViewDeploymentsClick: (PagesProject) -> Unit,
        private val onViewDomainsClick: (PagesProject) -> Unit,
        private val onAddDomainClick: (PagesProject) -> Unit,
        private val onRuntimeSettingsClick: (PagesProject) -> Unit,
        private val onLogsClick: (PagesProject) -> Unit,
        private val onSelectionModeClick: (PagesProject, Boolean) -> Unit = { _, _ -> }
    ) : RecyclerView.Adapter<ProjectAdapter.ViewHolder>() {
        
        private var projects = listOf<PagesProject>()
        private var selectionMode = false
        private val selectedItems = mutableSetOf<String>()
        
        fun submitList(newList: List<PagesProject>) {
            projects = newList
            notifyDataSetChanged()
        }
        
        fun setSelectionMode(enabled: Boolean) {
            selectionMode = enabled
            selectedItems.clear()
            notifyDataSetChanged()
        }
        
        fun getAllProjects(): List<PagesProject> = projects
        
        fun selectAll() {
            selectedItems.clear()
            projects.forEach { selectedItems.add(it.name) }
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemPagesProjectBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(projects[position])
        }
        
        override fun getItemCount() = projects.size
        
        inner class ViewHolder(
            private val binding: ItemPagesProjectBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(project: PagesProject) {
                binding.projectNameText.text = project.name
                
                val dateText = formatDate(project.createdOn)
                binding.projectInfoText.text = binding.root.context.getString(R.string.pages_project_info_template, project.productionBranch, dateText)
                
                // 添加多选模式支持 - 通过改变卡片背景色表示选中状态
                if (selectionMode) {
                    binding.deleteBtn.visibility = android.view.View.GONE
                    binding.viewDeploymentsBtn.visibility = android.view.View.GONE
                    binding.viewDomainsBtn.visibility = android.view.View.GONE
                    binding.addDomainBtn.visibility = android.view.View.GONE
                    binding.runtimeSettingsBtn.visibility = android.view.View.GONE
                    binding.logsBtn.visibility = android.view.View.GONE
                    
                    val isSelected = selectedItems.contains(project.name)
                    updateSelectionUI(binding.root, isSelected)
                    
                    binding.root.setOnClickListener {
                        val newSelected = !selectedItems.contains(project.name)
                        if (newSelected) {
                            selectedItems.add(project.name)
                        } else {
                            selectedItems.remove(project.name)
                        }
                        updateSelectionUI(binding.root, newSelected)
                        onSelectionModeClick(project, newSelected)
                    }
                } else {
                    binding.deleteBtn.visibility = android.view.View.VISIBLE
                    binding.viewDeploymentsBtn.visibility = android.view.View.VISIBLE
                    binding.viewDomainsBtn.visibility = android.view.View.VISIBLE
                    binding.addDomainBtn.visibility = android.view.View.VISIBLE
                    binding.runtimeSettingsBtn.visibility = android.view.View.VISIBLE
                    binding.logsBtn.visibility = android.view.View.VISIBLE
                    updateSelectionUI(binding.root, false)
                    binding.root.setOnClickListener(null)
                }
                
                binding.configEnvBtn.setOnClickListener {
                    onConfigEnvClick(project)
                }

                binding.configSecretBtn.setOnClickListener {
                    onConfigSecretClick(project)
                }

                binding.configKvBtn.setOnClickListener {
                    onConfigKvClick(project)
                }
                
                binding.configD1Btn.setOnClickListener {
                    onConfigD1Click(project)
                }

                binding.configServiceBtn.setOnClickListener {
                    onConfigServiceClick(project)
                }

                binding.configR2Btn.setOnClickListener {
                    onConfigR2Click(project)
                }
                
                binding.viewDeploymentsBtn.setOnClickListener {
                    onViewDeploymentsClick(project)
                }
                
                binding.viewDomainsBtn.setOnClickListener {
                    onViewDomainsClick(project)
                }
                
                binding.addDomainBtn.setOnClickListener {
                    onAddDomainClick(project)
                }

                binding.runtimeSettingsBtn.setOnClickListener {
                    onRuntimeSettingsClick(project)
                }
                
                binding.logsBtn.setOnClickListener {
                    onLogsClick(project)
                }
                
                binding.deleteBtn.setOnClickListener {
                    onDeleteClick(project)
                }
            }
            
            private fun updateSelectionUI(view: android.view.View, isSelected: Boolean) {
                if (isSelected) {
                    view.setAlpha(0.8f)
                    val color = view.context.getColor(android.R.color.darker_gray)
                    view.setBackgroundColor(color)
                } else {
                    view.setAlpha(1.0f)
                    view.setBackgroundColor(view.context.getColor(android.R.color.transparent))
                }
            }
            
            private fun formatDate(dateString: String?): String {
                if (dateString == null) return binding.root.context.getString(R.string.worker_detail_unknown_date)
                
                return try {
                    val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    inputFormat.timeZone = java.util.TimeZone.getTimeZone("UTC")
                    
                    val outputFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
                    outputFormat.timeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai")
                    
                    val date = inputFormat.parse(dateString)
                    date?.let { outputFormat.format(it) } ?: dateString
                } catch (e: Exception) {
                    dateString.substringBefore('T')
                }
            }
        }
    }
    
    private fun showCleanupDeploymentsDialog() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast(getString(R.string.msg_please_select_account_first))
            return
        }
        
        val dialogBinding = com.muort.upworker.databinding.DialogCleanupDeploymentsBinding.inflate(layoutInflater)
        
        val projectNames = pagesViewModel.projects.value.map { it.name }
        val projectAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, projectNames)
        projectAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.projectSpinner.adapter = projectAdapter
        
        dialogBinding.cleanupModeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            dialogBinding.singleProjectContainer.visibility = 
                if (checkedId == R.id.cleanupSingleProjectRadio) View.VISIBLE else View.GONE
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(getString(R.string.worker_cleanup_start_button)) { _, _ ->
                val retainCountStr = dialogBinding.retainCountEdit.text.toString().trim()
                val retainCount = retainCountStr.toIntOrNull() ?: 10
                
                if (dialogBinding.cleanupAllProjectsRadio.isChecked) {
                    showCleanupConfirmDialog(true, null, retainCount)
                } else {
                    val selectedProjectName = dialogBinding.projectSpinner.selectedItem?.toString()
                    if (selectedProjectName.isNullOrEmpty()) {
                        showToast(getString(R.string.pages_cleanup_please_select_project))
                        return@setPositiveButton
                    }
                    showCleanupConfirmDialog(false, selectedProjectName, retainCount)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showCleanupConfirmDialog(isAllProjects: Boolean, projectName: String?, retainCount: Int) {
        val account = accountViewModel.defaultAccount.value ?: return
        
        val title = if (isAllProjects)
            getString(R.string.pages_cleanup_all_title)
        else
            getString(R.string.pages_cleanup_single_title_template, projectName ?: "")
        val message = if (isAllProjects) {
            getString(R.string.pages_cleanup_all_message_template, retainCount)
        } else {
            getString(R.string.pages_cleanup_single_message_template, projectName ?: "", retainCount)
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(getString(R.string.worker_cleanup_confirm_button)) { dialog, _ ->
                dialog.dismiss()
                
                // 显示加载对话框
                val loadingDialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.worker_cleanup_in_progress_title)
                    .setMessage(R.string.pages_cleaning_deployments_message)
                    .setCancelable(false)
                    .show()
                
                if (isAllProjects) {
                    pagesViewModel.cleanupDeploymentsForAllProjects(account, retainCount)
                } else {
                    projectName?.let {
                        pagesViewModel.cleanupDeploymentsForSingleProject(account, it, retainCount)
                    }
                }
                
                viewLifecycleOwner.lifecycleScope.launch {
                    // 等待清理完成：先跳过初始的 false，等到 true 后再等 false
                    pagesViewModel.loadingState.dropWhile { !it }.first { !it }
                    loadingDialog.dismiss()
                    val results = pagesViewModel.cleanupResults.value
                    if (results.isNotEmpty()) {
                        showCleanupResultsDialog(results)
                        pagesViewModel.clearCleanupResults()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showCleanupResultsDialog(results: List<CleanupResult>) {
        val totalDeleted = results.sumOf { it.deletedCount }
        val totalProjects = results.size
        
        val resultBuilder = StringBuilder()
        resultBuilder.append(getString(R.string.worker_cleanup_result_header))
        resultBuilder.append("\n\n")
        
        results.forEach { result ->
            if (result.success) {
                val status = if (result.deletedCount > 0) {
                    getString(R.string.pages_cleanup_result_cleaned_template, result.deletedCount)
                } else {
                    getString(R.string.pages_cleanup_result_skip_template, result.totalDeployments)
                }
                resultBuilder.append(getString(R.string.pages_cleanup_result_line_template, result.projectName, status))
                resultBuilder.append('\n')
            } else {
                resultBuilder.append(getString(R.string.pages_cleanup_result_error_line_template, result.projectName, result.errorMessage ?: ""))
                resultBuilder.append('\n')
            }
        }
        
        resultBuilder.append('\n')
        resultBuilder.append(getString(R.string.pages_cleanup_result_total_template, totalProjects, totalDeleted))
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.worker_cleanup_finished_title)
            .setMessage(resultBuilder.toString())
            .setPositiveButton(R.string.cancel, null)
            .show()
    }
}

// ==================== Adapter Classes ====================

class PagesKvBindingsAdapter(
    private val onDeleteClick: (Pair<String, String>) -> Unit
) : RecyclerView.Adapter<PagesKvBindingsAdapter.BindingViewHolder>() {
    
    private var bindings = listOf<Pair<String, String>>()
    
    fun submitList(newBindings: List<Pair<String, String>>) {
        bindings = newBindings
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        val binding = com.muort.upworker.databinding.ItemKvBindingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BindingViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        holder.bind(bindings[position])
    }
    
    override fun getItemCount() = bindings.size
    
    inner class BindingViewHolder(
        private val binding: com.muort.upworker.databinding.ItemKvBindingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(kvBinding: Pair<String, String>) {
            binding.bindingNameText.text = kvBinding.first
            binding.namespaceIdText.text = kvBinding.second
            
            binding.deleteBindingBtn.setOnClickListener {
                onDeleteClick(kvBinding)
            }
        }
    }
}

class PagesR2BindingsAdapter(
    private val onDeleteClick: (Pair<String, String>) -> Unit
) : RecyclerView.Adapter<PagesR2BindingsAdapter.BindingViewHolder>() {
    
    private var bindings = listOf<Pair<String, String>>()
    
    fun submitList(newBindings: List<Pair<String, String>>) {
        bindings = newBindings
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        val binding = com.muort.upworker.databinding.ItemR2BindingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BindingViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        holder.bind(bindings[position])
    }
    
    override fun getItemCount() = bindings.size
    
    inner class BindingViewHolder(
        private val binding: com.muort.upworker.databinding.ItemR2BindingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(r2Binding: Pair<String, String>) {
            binding.bindingNameText.text = r2Binding.first
            binding.bucketNameText.text = binding.root.context.getString(R.string.pages_binding_bucket_label_template, r2Binding.second)
            
            binding.deleteBindingBtn.setOnClickListener {
                onDeleteClick(r2Binding)
            }
        }
    }
}

class PagesD1BindingsAdapter(
    private val onDeleteClick: (Pair<String, String>) -> Unit
) : RecyclerView.Adapter<PagesD1BindingsAdapter.BindingViewHolder>() {
    
    private var bindings = listOf<Pair<String, String>>()
    
    fun submitList(newBindings: List<Pair<String, String>>) {
        bindings = newBindings
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        val binding = com.muort.upworker.databinding.ItemPagesD1BindingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BindingViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        holder.bind(bindings[position])
    }
    
    override fun getItemCount() = bindings.size
    
    inner class BindingViewHolder(
        private val binding: com.muort.upworker.databinding.ItemPagesD1BindingBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(d1Binding: Pair<String, String>) {
            binding.bindingNameText.text = d1Binding.first
            binding.databaseNameText.text = binding.root.context.getString(R.string.pages_binding_database_label_template, d1Binding.second)
            
            binding.deleteBindingBtn.setOnClickListener {
                onDeleteClick(d1Binding)
            }
        }
    }
}

class PagesServiceBindingsAdapter(
    private val onDeleteClick: (Triple<String, String, String>) -> Unit
) : RecyclerView.Adapter<PagesServiceBindingsAdapter.BindingViewHolder>() {

    private var bindings = listOf<Triple<String, String, String>>()

    fun submitList(newBindings: List<Triple<String, String, String>>) {
        bindings = newBindings
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingViewHolder {
        val binding = com.muort.upworker.databinding.ItemPagesServiceBindingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return BindingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingViewHolder, position: Int) {
        holder.bind(bindings[position])
    }

    override fun getItemCount() = bindings.size

    inner class BindingViewHolder(
        private val binding: com.muort.upworker.databinding.ItemPagesServiceBindingBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(serviceBinding: Triple<String, String, String>) {
            binding.bindingNameText.text = serviceBinding.first
            binding.serviceNameText.text = binding.root.context.getString(R.string.pages_binding_service_label_template, serviceBinding.second)

            binding.deleteBindingBtn.setOnClickListener {
                onDeleteClick(serviceBinding)
            }
        }
    }
}

// Adapter for combined variables and secrets (Triple: name, value, type)
class PagesVariablesAndSecretsAdapter(
    private val onEditClick: (Triple<String, String, String>) -> Unit,
    private val onDeleteClick: (Triple<String, String, String>) -> Unit
) : RecyclerView.Adapter<PagesVariablesAndSecretsAdapter.VariableViewHolder>() {
    
    private var variables = listOf<Triple<String, String, String>>()
    
    fun submitList(newVariables: List<Triple<String, String, String>>) {
        variables = newVariables
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VariableViewHolder {
        val binding = com.muort.upworker.databinding.ItemVariableBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VariableViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: VariableViewHolder, position: Int) {
        holder.bind(variables[position])
    }
    
    override fun getItemCount() = variables.size
    
    inner class VariableViewHolder(
        private val binding: com.muort.upworker.databinding.ItemVariableBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(variable: Triple<String, String, String>) {
            val (name, value, type) = variable
            val isSecret = type == "secret_text"

            binding.variableNameText.text = name

            // For secrets, show encrypted indicator; for plain text, show value
            if (isSecret) {
                binding.variableValueText.text = binding.root.context.getString(R.string.pages_var_encrypted_hidden)
                binding.variableValueText.setTypeface(null, Typeface.ITALIC)
            } else {
                binding.variableValueText.text = value
                binding.variableValueText.setTypeface(null, Typeface.NORMAL)
            }

            // Show type label
            binding.variableTypeText.visibility = View.VISIBLE
            binding.variableTypeText.text = if (isSecret)
                binding.root.context.getString(R.string.pages_label_secret)
            else
                binding.root.context.getString(R.string.pages_label_variable)
            binding.variableTypeText.setBackgroundColor(
                if (isSecret)
                    binding.root.context.getColor(android.R.color.holo_red_light)
                else
                    binding.root.context.getColor(android.R.color.holo_blue_light)
            )
            
            binding.editVariableBtn.setOnClickListener {
                onEditClick(variable)
            }
            
            binding.deleteVariableBtn.setOnClickListener {
                onDeleteClick(variable)
            }
        }
    }
}
