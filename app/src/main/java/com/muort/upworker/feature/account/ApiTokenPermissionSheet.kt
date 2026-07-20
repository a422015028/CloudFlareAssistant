package com.muort.upworker.feature.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.AccountInfo
import com.muort.upworker.core.model.AuthType
import com.muort.upworker.core.model.PermissionGroup
import com.muort.upworker.core.model.ScopeCategory
import com.muort.upworker.core.model.ZoneInfo
import com.muort.upworker.core.repository.ApiTokenRepository
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.DialogApiTokenPermissionBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * API Token 权限管理底部弹窗
 *
 * 两个能力：
 * 1. 查看当前 Token 的权限策略（verify + GET /user/tokens/{id}）
 * 2. 创建一个权限受控的新 Token（POST /user/tokens），可回填到左侧 API Token 输入框
 *
 * 仔细对齐 Cloudflare 创建 API Token 时的权限设置：权限组按 User/Account/Zone 作用域分组，
 * 资源范围支持所有账号/指定账号、所有域名/指定域名，可选 TTL。
 */
@AndroidEntryPoint
class ApiTokenPermissionSheet : BottomSheetDialogFragment() {

    interface Listener {
        /** 用户点击“填入 API Token”时回调 */
        fun onFillApiToken(value: String)
    }

    private var _binding: DialogApiTokenPermissionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ApiTokenViewModel by activityViewModels()

    private var listener: Listener? = null

    private lateinit var adapter: PermissionGroupAdapter

    private var allGroups: List<PermissionGroup> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogApiTokenPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listener = parentFragment as? Listener ?: requireActivity() as? Listener

        adapter = PermissionGroupAdapter { updateSelectedCount() }
        binding.permRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.permRecyclerView.adapter = adapter
        // 固定高度 + 内部滚动，配合外层 NestedScrollView 的嵌套滚动
        binding.permRecyclerView.setHasFixedSize(true)

        setupToggle()
        setupSearch()
        setupSpinners()
        setupScopeRadios()
        setupButtons()
        observeViewModel()

