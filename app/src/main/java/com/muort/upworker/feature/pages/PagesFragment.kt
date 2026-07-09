package com.muort.upworker.feature.pages

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
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
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.DnsRecordRequest
import com.muort.upworker.core.model.PagesDeployment
import com.muort.upworker.core.model.PagesDeploymentLogLine
import com.muort.upworker.core.model.PagesDeploymentLogs
import com.muort.upworker.core.model.PagesDomain
import com.muort.upworker.core.model.DEFAULT_COMPATIBILITY_DATE
import com.muort.upworker.core.model.PagesProject
import com.muort.upworker.core.model.Resource
import com.muort.upworker.feature.pages.CleanupResult
import com.muort.upworker.core.repository.KvRepository
import com.muort.upworker.core.repository.R2Repository
import com.muort.upworker.core.repository.D1Repository
import com.muort.upworker.databinding.DialogPagesInputBinding
import com.muort.upworker.databinding.FragmentPagesBinding
import com.muort.upworker.databinding.ItemPagesProjectBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.launch
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
    lateinit var dnsRepository: com.muort.upworker.core.repository.DnsRepository
    
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
                binding.filePathEdit.setText(cacheFile.name)
                
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
            showToast("文件处理失败: ${e.message}")
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
            onLogsClick = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showProjectLogsDialog(account, project)
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
        
        toggleSelectionBtn?.text = if (isSelectionMode) "取消" else "管理项目"
        selectionActionsLayout?.visibility = if (isSelectionMode) android.view.View.VISIBLE else android.view.View.GONE
        selectionStatusText?.text = "已选择 ${selectedProjects.size} 个项目"
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
    
    private fun showProjectManagementDialog(account: Account, project: PagesProject) {
        val options = arrayOf(
            "查看部署",
            "环境变量 (生产)",
            "环境变量 (预览)",
            "KV 绑定",
            "R2 绑定",
            "D1 绑定",
            "机密 (Secrets)"
        )
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${project.name} - 项目管理")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showDeploymentsDialogWithLoading(account, project)
                    1 -> showConfigDialog(account, project, "production", "env")
                    2 -> showConfigDialog(account, project, "preview", "env")
                    3 -> showConfigDialog(account, project, "production", "kv")
                    4 -> showConfigDialog(account, project, "production", "r2")
                    5 -> showConfigDialog(account, project, "production", "d1")
                    6 -> showConfigDialog(account, project, "production", "secret")
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun showConfigDialog(account: Account, project: PagesProject, environment: String, configType: String) {
        when (configType) {
            "env" -> showVariablesDialog(account, project, environment)
            "kv" -> showKvBindingsDialog(account, project, environment)
            "r2" -> showR2BindingsDialog(account, project, environment)
            "d1" -> showD1BindingsDialog(account, project, environment)
        }
    }
    
    private fun showVariablesDialog(account: Account, project: PagesProject, environment: String) {
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("加载中...")
            .setMessage("正在获取当前环境变量配置")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Fetch current project detail to get existing variables
        viewLifecycleOwner.lifecycleScope.launch {
            pagesViewModel.getProjectDetail(account, project.name) { projectResult ->
                loadingDialog.dismiss()
                
                val dialogBinding = com.muort.upworker.databinding.DialogPagesVariablesBinding.inflate(layoutInflater)
                
                // Setup title
                dialogBinding.projectNameText.text = "项目名称: ${project.name} (${if (environment == "production") "生产" else "预览"}环境)"
                
                // Temporary lists for this dialog - initialize with existing variables and secrets
                // Triple<名称, 值, 类型>: 类型为 "plain_text" 表示环境变量，"secret_text" 表示机密
                val tempVariables = mutableListOf<Triple<String, String, String>>()
                val originalVariables = mutableListOf<Triple<String, String, String>>()
                
                // Load existing environment variables and secrets from project settings
                if (projectResult is Resource.Success) {
                    val envConfig = if (environment == "production") {
                        projectResult.data.deploymentConfigs?.production
                    } else {
                        projectResult.data.deploymentConfigs?.preview
                    }
                    envConfig?.envVars?.forEach { (varName, varValue) ->
                        val value = varValue.value ?: ""
                        val type = varValue.type ?: "plain_text"
                        val variable = Triple(varName, if (type == "secret_text") "" else value, type)
                        tempVariables.add(variable)
                        originalVariables.add(variable)
                        Timber.d("Loaded existing variable: $varName (type=$type), value=${if (type == "secret_text") "***" else value}")
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
                    text = "+ 添加变量/机密"
                    setOnClickListener {
                        showAddVariableOrSecretDialogForPages(tempVariables) {
                            tempAdapter.submitList(tempVariables.toList())
                            updateVariablesDialogUI(dialogBinding, tempAdapter, tempVariables)
                        }
                    }
                }
                
                updateVariablesDialogUI(dialogBinding, tempAdapter, tempVariables)
                
                // Show dialog
                MaterialAlertDialogBuilder(requireContext())
                    .setView(dialogBinding.root)
                    .setPositiveButton("应用配置") { _, _ ->
                        applyVariablesToPages(account, project, environment, originalVariables, tempVariables)
                    }
                    .setNegativeButton("取消", null)
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
        onAdded: () -> Unit
    ) {
        val dialogBinding = com.muort.upworker.databinding.DialogAddPagesVariableBinding.inflate(layoutInflater)
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("添加") { _, _ ->
                val name = dialogBinding.variableNameEdit.text.toString().trim()
                val value = dialogBinding.variableValueEdit.text.toString().trim()
                val type = if (dialogBinding.typeSecretRadio.isChecked) "secret_text" else "plain_text"
                
                if (name.isEmpty()) {
                    showToast("请输入变量名称")
                    return@setPositiveButton
                }
                
                if (value.isEmpty()) {
                    showToast("请输入变量值")
                    return@setPositiveButton
                }
                
                tempVariables.add(Triple(name, value, type))
                onAdded()
                showToast(if (type == "secret_text") "机密已添加" else "环境变量已添加")
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showEditVariableOrSecretDialogForPages(
        tempVariables: MutableList<Triple<String, String, String>>,
        variable: Triple<String, String, String>,
        onEdited: () -> Unit
    ) {
        val dialogBinding = com.muort.upworker.databinding.DialogAddPagesVariableBinding.inflate(layoutInflater)
        
        val isSecret = variable.third == "secret_text"
        val title = if (isSecret) "编辑机密" else "编辑环境变量"
        
        // Pre-fill with existing values
        dialogBinding.variableNameEdit.setText(variable.first)
        dialogBinding.variableNameEdit.isEnabled = false  // Can't change name
        
        // For secrets, don't show the old value (it's encrypted)
        if (!isSecret) {
            dialogBinding.variableValueEdit.setText(variable.second)
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val newValue = dialogBinding.variableValueEdit.text.toString().trim()
                
                if (newValue.isEmpty()) {
                    showToast("请输入值")
                    return@setPositiveButton
                }
                
                // Find and update the variable
                val index = tempVariables.indexOf(variable)
                if (index >= 0) {
                    tempVariables[index] = Triple(variable.first, newValue, variable.third)
                    onEdited()
                    showToast(if (isSecret) "机密已更新" else "环境变量已更新")
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun applyVariablesToPages(
        account: Account,
        project: PagesProject,
        environment: String,
        originalVariables: List<Triple<String, String, String>>,
        newVariables: List<Triple<String, String, String>>
    ) {
        val plainTextCount = newVariables.count { it.third == "plain_text" }
        val secretCount = newVariables.count { it.third == "secret_text" }
        Timber.d("Applying $plainTextCount environment variables and $secretCount secrets to Pages project '${project.name}' ($environment)")
        
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("正在更新...")
            .setMessage("正在更新环境变量和机密配置")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Convert to Map format for API
        // Include all new variables and secrets with their values and types
        val variablesMap: MutableMap<String, Pair<String, String>?> = newVariables.associate { (name, value, type) ->
            name to (type to value)
        }.toMutableMap()
        
        // Add deleted variables with null values
        val newVariableNames = newVariables.map { it.first }.toSet()
        originalVariables.forEach { (name, _, _) ->
            if (name !in newVariableNames) {
                variablesMap[name] = null
                Timber.d("Marking variable/secret for deletion: $name")
            }
        }
        
        pagesViewModel.updateEnvironmentVariables(account, project.name, environment, variablesMap)
        
        // Dismiss loading dialog after a short delay
        lifecycleScope.launch {
            kotlinx.coroutines.delay(500)
            loadingDialog.dismiss()
            showToast("环境变量和机密配置已更新")
        }
    }
    
    private fun showKvBindingsDialog(account: Account, project: PagesProject, environment: String) {
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("加载中...")
            .setMessage("正在获取当前 KV 绑定配置")
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
                dialogBinding.projectNameText.text = "项目名称: ${project.name} (${if (environment == "production") "生产" else "预览"}环境)"
                
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
                    .setPositiveButton("应用配置") { _, _ ->
                        applyKvBindingsToPages(account, project, environment, originalKvBindings, tempKvBindings)
                    }
                    .setNegativeButton("取消", null)
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
                    showToast("暂无 KV 命名空间，请先创建")
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
            } else if (result is Resource.Error) {
                showToast("加载 KV 命名空间失败: ${result.message}")
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
            .setTitle("正在更新...")
            .setMessage("正在更新 KV 绑定配置")
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
            showToast("KV 绑定配置已更新")
        }
    }
    
    private fun showR2BindingsDialog(account: Account, project: PagesProject, environment: String) {
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("加载中...")
            .setMessage("正在获取当前 R2 绑定配置")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Fetch current project detail to get existing bindings
        viewLifecycleOwner.lifecycleScope.launch {
            pagesViewModel.getProjectDetail(account, project.name) { projectResult ->
                loadingDialog.dismiss()
                
                val dialogBinding = com.muort.upworker.databinding.DialogPagesR2BindingsBinding.inflate(layoutInflater)
                
                // Setup title
                dialogBinding.projectNameText.text = "项目名称: ${project.name} (${if (environment == "production") "生产" else "预览"}环境)"
                
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
                    .setPositiveButton("应用配置") { _, _ ->
                        applyR2BindingsToPages(account, project, environment, originalR2Bindings, tempR2Bindings)
                    }
                    .setNegativeButton("取消", null)
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
                    showToast("暂无 R2 存储桶，请先创建")
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
            } else if (result is Resource.Error) {
                showToast("加载 R2 存储桶失败: ${result.message}")
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
            .setTitle("正在更新...")
            .setMessage("正在更新 R2 绑定配置")
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
            showToast("R2 绑定配置已更新")
        }
    }
    

    private fun showD1BindingsDialog(account: Account, project: PagesProject, environment: String) {
        // Show loading dialog
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("加载中...")
            .setMessage("正在获取当前 D1 绑定配置")
            .setCancelable(false)
            .create()
        loadingDialog.show()
        
        // Fetch current project detail to get existing bindings
        viewLifecycleOwner.lifecycleScope.launch {
            pagesViewModel.getProjectDetail(account, project.name) { projectResult ->
                loadingDialog.dismiss()
                
                val dialogBinding = com.muort.upworker.databinding.DialogPagesD1BindingsBinding.inflate(layoutInflater)
                
                // Setup title
                dialogBinding.projectNameText.text = "项目名称: ${project.name} (${if (environment == "production") "生产" else "预览"}环境)"
                
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
                    .setPositiveButton("应用配置") { _, _ ->
                        applyD1BindingsToPages(account, project, environment, originalD1Bindings, tempD1Bindings)
                    }
                    .setNegativeButton("取消", null)
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
                    showToast("暂无 D1 数据库，请先创建")
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
                    .setPositiveButton("添加") { _, _ ->
                        val bindingName = dialogBinding.bindingNameEdit.text.toString().trim()
                        val selectedIndex = dialogBinding.databaseSpinner.selectedItemPosition
                        
                        if (bindingName.isEmpty()) {
                            showToast("请输入绑定名称")
                            return@setPositiveButton
                        }
                        
                        if (selectedIndex >= 0 && selectedIndex < databases.size) {
                            val database = databases[selectedIndex]
                            tempBindings.add(Pair(bindingName, database.uuid))
                            onAdded()
                            showToast("D1 绑定已添加")
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else if (result is Resource.Error) {
                showToast("加载 D1 数据库失败: ${result.message}")
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
            .setTitle("正在更新...")
            .setMessage("正在更新 D1 绑定配置")
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
            showToast("D1 绑定配置已更新")
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
        
        binding.filePathEdit.setOnClickListener {
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
        val file = selectedFile
        
        when {
            projectName.isEmpty() -> {
                showToast("请输入项目名称")
                return
            }
            branch.isEmpty() -> {
                showToast("请输入分支名称")
                return
            }
            file == null -> {
                showToast("请选择部署文件")
                return
            }
            !file.exists() -> {
                showToast("文件不存在")
                return
            }
            !file.name.endsWith(".zip", ignoreCase = true) &&
            !file.name.endsWith(".js", ignoreCase = true) &&
            !file.name.endsWith(".htm", ignoreCase = true) &&
            !file.name.endsWith(".html", ignoreCase = true) -> {
                showToast("仅支持 .zip、.js 或 .html 文件")
                return
            }
            file.length() > 25 * 1024 * 1024 -> {
                showToast("文件大小不能超过 25MB")
                return
            }
        }
        
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }

        val customCompatibilityDate = binding.compatibilityDateEdit.text.toString().trim()
            .takeIf { it.isNotEmpty() }
        
        // Show progress
        binding.uploadProgress.visibility = View.VISIBLE
        binding.deployBtn.isEnabled = false
        
        Timber.d("Deploying project: $projectName, branch: $branch, file: ${file?.name}, compatibilityDate: $customCompatibilityDate")
        
        pagesViewModel.createDeployment(account, projectName, branch, file!!, customCompatibilityDate)
        
        // Hide progress after a delay (will be handled by loading state)
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(1000)
            binding.uploadProgress.visibility = View.GONE
            binding.deployBtn.isEnabled = true
        }
    }
    
    private fun showCreateProjectDialog() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        
        val dialogBinding = com.muort.upworker.databinding.DialogPagesCreateProjectBinding.inflate(layoutInflater)
        dialogBinding.compatibilityDateInput.setText(DEFAULT_COMPATIBILITY_DATE)
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("创建") { _, _ ->
                val projectName = dialogBinding.projectNameInput.text.toString().trim()
                val productionBranch = dialogBinding.productionBranchInput.text.toString().trim()
                val buildCommand = dialogBinding.buildCommandInput.text.toString().trim().takeIf { it.isNotEmpty() }
                val destinationDir = dialogBinding.destinationDirInput.text.toString().trim().takeIf { it.isNotEmpty() }
                val rootDir = dialogBinding.rootDirInput.text.toString().trim().takeIf { it.isNotEmpty() }
                val buildCaching = dialogBinding.buildCachingCheck.isChecked.takeIf { it }
                val compatibilityDate = dialogBinding.compatibilityDateInput.text.toString().trim().takeIf { it.isNotEmpty() }
                
                if (projectName.isEmpty()) {
                    showToast("请输入项目名称")
                    return@setPositiveButton
                }
                
                if (productionBranch.isEmpty()) {
                    showToast("请输入生产分支")
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
            .setNegativeButton("取消", null)
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
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                        if (message == "部署创建成功") {
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
            .setPositiveButton("创建") { _, _ ->
                val name = dialogBinding.projectName.text.toString()
                val branch = dialogBinding.productionBranch.text.toString()
                accountViewModel.defaultAccount.value?.let { account ->
                    pagesViewModel.createProject(account, name, branch)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteProjectDialog(project: PagesProject) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除项目")
            .setMessage("确定要删除项目 \"${project.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    pagesViewModel.deleteProject(account, project.name)
                }
            }
            .setNegativeButton("取消", null)
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
        
        toggleSelectionBtn?.text = if (isSelectionMode) "取消" else "管理项目"
        selectionActionsLayout?.visibility = if (isSelectionMode) android.view.View.VISIBLE else android.view.View.GONE
        selectionStatusText?.text = "已选择 ${selectedProjects.size} 个项目"
        batchDeleteBtn?.isEnabled = selectedProjects.isNotEmpty()
    }
    
    private fun showBatchDeleteConfirmDialog() {
        val message = if (selectedProjects.size == 1) {
            "确定要删除 1 个项目吗？\n\n${selectedProjects.first()}\n\n此操作无法撤销。"
        } else {
            "确定要删除 ${selectedProjects.size} 个项目吗？此操作无法撤销。"
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("批量删除项目")
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
        
        val projectsToDelete = selectedProjects.toList()
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除中...")
            .setMessage("正在删除 ${projectsToDelete.size} 个项目")
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
                "成功删除 $deletedCount 个项目"
            } else {
                "删除了 $deletedCount 个项目，$failedCount 个失败"
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
            .setTitle("${project.name} - 部署记录")
            .setMessage("加载中...")
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
            showToast("暂无部署记录")
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
        val closeBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeBtn)

        titleText.text = "${project.name} - 部署详情"
        deploymentIdText.text = deployment.id
        shortIdText.text = deployment.shortId ?: "未知"
        projectNameText.text = deployment.projectName ?: project.name
        environmentText.text = deployment.environment ?: "未知"
        urlText.text = deployment.url ?: ""
        stageNameText.text = deployment.latestStage?.name ?: "未知"
        createTimeText.text = formatDeploymentDate(deployment.createdOn)
        modifiedTimeText.text = formatDeploymentDate(deployment.modifiedOn)
        triggerTypeText.text = deployment.deploymentTrigger?.type ?: "未知"
        branchText.text = deployment.deploymentTrigger?.metadata?.branch ?: "未知"
        commitHashText.text = deployment.deploymentTrigger?.metadata?.commitHash ?: "未知"
        commitMessageText.text = deployment.deploymentTrigger?.metadata?.commitMessage ?: "未知"
        projectIdText.text = deployment.projectId ?: "未知"
        aliasesText.text = deployment.aliases?.takeIf { it.isNotEmpty() }?.joinToString("\n") ?: "无"
        isSkippedText.text = deployment.isSkipped?.let { if (it) "是" else "否" } ?: "未知"
        usesFunctionsText.text = deployment.usesFunctions?.let { if (it) "是" else "否" } ?: "未知"
        commitDirtyText.text = deployment.deploymentTrigger?.metadata?.commitDirty?.let { if (it) "是" else "否" } ?: "未知"
        buildCommandText.text = deployment.buildConfig?.buildCommand ?: "无"
        destinationDirText.text = deployment.buildConfig?.destinationDir ?: "无"
        rootDirText.text = deployment.buildConfig?.rootDir ?: "无"
        sourceTypeText.text = deployment.source?.type ?: "无"
        repoInfoText.text = deployment.source?.config?.let { cfg ->
            val owner = cfg.owner ?: ""
            val repo = cfg.repoName ?: ""
            if (owner.isEmpty() && repo.isEmpty()) "无" else "$owner/$repo"
        } ?: "无"

        // 阶段详情
        val stageNameDetailText = dialogView.findViewById<android.widget.TextView>(R.id.stageNameDetailText)
        val stageStatusText = dialogView.findViewById<android.widget.TextView>(R.id.stageStatusText)
        val stageStartedText = dialogView.findViewById<android.widget.TextView>(R.id.stageStartedText)
        val stageEndedText = dialogView.findViewById<android.widget.TextView>(R.id.stageEndedText)
        val stagesContainer = dialogView.findViewById<android.widget.LinearLayout>(R.id.stagesContainer)

        stageNameDetailText.text = deployment.latestStage?.name ?: "未知"
        stageStatusText.text = deployment.latestStage?.status ?: "未知"
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

        buildCachingText.text = deployment.buildConfig?.buildCaching?.let { if (it) "启用" else "禁用" } ?: "未知"
        webAnalyticsTagText.text = deployment.buildConfig?.webAnalyticsTag ?: "无"
        webAnalyticsTokenText.text = deployment.buildConfig?.webAnalyticsToken ?: "无"

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
        ownerIdText.text = srcCfg?.ownerId ?: "未知"
        repoIdText.text = srcCfg?.repoId ?: "未知"
        productionBranchText.text = srcCfg?.productionBranch ?: "未知"
        deploymentsEnabledText.text = srcCfg?.deploymentsEnabled?.let { if (it) "启用" else "禁用" } ?: "未知"
        prodDeploymentsText.text = srcCfg?.productionDeploymentsEnabled?.let { if (it) "启用" else "禁用" } ?: "未知"
        prCommentsText.text = srcCfg?.prCommentsEnabled?.let { if (it) "启用" else "禁用" } ?: "未知"
        previewDeploySettingText.text = srcCfg?.previewDeploymentSetting ?: "未知"

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
                    text = if (value.type == "secret_text") "🔒 加密" else "📝 明文"
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

        dialog.show()
    }
    
    private fun showProjectLogsDialog(account: com.muort.upworker.core.model.Account, project: PagesProject) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_pages_logs, null)
        val titleText = dialogView.findViewById<android.widget.TextView>(R.id.titleText)
        val deploymentSelector = dialogView.findViewById<android.widget.AutoCompleteTextView>(R.id.deploymentSelector)
        val logContent = dialogView.findViewById<android.widget.TextView>(R.id.logContent)
        val logInfoText = dialogView.findViewById<android.widget.TextView>(R.id.logInfoText)
        val closeBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.closeBtn)

        titleText.text = "${project.name} - 部署日志"
        logContent.text = "加载中..."

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .create()

        closeBtn.setOnClickListener {
            dialog.dismiss()
        }

        lifecycleScope.launch {
            val result = pagesViewModel.getDeploymentListSuspend(account, project.name)
            if (result is Resource.Success<*>) {
                @Suppress("UNCHECKED_CAST")
                val deploymentList = result.data as List<PagesDeployment>
                val displayItems = deploymentList.map { dep ->
                    val shortId = dep.shortId ?: dep.id.take(8)
                    val env = if (dep.environment == "production") "生产" else "预览"
                    val date = dep.createdOn?.substringBefore('T') ?: "未知时间"
                    "$shortId • $env • $date"
                }
                val adapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, displayItems)
                deploymentSelector.setAdapter(adapter)

                if (deploymentList.isNotEmpty()) {
                    deploymentSelector.setText(displayItems[0], false)
                    loadDeploymentLogs(account, project.name, deploymentList[0].id, logContent, logInfoText)
                }

                deploymentSelector.setOnItemClickListener { _, _, position, _ ->
                    if (position < deploymentList.size) {
                        logContent.text = "加载中..."
                        loadDeploymentLogs(account, project.name, deploymentList[position].id, logContent, logInfoText)
                    }
                }
            } else {
                logContent.text = "加载部署列表失败"
            }
        }

        dialog.show()
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
                        logContent.text = "暂无日志"
                    } else {
                        val logText = lines.joinToString("\n") { line -> line.line ?: "" }
                        logContent.text = logText
                    }
                    val total = logs?.total ?: lines.size
                    val containerLogs = if (logs?.includesContainerLogs == true) "包含容器日志" else "仅构建日志"
                    logInfoText.text = "共 $total 条 • $containerLogs"
                }
                is Resource.Error -> {
                    logContent.text = "加载日志失败: ${result.message}"
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
            hint = "自定义域名（例如：app.example.com）"
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
        }
        val editText = com.google.android.material.textfield.TextInputEditText(inputLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        inputLayout.addView(editText)
        container.addView(inputLayout)

        MaterialAlertDialogBuilder(context)
            .setTitle("为 ${project.name} 添加域名")
            .setView(container)
            .setPositiveButton("添加") { _, _ ->
                val hostname = editText.text?.toString()?.trim().orEmpty()
                if (hostname.isEmpty()) {
                    Snackbar.make(binding.root, "域名不能为空", Snackbar.LENGTH_SHORT).show()
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
            .setNegativeButton("取消", null)
            .show()
    }

    private fun autoConfigureDnsForDomain(account: Account, domain: PagesDomain, subdomain: String) {
        if (account.zoneId.isNullOrBlank()) {
            Snackbar.make(
                binding.root,
                "域名已添加，但账号未配置 Zone ID，无法自动添加 DNS 记录，请手动配置",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        val validation = domain.validationData
        val recordType = when (validation?.method) {
            "txt" -> "TXT"
            else -> "CNAME"
        }
        val recordName = validation?.txtName?.takeIf { it.isNotEmpty() } ?: domain.name
        val recordValue = validation?.txtValue?.takeIf { it.isNotEmpty() } ?: subdomain

        viewLifecycleOwner.lifecycleScope.launch {
            Snackbar.make(binding.root, "正在自动配置 DNS 记录...", Snackbar.LENGTH_SHORT).show()
            val dnsRequest = DnsRecordRequest(
                type = recordType,
                name = recordName,
                content = recordValue,
                proxied = true,
                ttl = 1
            )
            when (val result = dnsRepository.createDnsRecord(account, dnsRequest)) {
                is Resource.Success -> {
                    Snackbar.make(
                        binding.root,
                        "域名添加成功，DNS 记录已自动配置，验证可能需要几分钟",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
                is Resource.Error -> {
                    Snackbar.make(
                        binding.root,
                        "域名已添加，DNS 自动配置失败: ${result.message}，请手动添加 $recordType 记录",
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

        titleText.text = "${project.name} - 域名"
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
            .setTitle("删除域名")
            .setMessage("确定要删除域名 \"${domain.name}\" 吗？\n\n此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    when (val result = pagesViewModel.deleteDomainSuspend(account, project.name, domain.name)) {
                        is Resource.Success -> {
                            Snackbar.make(binding.root, "域名删除成功", Snackbar.LENGTH_SHORT).show()
                            onDeleted()
                        }
                        is Resource.Error -> {
                            Snackbar.make(binding.root, "删除失败: ${result.message}", Snackbar.LENGTH_LONG).show()
                        }
                        is Resource.Loading -> {}
                    }
                }
            }
            .setNegativeButton("取消", null)
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
                    copyToClipboard("https://$domain", "预览域名已复制")
                }
                statusText.text = "预览"
                statusText.setBackgroundColor(0xFF2196F3.toInt())
                statusText.setTextColor(0xFFFFFFFF.toInt())
                infoText.text = "Pages 默认域名"
                errorText.visibility = android.view.View.GONE
                // 预览域名不可删除
                deleteBtn.visibility = android.view.View.GONE
            }

            fun bindCustom(domain: PagesDomain) {
                nameText.text = domain.name
                nameText.setOnClickListener {
                    copyToClipboard("https://${domain.name}", "域名已复制")
                }
                statusText.text = domain.status ?: "未知"

                val statusColor = when (domain.status) {
                    "active" -> 0xFF4CAF50.toInt()
                    "pending", "initializing" -> 0xFFFF9800.toInt()
                    "error", "blocked" -> 0xFFF44336.toInt()
                    "deactivated" -> 0xFF9E9E9E.toInt()
                    else -> 0xFF9E9E9E.toInt()
                }
                statusText.setBackgroundColor(statusColor)
                statusText.setTextColor(0xFFFFFFFF.toInt())

                val method = domain.validationData?.method ?: "未知"
                val createdDate = domain.createdOn?.substringBefore('T') ?: "未知时间"
                infoText.text = "验证方式: $method • 创建于: $createdDate"

                val validationError = domain.validationData?.errorMessage
                val verificationError = domain.verificationData?.errorMessage
                val errorStatus = domain.verificationData?.status
                val errorToShow = validationError ?: verificationError
                if (errorToShow != null || errorStatus == "error" || errorStatus == "blocked") {
                    errorText.text = errorToShow ?: "状态异常: ${errorStatus ?: "未知"}"
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
            .setTitle("删除部署")
            .setMessage("确定要删除部署 ${deployment.shortId} 吗？\n\n此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        pagesViewModel.deleteDeployment(account, project.name, deployment.id)
                        deploymentsDialog?.dismiss()
                        showDeploymentsDialogWithLoading(account, project)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRollbackDeploymentConfirmDialog(project: PagesProject, deployment: PagesDeployment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("回滚部署")
            .setMessage("确定要回滚到部署 ${deployment.shortId} 吗？\n\n这将使此部署成为活动部署。")
            .setPositiveButton("回滚") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        pagesViewModel.rollbackDeployment(account, project.name, deployment.id)
                        deploymentsDialog?.dismiss()
                        showDeploymentsDialogWithLoading(account, project)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showRetryDeploymentConfirmDialog(project: PagesProject, deployment: PagesDeployment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("重新部署")
            .setMessage("确定要重新部署 ${deployment.shortId} 吗？\n\n将基于此部署的配置重新发起一次部署。")
            .setPositiveButton("重新部署") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        pagesViewModel.retryDeployment(account, project.name, deployment.id)
                        deploymentsDialog?.dismiss()
                        showDeploymentsDialogWithLoading(account, project)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun formatDeploymentDate(dateString: String?): String {
        if (dateString == null) return "未知"
        
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
        private val onConfigKvClick: (PagesProject) -> Unit,
        private val onConfigD1Click: (PagesProject) -> Unit,
        private val onConfigR2Click: (PagesProject) -> Unit,
        private val onViewDeploymentsClick: (PagesProject) -> Unit,
        private val onViewDomainsClick: (PagesProject) -> Unit,
        private val onAddDomainClick: (PagesProject) -> Unit,
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
                binding.projectInfoText.text = "${project.productionBranch} 分支 • $dateText"
                
                // 添加多选模式支持 - 通过改变卡片背景色表示选中状态
                if (selectionMode) {
                    binding.deleteBtn.visibility = android.view.View.GONE
                    binding.viewDeploymentsBtn.visibility = android.view.View.GONE
                    binding.viewDomainsBtn.visibility = android.view.View.GONE
                    binding.addDomainBtn.visibility = android.view.View.GONE
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
                    binding.logsBtn.visibility = android.view.View.VISIBLE
                    updateSelectionUI(binding.root, false)
                    binding.root.setOnClickListener(null)
                }
                
                binding.configEnvBtn.setOnClickListener {
                    onConfigEnvClick(project)
                }
                
                binding.configKvBtn.setOnClickListener {
                    onConfigKvClick(project)
                }
                
                binding.configD1Btn.setOnClickListener {
                    onConfigD1Click(project)
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
                    view.setBackgroundColor(android.R.color.transparent)
                }
            }
            
            private fun formatDate(dateString: String?): String {
                if (dateString == null) return "未知日期"
                
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
            showToast("请先选择账号")
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
            .setPositiveButton("开始清理") { _, _ ->
                val retainCountStr = dialogBinding.retainCountEdit.text.toString().trim()
                val retainCount = retainCountStr.toIntOrNull() ?: 10
                
                if (dialogBinding.cleanupAllProjectsRadio.isChecked) {
                    showCleanupConfirmDialog(true, null, retainCount)
                } else {
                    val selectedProjectName = dialogBinding.projectSpinner.selectedItem?.toString()
                    if (selectedProjectName.isNullOrEmpty()) {
                        showToast("请选择项目")
                        return@setPositiveButton
                    }
                    showCleanupConfirmDialog(false, selectedProjectName, retainCount)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showCleanupConfirmDialog(isAllProjects: Boolean, projectName: String?, retainCount: Int) {
        val account = accountViewModel.defaultAccount.value ?: return
        
        val title = if (isAllProjects) "清理所有项目的旧部署" else "清理项目 \"$projectName\" 的旧部署"
        val message = if (isAllProjects) {
            "将清理账号下所有 Pages 项目的旧部署，每个项目保留最新 $retainCount 个部署记录。\n\n此操作不可撤销，确定继续吗？"
        } else {
            "将清理项目 \"$projectName\" 的旧部署，保留最新 $retainCount 个部署记录。\n\n此操作不可撤销，确定继续吗？"
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定清理") { dialog, _ ->
                dialog.dismiss()
                
                // 显示加载对话框
                val loadingDialog = MaterialAlertDialogBuilder(requireContext())
                    .setTitle("正在清理")
                    .setMessage("正在清理旧部署，请稍候...")
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
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showCleanupResultsDialog(results: List<CleanupResult>) {
        val totalDeleted = results.sumOf { it.deletedCount }
        val totalProjects = results.size
        
        val resultBuilder = StringBuilder()
        resultBuilder.append("清理结果：\n\n")
        
        results.forEach { result ->
            if (result.success) {
                val status = if (result.deletedCount > 0) {
                    "成功清理 ${result.deletedCount} 个旧部署"
                } else {
                    "无需清理（当前 ${result.totalDeployments} 个部署 ≤ 保留数量）"
                }
                resultBuilder.append("• ${result.projectName}: $status\n")
            } else {
                resultBuilder.append("• ${result.projectName}: ❌ 失败 - ${result.errorMessage}\n")
            }
        }
        
        resultBuilder.append("\n总计：处理 $totalProjects 个项目，成功清理 $totalDeleted 个旧部署")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("清理完成")
            .setMessage(resultBuilder.toString())
            .setPositiveButton("关闭", null)
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
            binding.bucketNameText.text = "Bucket: ${r2Binding.second}"
            
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
            binding.databaseNameText.text = "Database: ${d1Binding.second}"
            
            binding.deleteBindingBtn.setOnClickListener {
                onDeleteClick(d1Binding)
            }
        }
    }
}

class PagesVariablesAdapter(
    private val onEditClick: (Pair<String, String>) -> Unit,
    private val onDeleteClick: (Pair<String, String>) -> Unit
) : RecyclerView.Adapter<PagesVariablesAdapter.VariableViewHolder>() {
    
    private var variables = listOf<Pair<String, String>>()
    
    fun submitList(newVariables: List<Pair<String, String>>) {
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
        
        fun bind(variable: Pair<String, String>) {
            binding.variableNameText.text = variable.first
            binding.variableValueText.text = variable.second
            binding.variableTypeText.visibility = View.GONE  // Pages 只支持文本类型，不显示类型标签
            
            binding.editVariableBtn.setOnClickListener {
                onEditClick(variable)
            }
            
            binding.deleteVariableBtn.setOnClickListener {
                onDeleteClick(variable)
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
                binding.variableValueText.text = "🔒 加密存储，无法查看"
                binding.variableValueText.setTypeface(null, Typeface.ITALIC)
                binding.variableValueText.setTextColor(
                    binding.root.context.getColor(android.R.color.darker_gray)
                )
            } else {
                binding.variableValueText.text = value
                binding.variableValueText.setTypeface(null, Typeface.NORMAL)
                val typedValue = TypedValue()
                binding.root.context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
                binding.variableValueText.setTextColor(typedValue.data)
            }
            
            // Show type label
            binding.variableTypeText.visibility = View.VISIBLE
            binding.variableTypeText.text = if (isSecret) "机密" else "变量"
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
