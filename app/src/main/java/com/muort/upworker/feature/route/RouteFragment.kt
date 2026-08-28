package com.muort.upworker.feature.route

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.PopupMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.muort.upworker.R
import com.muort.upworker.core.model.CustomDomain
import com.muort.upworker.core.model.DnsRecordRequest
import com.muort.upworker.core.model.PagesDomain
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.Route
import com.muort.upworker.databinding.DialogDomainInputBinding
import com.muort.upworker.databinding.DialogRouteInputBinding
import com.muort.upworker.databinding.FragmentRouteBinding
import com.muort.upworker.databinding.ItemCustomDomainBinding
import com.muort.upworker.databinding.ItemRouteBinding
import com.muort.upworker.core.repository.DnsRepository
import com.muort.upworker.feature.account.AccountViewModel
import com.muort.upworker.feature.pages.PagesViewModel
import com.muort.upworker.feature.worker.WorkerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

// 统一的域名显示数据类
data class UnifiedDomain(
    val id: String,
    val hostname: String,
    val target: String,
    val type: DomainType,
    val originalWorkerDomain: CustomDomain? = null,
    val originalPagesDomain: PagesDomain? = null,
    val originalR2Domain: com.muort.upworker.core.model.R2CustomDomain? = null,
    val r2BucketName: String? = null,
    val projectName: String? = null
)

enum class DomainType {
    WORKER, PAGES, R2
}

@AndroidEntryPoint
class RouteFragment : Fragment() {
    
    private var _binding: FragmentRouteBinding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val workerViewModel: WorkerViewModel by viewModels()
    private val pagesViewModel: PagesViewModel by viewModels()
    private val r2ViewModel: com.muort.upworker.feature.r2.R2ViewModel by viewModels()

    private val zoneId: String by lazy { arguments?.getString("zoneId") ?: "" }
    private val zoneName: String by lazy { arguments?.getString("zoneName") ?: "" }

        // R2自定义域ViewModel，需你实现
        // private val r2ViewModel: R2ViewModel by viewModels()
    
    @Inject
    lateinit var dnsRepository: DnsRepository
    
    @Inject
    lateinit var zoneRepository: com.muort.upworker.core.repository.ZoneRepository
    
    @Inject
    lateinit var pagesRepository: com.muort.upworker.core.repository.PagesRepository
    
    private lateinit var routeAdapter: RouteAdapter
    private lateinit var domainAdapter: CustomDomainAdapter
    private var currentTab = 0
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRouteBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 模式切换：
        // - zone 模式（有 zoneId）：只显示该域名的路由规则，单栏布局
        // - account 模式（无 zoneId）：只显示自定义域管理，单栏布局（首页入口）
        val isZoneMode = zoneId.isNotBlank()
        if (isZoneMode) {
            binding.domainPanel.visibility = View.GONE
            binding.dividerView.visibility = View.GONE
            // 左侧路由占满宽度
            binding.routePanel.layoutParams =
                (binding.routePanel.layoutParams as LinearLayout.LayoutParams).apply {
                    weight = 1f
                    width = 0
                }
            activity?.setTitle(R.string.route_title_routes)
        } else {
            binding.routePanel.visibility = View.GONE
            binding.dividerView.visibility = View.GONE
            // 右侧自定义域占满宽度
            binding.domainPanel.layoutParams =
                (binding.domainPanel.layoutParams as LinearLayout.LayoutParams).apply {
                    weight = 1f
                    width = 0
                }
            activity?.setTitle(R.string.route_title_custom_domains)
        }

        setupAdapter()
        setupTabs()
        setupClickListeners()
        observeViewModel()

        accountViewModel.defaultAccount.value?.let { account ->
            workerViewModel.loadWorkerScripts(account)
            if (isZoneMode) {
                workerViewModel.loadRoutes(account, zoneId)
            } else {
                workerViewModel.loadCustomDomains(account)
                pagesViewModel.loadProjects(account)
                // 加载R2存储桶列表
                r2ViewModel.loadBuckets(account)
            }
        }

