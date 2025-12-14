package com.muort.upworker.feature.route

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
import com.muort.upworker.core.model.Route
import com.muort.upworker.databinding.DialogDomainInputBinding
import com.muort.upworker.databinding.DialogRouteInputBinding
import com.muort.upworker.databinding.FragmentRouteBinding
import com.muort.upworker.databinding.ItemCustomDomainBinding
import com.muort.upworker.databinding.ItemRouteBinding
import com.muort.upworker.feature.account.AccountViewModel
import com.muort.upworker.feature.worker.WorkerViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RouteFragment : Fragment() {
    
    private var _binding: FragmentRouteBinding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val workerViewModel: WorkerViewModel by viewModels()
    
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
                    workerViewModel.customDomains.collect { domains ->
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
                    accountViewModel.defaultAccount.collect { account ->
                        if (account != null) {
                            workerViewModel.loadWorkerScripts(account)
                            workerViewModel.loadRoutes(account)
                            workerViewModel.loadCustomDomains(account)
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
        
        // 设置脚本下拉列表
        val scriptNames = workerViewModel.scripts.value.map { it.id }
        val adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_dropdown_item_1line, scriptNames)
        dialogBinding.domainScript.setAdapter(adapter)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("添加自定义域")
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val hostname = dialogBinding.domainHostname.text.toString()
                val script = dialogBinding.domainScript.text.toString()
                
                accountViewModel.defaultAccount.value?.let { account ->
                    workerViewModel.addCustomDomain(account, hostname, script)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteDomainDialog(domain: CustomDomain) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除自定义域")
            .setMessage("确定要删除域名 \"${domain.hostname}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    workerViewModel.deleteCustomDomain(account, domain.id)
                }
            }
            .setNegativeButton("取消", null)
            .show()
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
        private val onDeleteClick: (CustomDomain) -> Unit
    ) : RecyclerView.Adapter<CustomDomainAdapter.ViewHolder>() {
        
        private var domains = listOf<CustomDomain>()
        
        fun submitList(newList: List<CustomDomain>) {
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
            
            fun bind(domain: CustomDomain) {
                binding.domainHostnameText.text = domain.hostname
                binding.domainScriptText.text = "→ ${domain.service}"
                
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
}
