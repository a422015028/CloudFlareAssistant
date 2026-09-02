package com.muort.upworker.feature.store

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.CatalogTemplate
import com.muort.upworker.core.model.DeployBindingConfig
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.repository.AccountRepository
import com.muort.upworker.core.repository.CatalogRepository
import com.muort.upworker.core.repository.TemplateDeployRepository
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.DialogDeployTemplateBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * 模板部署对话框
 * 支持选择账户、设置部署名称、配置绑定、填写环境变量
 * 两阶段流程：预检 → 确认部署
 */
@AndroidEntryPoint
class DeployTemplateDialog : BottomSheetDialogFragment() {

    private var _binding: DialogDeployTemplateBinding? = null
    private val binding get() = _binding!!

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var catalogRepository: CatalogRepository

    @Inject
    lateinit var deployRepository: TemplateDeployRepository

    private lateinit var template: CatalogTemplate
    private var onDeploySuccess: (() -> Unit)? = null

    private var accounts: List<Account> = emptyList()
    private var selectedAccount: Account? = null
    private var bindingConfigs: MutableList<DeployBindingConfig> = mutableListOf()
    private var envValues: MutableMap<String, String> = mutableMapOf()
    private var secretValues: MutableMap<String, String> = mutableMapOf()

    // Hybrid 模式部署选项
    private var deployWorker: Boolean = true
    private var deployPages: Boolean = true

    companion object {
        fun newInstance(
            template: CatalogTemplate,
            onDeploySuccess: (() -> Unit)? = null
        ): DeployTemplateDialog {
            return DeployTemplateDialog().apply {
                this.template = template
                this.onDeploySuccess = onDeploySuccess
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogDeployTemplateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        setupTemplateType()
        setupAccountSelector()
        setupNameInput()
        setupBindings()
        setupEnvVars()
        setupDeployButton()
        loadAccounts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ========== 初始化 ==========

    private fun setupHeader() {
        binding.templateNameText.text = getString(R.string.store_name_with_version, template.name, template.version)
    }

    private fun setupTemplateType() {
        when (template.type) {
            "worker" -> {
                // Worker 模板：显示绑定配置，隐藏 Hybrid 相关
                binding.hybridSection.visibility = View.GONE
                binding.pagesNameSection.visibility = View.GONE
                binding.bindingsSection.visibility = View.VISIBLE
                binding.nameLabel.text = getString(R.string.store_template_name)
            }
            "pages" -> {
                // Pages 模板：隐藏绑定配置
                binding.hybridSection.visibility = View.GONE
                binding.pagesNameSection.visibility = View.GONE
                binding.bindingsSection.visibility = View.GONE
                binding.nameLabel.text = getString(R.string.store_pages_project_name)
            }
            "hybrid" -> {
                // Hybrid 模板：显示部署方式选择
                binding.hybridSection.visibility = View.VISIBLE
                binding.pagesNameSection.visibility = View.VISIBLE
                binding.bindingsSection.visibility = View.VISIBLE
                binding.nameLabel.text = getString(R.string.store_template_name)

                // 默认 Pages 名称和 Worker 名称相同
                binding.pagesNameEditText.setText(template.templateId)

                // 部署方式切换
                binding.deployModeChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
                    deployWorker = checkedIds.contains(R.id.chipModeBoth) || checkedIds.contains(R.id.chipModeWorker)
                    deployPages = checkedIds.contains(R.id.chipModeBoth) || checkedIds.contains(R.id.chipModePages)

                    // 根据选择显示/隐藏相关区域
                    binding.bindingsSection.visibility = if (deployWorker) View.VISIBLE else View.GONE
                    binding.pagesNameSection.visibility = if (deployPages) View.VISIBLE else View.GONE

                    runPreflight()
                }
            }
        }
    }

    private fun setupAccountSelector() {
        binding.accountAutoComplete.setOnItemClickListener { _, _, position, _ ->
            if (position < accounts.size) {
                selectedAccount = accounts[position]
                runPreflight()
            }
        }
    }

    private fun setupNameInput() {
        // 默认使用模板 ID 作为部署名称
        binding.nameEditText.setText(template.templateId)

        binding.nameEditText.doAfterTextChanged {
            runPreflight()
        }
    }

    private fun setupBindings() {
        val bindings = catalogRepository.parseBindings(template.bindingsJson)
        if (bindings.isEmpty()) return

        binding.bindingsSection.visibility = View.VISIBLE
        binding.bindingsContainer.removeAllViews()

        bindingConfigs = deployRepository.buildBindingConfigs(bindings).toMutableList()

        for (i in bindings.indices) {
            val b = bindings[i]

            // 跳过 var 类型，在环境变量区域处理
            if (b.type == "var") continue

            val rowLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, 8, 0, 8)
            }

            // 绑定名称 + 类型
            val nameLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val iconText = android.widget.TextView(requireContext()).apply {
                text = getBindingIcon(b.type)
                textSize = 18f
                setPadding(0, 0, 12, 0)
            }

            val nameText = android.widget.TextView(requireContext()).apply {
                text = b.title ?: b.name
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }

            val typeText = android.widget.TextView(requireContext()).apply {
                text = getBindingTypeName(b.type)
                textSize = 12f
                setTextColor(resources.getColor(android.R.color.tab_indicator_text, null))
            }

            nameLayout.addView(iconText)
            nameLayout.addView(nameText)
            nameLayout.addView(typeText)
            rowLayout.addView(nameLayout)

            // 资源名称输入框
            val inputLayout = TextInputLayout(
                requireContext(),
                null,
                com.google.android.material.R.attr.textInputOutlinedStyle
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = 8 }
                hint = "资源名称"
                isHintEnabled = true
            }

            val editText = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setText(b.resourceName ?: b.name)
                inputType = android.text.InputType.TYPE_CLASS_TEXT
                maxLines = 1
                isFocusable = true
                isFocusableInTouchMode = true
            }

