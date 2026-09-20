package com.muort.upworker.feature.zerotrust.tunnels

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.*
import com.muort.upworker.core.util.DisplaySizeHelper
import com.muort.upworker.core.util.LocaleHelper
import com.muort.upworker.core.util.ThemeHelper
import com.muort.upworker.core.repository.ZoneRepository
import com.muort.upworker.databinding.ActivityTunnelConfigBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import javax.inject.Inject

/**
 * 隧道配置详情 Activity
 * 替代原对话框方案，提供完整的隧道配置编辑功能
 */
@AndroidEntryPoint
class TunnelConfigActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(DisplaySizeHelper.wrap(LocaleHelper.applyLocale(newBase)))
    }

    private lateinit var binding: ActivityTunnelConfigBinding
    private val viewModel: TunnelsViewModel by viewModels()
    private val accountViewModel: AccountViewModel by viewModels()

    @Inject
    lateinit var zoneRepository: ZoneRepository

    private lateinit var ingressRuleAdapter: IngressRuleAdapter
    private val ingressRules = mutableListOf<IngressRule>()

    private lateinit var teamnetRouteAdapter: TeamnetRouteAdapter

    private var tunnelId: String = ""
    private var tunnelName: String = ""
    private var readOnly: Boolean = false
    private var remoteConfig: Boolean = true

    private var originRequestExpanded = false
    private var dialogOriginRequestExpanded = false

    companion object {
        const val EXTRA_TUNNEL_ID = "tunnel_id"
        const val EXTRA_TUNNEL_NAME = "tunnel_name"
        const val EXTRA_READ_ONLY = "read_only"
        const val EXTRA_REMOTE_CONFIG = "remote_config"

        fun start(context: Context, tunnelId: String, tunnelName: String, readOnly: Boolean = false, remoteConfig: Boolean = true) {
            val intent = Intent(context, TunnelConfigActivity::class.java).apply {
                putExtra(EXTRA_TUNNEL_ID, tunnelId)
                putExtra(EXTRA_TUNNEL_NAME, tunnelName)
                putExtra(EXTRA_READ_ONLY, readOnly)
                putExtra(EXTRA_REMOTE_CONFIG, remoteConfig)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.applyDynamicColorIfEnabled(this)
        super.onCreate(savedInstanceState)
        binding = ActivityTunnelConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 读取 Intent 参数
        tunnelId = intent.getStringExtra(EXTRA_TUNNEL_ID) ?: ""
        tunnelName = intent.getStringExtra(EXTRA_TUNNEL_NAME) ?: ""
        readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false)
        remoteConfig = intent.getBooleanExtra(EXTRA_REMOTE_CONFIG, true)

        setupToolbar()
        applyStatusBarStyle()
        setupIngressRules()
        setupWarpRouting()
        setupTeamnetRoutes()
        setupOriginRequest()
        setupReadOnlyMode()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)
        supportActionBar?.title = if (readOnly) {
            getString(R.string.common_view_routes)
        } else {
            getString(R.string.zt_tunnel_config_title, tunnelName)
        }
        // Toolbar 使用 colorSurfaceContainer 与卡片背景色保持一致，自动跟随动态取色
        val surfaceContainerColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorSurfaceContainer, 0
        )
        if (surfaceContainerColor != 0) {
            binding.toolbar.setBackgroundColor(surfaceContainerColor)
        }
    }

    private fun applyStatusBarStyle() {
        // Edge-to-edge：状态栏透明，内容延伸到状态栏下方
        // Toolbar 通过 paddingTop 适配状态栏高度，保证内容不被遮挡
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.toolbar.updatePadding(top = statusBarHeight)
            insets
        }

        // 根据昼夜模式切换状态栏图标颜色
        val isDarkMode = resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.isAppearanceLightStatusBars = !isDarkMode
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if (!readOnly) {
            menuInflater.inflate(R.menu.menu_tunnel_config, menu)
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_save -> {
                saveConfiguration()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setupIngressRules() {
        ingressRuleAdapter = IngressRuleAdapter(
            onEdit = { position -> showEditIngressRuleDialog(position) },
            onDelete = { position -> deleteIngressRule(position) },
            readOnly = readOnly
        )

        binding.ingressRulesRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@TunnelConfigActivity)
            adapter = ingressRuleAdapter
        }

        // 添加按钮
        if (readOnly) {
            binding.addIngressRuleButton.visibility = View.GONE
        } else {
            binding.addIngressRuleButton.visibility = View.VISIBLE
            binding.addIngressRuleButton.setOnClickListener {
                showAddIngressRuleDialog()
            }
        }
    }

    private fun setupWarpRouting() {
        // 远程配置的 warp-routing 字段在 Cloudflare API 中是 deprecated + readOnly
        // 私有网络路由通过 /teamnet/routes API 单独管理，此处仅作状态展示
        if (remoteConfig || readOnly) {
            binding.warpRoutingSwitch.isEnabled = false
        }
        // 远程配置时显示只读提示
        binding.warpRoutingNotice.visibility = if (remoteConfig) View.VISIBLE else View.GONE
    }

    private fun setupTeamnetRoutes() {
        teamnetRouteAdapter = TeamnetRouteAdapter(
            onDeleteClick = { route ->
                if (!readOnly) {
                    showDeleteTeamnetRouteConfirm(route)
                }
            }
        )
        binding.teamnetRoutesRecyclerView.apply {
            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this@TunnelConfigActivity)
            adapter = teamnetRouteAdapter
        }

        // 添加按钮
        binding.addTeamnetRouteButton.setOnClickListener {
            showAddTeamnetRouteDialog()
        }

        // 只读模式禁用添加按钮
        if (readOnly) {
            binding.addTeamnetRouteButton.visibility = View.GONE
        }
    }

    private fun setupOriginRequest() {
        // 展开/折叠
        binding.originRequestHeader.setOnClickListener {
            originRequestExpanded = !originRequestExpanded
            binding.originRequestContent.visibility =
                if (originRequestExpanded) View.VISIBLE else View.GONE
            binding.originRequestExpandIcon.rotation = if (originRequestExpanded) 180f else 0f
        }

        // proxyType 下拉选项
        val proxyTypeOptions = listOf("", "socks")
        val proxyTypeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            proxyTypeOptions
        )
        (binding.proxyTypeSpinner as? AutoCompleteTextView)?.setAdapter(proxyTypeAdapter)

        // 只读模式下禁用所有输入
        if (readOnly) {
            disableOriginRequestInputs()
        }
    }

    private fun disableOriginRequestInputs() {
        binding.apply {
            connectTimeoutInput.isFocusable = false
            connectTimeoutInput.isCursorVisible = false
            connectTimeoutInput.keyListener = null
            tlsTimeoutInput.isFocusable = false
            tlsTimeoutInput.isCursorVisible = false
            tlsTimeoutInput.keyListener = null
            tcpKeepAliveInput.isFocusable = false
            tcpKeepAliveInput.isCursorVisible = false
            tcpKeepAliveInput.keyListener = null
            noHappyEyeballsSwitch.isEnabled = false
            keepAliveConnectionsInput.isFocusable = false
            keepAliveConnectionsInput.isCursorVisible = false
            keepAliveConnectionsInput.keyListener = null
            keepAliveTimeoutInput.isFocusable = false
            keepAliveTimeoutInput.isCursorVisible = false
            keepAliveTimeoutInput.keyListener = null
            httpHostHeaderInput.isFocusable = false
            httpHostHeaderInput.isCursorVisible = false
            httpHostHeaderInput.keyListener = null
            originServerNameInput.isFocusable = false
            originServerNameInput.isCursorVisible = false
            originServerNameInput.keyListener = null
            caPoolInput.isFocusable = false
            caPoolInput.isCursorVisible = false
            caPoolInput.keyListener = null
            noTLSVerifySwitch.isEnabled = false
            disableChunkedEncodingSwitch.isEnabled = false
            http2OriginSwitch.isEnabled = false
            matchSNItoHostSwitch.isEnabled = false
            proxyAddressInput.isFocusable = false
            proxyAddressInput.isCursorVisible = false
            proxyAddressInput.keyListener = null
            proxyPortInput.isFocusable = false
            proxyPortInput.isCursorVisible = false
            proxyPortInput.keyListener = null
            proxyTypeSpinner.isEnabled = false
        }
    }

    private fun setupReadOnlyMode() {
        if (readOnly) {
            binding.readonlyNoticeText.visibility = View.VISIBLE
        } else {
            binding.readonlyNoticeText.visibility = View.GONE
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tunnelConfiguration.collect { config ->
                        config?.config?.let { tunnelConfig ->
                            populateConfig(tunnelConfig)
                        }
                    }
                }
                launch {
                    // 合并 CIDR 路由和主机名路由
                    val cidrRoutesFlow = viewModel.teamnetRoutes
                    val hostnameRoutesFlow = viewModel.hostnameRoutes
                    cidrRoutesFlow.combine(hostnameRoutesFlow) { cidrRoutes, hostnameRoutes ->
                        val cidrItems = cidrRoutes.map { NetworkRouteItem.CidrRoute(it) }
                        val hostnameItems = hostnameRoutes.map { NetworkRouteItem.HostnameRouteItem(it) }
                        cidrItems + hostnameItems
                    }.collect { combinedRoutes ->
                        teamnetRouteAdapter.submitList(combinedRoutes)
                        binding.teamnetRoutesEmptyView.visibility =
                            if (combinedRoutes.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.message.collect { message ->
                        android.widget.Toast.makeText(
                            this@TunnelConfigActivity,
                            message.asString(this@TunnelConfigActivity),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                launch {
                    viewModel.error.collect { error ->
                        android.widget.Toast.makeText(
                            this@TunnelConfigActivity,
                            error.asString(this@TunnelConfigActivity),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
                launch {
                    // 等待账户数据加载完成后再请求配置
                    accountViewModel.defaultAccount.collect { account ->
                        if (account != null) {
                            if (viewModel.tunnelConfiguration.value == null) {
                                viewModel.loadTunnelConfiguration(account, tunnelId)
                            }
                            // 加载私有网络路由 (CIDR)
                            if (viewModel.teamnetRoutes.value.isEmpty()) {
                                viewModel.loadTeamnetRoutes(account, tunnelId)
                            }
                            // 加载主机名路由
                            if (viewModel.hostnameRoutes.value.isEmpty()) {
                                viewModel.loadHostnameRoutes(account, tunnelId)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun populateConfig(config: TunnelConfig) {
        // Ingress Rules
        val rules = config.ingress ?: emptyList()
        ingressRules.clear()
        ingressRules.addAll(rules)
        ingressRuleAdapter.submitList(rules)

        // WARP Routing
        binding.warpRoutingSwitch.isChecked = config.warpRouting?.enabled == true

        // Origin Request
        val originRequest = config.originRequest
        if (originRequest != null) {
            binding.connectTimeoutInput.setText(originRequest.connectTimeout?.toString() ?: "")
            binding.tlsTimeoutInput.setText(originRequest.tlsTimeout?.toString() ?: "")
            binding.tcpKeepAliveInput.setText(originRequest.tcpKeepAlive?.toString() ?: "")
            binding.noHappyEyeballsSwitch.isChecked = originRequest.noHappyEyeballs == true
            binding.keepAliveConnectionsInput.setText(originRequest.keepAliveConnections?.toString() ?: "")
            binding.keepAliveTimeoutInput.setText(originRequest.keepAliveTimeout?.toString() ?: "")
            binding.httpHostHeaderInput.setText(originRequest.httpHostHeader ?: "")
            binding.originServerNameInput.setText(originRequest.originServerName ?: "")
            binding.caPoolInput.setText(originRequest.caPool ?: "")
            binding.noTLSVerifySwitch.isChecked = originRequest.noTLSVerify == true
            binding.disableChunkedEncodingSwitch.isChecked = originRequest.disableChunkedEncoding == true
            binding.http2OriginSwitch.isChecked = originRequest.http2Origin == true
            binding.matchSNItoHostSwitch.isChecked = originRequest.matchSNItoHost == true
            binding.proxyAddressInput.setText(originRequest.proxyAddress ?: "")
            binding.proxyPortInput.setText(originRequest.proxyPort?.toString() ?: "")
            (binding.proxyTypeSpinner as? AutoCompleteTextView)?.setText(
                originRequest.proxyType ?: "", false
            )
        }
    }

    // ==================== 私有网络路由对话框 ====================

    private fun showAddTeamnetRouteDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_teamnet_route_add, null)
        val chipGroup = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.routeTypeChipGroup)
        val networkInputLayout = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.networkInputLayout)
        val networkInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.networkInput)
        val commentInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.commentInput)

        var isHostnameMode = false

        // 类型切换
        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            isHostnameMode = checkedIds.contains(R.id.chipTypeHostname)
            if (isHostnameMode) {
                networkInputLayout.hint = getString(R.string.common_hostname)
                networkInputLayout.helperText = getString(R.string.zt_tunnel_route_hostname_hint)
            } else {
                networkInputLayout.hint = getString(R.string.zt_tunnel_private_route_network)
                networkInputLayout.helperText = getString(R.string.zt_tunnel_private_route_network_hint)
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.common_add_route)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val network = networkInput.text?.toString()?.trim() ?: ""
                val comment = commentInput.text?.toString()?.trim()?.ifBlank { null }

                if (network.isBlank()) {
                    val hint = if (isHostnameMode) R.string.common_hostname else R.string.zt_tunnel_private_route_network
                    android.widget.Toast.makeText(this, hint, android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // 格式验证
                if (isHostnameMode) {
                    // 主机名格式验证
                    val hostnamePattern = """^[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?(\.[a-zA-Z0-9]([a-zA-Z0-9\-]*[a-zA-Z0-9])?)+$""".toRegex()
                    if (!hostnamePattern.matches(network)) {
                        android.widget.Toast.makeText(this, "请输入正确的主机名格式，如 wiki.internal.local", android.widget.Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                } else {
                    // CIDR 格式验证
                    val cidrPattern = """^(\d{1,3}\.){3}\d{1,3}/\d{1,2}$|^[0-9a-fA-F:]+/\d{1,3}$""".toRegex()
                    if (!cidrPattern.matches(network)) {
                        android.widget.Toast.makeText(this, "请输入正确的 CIDR 格式，如 10.0.0.0/8", android.widget.Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                }

                val account = accountViewModel.defaultAccount.value
                if (account != null) {
                    if (isHostnameMode) {
                        viewModel.createHostnameRoute(account, network, tunnelId, comment)
                    } else {
                        viewModel.createTeamnetRoute(account, network, tunnelId, comment)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteTeamnetRouteConfirm(route: NetworkRouteItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(getString(R.string.zt_tunnel_private_route_delete_confirm))
            .setPositiveButton(R.string.delete) { _, _ ->
                val account = accountViewModel.defaultAccount.value
                if (account != null) {
                    when (route) {
                        is NetworkRouteItem.CidrRoute ->
                            route.route.id?.let {
                                viewModel.deleteTeamnetRoute(account, it, tunnelId)
                            }
                        is NetworkRouteItem.HostnameRouteItem ->
                            viewModel.deleteHostnameRoute(account, route.id, tunnelId)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ==================== Ingress Rule 编辑对话框 ====================

    private fun showAddIngressRuleDialog() {
        showIngressRuleDialog(null, -1)
    }

    private fun showEditIngressRuleDialog(position: Int) {
        if (position < 0 || position >= ingressRules.size) return
        val rule = ingressRules[position]
        showIngressRuleDialog(rule, position)
    }

    private fun showIngressRuleDialog(rule: IngressRule?, position: Int) {
        val isEdit = rule != null
        val dialogView = layoutInflater.inflate(R.layout.dialog_ingress_rule_edit, null)

        val zoneSpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.dialogZoneSpinner)
        val hostnameInput = dialogView.findViewById<TextInputEditText>(R.id.dialogHostnameInput)
        val pathInput = dialogView.findViewById<TextInputEditText>(R.id.dialogPathInput)
        val serviceInput = dialogView.findViewById<TextInputEditText>(R.id.dialogServiceInput)

        val connectTimeoutInput = dialogView.findViewById<TextInputEditText>(R.id.dialogConnectTimeoutInput)
        val tlsTimeoutInput = dialogView.findViewById<TextInputEditText>(R.id.dialogTlsTimeoutInput)
        val tcpKeepAliveInput = dialogView.findViewById<TextInputEditText>(R.id.dialogTcpKeepAliveInput)
        val noHappyEyeballsSwitch = dialogView.findViewById<SwitchMaterial>(R.id.dialogNoHappyEyeballsSwitch)
        val keepAliveConnectionsInput = dialogView.findViewById<TextInputEditText>(R.id.dialogKeepAliveConnectionsInput)
        val keepAliveTimeoutInput = dialogView.findViewById<TextInputEditText>(R.id.dialogKeepAliveTimeoutInput)
        val httpHostHeaderInput = dialogView.findViewById<TextInputEditText>(R.id.dialogHttpHostHeaderInput)
        val originServerNameInput = dialogView.findViewById<TextInputEditText>(R.id.dialogOriginServerNameInput)
        val caPoolInput = dialogView.findViewById<TextInputEditText>(R.id.dialogCaPoolInput)
        val noTLSVerifySwitch = dialogView.findViewById<SwitchMaterial>(R.id.dialogNoTLSVerifySwitch)
        val disableChunkedEncodingSwitch = dialogView.findViewById<SwitchMaterial>(R.id.dialogDisableChunkedEncodingSwitch)
        val http2OriginSwitch = dialogView.findViewById<SwitchMaterial>(R.id.dialogHttp2OriginSwitch)
        val matchSNItoHostSwitch = dialogView.findViewById<SwitchMaterial>(R.id.dialogMatchSNItoHostSwitch)
        val proxyAddressInput = dialogView.findViewById<TextInputEditText>(R.id.dialogProxyAddressInput)
        val proxyPortInput = dialogView.findViewById<TextInputEditText>(R.id.dialogProxyPortInput)
        val proxyTypeSpinner = dialogView.findViewById<AutoCompleteTextView>(R.id.dialogProxyTypeSpinner)

        val originRequestHeader = dialogView.findViewById<View>(R.id.dialogOriginRequestHeader)
        val originRequestContent = dialogView.findViewById<View>(R.id.dialogOriginRequestContent)
        val originRequestExpandIcon = dialogView.findViewById<android.widget.ImageView>(R.id.dialogOriginRequestExpandIcon)

        // 填充数据
        if (rule != null) {
            hostnameInput.setText(rule.hostname ?: "")
            pathInput.setText(rule.path ?: "")
            serviceInput.setText(rule.service)

            rule.originRequest?.let { or ->
                connectTimeoutInput.setText(or.connectTimeout?.toString() ?: "")
                tlsTimeoutInput.setText(or.tlsTimeout?.toString() ?: "")
                tcpKeepAliveInput.setText(or.tcpKeepAlive?.toString() ?: "")
                noHappyEyeballsSwitch.isChecked = or.noHappyEyeballs == true
                keepAliveConnectionsInput.setText(or.keepAliveConnections?.toString() ?: "")
                keepAliveTimeoutInput.setText(or.keepAliveTimeout?.toString() ?: "")
                httpHostHeaderInput.setText(or.httpHostHeader ?: "")
                originServerNameInput.setText(or.originServerName ?: "")
                caPoolInput.setText(or.caPool ?: "")
                noTLSVerifySwitch.isChecked = or.noTLSVerify == true
                disableChunkedEncodingSwitch.isChecked = or.disableChunkedEncoding == true
                http2OriginSwitch.isChecked = or.http2Origin == true
                matchSNItoHostSwitch.isChecked = or.matchSNItoHost == true
                proxyAddressInput.setText(or.proxyAddress ?: "")
                proxyPortInput.setText(or.proxyPort?.toString() ?: "")
                proxyTypeSpinner.setText(or.proxyType ?: "", false)
            }
        }

        // proxyType 下拉选项
        val proxyTypeOptions = listOf("", "socks")
        val proxyTypeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            proxyTypeOptions
        )
        proxyTypeSpinner.setAdapter(proxyTypeAdapter)

        // 加载域名列表（Zone）
        val zones = mutableListOf<com.muort.upworker.core.model.Zone>()
        val account = accountViewModel.defaultAccount.value
        if (account != null) {
            // 先显示加载中
            val loadingAdapter = ArrayAdapter<String>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                listOf(getString(R.string.dialog_utils_loading_message))
            )
            zoneSpinner.setAdapter(loadingAdapter)

            lifecycleScope.launch {
                when (val result = zoneRepository.fetchAndSaveZones(account)) {
                    is Resource.Success -> {
                        zones.clear()
                        val sorted = result.data.sortedBy { it.name }
                        zones.addAll(sorted)
                        if (zones.isEmpty()) {
                            zoneSpinner.setAdapter(
                                ArrayAdapter(
                                    this@TunnelConfigActivity,
                                    android.R.layout.simple_dropdown_item_1line,
                                    listOf(getString(R.string.worker_route_zone_empty))
                                )
                            )
                        } else {
                            val zoneAdapter = ArrayAdapter(
                                this@TunnelConfigActivity,
                                android.R.layout.simple_spinner_dropdown_item,
                                zones.map { it.name }
                            )
                            zoneSpinner.setAdapter(zoneAdapter)

                            // 编辑模式下，根据已有 hostname 匹配 zone
                            if (isEdit && !rule?.hostname.isNullOrBlank()) {
                                val hostname = rule!!.hostname!!
                                val matchedZone = zones.find { zone ->
                                    hostname.endsWith(zone.name)
                                }
                                if (matchedZone != null) {
                                    zoneSpinner.setText(matchedZone.name, false)
                                }
                            }
                        }
                    }
                    is Resource.Error -> {
                        zoneSpinner.setAdapter(
                            ArrayAdapter(
                                this@TunnelConfigActivity,
                                android.R.layout.simple_dropdown_item_1line,
                                listOf(getString(R.string.worker_route_zone_load_failed, result.message))
                            )
                        )
                    }
                    else -> {}
                }
            }

            // 选择 zone 后自动填充/替换 hostname
            zoneSpinner.setOnItemClickListener { _, _, position, _ ->
                if (position in zones.indices) {
                    val zoneName = zones[position].name
                    val currentHostname = hostnameInput.text?.toString()?.trim().orEmpty()
                    val newHostname = if (currentHostname.isBlank()) {
                        // 空的话填默认通配符
                        "*.$zoneName"
                    } else {
                        // 尝试从当前 hostname 中提取前缀（子域名部分），替换主域名
                        val matchedOldZone = zones.find { z ->
                            currentHostname.endsWith(".${z.name}") || currentHostname == z.name
                        }
                        if (matchedOldZone != null) {
                            val prefix = currentHostname.removeSuffix(".${matchedOldZone.name}")
                            if (prefix.isBlank()) "*.$zoneName" else "$prefix.$zoneName"
                        } else {
                            // 无法匹配就直接追加
                            "$currentHostname.$zoneName"
                        }
                    }
                    hostnameInput.setText(newHostname)
                    hostnameInput.setSelection(newHostname.length)
                }
            }
        }

        // 展开/折叠源站请求设置
        var dialogExpanded = false
        originRequestHeader.setOnClickListener {
            dialogExpanded = !dialogExpanded
            originRequestContent.visibility = if (dialogExpanded) View.VISIBLE else View.GONE
            originRequestExpandIcon.rotation = if (dialogExpanded) 180f else 0f
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(if (isEdit) R.string.edit else R.string.add)
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val service = serviceInput.text?.toString()
                if (service.isNullOrBlank()) {
                    android.widget.Toast.makeText(
                        this,
                        getString(R.string.msg_tunnel_service_empty),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    return@setPositiveButton
                }

                val hostname = hostnameInput.text?.toString()?.takeIf { it.isNotBlank() }
                val path = pathInput.text?.toString()?.takeIf { it.isNotBlank() }

                // 构建 originRequest
                val originRequest = buildOriginRequestFromDialog(
                    connectTimeoutInput, tlsTimeoutInput, tcpKeepAliveInput,
                    noHappyEyeballsSwitch, keepAliveConnectionsInput, keepAliveTimeoutInput,
                    httpHostHeaderInput, originServerNameInput, caPoolInput,
                    noTLSVerifySwitch, disableChunkedEncodingSwitch, http2OriginSwitch,
                    matchSNItoHostSwitch, proxyAddressInput, proxyPortInput, proxyTypeSpinner
                )

                val newRule = IngressRule(
                    hostname = hostname,
                    path = path,
                    service = service,
                    originRequest = originRequest
                )

                if (isEdit && position >= 0) {
                    ingressRules[position] = newRule
                } else {
                    // 新规则插入到 catch-all 规则之前
                    val catchAllIndex = ingressRules.indexOfFirst { it.hostname.isNullOrBlank() }
                    if (catchAllIndex >= 0) {
                        ingressRules.add(catchAllIndex, newRule)
                    } else {
                        ingressRules.add(newRule)
                    }
                }

                // 确保最后有 catch-all 规则
                ensureCatchAllRule()
                ingressRuleAdapter.submitList(ingressRules.toList())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun buildOriginRequestFromDialog(
        connectTimeoutInput: TextInputEditText,
        tlsTimeoutInput: TextInputEditText,
        tcpKeepAliveInput: TextInputEditText,
        noHappyEyeballsSwitch: SwitchMaterial,
        keepAliveConnectionsInput: TextInputEditText,
        keepAliveTimeoutInput: TextInputEditText,
        httpHostHeaderInput: TextInputEditText,
        originServerNameInput: TextInputEditText,
        caPoolInput: TextInputEditText,
        noTLSVerifySwitch: SwitchMaterial,
        disableChunkedEncodingSwitch: SwitchMaterial,
        http2OriginSwitch: SwitchMaterial,
        matchSNItoHostSwitch: SwitchMaterial,
        proxyAddressInput: TextInputEditText,
        proxyPortInput: TextInputEditText,
        proxyTypeSpinner: AutoCompleteTextView
    ): OriginRequest? {
        val connectTimeout = connectTimeoutInput.text?.toString()?.toIntOrNull()
        val tlsTimeout = tlsTimeoutInput.text?.toString()?.toIntOrNull()
        val tcpKeepAlive = tcpKeepAliveInput.text?.toString()?.toIntOrNull()
        val noHappyEyeballs = noHappyEyeballsSwitch.isChecked
        val keepAliveConnections = keepAliveConnectionsInput.text?.toString()?.toIntOrNull()
        val keepAliveTimeout = keepAliveTimeoutInput.text?.toString()?.toIntOrNull()
        val httpHostHeader = httpHostHeaderInput.text?.toString()?.takeIf { it.isNotBlank() }
        val originServerName = originServerNameInput.text?.toString()?.takeIf { it.isNotBlank() }
        val caPool = caPoolInput.text?.toString()?.takeIf { it.isNotBlank() }
        val noTLSVerify = noTLSVerifySwitch.isChecked
        val disableChunkedEncoding = disableChunkedEncodingSwitch.isChecked
        val http2Origin = http2OriginSwitch.isChecked
        val matchSNItoHost = matchSNItoHostSwitch.isChecked
        val proxyAddress = proxyAddressInput.text?.toString()?.takeIf { it.isNotBlank() }
        val proxyPort = proxyPortInput.text?.toString()?.toIntOrNull()
        val proxyType = proxyTypeSpinner.text?.toString()?.takeIf { it.isNotBlank() }

        // 如果所有字段都为空/默认值，返回 null
        val allDefault = connectTimeout == null && tlsTimeout == null && tcpKeepAlive == null &&
                !noHappyEyeballs && keepAliveConnections == null && keepAliveTimeout == null &&
                httpHostHeader == null && originServerName == null && caPool == null &&
                !noTLSVerify && !disableChunkedEncoding && !http2Origin && !matchSNItoHost &&
                proxyAddress == null && proxyPort == null && proxyType == null

        if (allDefault) return null

        return OriginRequest(
            connectTimeout = connectTimeout,
            tlsTimeout = tlsTimeout,
            tcpKeepAlive = tcpKeepAlive,
            noHappyEyeballs = noHappyEyeballs.takeIf { it },
            keepAliveConnections = keepAliveConnections,
            keepAliveTimeout = keepAliveTimeout,
            httpHostHeader = httpHostHeader,
            originServerName = originServerName,
            caPool = caPool,
            noTLSVerify = noTLSVerify.takeIf { it },
            disableChunkedEncoding = disableChunkedEncoding.takeIf { it },
            http2Origin = http2Origin.takeIf { it },
            matchSNItoHost = matchSNItoHost.takeIf { it },
            proxyAddress = proxyAddress,
            proxyPort = proxyPort,
            proxyType = proxyType
        )
    }

    private fun deleteIngressRule(position: Int) {
        if (position < 0 || position >= ingressRules.size) return
        val rule = ingressRules[position]
        // catch-all 规则不允许删除
        if (rule.hostname.isNullOrBlank()) return

        ingressRules.removeAt(position)
        ensureCatchAllRule()
        ingressRuleAdapter.submitList(ingressRules.toList())
    }

    private fun ensureCatchAllRule() {
        val hasCatchAll = ingressRules.any { it.hostname.isNullOrBlank() }
        if (!hasCatchAll) {
            ingressRules.add(IngressRule(service = "http_status:404"))
        }
    }

    // ==================== 保存配置 ====================

    private fun saveConfiguration() {
        val account = accountViewModel.defaultAccount.value ?: run {
            android.widget.Toast.makeText(
                this,
                getString(R.string.msg_please_select_account_first),
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }

        // 确保最后有 catch-all 规则
        ensureCatchAllRule()

        // 构建 WARP Routing（远程配置时此字段为 deprecated/readOnly，不发送）
        val warpRouting = if (!remoteConfig) {
            WarpRouting(enabled = binding.warpRoutingSwitch.isChecked)
        } else null

        // 构建 Origin Request
        val originRequest = buildOriginRequestFromActivity()

        // 构建 TunnelConfig
        val tunnelConfig = TunnelConfig(
            ingress = ingressRules.toList(),
            warpRouting = warpRouting,
            originRequest = originRequest
        )

        val request = TunnelConfigurationRequest(config = tunnelConfig)
        viewModel.updateTunnelConfiguration(account, tunnelId, request)
    }

    private fun buildOriginRequestFromActivity(): OriginRequest? {
        val connectTimeout = binding.connectTimeoutInput.text?.toString()?.toIntOrNull()
        val tlsTimeout = binding.tlsTimeoutInput.text?.toString()?.toIntOrNull()
        val tcpKeepAlive = binding.tcpKeepAliveInput.text?.toString()?.toIntOrNull()
        val noHappyEyeballs = binding.noHappyEyeballsSwitch.isChecked
        val keepAliveConnections = binding.keepAliveConnectionsInput.text?.toString()?.toIntOrNull()
        val keepAliveTimeout = binding.keepAliveTimeoutInput.text?.toString()?.toIntOrNull()
        val httpHostHeader = binding.httpHostHeaderInput.text?.toString()?.takeIf { it.isNotBlank() }
        val originServerName = binding.originServerNameInput.text?.toString()?.takeIf { it.isNotBlank() }
        val caPool = binding.caPoolInput.text?.toString()?.takeIf { it.isNotBlank() }
        val noTLSVerify = binding.noTLSVerifySwitch.isChecked
        val disableChunkedEncoding = binding.disableChunkedEncodingSwitch.isChecked
        val http2Origin = binding.http2OriginSwitch.isChecked
        val matchSNItoHost = binding.matchSNItoHostSwitch.isChecked
        val proxyAddress = binding.proxyAddressInput.text?.toString()?.takeIf { it.isNotBlank() }
        val proxyPort = binding.proxyPortInput.text?.toString()?.toIntOrNull()
        val proxyType = (binding.proxyTypeSpinner as? AutoCompleteTextView)?.text?.toString()?.takeIf { it.isNotBlank() }

        // 如果所有字段都为空/默认值，返回 null
        val allDefault = connectTimeout == null && tlsTimeout == null && tcpKeepAlive == null &&
                !noHappyEyeballs && keepAliveConnections == null && keepAliveTimeout == null &&
                httpHostHeader == null && originServerName == null && caPool == null &&
                !noTLSVerify && !disableChunkedEncoding && !http2Origin && !matchSNItoHost &&
                proxyAddress == null && proxyPort == null && proxyType == null

        if (allDefault) return null

        return OriginRequest(
            connectTimeout = connectTimeout,
            tlsTimeout = tlsTimeout,
            tcpKeepAlive = tcpKeepAlive,
            noHappyEyeballs = noHappyEyeballs.takeIf { it },
            keepAliveConnections = keepAliveConnections,
            keepAliveTimeout = keepAliveTimeout,
            httpHostHeader = httpHostHeader,
            originServerName = originServerName,
            caPool = caPool,
            noTLSVerify = noTLSVerify.takeIf { it },
            disableChunkedEncoding = disableChunkedEncoding.takeIf { it },
            http2Origin = http2Origin.takeIf { it },
            matchSNItoHost = matchSNItoHost.takeIf { it },
            proxyAddress = proxyAddress,
            proxyPort = proxyPort,
            proxyType = proxyType
        )
    }
}
