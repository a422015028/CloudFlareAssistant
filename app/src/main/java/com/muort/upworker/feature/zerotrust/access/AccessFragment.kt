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
import android.widget.Toast
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
    }
    
    override fun onResume() {
        super.onResume()
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
                android.widget.Toast.makeText(requireContext(), getString(R.string.zt_app_selected_toast, app.name), android.widget.Toast.LENGTH_SHORT).show()
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
                
                // Messages
                launch {
                    accessViewModel.message.collect { message ->
                        android.widget.Toast.makeText(requireContext(), message.asString(requireContext()), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                
                // Errors
                launch {
                    accessViewModel.error.collect { error ->
                        android.widget.Toast.makeText(requireContext(), error.asString(requireContext()), android.widget.Toast.LENGTH_LONG).show()
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
            "self_hosted" to getString(R.string.zt_app_type_self_hosted),
            "saas" to getString(R.string.zt_app_type_saas),
            "ssh" to getString(R.string.zt_app_type_ssh),
            "vnc" to getString(R.string.zt_app_type_vnc),
            "app_launcher" to getString(R.string.zt_app_type_app_launcher),
            "warp" to getString(R.string.zt_app_type_warp),
            "biso" to getString(R.string.zt_app_type_biso),
            "bookmark" to getString(R.string.zt_app_type_bookmark)
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
            .setTitle(R.string.zt_access_create_app_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val name = nameInput.text.toString()
                val domain = domainInput.text.toString()
                val selectedType = appTypes[typeSpinner.selectedItemPosition].first
                val sessionDuration = sessionDurationInput.text.toString()
                if (name.isBlank()) {
                    android.widget.Toast.makeText(requireContext(), R.string.msg_app_name_empty, android.widget.Toast.LENGTH_SHORT).show()
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
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun confirmDelete(app: com.muort.upworker.core.model.AccessApplication) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_access_delete_app_title)
            .setMessage(getString(R.string.zt_access_delete_app_confirm, app.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    accessViewModel.deleteApplication(account, app.id)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
