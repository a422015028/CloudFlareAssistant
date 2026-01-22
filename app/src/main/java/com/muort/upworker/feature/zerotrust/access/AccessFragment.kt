package com.muort.upworker.feature.zerotrust.access

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.AccessApplicationRequest
import com.muort.upworker.databinding.FragmentAccessBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Fragment for managing Access Applications
 */
@AndroidEntryPoint
class AccessFragment : Fragment() {
    
    private var _binding: FragmentAccessBinding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val accessViewModel: AccessViewModel by viewModels()
    
    private lateinit var applicationAdapter: AccessApplicationAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccessBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        
        // Load data
        accountViewModel.defaultAccount.value?.let { account ->
            accessViewModel.loadApplications(account)
        }
    }
    
    private fun setupRecyclerView() {
        applicationAdapter = AccessApplicationAdapter(
            onItemClick = { app ->
                // TODO: Navigate to app detail page when implemented
                accessViewModel.selectApplication(app)
                Snackbar.make(binding.root, "应用: ${app.name}", Snackbar.LENGTH_SHORT).show()
            },
            onDeleteClick = { app ->
                confirmDelete(app)
            }
        )
        
        binding.applicationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = applicationAdapter
        }
    }
    
    private fun setupClickListeners() {
        binding.createAppButton.setOnClickListener {
            showCreateApplicationDialog()
        }
        
        // Refresh when swiping down - handled in observeViewModel
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Applications
                launch {
                    accessViewModel.applications.collect { applications ->
                        applicationAdapter.submitList(applications)
                        binding.emptyView.visibility = if (applications.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                
                // Loading state
                launch {
                    accessViewModel.loadingState.collect { isLoading ->
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                // Messages
                launch {
                    accessViewModel.message.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }
                
                // Errors
                launch {
                    accessViewModel.error.collect { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    
    private fun showCreateApplicationDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_access_app, null)
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.appNameInput)
        val domainInput = dialogView.findViewById<TextInputEditText>(R.id.appDomainInput)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("创建 Access 应用")
            .setView(dialogView)
            .setPositiveButton("创建") { _, _ ->
                val name = nameInput.text.toString()
                val domain = domainInput.text.toString()
                
                if (name.isNotBlank()) {
                    accountViewModel.defaultAccount.value?.let { account ->
                        val request = AccessApplicationRequest(
                            name = name,
                            domain = domain.ifBlank { null },
                            type = "self_hosted"
                        )
                        accessViewModel.createApplication(account, request)
                    }
                } else {
                    Snackbar.make(binding.root, "应用名称不能为空", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun confirmDelete(app: com.muort.upworker.core.model.AccessApplication) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除应用")
            .setMessage("确定要删除 ${app.name} 吗？此操作无法撤销。")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    accessViewModel.deleteApplication(account, app.id)
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
