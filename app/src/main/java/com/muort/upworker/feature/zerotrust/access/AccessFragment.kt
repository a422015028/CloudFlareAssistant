package com.muort.upworker.feature.zerotrust.access

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
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
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.AccessApplicationRequest
import com.muort.upworker.core.model.SaasApplication
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
                // Navigate to app detail page
                accessViewModel.selectApplication(app)
                val action = AccessFragmentDirections.actionAccessToDetail(app.id)
                findNavController().navigate(action)
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
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.appTypeSpinner)
        val sessionDurationInput = dialogView.findViewById<TextInputEditText>(R.id.sessionDurationInput)
        val saasConfigCard = dialogView.findViewById<View>(R.id.saasConfigCard)
        val saasConsumerUrlInput = dialogView.findViewById<TextInputEditText>(R.id.saasConsumerUrlInput)
        val saasSpEntityIdInput = dialogView.findViewById<TextInputEditText>(R.id.saasSpEntityIdInput)
        val saasNameIdFormatInput = dialogView.findViewById<TextInputEditText>(R.id.saasNameIdFormatInput)
        val appLauncherSwitch = dialogView.findViewById<SwitchMaterial>(R.id.appLauncherSwitch)
        val autoRedirectSwitch = dialogView.findViewById<SwitchMaterial>(R.id.autoRedirectSwitch)
        
        // Setup app type spinner
        val appTypes = arrayOf(
            "self_hosted" to "自托管应用",
            "saas" to "SaaS 应用",
            "ssh" to "SSH",
            "vnc" to "VNC",
            "app_launcher" to "应用启动器",
            "warp" to "WARP",
            "biso" to "浏览器隔离",
            "bookmark" to "书签"
        )
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            appTypes.map { it.second }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = adapter
        
        // Show/hide SaaS config based on type selection
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isSaas = appTypes[position].first == "saas"
                saasConfigCard.visibility = if (isSaas) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("创建 Access 应用")
            .setView(dialogView)
            .setPositiveButton("创建") { _, _ ->
                val name = nameInput.text.toString()
                val domain = domainInput.text.toString()
                val selectedType = appTypes[typeSpinner.selectedItemPosition].first
                val sessionDuration = sessionDurationInput.text.toString()
                
                if (name.isBlank()) {
                    Snackbar.make(binding.root, "应用名称不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                accountViewModel.defaultAccount.value?.let { account ->
                    val saasApp = if (selectedType == "saas") {
                        val consumerUrl = saasConsumerUrlInput.text.toString()
                        val spEntityId = saasSpEntityIdInput.text.toString()
                        val nameIdFormat = saasNameIdFormatInput.text.toString()
                        if (consumerUrl.isNotBlank() || spEntityId.isNotBlank()) {
                            SaasApplication(
                                consumerServiceUrl = consumerUrl.ifBlank { null },
                                spEntityId = spEntityId.ifBlank { null },
                                nameIdFormat = nameIdFormat.ifBlank { null }
                            )
                        } else null
                    } else null
                    
                    val request = AccessApplicationRequest(
                        name = name,
                        domain = domain.ifBlank { null },
                        type = selectedType,
                        sessionDuration = sessionDuration.ifBlank { null },
                        appLauncherVisible = appLauncherSwitch.isChecked,
                        autoRedirectToIdentity = autoRedirectSwitch.isChecked,
                        saasApp = saasApp
                    )
                    accessViewModel.createApplication(account, request)
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
