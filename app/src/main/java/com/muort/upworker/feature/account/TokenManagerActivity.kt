package com.muort.upworker.feature.account

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.ApiToken
import com.muort.upworker.core.model.AuthType
import com.muort.upworker.core.model.PermissionGroup
import com.muort.upworker.core.model.TokenCondition
import com.muort.upworker.core.model.TokenIpCondition
import com.muort.upworker.core.model.TokenPermissionGroupRef
import com.muort.upworker.core.model.TokenPolicy
import com.muort.upworker.core.model.TokenUpsertRequest
import com.muort.upworker.core.util.DisplaySizeHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TokenManagerActivity : AppCompatActivity() {

    private val accountViewModel: AccountViewModel by viewModels()
    private val viewModel: TokenManagerViewModel by viewModels()

    private lateinit var adapter: TokenAdapter
    private var currentAccount: Account? = null
    private var accounts: List<Account> = emptyList()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DisplaySizeHelper.wrap(newBase))
    }

    companion object {
        /** Cloudflare API 限制: 每个策略 permission_groups 数量 1-300 */
        const val MAX_PG_PER_POLICY = 300

        /** 策略数量上限（超出 300 个权限组时自动拆分为多个策略） */
        const val MAX_POLICIES = 3

        /** 权限组总上限 = 每策略上限 × 策略数上限 */
        const val MAX_TOTAL_PG = MAX_PG_PER_POLICY * MAX_POLICIES

        fun policiesNeeded(pgCount: Int): Int = (pgCount + MAX_PG_PER_POLICY - 1) / MAX_PG_PER_POLICY

        fun start(context: Context) {
            context.startActivity(Intent(context, TokenManagerActivity::class.java))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_token_manager)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        applySystemBarStyle()
        setupViews()
        observeViewModels()
    }

    private fun applySystemBarStyle() {
        val isDarkMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        if (isDarkMode) {
            @Suppress("DEPRECATION")
            window.statusBarColor = resources.getColor(R.color.black, theme)
            window.insetsController?.setSystemBarsAppearance(
                0,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.statusBarColor = resources.getColor(R.color.white, theme)
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
    }

    private fun setupViews() {
        adapter = TokenAdapter(
            onDetail = { token -> viewModel.showTokenDetail(requireAccount(), token.id) },
            onEdit = { token -> showEditDialog(token) },
            onRoll = { token -> showRollConfirmation(token) },
            onDelete = { token -> showDeleteConfirmation(token) }
        )
        findViewById<RecyclerView>(R.id.tokensRecyclerView).apply {
            layoutManager = LinearLayoutManager(this@TokenManagerActivity)
            adapter = this@TokenManagerActivity.adapter
        }

        findViewById<View>(R.id.createTokenBtn).setOnClickListener { showEditDialog(null) }
        findViewById<View>(R.id.switchAccountBtn).setOnClickListener { showAccountSelector() }
        findViewById<View>(R.id.retryBtn).setOnClickListener {
            currentAccount?.let { viewModel.loadTokens(it) }
        }

        findViewById<com.google.android.material.tabs.TabLayout>(R.id.tokenScopeTabs)
            .addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                    val newScope = if (tab.position == 0) TokenScope.USER else TokenScope.ACCOUNT
                    invalidateOptionsMenu()
                    currentAccount?.let {
                        viewModel.loadTokens(it, newScope)
                        viewModel.loadPermissionGroups(it)
                    }
                }

                override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
                override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            })
    }

    private fun observeViewModels() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    accountViewModel.accounts.collect { list ->
                        accounts = list
                        if (currentAccount == null && list.isNotEmpty()) {
                            val default = accountViewModel.defaultAccount.value
                            selectAccount(default ?: list.first())
                        }
                    }
                }
                launch {
                    accountViewModel.defaultAccount.collect { default ->
                        if (currentAccount == null && default != null) {
                            selectAccount(default)
                        }
                    }
                }
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is TokenUiState.Loading -> {
                                setViewVisibility(loading = true)
                            }
                            is TokenUiState.Empty -> {
                                setViewVisibility(empty = true)
                                adapter.submitList(emptyList())
                            }
                            is TokenUiState.Success -> {
                                setViewVisibility(content = true)
                                adapter.submitList(state.tokens)
                            }
                            is TokenUiState.Error -> {
                                setViewVisibility(error = true)
                                findViewById<TextView>(R.id.errorText).text = state.message
                            }
                        }
                    }
                }
                launch {
                    viewModel.message.collect { showToast(it) }
                }
                launch {
                    viewModel.tokenDetail.collect { token -> showTokenDetailDialog(token) }
                }
                launch {
                    viewModel.tokenCreated.collect { token ->
                        token.value?.let { showTokenValueDialog(it) }
                    }
                }
                launch {
                    viewModel.tokenRolled.collect { newValue ->
                        showTokenValueDialog(newValue)
                    }
                }
                launch {
                    viewModel.verifyResult.collect { result ->
                        showVerifyResultDialog(result)
                    }
                }
            }
        }
    }

    private fun setViewVisibility(
        content: Boolean = false,
        loading: Boolean = false,
        empty: Boolean = false,
        error: Boolean = false
    ) {
        findViewById<View>(R.id.tokensRecyclerView).visibility = if (content) View.VISIBLE else View.GONE
        findViewById<View>(R.id.progressBar).visibility = if (loading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.emptyStateLayout).visibility = if (empty) View.VISIBLE else View.GONE
        findViewById<View>(R.id.errorStateLayout).visibility = if (error) View.VISIBLE else View.GONE
    }

    private fun selectAccount(account: Account) {
        currentAccount = account
        findViewById<TextView>(R.id.accountNameText).text = account.name
        viewModel.loadTokens(account)
        viewModel.loadPermissionGroups(account)
        invalidateOptionsMenu()
    }

    private fun requireAccount(): Account {
        return currentAccount ?: throw IllegalStateException("账号未选择")
    }

    private fun showAccountSelector() {
        if (accounts.isEmpty()) {
            showToast("暂无可用账号")
            return
        }
        val names = accounts.map { it.name }.toTypedArray()
        val selectedIndex = accounts.indexOfFirst { it.id == currentAccount?.id }
        MaterialAlertDialogBuilder(this)
            .setTitle("选择账号")
            .setSingleChoiceItems(names, selectedIndex) { dialog, which ->
                selectAccount(accounts[which])
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 编辑/创建 ====================

    private fun showEditDialog(existing: ApiToken?) {
        val account = currentAccount
        if (account == null) {
            showToast("请先选择账号")
            return
        }
        if (viewModel.permissionGroups.value.isEmpty()) {
            viewModel.loadPermissionGroups(account, force = true)
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_token_edit, null)
        val titleText = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.tokenNameInput)
        val statusSwitch = dialogView.findViewById<com.google.android.material.materialswitch.MaterialSwitch>(R.id.tokenStatusSwitch)
        val expiresInput = dialogView.findViewById<TextInputEditText>(R.id.tokenExpiresInput)
        val ipInput = dialogView.findViewById<TextInputEditText>(R.id.tokenIpInput)
        val selectPgBtn = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.selectPermissionGroupsBtn)

        titleText.text = when {
            existing == null && viewModel.scope.value == TokenScope.USER -> "创建用户级令牌"
            existing == null -> "创建账户级令牌"
            viewModel.scope.value == TokenScope.USER -> "编辑用户级令牌"
            else -> "编辑账户级令牌"
        }
        statusSwitch.visibility = if (existing == null) View.GONE else View.VISIBLE

        // 已选权限组（合并所有策略的权限组，保存时重新按 300 分块）
        val selectedPgIds = mutableSetOf<String>()
        existing?.policies?.forEach { policy ->
            policy.permissionGroups.forEach { selectedPgIds.add(it.id) }
        }

        fun updatePgButtonText() {
            val suffix = if (selectedPgIds.size > MAX_PG_PER_POLICY) {
                "，将拆为 ${policiesNeeded(selectedPgIds.size)} 个策略"
            } else ""
            selectPgBtn.text = "选择权限组 (已选 ${selectedPgIds.size} 个，上限 $MAX_TOTAL_PG$suffix)"
        }

        if (existing != null) {
            nameInput.setText(existing.name)
            statusSwitch.isChecked = existing.status != "disabled"
            expiresInput.setText(existing.expiresOn?.take(10) ?: "")
            ipInput.setText(existing.condition?.requestIp?.inList?.joinToString(", ") ?: "")
        }
        updatePgButtonText()

        selectPgBtn.setOnClickListener {
            showPermissionGroupSelector(selectedPgIds) { updatePgButtonText() }
        }

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton(if (existing == null) "创建" else "保存") { dialog, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    showToast("令牌名称不能为空")
                    return@setPositiveButton
                }
                if (selectedPgIds.isEmpty()) {
                    showToast("请至少选择一个权限组")
                    return@setPositiveButton
                }
                if (selectedPgIds.size > MAX_TOTAL_PG) {
                    showToast("权限组数量超出上限（最多 $MAX_TOTAL_PG 个 = $MAX_POLICIES 个策略 × $MAX_PG_PER_POLICY 个，当前 ${selectedPgIds.size} 个）")
                    return@setPositiveButton
                }

                val expiresDate = expiresInput.text?.toString()?.trim().orEmpty()
                if (expiresDate.isNotEmpty() && !Regex("""^\d{4}-\d{2}-\d{2}$""").matches(expiresDate)) {
                    showToast("过期日期格式应为 yyyy-MM-dd")
                    return@setPositiveButton
                }

                val ips = ipInput.text?.toString()?.trim()
                    ?.split(",", "，", ";")
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    ?: emptyList()

                val allGroups = viewModel.permissionGroups.value
                val selectedGroups = selectedPgIds.mapNotNull { id -> allGroups.firstOrNull { it.id == id } }
                if (selectedGroups.isEmpty()) {
                    showToast("权限组数据加载中，请重新打开对话框")
                    return@setPositiveButton
                }

                dialog.dismiss()
                lifecycleScope.launch {
                    val resources = buildResources(account, selectedGroups, existing)
                    if (resources.isEmpty()) {
                        showToast("无法确定资源范围，请稍后重试")
                        return@launch
                    }

                    val condition = if (ips.isEmpty()) null else
                        TokenCondition(requestIp = TokenIpCondition(inList = ips))

                    // 按 300 个一组拆分为多个策略（超过 300 时自动多策略）
                    val origPolicies = existing?.policies.orEmpty()
                    val policies = selectedGroups
                        .map { TokenPermissionGroupRef(id = it.id, name = it.name) }
                        .chunked(MAX_PG_PER_POLICY)
                        .mapIndexed { index, pgRefs ->
                            TokenPolicy(
                                id = origPolicies.getOrNull(index)?.id,
                                effect = origPolicies.getOrNull(index)?.effect ?: "allow",
                                permissionGroups = pgRefs,
                                resources = resources
                            )
                        }

                    val request = TokenUpsertRequest(
                        name = name,
                        policies = policies,
                        expires_on = if (expiresDate.isNotEmpty()) "${expiresDate}T23:59:59Z"
                        else existing?.expiresOn, // 留空表示保持原有设置
                        condition = condition,
                        status = if (existing == null) null
                        else if (statusSwitch.isChecked) "active" else "disabled"
                    )

                    if (existing == null) {
                        viewModel.createToken(account, request)
                    } else {
                        viewModel.updateToken(account, existing.id, request)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 按所选权限组的 scopes 构造策略 resources。
     * 账户级令牌必须包含账户资源（API 校验 resources 长度 1-300）；
     * 用户级令牌按 account/zone/user scope 生成对应资源。
     */
    private suspend fun buildResources(
        account: Account,
        groups: List<PermissionGroup>,
        existing: ApiToken?
    ): Map<String, Any> {
        // 编辑时保留原 resources（扁平化，避免回传嵌套结构损坏服务端记录）
        existing?.policies?.firstOrNull()?.resources
            ?.flatMap { (k, v) -> flattenResource(k, v) }
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it.toMap() }

        if (viewModel.scope.value == TokenScope.ACCOUNT) {
            return mapOf("com.cloudflare.api.account.${account.accountId}" to "*")
        }

        val scopes = groups.flatMap { it.scopes ?: emptyList() }.toSet()
        val res = linkedMapOf<String, Any>()
        if (scopes.contains("com.cloudflare.api.account")) {
            res["com.cloudflare.api.account.${account.accountId}"] = "*"
        }
        if (scopes.contains("com.cloudflare.api.account.zone")) {
            res["com.cloudflare.api.account.zone.*"] = "*"
        }
        if (scopes.contains("com.cloudflare.api.user")) {
            viewModel.fetchUserId(account)?.let { res["com.cloudflare.api.user.$it"] = "*" }
        }
        return res
    }

    /**
     * 服务端有时返回嵌套形式 {"account.<id>": {"account.zone.*": "*"}}，
     * 直接回传会触发服务端存储损坏（列表/详情/删除全部 500），必须展平为扁平键值
     */
    private fun flattenResource(key: String, value: Any): List<Pair<String, Any>> =
        if (value is Map<*, *>) {
            value.flatMap { (k, v) -> flattenResource(k.toString(), v ?: "*") }
        } else {
            listOf(key to value)
        }

    /**
     * 权限组多选对话框（带搜索）
     */
    private fun showPermissionGroupSelector(selectedIds: MutableSet<String>, onChanged: () -> Unit) {
        val allGroups = viewModel.permissionGroups.value
        if (allGroups.isEmpty()) {
            showToast("权限组加载中，请稍后重试")
            viewModel.loadPermissionGroups(requireAccount(), force = true)
            return
        }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_permission_groups, null)
        val searchInput = dialogView.findViewById<TextInputEditText>(R.id.searchInput)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.permissionGroupsRecyclerView)

        val limitToast = {
            showToast("已达权限组总上限（$MAX_TOTAL_PG 个 = $MAX_POLICIES 策略 × $MAX_PG_PER_POLICY，超出部分将拆分为多策略）")
        }
        val pgAdapter = PermissionGroupAdapter(allGroups, selectedIds, onLimitReached = limitToast)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = pgAdapter

        // 全选当前筛选结果（受搜索词影响，遵守上限）
        dialogView.findViewById<View>(R.id.selectAllBtn).setOnClickListener {
            pgAdapter.selectAllFiltered()
        }
        dialogView.findViewById<View>(R.id.clearAllBtn).setOnClickListener {
            pgAdapter.clearAll()
        }

        searchInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                pgAdapter.filter(s?.toString().orEmpty())
            }
        })

        MaterialAlertDialogBuilder(this)
            .setTitle("选择权限组")
            .setView(dialogView)
            .setPositiveButton("确定") { dialog, _ ->
                onChanged()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== 详情/验证/删除/令牌值 ====================

    private fun showTokenDetailDialog(token: ApiToken) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_token_detail, null)
        val content = buildString {
            appendLine("名称: ${token.name ?: "无"}")
            appendLine("状态: ${statusDisplay(token.status)}")
            appendLine("ID: ${token.id}")
            appendLine()
            appendLine("—— 时间信息 ——")
            appendLine("创建时间: ${formatIsoTime(token.issuedOn)}")
            appendLine("修改时间: ${formatIsoTime(token.modifiedOn)}")
            appendLine("最后使用: ${formatIsoTime(token.lastUsedOn)}")
            appendLine("生效时间: ${formatIsoTime(token.notBefore)}")
            appendLine("过期时间: ${formatIsoTime(token.expiresOn)}")
            token.condition?.requestIp?.let { ip ->
                appendLine()
                appendLine("—— IP 限制 ——")
                ip.inList?.takeIf { it.isNotEmpty() }?.let { appendLine("允许: ${it.joinToString(", ")}") }
                ip.notInList?.takeIf { it.isNotEmpty() }?.let { appendLine("拒绝: ${it.joinToString(", ")}") }
            }
            token.policies?.forEachIndexed { index, policy ->
                appendLine()
                appendLine("—— 策略 ${index + 1} (${policy.effect}) ——")
                policy.permissionGroups.forEach { pg ->
                    appendLine("• ${pg.name ?: pg.id}")
                }
                val resources = policy.resources
                if (resources != null && resources.isNotEmpty()) {
                    appendLine("资源范围:")
                    resources.forEach { (k, v) -> appendLine("  $k = $v") }
                } else {
                    appendLine("资源范围: 所有资源")
                }
            }
        }
        dialogView.findViewById<TextView>(R.id.detailContentText).text = content
        dialogView.findViewById<TextView>(R.id.detailTitleText).text = token.name ?: "令牌详情"

        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showVerifyResultDialog(result: com.muort.upworker.core.model.TokenVerifyResult) {
        val content = buildString {
            appendLine("状态: ${statusDisplay(result.status)}")
            appendLine("令牌 ID: ${result.id ?: "无"}")
            appendLine("生效时间: ${formatIsoTime(result.notBefore)}")
            appendLine("过期时间: ${formatIsoTime(result.expiresOn)}")
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("验证结果（当前账号凭据）")
            .setMessage(content)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showTokenValueDialog(value: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_token_value, null)
        val valueInput = dialogView.findViewById<TextInputEditText>(R.id.tokenValueInput)
        valueInput.setText(value)
        dialogView.findViewById<View>(R.id.copyValueBtn).setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("token", value))
            showToast("已复制到剪贴板")
        }
        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("关闭", null)
            .setCancelable(false)
            .show()
    }

    /**
     * 更换令牌 secret 确认（仅账户级令牌支持，旧值立即失效）
     */
    private fun showRollConfirmation(token: ApiToken) {
        val account = currentAccount ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("更换令牌")
            .setMessage("确定更换 \"${token.name}\" 的令牌值吗？\n\n旧令牌值将立即失效，所有使用它的服务都会中断，请谨慎操作。")
            .setPositiveButton("更换") { _, _ ->
                viewModel.rollToken(account, token.id)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeleteConfirmation(token: ApiToken) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除令牌")
            .setMessage("确定要删除令牌 \"${token.name}\" 吗？\n使用该令牌的应用将立即失效。")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteToken(requireAccount(), token.id)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ==================== Menu ====================

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_token_manager, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // verify 只接受 Bearer 认证（用户级令牌走 /user/tokens/verify，
        // cfat_ 账户令牌走 /accounts/{id}/tokens/verify，Repository 自动分发）；
        // Global API Key 账号无法验证，隐藏
        menu.findItem(R.id.action_verify_token)?.isVisible =
            currentAccount?.getAuthTypeEnum() != AuthType.GLOBAL_API_KEY
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_verify_token -> {
                currentAccount?.let { viewModel.verifyToken(it) } ?: showToast("请先选择账号")
                true
            }
            R.id.action_refresh_tokens -> {
                currentAccount?.let { viewModel.loadTokens(it) }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    // ==================== 工具方法 ====================

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun statusDisplay(status: String?): String {
        return when (status) {
            "active" -> "启用"
            "disabled" -> "已禁用"
            "expired" -> "已过期"
            else -> status ?: "未知"
        }
    }

    private fun formatIsoTime(iso: String?): String {
        if (iso.isNullOrBlank()) return "无"
        return iso.take(16).replace("T", " ") + " UTC"
    }
}

// ==================== Adapter ====================

class TokenAdapter(
    private val onDetail: (ApiToken) -> Unit,
    private val onEdit: (ApiToken) -> Unit,
    private val onRoll: (ApiToken) -> Unit = {},
    private val onDelete: (ApiToken) -> Unit
) : RecyclerView.Adapter<TokenAdapter.TokenViewHolder>() {

    private var tokens = listOf<ApiToken>()

    fun submitList(newTokens: List<ApiToken>) {
        tokens = newTokens
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TokenViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_token, parent, false)
        return TokenViewHolder(view)
    }

    override fun onBindViewHolder(holder: TokenViewHolder, position: Int) {
        holder.bind(tokens[position])
    }

    override fun getItemCount() = tokens.size

    inner class TokenViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.tokenNameText)
        private val statusChip: com.google.android.material.chip.Chip = itemView.findViewById(R.id.tokenStatusChip)
        private val idText: TextView = itemView.findViewById(R.id.tokenIdText)
        private val permissionsText: TextView = itemView.findViewById(R.id.tokenPermissionsText)
        private val timeText: TextView = itemView.findViewById(R.id.tokenTimeText)

        fun bind(token: ApiToken) {
            val context = itemView.context
            nameText.text = token.name ?: "未命名"
            idText.text = token.id

            statusChip.text = when (token.status) {
                "active" -> "启用"
                "disabled" -> "已禁用"
                "expired" -> "已过期"
                else -> token.status ?: "未知"
            }
            val chipColor = when (token.status) {
                "active" -> android.R.color.holo_green_dark
                "expired" -> R.color.md_theme_error
                else -> android.R.color.darker_gray
            }
            statusChip.setTextColor(context.getColor(chipColor))

            val pgNames = token.policies?.firstOrNull()?.permissionGroups
                ?.mapNotNull { it.name }
                .orEmpty()
            permissionsText.text = if (pgNames.isEmpty()) {
                "权限组: 无"
            } else if (pgNames.size <= 2) {
                "权限组: ${pgNames.joinToString(", ")}"
            } else {
                "权限组: ${pgNames.take(2).joinToString(", ")} 等 ${pgNames.size} 项"
            }

            val times = mutableListOf<String>()
            token.lastUsedOn?.let { times.add("最后使用 ${it.take(10)}") }
            token.expiresOn?.let { times.add("过期 ${it.take(10)}") } ?: times.add("永不过期")
            timeText.text = times.joinToString(" · ")

            itemView.findViewById<View>(R.id.detailBtn).setOnClickListener { onDetail(token) }
            itemView.findViewById<View>(R.id.editBtn).setOnClickListener { onEdit(token) }
            itemView.findViewById<View>(R.id.rollBtn).setOnClickListener { onRoll(token) }
            itemView.findViewById<View>(R.id.deleteBtn).setOnClickListener { onDelete(token) }
        }
    }
}

