package com.muort.upworker.feature.zerotrust.access

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.AccessGroupRequest
import com.muort.upworker.databinding.FragmentGroupListBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for managing Access Groups
 */
@AndroidEntryPoint
class GroupFragment : Fragment() {

    private var _binding: FragmentGroupListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AccessViewModel by viewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    private lateinit var groupAdapter: AccessGroupAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        loadGroups()
    }

    private fun setupRecyclerView() {
        groupAdapter = AccessGroupAdapter(
            onDeleteClick = { group ->
                confirmDeleteGroup(group.id, group.name)
            }
        )

        binding.groupsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = groupAdapter
        }
    }

    private fun setupClickListeners() {
        binding.createGroupButton.setOnClickListener {
            showCreateGroupDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Groups
                launch {
                    viewModel.groups.collect { groups ->
                        groupAdapter.submitList(groups)
                        binding.emptyView.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                // Loading state
                launch {
                    viewModel.loadingState.collect { isLoading ->
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }

                // Messages
                launch {
                    viewModel.message.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }

                // Errors
                launch {
                    viewModel.error.collect { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun loadGroups() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            Snackbar.make(binding.root, "未选择账户", Snackbar.LENGTH_SHORT).show()
            return
        }

        viewModel.loadGroups(account)
    }

    private fun showCreateGroupDialog() {
        val account = accountViewModel.defaultAccount.value ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_group_edit, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.groupNameInput)
        
        val includeRecyclerView = dialogView.findViewById<RecyclerView>(R.id.includeRulesRecyclerView)
        val excludeRecyclerView = dialogView.findViewById<RecyclerView>(R.id.excludeRulesRecyclerView)
        val requireRecyclerView = dialogView.findViewById<RecyclerView>(R.id.requireRulesRecyclerView)
        
        val addIncludeButton = dialogView.findViewById<Button>(R.id.addIncludeRuleButton)
        val addExcludeButton = dialogView.findViewById<Button>(R.id.addExcludeRuleButton)
        val addRequireButton = dialogView.findViewById<Button>(R.id.addRequireRuleButton)

        val includeRules = mutableListOf<com.muort.upworker.core.model.AccessRule>()
        val excludeRules = mutableListOf<com.muort.upworker.core.model.AccessRule>()
        val requireRules = mutableListOf<com.muort.upworker.core.model.AccessRule>()

        lateinit var includeAdapter: PolicyRuleAdapter
        lateinit var excludeAdapter: PolicyRuleAdapter
        lateinit var requireAdapter: PolicyRuleAdapter
        
        includeAdapter = PolicyRuleAdapter { rule -> 
            includeRules.remove(rule)
            includeAdapter.submitList(includeRules.toList())
        }
        excludeAdapter = PolicyRuleAdapter { rule ->
            excludeRules.remove(rule)
            excludeAdapter.submitList(excludeRules.toList())
        }
        requireAdapter = PolicyRuleAdapter { rule ->
            requireRules.remove(rule)
            requireAdapter.submitList(requireRules.toList())
        }

        includeRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        excludeRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        requireRecyclerView?.layoutManager = LinearLayoutManager(requireContext())
        
        includeRecyclerView?.adapter = includeAdapter
        excludeRecyclerView?.adapter = excludeAdapter
        requireRecyclerView?.adapter = requireAdapter

        val ruleHelper = PolicyRuleDialogHelper(requireContext())

        addIncludeButton?.setOnClickListener {
            ruleHelper.showAddRuleDialog(viewModel.groups.value) { rule ->
                includeRules.add(rule)
                includeAdapter.submitList(includeRules.toList())
            }
        }

        addExcludeButton?.setOnClickListener {
            ruleHelper.showAddRuleDialog(viewModel.groups.value) { rule ->
                excludeRules.add(rule)
                excludeAdapter.submitList(excludeRules.toList())
            }
        }

        addRequireButton?.setOnClickListener {
            ruleHelper.showAddRuleDialog(viewModel.groups.value) { rule ->
                requireRules.add(rule)
                requireAdapter.submitList(requireRules.toList())
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("创建 Access 组")
            .setView(dialogView)
            .setPositiveButton("创建") { _, _ ->
                val name = nameInput?.text?.toString()
                if (name.isNullOrBlank()) {
                    Snackbar.make(binding.root, "请输入组名称", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (includeRules.isEmpty()) {
                    Snackbar.make(binding.root, "至少需要一条包含规则", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val request = AccessGroupRequest(
                    name = name,
                    include = includeRules,
                    exclude = excludeRules.takeIf { it.isNotEmpty() },
                    require = requireRules.takeIf { it.isNotEmpty() }
                )

                viewModel.createGroup(account, request)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteGroup(groupId: String, groupName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除组")
            .setMessage("确定要删除组 \"$groupName\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteGroup(groupId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteGroup(groupId: String) {
        val account = accountViewModel.defaultAccount.value ?: return
        viewModel.deleteGroup(account, groupId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
