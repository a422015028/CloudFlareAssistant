package com.muort.upworker.feature.route

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

// 统一的域名显示数据类
data class UnifiedDomain(
    val id: String,
    val hostname: String,
    val target: String,
    val type: DomainType,
    val originalWorkerDomain: CustomDomain? = null,
    val originalPagesDomain: PagesDomain? = null,
    val projectName: String? = null
)

enum class DomainType {
    WORKER, PAGES
}

@AndroidEntryPoint
class RouteFragment : Fragment() {
    
    private var _binding: FragmentRouteBinding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val workerViewModel: WorkerViewModel by viewModels()
    private val pagesViewModel: PagesViewModel by viewModels()
    
    @Inject
    lateinit var dnsRepository: DnsRepository
    
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
        
        setupAdapter()
        setupTabs()
        setupClickListeners()
        observeViewModel()
        
        accountViewModel.defaultAccount.value?.let { account ->
            workerViewModel.loadWorkerScripts(account)
            workerViewModel.loadRoutes(account)
            workerViewModel.loadCustomDomains(account)
            pagesViewModel.loadProjects(account)
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
            onDeleteClick = { domain ->
                showDeleteDomainDialog(domain)
            }
        )
        binding.domainRecyclerView.adapter = domainAdapter
    }
    
    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                currentTab = tab?.position ?: 0
                updateTabContent()
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    
    private fun updateTabContent() {
        when (currentTab) {
            0 -> {
                binding.routeRecyclerView.visibility = View.VISIBLE
                binding.domainRecyclerView.visibility = View.GONE
                binding.emptyText.text = "暂无路由\n点击 + 添加"
                binding.emptyText.visibility = 
                    if (routeAdapter.itemCount == 0) View.VISIBLE else View.GONE
            }
            1 -> {
                binding.routeRecyclerView.visibility = View.GONE
                binding.domainRecyclerView.visibility = View.VISIBLE
                binding.emptyText.text = "暂无自定义域\n点击 + 添加"
                binding.emptyText.visibility = 
                    if (domainAdapter.itemCount == 0) View.VISIBLE else View.GONE
            }
        }
    }
    
    private fun setupClickListeners() {
        binding.fabAddRoute.setOnClickListener {
            when (currentTab) {
                0 -> showAddRouteDialog()
                1 -> showAddDomainDialog()
            }
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    workerViewModel.routes.collect { routes ->
                        routeAdapter.submitList(routes)
                        if (currentTab == 0) {
                            binding.emptyText.visibility = 
                                if (routes.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
                
                launch {
                    // 合并 Worker 和 Pages 的自定义域
                    combine(
                        workerViewModel.customDomains,
                        pagesViewModel.projects
                    ) { workerDomains, projects ->
                        val unified = mutableListOf<UnifiedDomain>()
                        
                        // 添加 Worker 域名
                        workerDomains.forEach { domain ->
                            unified.add(UnifiedDomain(
                                id = domain.id,
                                hostname = domain.hostname,
                                target = domain.service ?: "Unknown",
                                type = DomainType.WORKER,
                                originalWorkerDomain = domain
                            ))
                        }
                        
                        // 添加 Pages 域名（从项目的 domains 字段）
                        projects.forEach { project ->
                            project.domains?.forEach { domainName ->
                                // 过滤掉默认的 .pages.dev 域名
                                if (!domainName.endsWith(".pages.dev")) {
                                    unified.add(UnifiedDomain(
                                        id = "${project.id}_$domainName",
                                        hostname = domainName,
                                        target = project.subdomain ?: "${project.name}.pages.dev",
                                        type = DomainType.PAGES,
                                        projectName = project.name
                                    ))
                                }
                            }
                        }
                        
                        unified
                    }.collect { domains ->
                        domainAdapter.submitList(domains)
                        if (currentTab == 1) {
                            binding.emptyText.visibility = 
                                if (domains.isEmpty()) View.VISIBLE else View.GONE
                        }
                    }
                }
                
                launch {
                    workerViewModel.loadingState.collect { isLoading ->
                        binding.progressBar.visibility = 
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    workerViewModel.message.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
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
                            workerViewModel.loadWorkerScripts(account)
                            workerViewModel.loadRoutes(account)
                            workerViewModel.loadCustomDomains(account)
                            pagesViewModel.loadProjects(account)
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
            .setTitle("添加路由")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val pattern = dialogBinding.routePattern.text.toString()
                val script = dialogBinding.routeScript.text.toString()
                
                accountViewModel.defaultAccount.value?.let { account ->
                    workerViewModel.createRoute(account, pattern, script)
                }
            }
            .setNegativeButton("取消", null)
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
            .setTitle("编辑路由")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val pattern = dialogBinding.routePattern.text.toString()
                val script = dialogBinding.routeScript.text.toString()
                
                accountViewModel.defaultAccount.value?.let { account ->
                    workerViewModel.updateRoute(account, route.id, pattern, script)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteRouteDialog(route: Route) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除路由")
            .setMessage("确定要删除路由 \"${route.pattern}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    workerViewModel.deleteRoute(account, route.id)
                }
            }
            .setNegativeButton("取消", null)
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
        
        // 类型切换监听
        dialogBinding.serviceTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                dialogBinding.typeWorkerRadio.id -> {
                    dialogBinding.workerScriptLayout.visibility = View.VISIBLE
                    dialogBinding.pagesProjectLayout.visibility = View.GONE
                }
                dialogBinding.typePagesRadio.id -> {
                    dialogBinding.workerScriptLayout.visibility = View.GONE
                    dialogBinding.pagesProjectLayout.visibility = View.VISIBLE
                }
            }
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加自定义域")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val hostname = dialogBinding.domainHostname.text.toString()
                
                accountViewModel.defaultAccount.value?.let { account ->
                    if (dialogBinding.typeWorkerRadio.isChecked) {
                        // Worker 脚本
                        val script = dialogBinding.domainScript.text.toString()
                        if (script.isNotEmpty()) {
                            workerViewModel.addCustomDomain(account, hostname, script)
                        } else {
                            Snackbar.make(binding.root, "请选择 Worker 脚本", Snackbar.LENGTH_SHORT).show()
                        }
                    } else {
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
                            Snackbar.make(binding.root, "请选择 Pages 项目", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteDomainDialog(domain: UnifiedDomain) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除自定义域")
            .setMessage("确定要删除域名 \"${domain.hostname}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
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
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun deletePagesDomain(account: com.muort.upworker.core.model.Account, projectName: String, domainName: String) {
        lifecycleScope.launch {
            when (val result = pagesRepository.deleteDomain(account, projectName, domainName)) {
                is Resource.Success -> {
                    Snackbar.make(binding.root, "域名删除成功", Snackbar.LENGTH_SHORT).show()
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
        }
        
        override fun getItemCount() = domains.size
        
        inner class ViewHolder(
            private val binding: ItemCustomDomainBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(domain: UnifiedDomain) {
                binding.domainHostnameText.text = domain.hostname
                val targetPrefix = when (domain.type) {
                    DomainType.WORKER -> "→ Worker: "
                    DomainType.PAGES -> "→ Pages: "
                }
                binding.domainScriptText.text = "$targetPrefix${domain.target}"
                
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
        val verification = domain.verificationData
        val recordType = verification?.type ?: "CNAME"
        val recordName = verification?.name ?: domain.name
        // 如果API没有返回验证数据，使用项目的subdomain（已包含.pages.dev）
        val recordValue = verification?.value?.takeIf { it.isNotEmpty() } 
            ?: subdomain
        
        val message = buildString {
            appendLine("域名添加成功！")
            appendLine()
            appendLine("域名: ${domain.name}")
            appendLine()
            appendLine("需要添加以下 DNS 记录：")
            appendLine()
            appendLine("类型: $recordType")
            appendLine("名称: $recordName")
            appendLine("目标: $recordValue")
            appendLine()
            if (verification?.value?.isNotEmpty() == true) {
                appendLine("点击【自动配置 DNS】按钮，系统将自动在 CloudFlare DNS 中添加此记录。")
            } else {
                appendLine("点击【自动配置 DNS】按钮，系统将使用默认配置自动添加 DNS 记录。")
            }
            appendLine()
            appendLine("状态: ${domain.status ?: "待验证"}")
        }
        
        // 总是显示自动配置按钮
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("完成 DNS 设置")
            .setMessage(message)
            .setPositiveButton("自动配置 DNS") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    if (account.zoneId.isNullOrBlank()) {
                        Snackbar.make(binding.root, "账号未配置 Zone ID，无法自动添加 DNS 记录", Snackbar.LENGTH_LONG).show()
                    } else {
                        autoConfigureDns(account, recordType, recordName, recordValue)
                    }
                }
            }
            .setNegativeButton("关闭", null)
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
                // 显示加载状态
                Snackbar.make(binding.root, "正在自动配置 DNS 记录...", Snackbar.LENGTH_SHORT).show()
                
                val dnsRequest = DnsRecordRequest(
                    type = recordType,
                    name = recordName,
                    content = recordValue,
                    proxied = true, // 开启 CloudFlare 代理（橙色云朵）
                    ttl = 1 // Auto TTL
                )
                
                when (val result = dnsRepository.createDnsRecord(account, dnsRequest)) {
                    is Resource.Success -> {
                        Snackbar.make(
                            binding.root,
                            "DNS 记录添加成功！域名验证可能需要几分钟时间。",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    is Resource.Error -> {
                        MaterialAlertDialogBuilder(requireContext())
                            .setTitle("DNS 配置失败")
                            .setMessage("无法自动添加 DNS 记录：${result.message}\n\n请手动在 DNS 管理中添加记录。")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                    is Resource.Loading -> {}
                }
            } catch (e: Exception) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("DNS 配置失败")
                    .setMessage("发生错误：${e.message}\n\n请手动在 DNS 管理中添加记录。")
                    .setPositiveButton("确定", null)
                    .show()
            }
        }
    }
}
