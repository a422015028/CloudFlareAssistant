package com.muort.upworker.feature.pages

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.muort.upworker.R
import kotlinx.coroutines.flow.first
import com.muort.upworker.core.model.PagesDeployment
import com.muort.upworker.core.model.PagesProject
import com.muort.upworker.databinding.DialogPagesInputBinding
import com.muort.upworker.databinding.FragmentPagesBinding
import com.muort.upworker.databinding.ItemPagesProjectBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class PagesFragment : Fragment() {
    
    private var _binding: FragmentPagesBinding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val pagesViewModel: PagesViewModel by viewModels()
    
    private lateinit var projectAdapter: ProjectAdapter
    
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
    
    private fun setupAdapter() {
        projectAdapter = ProjectAdapter(
            onProjectClick = { project ->
                accountViewModel.defaultAccount.value?.let { account ->
                    showDeploymentsDialogWithLoading(account, project)
                }
            },
            onDeleteClick = { project ->
                showDeleteProjectDialog(project)
            }
        )
        binding.projectRecyclerView.adapter = projectAdapter
    }
    
    private fun setupClickListeners() {
        binding.fabAddProject.setOnClickListener {
            showAddProjectDialog()
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
            }
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
        
        val items = if (deployments.isEmpty()) {
            arrayOf("暂无部署")
        } else {
            deployments.map { deployment ->
                val status = deployment.latestStage?.status ?: "unknown"
                val time = deployment.createdOn ?: ""
                "${deployment.shortId} - $status - $time"
            }.toTypedArray()
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("${project.name} - 部署列表")
            .setItems(items) { _, which ->
                if (deployments.isNotEmpty() && which < deployments.size) {
                    showDeploymentOptionsDialog(project, deployments[which])
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun showDeploymentOptionsDialog(project: PagesProject, deployment: PagesDeployment) {
        val options = arrayOf("查看详情", "重新部署", "删除部署")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("部署操作")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showDeploymentDetailDialog(deployment)
                    1 -> {
                        accountViewModel.defaultAccount.value?.let { account ->
                            pagesViewModel.retryDeployment(account, project.name, deployment.id)
                        }
                    }
                    2 -> showDeleteDeploymentDialog(project, deployment)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeploymentDetailDialog(deployment: PagesDeployment) {
        val details = buildString {
            append("ID: ${deployment.id}\n")
            append("短ID: ${deployment.shortId}\n")
            append("环境: ${deployment.environment}\n")
            append("URL: ${deployment.url}\n")
            append("状态: ${deployment.latestStage?.status}\n")
            append("创建时间: ${deployment.createdOn}\n")
            deployment.deploymentTrigger?.metadata?.let { meta ->
                append("分支: ${meta.branch}\n")
                append("提交: ${meta.commitHash}\n")
                append("提交信息: ${meta.commitMessage}\n")
            }
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("部署详情")
            .setMessage(details)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun showDeleteDeploymentDialog(project: PagesProject, deployment: PagesDeployment) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除部署")
            .setMessage("确定要删除部署 ${deployment.shortId} 吗？")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    pagesViewModel.deleteDeployment(account, project.name, deployment.id)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private class ProjectAdapter(
        private val onProjectClick: (PagesProject) -> Unit,
        private val onDeleteClick: (PagesProject) -> Unit
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
                binding.projectBranchText.text = "分支: ${project.productionBranch}"
                
                binding.root.setOnClickListener {
                    onProjectClick(project)
                }
                
                binding.projectMenuButton.setOnClickListener { view ->
                    PopupMenu(view.context, view).apply {
                        inflate(R.menu.menu_account)
                        menu.findItem(R.id.action_set_default)?.isVisible = false
                        menu.findItem(R.id.action_edit)?.isVisible = false
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_delete -> {
                                    onDeleteClick(project)
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