        // 构建临时凭据账号并加载数据
        val account = buildAccountFromArgs()
        viewModel.setCreatorAccount(account)
        viewModel.loadPermissionData(account)
    }

    private fun buildAccountFromArgs(): Account {
        val args = requireArguments()
        return Account(
            id = 0,
            name = args.getString(ARG_ACCOUNT_NAME) ?: "临时",
            accountId = args.getString(ARG_ACCOUNT_ID) ?: "",
            token = args.getString(ARG_TOKEN) ?: "",
            email = args.getString(ARG_EMAIL)?.ifBlank { null },
            globalApiKey = args.getString(ARG_GLOBAL_KEY)?.ifBlank { null },
            authType = args.getString(ARG_AUTH_TYPE) ?: AuthType.TOKEN.name
        )
    }

    // ==================== 模式切换 ====================

    private fun setupToggle() {
        binding.toggleGroup.check(binding.btnViewCurrent.id)
        showCurrentMode()
        binding.toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            when (checkedId) {
                binding.btnViewCurrent.id -> showCurrentMode()
                binding.btnCreateNew.id -> showCreateMode()
            }
        }
    }

    private fun showCurrentMode() {
        binding.currentSection.isVisible = true
        binding.createSection.isVisible = false
        binding.resultSection.isVisible = false
    }

    private fun showCreateMode() {
        binding.currentSection.isVisible = false
        binding.createSection.isVisible = true
        binding.resultSection.isVisible = false
    }

    private fun showResultMode() {
        binding.currentSection.isVisible = false
        binding.createSection.isVisible = false
        binding.resultSection.isVisible = true
    }

    // ==================== 搜索 ====================

    private fun setupSearch() {
        binding.searchEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.setQuery(s?.toString() ?: "")
                updateSelectedCount()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    // ==================== Spinners ====================

    private fun setupSpinners() {
        // 账号 Spinner
        binding.accountSpinner.adapter = object : ArrayAdapter<AccountInfo>(
            requireContext(), android.R.layout.simple_spinner_item
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(context)
                    .inflate(android.R.layout.simple_spinner_item, parent, false)
                val item = getItem(position) ?: return v
                (v as TextView).text = "${item.name} (${item.id})"
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = LayoutInflater.from(context)
                    .inflate(android.R.layout.simple_spinner_dropdown_item, parent, false)
                (v as TextView).text = "${getItem(position)?.name} (${getItem(position)?.id})"
                return v
            }
        }

        // 域名 Spinner
        binding.zoneSpinner.adapter = object : ArrayAdapter<ZoneInfo>(
            requireContext(), android.R.layout.simple_spinner_item
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = convertView ?: LayoutInflater.from(context)
                    .inflate(android.R.layout.simple_spinner_item, parent, false)
                (v as TextView).text = getItem(position)?.name ?: ""
                return v
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val v = LayoutInflater.from(context)
                    .inflate(android.R.layout.simple_spinner_dropdown_item, parent, false)
                (v as TextView).text = getItem(position)?.name ?: ""
                return v
            }
        }
    }

    // ==================== 资源范围 ====================

    private fun setupScopeRadios() {
        binding.accountScopeRadio.setOnCheckedChangeListener { _, checkedId ->
            binding.accountSpinner.isVisible = checkedId == binding.radioSpecificAccount.id
        }
        binding.zoneScopeRadio.setOnCheckedChangeListener { _, checkedId ->
            binding.zoneSpinner.isVisible = checkedId == binding.radioSpecificZone.id
        }
    }

    // ==================== 按钮 ====================

    private fun setupButtons() {
        binding.closeBtn.setOnClickListener { dismiss() }

        binding.refreshCurrentBtn.setOnClickListener {
            val account = viewModel.creatorAccount.value ?: return@setOnClickListener
            viewModel.loadCurrentTokenDetail(account)
        }

        binding.selectAllVisibleBtn.setOnClickListener { adapter.selectAllVisible(); updateSelectedCount() }
        binding.clearSelectionBtn.setOnClickListener { adapter.clearSelection(); updateSelectedCount() }

        binding.createTokenBtn.setOnClickListener { onCreateToken() }

        binding.copyTokenBtn.setOnClickListener {
            val value = viewModel.createdToken.value?.value ?: return@setOnClickListener
            copyToClipboard(value)
            showToast("已复制到剪贴板")
        }
        binding.fillTokenBtn.setOnClickListener {
            val value = viewModel.createdToken.value?.value ?: return@setOnClickListener
            listener?.onFillApiToken(value)
            dismiss()
        }
        binding.createAnotherBtn.setOnClickListener {
            viewModel.clearCreatedToken()
            showCreateMode()
        }
    }

    private fun onCreateToken() {
        val account = viewModel.creatorAccount.value ?: run {
            showToast("凭据缺失"); return
        }
        val name = binding.tokenNameEdit.text?.toString()?.trim().orEmpty()
        if (name.isEmpty()) {
            showToast("请输入 Token 名称"); return
        }
        if (adapter.selectedCount() == 0) {
            showToast("请至少选择一个权限"); return
        }

        val selectedIds = adapter.getSelectedIds()
        val selectedByCategory = mutableMapOf<ScopeCategory, MutableList<PermissionGroup>>()
        allGroups.filter { it.id in selectedIds }.forEach { g ->
            val cat = ApiTokenRepository.categorize(g.scopes)
            selectedByCategory.getOrPut(cat) { mutableListOf() }.add(g)
        }

        val accountScope = if (binding.radioSpecificAccount.isChecked) {
            val acc = (binding.accountSpinner.selectedItem as? AccountInfo)
            if (acc == null) {
                showToast("请选择账号"); return
            }
            ApiTokenRepository.AccountResourceScope.SpecificAccount(acc.id)
        } else ApiTokenRepository.AccountResourceScope.AllAccounts

        val zoneScope = if (binding.radioSpecificZone.isChecked) {
            val zone = (binding.zoneSpinner.selectedItem as? ZoneInfo)
            if (zone == null) {
                showToast("请选择域名"); return
            }
            ApiTokenRepository.ZoneResourceScope.SpecificZone(zone.id)
        } else ApiTokenRepository.ZoneResourceScope.AllZones

        val expiresOn = computeExpiry()

        viewModel.createApiToken(account, name, selectedByCategory, accountScope, zoneScope, expiresOn)
    }

    private fun computeExpiry(): String? {
        val days = when (binding.expiryRadioGroup.checkedRadioButtonId) {
            binding.radio7d.id -> 7
            binding.radio30d.id -> 30
            binding.radio90d.id -> 90
            binding.radio1y.id -> 365
            else -> return null
        }
        val future = System.currentTimeMillis() + days * DateUtils.DAY_IN_MILLIS
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        return sdf.format(Date(future))
    }

    // ==================== 观察数据 ====================

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.permissionGroups.collect { renderGroups(it) } }
                launch { viewModel.permAccounts.collect { fillAccountSpinner(it) } }
                launch { viewModel.permZones.collect { fillZoneSpinner(it) } }
                launch { viewModel.currentTokenDetail.collect { renderCurrentToken(it) } }
                launch { viewModel.loading.collect { binding.loadingBar.isVisible = it } }
                launch { viewModel.createdToken.collect {
                    if (it != null && it.value != null) renderResult(it.name, it.value!!)
                } }
                launch { viewModel.message.collect { showToast(it) } }
            }
        }
    }

    private fun renderGroups(groups: List<PermissionGroup>) {
        allGroups = groups
        val items = mutableListOf<PermissionItem>()
        // 按作用域分组：User / Account / Zone
        val byCategory = groups.groupBy { ApiTokenRepository.categorize(it.scopes) }
        ScopeCategory.values().forEach { cat ->
            val list = byCategory[cat].orEmpty().sortedBy { it.name.lowercase() }
            if (list.isNotEmpty()) {
                items.add(PermissionItem.Header(PermissionGroupAdapter.titleFor(cat)))
                items.addAll(list.map { PermissionItem.Group(it, cat) })
            }
        }
        adapter.setData(items)
        if (groups.isEmpty()) {
            binding.selectedCountText.text = "无可用权限组（当前 Token 可能缺少 “API Tokens Read” 权限）"
        } else {
            updateSelectedCount()
        }
    }

    private fun fillAccountSpinner(accounts: List<AccountInfo>) {
        @Suppress("UNCHECKED_CAST")
        val a = binding.accountSpinner.adapter as? ArrayAdapter<AccountInfo> ?: return
        a.clear(); a.addAll(accounts); a.notifyDataSetChanged()
    }

    private fun fillZoneSpinner(zones: List<ZoneInfo>) {
        @Suppress("UNCHECKED_CAST")
        val a = binding.zoneSpinner.adapter as? ArrayAdapter<ZoneInfo> ?: return
        a.clear(); a.addAll(zones); a.notifyDataSetChanged()
    }

    private fun updateSelectedCount() {
        binding.selectedCountText.text = "已选 ${adapter.selectedCount()} 项"
    }

    // ==================== 当前 Token 渲染 ====================

    private fun renderCurrentToken(token: com.muort.upworker.core.model.ApiToken?) {
        val container = binding.currentPoliciesContainer
        container.removeAllViews()
        if (token == null) {
            binding.currentInfoText.text = "无法读取当前 Token（需 “API Tokens Read” 权限）"
            binding.currentErrorText.isVisible = true
            binding.currentErrorText.text = "若使用 Global API Key 可直接读取；若为 API Token，则该 Token 需具备 “API Tokens Read” 权限。"
            return
        }
        binding.currentErrorText.isVisible = false
        val info = buildString {
            token.id?.let { append("ID: $it\n") }
            token.name?.let { append("名称: $it\n") }
            append("状态: ${token.status ?: "未知"}")
            token.expiresOn?.let { append("\n过期: $it") }
        }
        binding.currentInfoText.text = info

        val policies = token.policies.orEmpty()
        if (policies.isEmpty()) {
            container.addView(makePolicyTextView("（无策略信息可读，或 Token 无任何权限策略）"))
            return
        }
        policies.forEachIndexed { index, policy ->
            val sb = buildString {
                append("策略 ${index + 1}  [${policy.effect}]")
                policy.resources.keys.forEach { res ->
                    append("\n  • 资源: $res")
                }
                append("\n  权限:")
                policy.permissionGroups.orEmpty().forEach { pg ->
                    append("\n    - ${pg.name ?: pg.id}")
                }
            }
            container.addView(makePolicyTextView(sb))
        }
    }

    private fun makePolicyTextView(text: String): TextView {
        // 解析主题颜色 colorPrimaryContainer（Material3 属性，兼容日/夜间模式）
        val ta = requireContext().theme.obtainStyledAttributes(
            intArrayOf(com.google.android.material.R.attr.colorPrimaryContainer)
        )
        val containerColor = ta.getColor(0, 0xFFEADDFF.toInt())
        ta.recycle()
        val radius = 12f * resources.displayMetrics.density
        val bg = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = radius
            setColor(containerColor)
        }
        return TextView(requireContext()).apply {
            this.text = text
            textSize = 12f
            setPadding((12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt(),
                (12 * resources.displayMetrics.density).toInt())
            this.background = bg
            val lp = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = (8 * resources.displayMetrics.density).toInt()
            layoutParams = lp
            setOnClickListener {
                copyToClipboard(text)
                showToast("已复制")
            }
        }
    }

    private fun renderResult(name: String?, value: String) {
        binding.resultTitleText.text = "✅ Token 「${name ?: ""}」创建成功"
        binding.tokenValueText.text = value
        showResultMode()
    }

    // ==================== 工具 ====================

    private fun copyToClipboard(text: String) {
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("api_token", text))
    }

    override fun onStart() {
        super.onStart()
        // 展开底部弹窗
        (dialog as? com.google.android.material.bottomsheet.BottomSheetDialog)?.behavior?.let {
            it.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            it.skipCollapsed = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        // 清理已创建的 Token，避免下次重新打开时残留旧结果
        viewModel.clearCreatedToken()
    }

    companion object {
        private const val ARG_TOKEN = "token"
        private const val ARG_EMAIL = "email"
        private const val ARG_GLOBAL_KEY = "global_key"
        private const val ARG_ACCOUNT_ID = "account_id"
        private const val ARG_AUTH_TYPE = "auth_type"
        private const val ARG_ACCOUNT_NAME = "account_name"

        fun newInstance(account: Account): ApiTokenPermissionSheet {
            return ApiTokenPermissionSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_TOKEN, account.token)
                    putString(ARG_EMAIL, account.email ?: "")
                    putString(ARG_GLOBAL_KEY, account.globalApiKey ?: "")
                    putString(ARG_ACCOUNT_ID, account.accountId)
                    putString(ARG_AUTH_TYPE, account.authType)
                    putString(ARG_ACCOUNT_NAME, account.name)
                }
            }
        }
    }
}
