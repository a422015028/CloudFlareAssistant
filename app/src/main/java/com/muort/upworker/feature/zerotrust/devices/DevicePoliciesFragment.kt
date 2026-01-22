package com.muort.upworker.feature.zerotrust.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.DeviceSettingsPolicy
import com.muort.upworker.core.model.DeviceSettingsPolicyRequest
import com.muort.upworker.databinding.FragmentDevicePoliciesBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DevicePoliciesFragment : Fragment() {

    private var _binding: FragmentDevicePoliciesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DevicesViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    
    private lateinit var policyAdapter: DevicePolicyAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDevicePoliciesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        loadPolicies()
    }

    private fun setupRecyclerView() {
        policyAdapter = DevicePolicyAdapter(
            onEditClick = { policy ->
                showPolicyDialog(policy)
            },
            onDeleteClick = { policy ->
                policy.policyId?.let { id ->
                    confirmDeletePolicy(id, policy.name ?: "策略")
                }
            },
            onEnabledChange = { policy, enabled ->
                updatePolicyEnabled(policy, enabled)
            }
        )
        
        binding.policiesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = policyAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddPolicy.setOnClickListener {
            showPolicyDialog(null)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.policies.collect { policies ->
                        policyAdapter.submitList(policies)
                        binding.emptyText.visibility = 
                            if (policies.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.message.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun loadPolicies() {
        accountViewModel.defaultAccount.value?.let { account ->
            viewModel.loadPolicies(account)
        }
    }

    private fun showPolicyDialog(existingPolicy: DeviceSettingsPolicy?) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_create_device_policy, null)
        
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.policyNameInput)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.policyDescriptionInput)
        val matchInput = dialogView.findViewById<TextInputEditText>(R.id.policyMatchInput)
        val precedenceInput = dialogView.findViewById<TextInputEditText>(R.id.policyPrecedenceInput)
        val enabledSwitch = dialogView.findViewById<SwitchMaterial>(R.id.policyEnabledSwitch)
        
        // Populate existing data
        existingPolicy?.let { policy ->
            nameInput.setText(policy.name ?: "")
            descriptionInput.setText(policy.description ?: "")
            matchInput.setText(policy.match ?: "")
            precedenceInput.setText(policy.precedence?.toString() ?: "100")
            enabledSwitch.isChecked = policy.enabled ?: true
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingPolicy == null) "创建策略" else "编辑策略")
            .setView(dialogView)
            .setPositiveButton(if (existingPolicy == null) "创建" else "保存") { _, _ ->
                val account = accountViewModel.defaultAccount.value ?: return@setPositiveButton
                val name = nameInput.text?.toString()
                
                if (name.isNullOrBlank()) {
                    Snackbar.make(binding.root, "策略名称不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val description = descriptionInput.text?.toString()?.takeIf { it.isNotBlank() }
                val match = matchInput.text?.toString()?.takeIf { it.isNotBlank() }
                val precedence = precedenceInput.text?.toString()?.toIntOrNull() ?: 100
                val enabled = enabledSwitch.isChecked
                
                val request = DeviceSettingsPolicyRequest(
                    name = name,
                    description = description,
                    match = match,
                    precedence = precedence,
                    enabled = enabled
                )
                
                if (existingPolicy == null) {
                    viewModel.createPolicy(account, request)
                } else {
                    existingPolicy.policyId?.let { policyId ->
                        viewModel.updatePolicy(account, policyId, request)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updatePolicyEnabled(policy: DeviceSettingsPolicy, enabled: Boolean) {
        val account = accountViewModel.defaultAccount.value ?: return
        policy.policyId?.let { policyId ->
            val request = DeviceSettingsPolicyRequest(
                name = policy.name ?: "",
                description = policy.description,
                match = policy.match,
                precedence = policy.precedence,
                enabled = enabled
            )
            viewModel.updatePolicy(account, policyId, request)
        }
    }

    private fun confirmDeletePolicy(policyId: String, policyName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除策略")
            .setMessage("确定要删除策略 \"$policyName\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    viewModel.deletePolicy(account, policyId)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