class PermissionGroupAdapter(
    private val allGroups: List<PermissionGroup>,
    private val selectedIds: MutableSet<String>,
    private val onLimitReached: () -> Unit = {},
    private val onChange: () -> Unit = {}
) : RecyclerView.Adapter<PermissionGroupAdapter.PgViewHolder>() {

    private var filtered = allGroups

    fun filter(query: String) {
        filtered = if (query.isBlank()) {
            allGroups
        } else {
            allGroups.filter {
                it.name?.contains(query, ignoreCase = true) == true || it.id.contains(query, ignoreCase = true)
            }
        }
        notifyDataSetChanged()
    }

    fun selectAllFiltered() {
        for (g in filtered) {
            if (selectedIds.size >= TokenManagerActivity.MAX_TOTAL_PG) break
            selectedIds.add(g.id)
        }
        notifyDataSetChanged()
        if (selectedIds.size >= TokenManagerActivity.MAX_TOTAL_PG && filtered.any { it.id !in selectedIds }) {
            onLimitReached()
        }
        onChange()
    }

    fun clearAll() {
        selectedIds.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PgViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_permission_group, parent, false)
        return PgViewHolder(view)
    }

    override fun onBindViewHolder(holder: PgViewHolder, position: Int) {
        val group = filtered[position]
        holder.checkBox.text = group.name ?: group.id
        // 先摘掉旧监听再赋值：视图复用时避免旧位置的监听器误改其他组的状态
        holder.checkBox.setOnCheckedChangeListener(null)
        holder.checkBox.isChecked = selectedIds.contains(group.id)
        holder.checkBox.setOnCheckedChangeListener { button, isChecked ->
            if (isChecked) {
                if (selectedIds.size >= TokenManagerActivity.MAX_TOTAL_PG) {
                    button.isChecked = false
                    onLimitReached()
                    return@setOnCheckedChangeListener
                }
                selectedIds.add(group.id)
            } else {
                selectedIds.remove(group.id)
            }
            onChange()
        }

        // 操作类型徽章（读/写/执行）+ 资源范围标签
        val context = holder.itemView.context
        val badgeRow = holder.badgeRow
        badgeRow.removeAllViews()
        group.opTypes().forEach { op ->
            val color = when (op) {
                "读" -> 0xFF1E88E5.toInt()
                "写" -> 0xFFE53935.toInt()
                else -> 0xFF8E24AA.toInt()
            }
            badgeRow.addView(makeBadge(context, op, color))
        }
        group.scopeLabels().forEach { label ->
            badgeRow.addView(makeBadge(context, label, 0xFF757575.toInt()))
        }
    }

    override fun getItemCount() = filtered.size

    private fun makeBadge(context: Context, text: String, color: Int): TextView {
        val density = context.resources.displayMetrics.density
        val tv = TextView(context)
        tv.text = text
        tv.textSize = 10f
        tv.setTextColor(color)
        tv.setPadding((8 * density).toInt(), (2 * density).toInt(), (8 * density).toInt(), (2 * density).toInt())
        val bg = android.graphics.drawable.GradientDrawable()
        bg.cornerRadius = 10f * density
        bg.setStroke(Math.max(1, (density).toInt()), (color and 0x00FFFFFF) or 0x66000000)
        tv.background = bg
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.marginEnd = (6 * density).toInt()
        tv.layoutParams = params
        return tv
    }

    class PgViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val checkBox: CheckBox = itemView.findViewById(R.id.permissionGroupCheck)
        val badgeRow: LinearLayout = itemView.findViewById(R.id.badgeRow)
    }
}