            editText.doAfterTextChanged { text ->
                val index = bindingConfigs.indexOfFirst { it.name == b.name }
                if (index >= 0) {
                    bindingConfigs[index] = bindingConfigs[index].copy(
                        resourceName = text?.toString() ?: b.name
                    )
                }
            }

            inputLayout.addView(editText)
            rowLayout.addView(inputLayout)

            binding.bindingsContainer.addView(rowLayout)

            // 分割线
            if (i < bindings.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    ).also { it.topMargin = 8 }
                    setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))
                    alpha = 0.2f
                }
                binding.bindingsContainer.addView(divider)
            }
        }
    }

    private fun setupEnvVars() {
        val bindings = catalogRepository.parseBindings(template.bindingsJson)
        val varBindings = bindings.filter { it.type == "var" }

        if (varBindings.isEmpty()) return

        binding.envSection.visibility = View.VISIBLE
        binding.envContainer.removeAllViews()

        for ((i, b) in varBindings.withIndex()) {
            val isSecret = b.secret

            val inputLayout = TextInputLayout(
                requireContext(),
                null,
                com.google.android.material.R.attr.textInputOutlinedStyle
            ).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.topMargin = if (i == 0) 0 else 10 }
                hint = b.title ?: b.name
                isHintEnabled = true
                if (isSecret) {
                    endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                }
            }

            val editText = TextInputEditText(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                b.value?.let { setText(it) }
                inputType = if (isSecret) {
                    android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
                } else {
                    android.text.InputType.TYPE_CLASS_TEXT
                }
                maxLines = 1
            }

            val bindingName = b.name
            editText.doAfterTextChanged { text ->
                val value = text?.toString() ?: ""
                if (isSecret) {
                    secretValues[bindingName] = value
                } else {
                    envValues[bindingName] = value
                }
            }

            // 如果有默认值，预先填充
            if (isSecret) {
                b.value?.let { secretValues[bindingName] = it }
            } else {
                b.value?.let { envValues[bindingName] = it }
            }

            inputLayout.addView(editText)
            binding.envContainer.addView(inputLayout)
        }
    }

    private fun setupDeployButton() {
        binding.deployBtn.setOnClickListener {
            performDeploy()
        }
    }

    // ========== 账户加载 ==========

    private fun loadAccounts() {
        lifecycleScope.launch {
            val result = accountRepository.getAllAccounts().first()
            if (result is Resource.Success && result.data.isNotEmpty()) {
                accounts = result.data
                val accountNames = accounts.map { it.name }
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    accountNames
                )
                binding.accountAutoComplete.setAdapter(adapter)

                // 默认选中第一个账户
                selectedAccount = accounts.first()
                binding.accountAutoComplete.setText(accounts.first().name, false)

                runPreflight()
            } else {
                showToast(getString(R.string.msg_no_account_available))
                dismiss()
            }
        }
    }

    // ========== 预检 ==========

    private fun runPreflight() {
        val account = selectedAccount ?: return
        val scriptName = binding.nameEditText.text?.toString()?.trim()
        if (scriptName.isNullOrBlank()) return

        lifecycleScope.launch {
            binding.preflightSection.visibility = View.GONE

            val result = deployRepository.preflightDeploy(account, template, scriptName)
            if (result !is Resource.Success) return@launch

            val info = result.data
            val messages = mutableListOf<String>()

            if (info.exists) {
                messages.add("⚠️ Worker「$scriptName」已存在，部署将覆盖现有配置")
            } else {
                messages.add("✅ 将创建新的 Worker「$scriptName」")
            }

            if (info.newBindings.isNotEmpty()) {
                messages.add("📦 将创建 ${info.newBindings.size} 个绑定资源")
            }

            if (info.secretsToOverride.isNotEmpty()) {
                messages.add("🔐 将覆盖 ${info.secretsToOverride.size} 个现有密钥")
            }

            binding.preflightSection.visibility = View.VISIBLE
            binding.preflightText.text = messages.joinToString("\n")
        }
    }

    // ========== 部署执行 ==========

    private fun performDeploy() {
        val account = selectedAccount ?: run {
            showToast("请选择目标账户")
            return
        }

        val name = binding.nameEditText.text?.toString()?.trim()
        if (name.isNullOrBlank()) {
            binding.nameInputLayout.error = "请输入部署名称"
            return
        }

        // Pages 项目名称验证（仅小写字母、数字、短横线）
        if (template.type == "pages" || (template.type == "hybrid" && deployPages)) {
            val pagesName = if (template.type == "hybrid") {
                binding.pagesNameEditText.text?.toString()?.trim()
            } else {
                name
            }
            if (!pagesName.isNullOrBlank() &&
                !pagesName.matches(Regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$"))) {
                if (template.type == "hybrid") {
                    binding.pagesNameInputLayout.error = "名称只能包含小写字母、数字和短横线"
                } else {
                    binding.nameInputLayout.error = "名称只能包含小写字母、数字和短横线"
                }
                return
            }
        }

        // Worker 名称验证
        if (template.type == "worker" || (template.type == "hybrid" && deployWorker)) {
            if (!name.matches(Regex("^[a-z0-9][a-z0-9-]*[a-z0-9]$"))) {
                binding.nameInputLayout.error = "名称只能包含小写字母、数字和短横线"
                return
            }
        }

        binding.deployBtn.isEnabled = false
        binding.progressSection.visibility = View.VISIBLE
        binding.progressText.text = getString(R.string.store_deploying)

        val enableLogs = binding.logsSwitch.isChecked
        val enableTracing = binding.tracingSwitch.isChecked

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    when (template.type) {
                        "pages" -> {
                            deployRepository.deployPagesTemplate(
                                account = account,
                                template = template,
                                projectName = name,
                                envValues = envValues
                            )
                        }
                        "hybrid" -> {
                            val pagesName = binding.pagesNameEditText.text?.toString()?.trim() ?: name
                            deployRepository.deployHybridTemplate(
                                account = account,
                                template = template,
                                workerName = name,
                                pagesName = pagesName,
                                bindings = bindingConfigs,
                                envValues = envValues,
                                secretValues = secretValues,
                                deployWorker = deployWorker,
                                deployPages = deployPages
                            )
                        }
                        else -> {
                            // Worker 模板（默认）
                            deployRepository.deployWorkerTemplate(
                                account = account,
                                template = template,
                                scriptName = name,
                                bindings = bindingConfigs,
                                envValues = envValues,
                                secretValues = secretValues,
                                enableObservability = enableLogs,
                                enableTracing = enableTracing
                            )
                        }
                    }
                }

                binding.progressSection.visibility = View.GONE
                binding.deployBtn.isEnabled = true

                when (result) {
                    is Resource.Success -> {
                        val info = result.data
                        showToast(getString(R.string.store_deploy_success))
                        Timber.d("[DeployDialog] 部署成功: ${info.url}")
                        onDeploySuccess?.invoke()
                        dismiss()
                    }
                    is Resource.Error -> {
                        showToast("${getString(R.string.store_deploy_failed)}: ${result.message}")
                    }
                    else -> {
                        // Unexpected state
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[DeployDialog] 部署异常")
                binding.progressSection.visibility = View.GONE
                binding.deployBtn.isEnabled = true
                showToast("${getString(R.string.store_deploy_failed)}: ${e.message}")
            }
        }
    }

    // ========== 辅助方法 ==========

    private fun getBindingIcon(type: String): String {
        return when (type) {
            "kv" -> "🗄️"
            "d1" -> "🗃️"
            "r2" -> "📦"
            "ai" -> "🤖"
            "var" -> "🔑"
            "durable_object" -> "📌"
            "service" -> "🔗"
            "queue" -> "📬"
            else -> "📎"
        }
    }

    private fun getBindingTypeName(type: String): String {
        return when (type) {
            "kv" -> "KV"
            "d1" -> "D1"
            "r2" -> "R2"
            "ai" -> "AI"
            "var" -> "变量"
            "durable_object" -> "DO"
            "service" -> "Service"
            "queue" -> "Queue"
            else -> type
        }
    }
}
