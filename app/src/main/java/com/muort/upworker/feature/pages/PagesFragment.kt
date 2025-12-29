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
import com.muort.upworker.core.model.PagesDeployment
import com.muort.upworker.core.model.PagesProject
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.KvRepository
import com.muort.upworker.core.repository.R2Repository
import com.muort.upworker.core.repository.D1Repository
import com.muort.upworker.databinding.DialogPagesInputBinding
import com.muort.upworker.databinding.FragmentPagesBinding
import com.muort.upworker.databinding.ItemPagesProjectBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
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
    
    private lateinit var projectAdapter: ProjectAdapter
    
    private var selectedFile: File? = null
    
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
                if (binding.projectNameEdit.text.isNullOrEmpty()) {
                    val projectName = fileName.substringBeforeLast(".")
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
            }
        )
        binding.projectRecyclerView.adapter = projectAdapter
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
    

    private fun setupClickListeners() {
        // File selection
        binding.selectFileBtn.setOnClickListener {
            selectFile()
        }
        
        binding.filePathEdit.setOnClickListener {
            selectFile()
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
    }
    
    private fun selectFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/javascript",
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
            !file.name.endsWith(".zip", ignoreCase = true) -> {
                showToast("仅支持 .zip 文件，请上传包含构建输出的压缩包")
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
        
        // Show progress
        binding.uploadProgress.visibility = View.VISIBLE
        binding.deployBtn.isEnabled = false
        
        Timber.d("Deploying project: $projectName, branch: $branch, file: ${file?.name}")
        
        pagesViewModel.createDeployment(account, projectName, branch, file!!)
        
        // Hide progress after a delay (will be handled by loading state)
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(1000)
            binding.uploadProgress.visibility = View.GONE
            binding.deployBtn.isEnabled = true
        }
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
    
    private fun showDeploymentsDialogWithLoading(account: com.muort.upworker.core.model.Account, project: PagesProject) {
        // 显示加载对话框
        val loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("${project.name} - 部署列表")
            .setMessage("加载中...")
            .setCancelable(true)
            .create()
        loadingDialog.show()
        
        // 加载并等待完成
        viewLifecycleOwner.lifecycleScope.launch {
            // 开始加载
            pagesViewModel.selectProject(project)
            pagesViewModel.loadDeployments(account, project.name)
            
            // 等待加载开始 (loading = true)
            pagesViewModel.loadingState.first { it }
            // 然后等待加载完成 (loading = false)
            pagesViewModel.loadingState.first { !it }
            
            loadingDialog.dismiss()
            showDeploymentsDialog(project)
        }
    }
    
    private fun showDeploymentsDialog(project: PagesProject) {
        val deployments = pagesViewModel.deployments.value
        
        if (deployments.isEmpty()) {
            showToast("暂无部署记录")
            return
        }
        
        val items = deployments.map { deployment ->
            val status = deployment.latestStage?.status ?: "unknown"
            val env = deployment.environment
            val time = formatDeploymentDate(deployment.createdOn)
            "${deployment.shortId} - $status ($env) - $time"
        }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${project.name} - 部署列表")
            .setItems(items) { _, which ->
                if (which < deployments.size) {
                    // 第一个部署是最新的
                    val isLatest = which == 0
                    showDeploymentDetailDialog(project, deployments[which], isLatest)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun showDeploymentDetailDialog(project: PagesProject, deployment: PagesDeployment, isLatest: Boolean) {
        val details = buildString {
            append("ID: ${deployment.id}\n\n")
            append("短ID: ${deployment.shortId}\n\n")
            append("环境: ${deployment.environment}\n\n")
            append("URL: ${deployment.url}\n\n")
            append("状态: ${deployment.latestStage?.status ?: "未知"}\n\n")
            append("阶段: ${deployment.latestStage?.name ?: "未知"}\n\n")
            append("创建时间: ${formatDeploymentDate(deployment.createdOn)}\n\n")
            append("修改时间: ${formatDeploymentDate(deployment.modifiedOn)}\n\n")
            
            deployment.deploymentTrigger?.metadata?.let { meta ->
                append("分支: ${meta.branch}\n\n")
                if (!meta.commitHash.isNullOrEmpty()) {
                    append("提交: ${meta.commitHash}\n\n")
                }
                if (!meta.commitMessage.isNullOrEmpty()) {
                    append("提交信息: ${meta.commitMessage}\n\n")
                }
            }
            
            // 显示所有阶段信息
            if (!deployment.stages.isNullOrEmpty()) {
                append("===== 部署阶段 =====\n\n")
                deployment.stages.forEach { stage ->
                    append("• ${stage.name}: ${stage.status ?: "未知"}\n")
                    if (stage.startedOn != null) {
                        append("  开始: ${formatDeploymentDate(stage.startedOn)}\n")
                    }
                    if (stage.endedOn != null) {
                        append("  结束: ${formatDeploymentDate(stage.endedOn)}\n")
                    }
                    append("\n")
                }
            }
        }
        
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("部署详情")
            .setMessage(details)
            .setPositiveButton("关闭", null)
            .setNeutralButton("访问") { _, _ ->
                // 打开部署URL
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deployment.url))
                startActivity(intent)
            }
        
        // 只有非最新的部署才能删除
        if (!isLatest) {
            dialog.setNegativeButton("删除") { _, _ ->
                showDeleteDeploymentConfirmDialog(project, deployment)
            }
        }
        
        dialog.show()
    }
    
    private fun showDeleteDeploymentConfirmDialog(project: PagesProject, deployment: PagesDeployment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除部署")
            .setMessage("确定要删除部署 ${deployment.shortId} 吗？\n\n此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    pagesViewModel.deleteDeployment(account, project.name, deployment.id)
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
            val outputFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            
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
        private val onViewDeploymentsClick: (PagesProject) -> Unit
    ) : RecyclerView.Adapter<ProjectAdapter.ViewHolder>() {
        
        private var projects = listOf<PagesProject>()
        
        fun submitList(newList: List<PagesProject>) {
            projects = newList
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
                
                binding.deleteBtn.setOnClickListener {
                    onDeleteClick(project)
                }
            }
            
            private fun formatDate(dateString: String?): String {
                if (dateString == null) return "未知日期"
                
                return try {
                    val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                    val outputFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                    val date = inputFormat.parse(dateString)
                    date?.let { outputFormat.format(it) } ?: dateString
                } catch (e: Exception) {
                    dateString.substringBefore('T')
                }
            }
        }
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