        // 监听R2存储桶变化，自动加载所有自定义域（仅 account 模式）
        if (!isZoneMode) {
            lifecycleScope.launch {
                r2ViewModel.buckets.collect { buckets ->
                    accountViewModel.defaultAccount.value?.let { account ->
                        buckets.forEach { bucket ->
                            r2ViewModel.loadCustomDomains(account, bucket.name)
                        }
                    }
                }
            }
        }
    }
    
    private fun setupAdapter() {
        routeAdapter = RouteAdapter(
            onEditClick = { route ->
                showEditRouteDialog(route)
            },
            onDeleteClick = { route ->
                showDeleteRouteDialog(route)
            }
        )
        binding.routeRecyclerView.adapter = routeAdapter
        
        domainAdapter = CustomDomainAdapter(
            onEditClick = { domain ->
                when (domain.type) {
                    DomainType.WORKER -> {
                        if (domain.originalWorkerDomain != null) {
                            showEditCustomDomainDialog(domain)
                        }
                    }
                    DomainType.PAGES -> {
                        Snackbar.make(binding.root, getString(R.string.route_pages_domain_not_editable), Snackbar.LENGTH_LONG).show()
                    }
                    DomainType.R2 -> {
                        Snackbar.make(binding.root, getString(R.string.route_r2_domain_not_editable), Snackbar.LENGTH_LONG).show()
                    }
                }
            },
            onDeleteClick = { domain ->
                showDeleteDomainDialog(domain)
            }
        )
        binding.domainRecyclerView.adapter = domainAdapter
    }
    
    private fun setupTabs() {
        // 已移除TabLayout，左右分栏独立显示，无需Tab切换
        // 保留方法以兼容旧代码调用但不做任何事
    }
    
    private fun showEditCustomDomainDialog(domain: UnifiedDomain) {
        val dialogBinding = DialogDomainInputBinding.inflate(layoutInflater)
        // 填充现有数据
        dialogBinding.domainHostname.setText(domain.hostname)
        dialogBinding.typeWorkerRadio.isChecked = true
        dialogBinding.workerScriptLayout.visibility = View.VISIBLE
        dialogBinding.pagesProjectLayout.visibility = View.GONE
        dialogBinding.domainScript.setText(domain.target, false)
        // 设置 Worker 脚本下拉列表
        val scriptNames = workerViewModel.scripts.value.map { it.id }
        val scriptAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, scriptNames)
        dialogBinding.domainScript.setAdapter(scriptAdapter)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.route_edit_custom_domain)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val hostname = dialogBinding.domainHostname.text.toString()
                val script = dialogBinding.domainScript.text.toString()
                accountViewModel.defaultAccount.value?.let { account ->
                    when (domain.type) {
                        DomainType.WORKER -> {
                            if (hostname.isNotEmpty() && script.isNotEmpty()) {
                                workerViewModel.updateCustomDomain(account, domain.id, hostname, script)
                            } else {
                                Snackbar.make(binding.root, getString(R.string.route_domain_and_script_required), Snackbar.LENGTH_SHORT).show()
                            }
                        }
                        DomainType.PAGES -> {
                            Snackbar.make(binding.root, getString(R.string.route_pages_domain_not_modifiable), Snackbar.LENGTH_LONG).show()
                        }
                        DomainType.R2 -> {
                            Snackbar.make(binding.root, getString(R.string.route_r2_domain_edit_not_supported), Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun updateTabContent() {
        // 新布局下不再切换Tab，直接根据各自Adapter数量控制左右两侧emptyText显示
        binding.routeEmptyText.visibility = if (routeAdapter.itemCount == 0) View.VISIBLE else View.GONE
        binding.domainEmptyText.visibility = if (domainAdapter.itemCount == 0) View.VISIBLE else View.GONE
    }
    
    private fun setupClickListeners() {
        binding.fabAddRoute.setOnClickListener {
            showAddRouteDialog()
        }
        binding.fabAddDomain.setOnClickListener {
            showAddDomainDialog()
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    workerViewModel.routes.collect { routes ->
                        routeAdapter.submitList(routes)
                        binding.routeEmptyText.visibility = if (routes.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    // 合并 Worker、Pages、R2 的自定义域
                    combine(
                        workerViewModel.customDomains,
                        pagesViewModel.projects,
                        r2ViewModel.allCustomDomains
                    ) { workerDomains, projects, allR2Domains ->
                        val unified = mutableListOf<UnifiedDomain>()
                        // Worker 域名
                        workerDomains.forEach { domain ->
                            unified.add(UnifiedDomain(
                                id = domain.id,
                                hostname = domain.hostname,
                                target = domain.service ?: "Unknown",
                                type = DomainType.WORKER,
                                originalWorkerDomain = domain
                            ))
                        }
                        // Pages 域名
                        projects.forEach { project ->
                            project.domains?.forEach { domainName ->
                                if (!domainName.endsWith(".pages.dev")) {
                                    unified.add(UnifiedDomain(
                                        id = "${project.id}_$domainName",
                                        hostname = domainName, // 显示域名
                                        target = project.name, // 显示项目名称
                                        type = DomainType.PAGES,
                                        projectName = project.name
                                    ))
                                }
                            }
                        }
                        // R2 域名
                        allR2Domains.forEach { (bucketName, r2Domains) ->
                            r2Domains.forEach { r2Domain ->
                                unified.add(UnifiedDomain(
                                    id = "${bucketName}_${r2Domain.domain}",
                                    hostname = r2Domain.domain,
                                    target = bucketName,
                                    type = DomainType.R2,
                                    originalR2Domain = r2Domain,
                                    r2BucketName = bucketName
                                ))
                            }
                        }
                        unified
                    }.collect { domains ->
                        domainAdapter.submitList(domains)
                        binding.domainEmptyText.visibility = if (domains.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    workerViewModel.loadingState.collect { isLoading ->
                        binding.routeProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                        binding.domainProgressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    workerViewModel.message.collect { message ->
                        Snackbar.make(binding.root, message.asString(requireContext()), Snackbar.LENGTH_SHORT).show()
                    }
                }
                launch {
                    pagesViewModel.message.collect { message ->
                        Snackbar.make(binding.root, message.asString(requireContext()), Snackbar.LENGTH_SHORT).show()
                    }
                }
                launch {
                    accountViewModel.defaultAccount.collect { account ->
                        if (account != null) {
                            workerViewModel.loadWorkerScripts(account)
                            if (zoneId.isNotBlank()) {
                                workerViewModel.loadRoutes(account, zoneId)
                            } else {
                                workerViewModel.loadCustomDomains(account)
                                pagesViewModel.loadProjects(account)
                            }
                        }
                    }
                }
            }
        }
    }
    
    private fun showAddRouteDialog() {
        val dialogBinding = DialogRouteInputBinding.inflate(layoutInflater)
        
        // 设置脚本下拉列表
        val scriptNames = workerViewModel.scripts.value.map { it.id }
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, scriptNames)
        dialogBinding.routeScript.setAdapter(adapter)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.route_add_route)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val pattern = dialogBinding.routePattern.text.toString()
                val script = dialogBinding.routeScript.text.toString()
                
                accountViewModel.defaultAccount.value?.let { account ->
                    workerViewModel.createRoute(account, zoneId, pattern, script)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showEditRouteDialog(route: Route) {
        val dialogBinding = DialogRouteInputBinding.inflate(layoutInflater)
        
        // 设置脚本下拉列表
        val scriptNames = workerViewModel.scripts.value.map { it.id }
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, scriptNames)
        dialogBinding.routeScript.setAdapter(adapter)
        
        // 填充现有数据
        dialogBinding.routePattern.setText(route.pattern)
        dialogBinding.routeScript.setText(route.script, false)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.route_edit_route)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val pattern = dialogBinding.routePattern.text.toString()
                val script = dialogBinding.routeScript.text.toString()
                
                accountViewModel.defaultAccount.value?.let { account ->
                    workerViewModel.updateRoute(account, zoneId, route.id, pattern, script)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showDeleteRouteDialog(route: Route) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.route_delete_route)
            .setMessage(getString(R.string.route_delete_route_confirm, route.pattern))
            .setPositiveButton(R.string.delete) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    workerViewModel.deleteRoute(account, zoneId, route.id)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showAddDomainDialog() {
        val dialogBinding = DialogDomainInputBinding.inflate(layoutInflater)

        // 设置 Worker 脚本下拉列表
        val scriptNames = workerViewModel.scripts.value.map { it.id }
        val scriptAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, scriptNames)
        dialogBinding.domainScript.setAdapter(scriptAdapter)

        // 设置 Pages 项目下拉列表
        val projectNames = pagesViewModel.projects.value.map { it.name }
        val projectAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, projectNames)
        dialogBinding.domainProject.setAdapter(projectAdapter)

        // 设置 R2 存储桶下拉列表（假设你已在布局中添加 domainR2Bucket AutoCompleteTextView 和 typeR2Radio RadioButton）
        val r2BucketNames = r2ViewModel.buckets.value.map { it.name }
        val r2BucketAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, r2BucketNames)
        dialogBinding.domainR2Bucket.setAdapter(r2BucketAdapter)

        // 类型切换监听
        dialogBinding.serviceTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                dialogBinding.typeWorkerRadio.id -> {
                    dialogBinding.workerScriptLayout.visibility = View.VISIBLE
                    dialogBinding.pagesProjectLayout.visibility = View.GONE
                    dialogBinding.r2BucketLayout.visibility = View.GONE
                }
                dialogBinding.typePagesRadio.id -> {
                    dialogBinding.workerScriptLayout.visibility = View.GONE
                    dialogBinding.pagesProjectLayout.visibility = View.VISIBLE
                    dialogBinding.r2BucketLayout.visibility = View.GONE
                }
                dialogBinding.typeR2Radio.id -> {
                    dialogBinding.workerScriptLayout.visibility = View.GONE
                    dialogBinding.pagesProjectLayout.visibility = View.GONE
                    dialogBinding.r2BucketLayout.visibility = View.VISIBLE
                }
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.route_add_custom_domain)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val hostname = dialogBinding.domainHostname.text.toString()

                accountViewModel.defaultAccount.value?.let { account ->
                    when {
                        dialogBinding.typeWorkerRadio.isChecked -> {
                            // Worker 脚本
                            val script = dialogBinding.domainScript.text.toString()
                            if (script.isNotEmpty()) {
                                workerViewModel.addCustomDomain(account, hostname, script)
                            } else {
                                Snackbar.make(binding.root, getString(R.string.route_please_select_worker_script), Snackbar.LENGTH_SHORT).show()
                            }
                        }
                        dialogBinding.typePagesRadio.isChecked -> {
                            // Pages 项目
                            val project = dialogBinding.domainProject.text.toString()
                            if (project.isNotEmpty()) {
                                // 从项目列表中查找项目的subdomain
                                val pagesProject = pagesViewModel.projects.value.find { it.name == project }
                                val subdomain = pagesProject?.subdomain ?: "$project.pages.dev"

                                pagesViewModel.addCustomDomain(account, project, hostname) { result: Resource<PagesDomain> ->
                                    if (result is Resource.Success) {
                                        // 显示 DNS 配置说明，传递正确的subdomain
                                        showDnsConfigDialog(result.data, subdomain)
                                    }
                                }
                            } else {
                                Snackbar.make(binding.root, getString(R.string.route_please_select_pages_project), Snackbar.LENGTH_SHORT).show()
                            }
                        }
                        dialogBinding.typeR2Radio.isChecked == true -> {
                            // R2 存储桶
                            val bucket = dialogBinding.domainR2Bucket.text.toString()
                            if (bucket.isNotEmpty()) {
                                r2ViewModel.createCustomDomain(account, bucket, hostname)
                                Snackbar.make(binding.root, getString(R.string.route_r2_domain_request_sent), Snackbar.LENGTH_SHORT).show()
                            } else {
                                Snackbar.make(binding.root, getString(R.string.route_please_select_r2_bucket), Snackbar.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showDeleteDomainDialog(domain: UnifiedDomain) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.route_delete_custom_domain)
            .setMessage(getString(R.string.route_delete_custom_domain_confirm, domain.hostname))
            .setPositiveButton(R.string.delete) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    when (domain.type) {
                        DomainType.WORKER -> {
                            domain.originalWorkerDomain?.let {
                                workerViewModel.deleteCustomDomain(account, it.id)
                            }
                        }
                        DomainType.PAGES -> {
                            domain.projectName?.let { projectName ->
                                deletePagesDomain(account, projectName, domain.hostname)
                            }
                        }
                        DomainType.R2 -> {
                            if (domain.r2BucketName != null) {
                                r2ViewModel.deleteCustomDomain(account, domain.r2BucketName, domain.hostname)
                            }
                        }
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun deletePagesDomain(account: com.muort.upworker.core.model.Account, projectName: String, domainName: String) {
        lifecycleScope.launch {
            when (val result = pagesRepository.deleteDomain(account, projectName, domainName)) {
                is Resource.Success -> {
                    Snackbar.make(binding.root, getString(R.string.route_pages_domain_deleted_success), Snackbar.LENGTH_SHORT).show()
                    // 重新加载项目列表
                    pagesViewModel.loadProjects(account)
                }
                is Resource.Error -> {
                    Snackbar.make(binding.root, result.message, Snackbar.LENGTH_LONG).show()
                }
                is Resource.Loading -> {}
            }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private class RouteAdapter(
        private val onEditClick: (Route) -> Unit,
        private val onDeleteClick: (Route) -> Unit
    ) : RecyclerView.Adapter<RouteAdapter.ViewHolder>() {
        
        private var routes = listOf<Route>()
        
        fun submitList(newList: List<Route>) {
            routes = newList
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemRouteBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(routes[position])
            holder.itemView.setOnClickListener {
                onEditClick(routes[position])
            }
        }
        
        override fun getItemCount() = routes.size
        
        inner class ViewHolder(
            private val binding: ItemRouteBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(route: Route) {
                binding.routePatternText.text = route.pattern
                binding.routeScriptText.text = "→ ${route.script}"
                
                binding.routeMenuButton.setOnClickListener { view ->
                    PopupMenu(view.context, view).apply {
                        inflate(R.menu.menu_account)
                        menu.findItem(R.id.action_set_default)?.isVisible = false
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_edit -> {
                                    onEditClick(route)
                                    true
                                }
                                R.id.action_delete -> {
                                    onDeleteClick(route)
                                    true
                                }
                                else -> false
                            }
                        }
                        show()
                    }
                }
            }
        }
    }
    
    private class CustomDomainAdapter(
        private val onEditClick: (UnifiedDomain) -> Unit,
        private val onDeleteClick: (UnifiedDomain) -> Unit
    ) : RecyclerView.Adapter<CustomDomainAdapter.ViewHolder>() {
        
        private var domains = listOf<UnifiedDomain>()
        
        fun submitList(newList: List<UnifiedDomain>) {
            domains = newList
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemCustomDomainBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(domains[position])
            holder.itemView.setOnClickListener {
                onEditClick(domains[position])
            }
        }
        
        override fun getItemCount() = domains.size
        
        inner class ViewHolder(
            private val binding: ItemCustomDomainBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(domain: UnifiedDomain) {
                // 所有类型都显示域名
                binding.domainHostnameText.text = domain.hostname
                val prefix = when (domain.type) {
                    DomainType.WORKER -> itemView.context.getString(R.string.route_label_worker_prefix)
                    DomainType.PAGES -> itemView.context.getString(R.string.route_label_pages_prefix)
                    DomainType.R2 -> itemView.context.getString(R.string.route_label_r2_prefix)
                }
                // 显示目标（Worker脚本名/Pages项目名/R2桶名）
                binding.domainScriptText.text = "$prefix${domain.target}"
                binding.domainMenuButton.setOnClickListener { view ->
                    PopupMenu(view.context, view).apply {
                        inflate(R.menu.menu_account)
                        menu.findItem(R.id.action_set_default)?.isVisible = false
                        menu.findItem(R.id.action_edit)?.isVisible = false
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_delete -> {
                                    onDeleteClick(domain)
                                    true
                                }
                                else -> false
                            }
                        }
                        show()
                    }
                }
            }
        }
    }
    
    private fun showDnsConfigDialog(domain: PagesDomain, subdomain: String) {
        val validation = domain.validationData
        // 根据验证方式决定记录类型：txt 方式用 TXT 记录，http 方式用 CNAME 记录
        val recordType = when (validation?.method) {
            "txt" -> "TXT"
            else -> "CNAME"
        }
        // txt_name 用于 TXT 验证，否则使用域名本身
        val recordName = validation?.txtName?.takeIf { it.isNotEmpty() } ?: domain.name
        // txt_value 用于 TXT 验证值，CNAME 方式使用 subdomain
        val recordValue = validation?.txtValue?.takeIf { it.isNotEmpty() }
            ?: subdomain

        val message = buildString {
            appendLine(getString(R.string.route_dns_add_success_title))
            appendLine()
            appendLine(getString(R.string.route_dns_record_name, domain.name))
            appendLine()
            appendLine(getString(R.string.route_dns_need_add_records))
            appendLine()
            appendLine(getString(R.string.route_dns_record_type, recordType))
            appendLine(getString(R.string.route_dns_record_name, recordName))
            appendLine(getString(R.string.route_dns_record_target, recordValue))
            appendLine()
            if (!validation?.txtValue.isNullOrEmpty()) {
                appendLine(getString(R.string.route_dns_auto_hint_with_txt))
            } else {
                appendLine(getString(R.string.route_dns_auto_hint_default))
            }
            appendLine()
            appendLine(getString(R.string.route_dns_status_format, domain.status
                ?: getString(R.string.route_dns_status_pending).substringAfter(": ").trim()))
        }
        
        // 总是显示自动配置按钮
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.route_dns_complete_setup_title)
            .setMessage(message)
            .setPositiveButton(R.string.route_dns_auto_configure) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    autoConfigureDns(account, recordType, recordName, recordValue)
                }
            }
            .setNegativeButton(R.string.dialog_close, null)
            .show()
    }
    
    private fun autoConfigureDns(
        account: com.muort.upworker.core.model.Account,
        recordType: String,
        recordName: String,
        recordValue: String
    ) {
        lifecycleScope.launch {
            try {
                // 根据 hostname 自动匹配 zone
                val zone = zoneRepository.findZoneByHostname(account.id, recordName)
                if (zone == null) {
                    Snackbar.make(
                        binding.root,
                        getString(R.string.route_dns_zone_not_found),
                        Snackbar.LENGTH_LONG
                    ).show()
                    return@launch
                }

                // 显示加载状态
                Snackbar.make(binding.root, getString(R.string.route_dns_configuring), Snackbar.LENGTH_SHORT).show()
                
                val dnsRequest = DnsRecordRequest(
                    type = recordType,
                    name = recordName,
                    content = recordValue,
                    proxied = true, // 开启 CloudFlare 代理（橙色云朵）
                    ttl = 1 // Auto TTL
                )
                
                when (val result = dnsRepository.createDnsRecord(account, zone.id, dnsRequest)) {
                    is Resource.Success -> {
                        Snackbar.make(
                            binding.root,
                            getString(R.string.route_dns_record_added_success),
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    is Resource.Error -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle(R.string.route_dns_config_failed_title)
                            .setMessage(getString(R.string.route_dns_config_failed_message, result.message))
                            .setPositiveButton(R.string.confirm, null)
                            .show()
                    }
                    is Resource.Loading -> {}
                }
            } catch (e: Exception) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.route_dns_config_failed_title)
                    .setMessage(getString(R.string.route_dns_config_error_message, e.message ?: ""))
                    .setPositiveButton(R.string.confirm, null)
                    .show()
            }
        }
    }
}
