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
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.AccessApplicationRequest
import com.muort.upworker.core.model.SaasApplication
import com.muort.upworker.databinding.FragmentAccessDetailBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Fragment for displaying Access Application details
 */
@AndroidEntryPoint
class AccessDetailFragment : Fragment() {

    private var _binding: FragmentAccessDetailBinding? = null
    private val binding get() = _binding!!

    private val args: AccessDetailFragmentArgs by navArgs()
    private val viewModel: AccessViewModel by viewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    
    private lateinit var policyAdapter: AccessPolicyAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccessDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPolicyRecyclerView()
        setupClickListeners()
        observeViewModel()
        loadAppDetail()
    }

    private fun setupPolicyRecyclerView() {
        policyAdapter = AccessPolicyAdapter(
            onEditClick = { policy ->
                showEditPolicyDialog(policy)
            },
            onDeleteClick = { policy ->
                confirmDeletePolicy(policy.id, policy.name)
            }
        )

        binding.policiesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = policyAdapter
        }
    }

    private fun setupClickListeners() {
        binding.addPolicyButton.setOnClickListener {
            showCreatePolicyDialog()
        }

        binding.editButton.setOnClickListener {
            showEditApplicationDialog()
        }

        binding.deleteButton.setOnClickListener {
            confirmDeleteApplication()
        }

        setupSwitchListeners()
    }

    private fun setupSwitchListeners() {
        binding.appLauncherSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateAppConfig("appLauncherVisible", isChecked)
        }

        binding.autoRedirectSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateAppConfig("autoRedirectToIdentity", isChecked)
        }

        binding.bindingCookieSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateAppConfig("enableBindingCookie", isChecked)
        }

        binding.skipInterstitialSwitch.setOnCheckedChangeListener { _, isChecked ->
            updateAppConfig("skipInterstitial", isChecked)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Observe selected app
                launch {
                    viewModel.selectedApp.collect { app ->
                        app?.let { displayAppDetails(it) }
                    }
                }

                // Observe policies
                launch {
                    viewModel.policies.collect { policies ->
                        policyAdapter.submitList(policies)
                        binding.noPoliciesText.visibility = 
                            if (policies.isEmpty()) View.VISIBLE else View.GONE
                        binding.policiesRecyclerView.visibility = 
                            if (policies.isEmpty()) View.GONE else View.VISIBLE
                    }
                }

                // Observe messages
                launch {
                    viewModel.message.collect { message ->
                        android.widget.Toast.makeText(requireContext(), message.asString(requireContext()), android.widget.Toast.LENGTH_SHORT).show()
                    }
                }

                // Observe errors
                launch {
                    viewModel.error.collect { error ->
                        android.widget.Toast.makeText(requireContext(), error.asString(requireContext()), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun loadAppDetail() {
        val account = accountViewModel.defaultAccount.value
                if (account == null) {
            android.widget.Toast.makeText(requireContext(), R.string.msg_no_account_selected, android.widget.Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        viewModel.loadAppDetail(account, args.appId)
        viewModel.loadGroups(account) // Load groups for policy rule selector
    }

    private fun displayAppDetails(app: com.muort.upworker.core.model.AccessApplication) {
        binding.appNameText.text = app.name
        binding.domainText.text = app.domain ?: getString(R.string.zt_access_detail_not_set)
        binding.typeChip.text = getTypeLabel(app.type)
        binding.sessionDurationText.text = app.sessionDuration ?: getString(R.string.zt_access_detail_default)
        binding.createdAtText.text = app.createdAt ?: getString(R.string.zt_device_status_unknown)

        // Advanced config - 先移除listener防止程序设置触发更新
        binding.appLauncherSwitch.setOnCheckedChangeListener(null)
        binding.autoRedirectSwitch.setOnCheckedChangeListener(null)
        binding.bindingCookieSwitch.setOnCheckedChangeListener(null)
        binding.skipInterstitialSwitch.setOnCheckedChangeListener(null)

        binding.appLauncherSwitch.isChecked = app.appLauncherVisible ?: false
        binding.autoRedirectSwitch.isChecked = app.autoRedirectToIdentity ?: false
        binding.bindingCookieSwitch.isChecked = app.enableBindingCookie ?: false
        binding.skipInterstitialSwitch.isChecked = app.skipInterstitial ?: false

        // 恢复listener
        setupSwitchListeners()

        // SaaS config
        if (app.type == "saas" && app.saasApp != null) {
            binding.saasConfigCard.visibility = View.VISIBLE
            binding.saasConsumerUrlText.text = app.saasApp.consumerServiceUrl ?: getString(R.string.zt_access_detail_not_set)
            binding.saasSpEntityIdText.text = app.saasApp.spEntityId ?: getString(R.string.zt_access_detail_not_set)
            binding.saasNameIdFormatText.text = app.saasApp.nameIdFormat ?: getString(R.string.zt_access_detail_not_set)
        } else {
            binding.saasConfigCard.visibility = View.GONE
        }
    }

    private fun getTypeLabel(type: String): String {
        return when (type) {
            "self_hosted" -> getString(R.string.zt_app_type_self_hosted)
            "saas" -> getString(R.string.zt_app_type_saas)
            "ssh" -> getString(R.string.zt_app_type_ssh)
            "vnc" -> getString(R.string.zt_app_type_vnc)
            "app_launcher" -> getString(R.string.zt_app_type_app_launcher)
            "warp" -> getString(R.string.zt_app_type_warp)
            "biso" -> getString(R.string.zt_app_type_biso)
            "bookmark" -> getString(R.string.zt_app_type_bookmark)
            else -> type
        }
    }

    private fun updateAppConfig(field: String, value: Boolean) {
        val account = accountViewModel.defaultAccount.value ?: return
        val app = viewModel.selectedApp.value ?: return

        val updateRequest = AccessApplicationRequest(
            name = app.name,
            domain = app.domain,
            type = app.type,
            sessionDuration = app.sessionDuration,
            appLauncherVisible = if (field == "appLauncherVisible") value else (app.appLauncherVisible ?: false),
            autoRedirectToIdentity = if (field == "autoRedirectToIdentity") value else (app.autoRedirectToIdentity ?: false),
            enableBindingCookie = if (field == "enableBindingCookie") value else (app.enableBindingCookie ?: false),
            skipInterstitial = if (field == "skipInterstitial") value else (app.skipInterstitial ?: false)
        )

        viewModel.updateApplication(account, app.id, updateRequest)
        Timber.d("Update $field = $value for app ${app.id}")
    }

    private fun confirmDeletePolicy(policyId: String, policyName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_policy_delete_title)
            .setMessage(getString(R.string.zt_policy_delete_confirm, policyName))
            .setPositiveButton(R.string.delete) { _, _ ->
                deletePolicy(policyId)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deletePolicy(policyId: String) {
        val account = accountViewModel.defaultAccount.value ?: return
        val app = viewModel.selectedApp.value ?: return
        viewModel.deleteAppPolicy(account, app.id, policyId)
    }

    private fun confirmDeleteApplication() {
        val app = viewModel.selectedApp.value ?: return
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_access_delete_app_title)
            .setMessage(getString(R.string.zt_access_delete_app_confirm_detail, app.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteApplication()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteApplication() {
        val account = accountViewModel.defaultAccount.value ?: return
        val app = viewModel.selectedApp.value ?: return
        
        viewModel.deleteApplication(account, app.id)
        
        // Navigate back after deletion
        findNavController().navigateUp()
    }

    private fun showCreatePolicyDialog() {
        val account = accountViewModel.defaultAccount.value ?: return
        val app = viewModel.selectedApp.value ?: return
        val groups = viewModel.groups.value

        val dialogHelper = PolicyEditDialogHelper(requireContext())
        dialogHelper.showCreatePolicyDialog(groups) { policyRequest ->
            viewModel.createAppPolicy(account, app.id, policyRequest)
        }
    }

    private fun showEditPolicyDialog(policy: com.muort.upworker.core.model.AccessPolicy) {
        val account = accountViewModel.defaultAccount.value ?: return
        val app = viewModel.selectedApp.value ?: return
        val groups = viewModel.groups.value

        val dialogHelper = PolicyEditDialogHelper(requireContext())
        dialogHelper.showEditPolicyDialog(policy, groups) { policyRequest ->
            viewModel.updateAppPolicy(account, app.id, policy.id, policyRequest)
        }
    }

    private fun showEditApplicationDialog() {
        val account = accountViewModel.defaultAccount.value ?: return
        val app = viewModel.selectedApp.value ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_create_access_app, null)
        
        // Get views
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.appNameInput)
        val domainInput = dialogView.findViewById<TextInputEditText>(R.id.appDomainInput)
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.appTypeSpinner)
        val sessionDurationInput = dialogView.findViewById<TextInputEditText>(R.id.sessionDurationInput)
        val saasConfigCard = dialogView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.saasConfigCard)
        val saasConsumerUrlInput = dialogView.findViewById<TextInputEditText>(R.id.saasConsumerUrlInput)
        val saasSpEntityIdInput = dialogView.findViewById<TextInputEditText>(R.id.saasSpEntityIdInput)
        val saasNameIdFormatInput = dialogView.findViewById<TextInputEditText>(R.id.saasNameIdFormatInput)
        val appLauncherSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.appLauncherSwitch)
        val autoRedirectSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.autoRedirectSwitch)

        // Set current values
        nameInput?.setText(app.name)
        domainInput?.setText(app.domain ?: "")
        sessionDurationInput?.setText(app.sessionDuration ?: "")
        appLauncherSwitch?.isChecked = app.appLauncherVisible ?: false
        autoRedirectSwitch?.isChecked = app.autoRedirectToIdentity ?: false

        // Set spinner type
        val types = listOf(
            "self_hosted" to getString(R.string.zt_app_type_self_hosted),
            "saas" to getString(R.string.zt_app_type_saas),
            "ssh" to getString(R.string.zt_app_type_ssh),
            "vnc" to getString(R.string.zt_app_type_vnc),
            "app_launcher" to getString(R.string.zt_app_type_app_launcher),
            "warp" to getString(R.string.zt_app_type_warp),
            "biso" to getString(R.string.zt_app_type_biso),
            "bookmark" to getString(R.string.zt_app_type_bookmark)
        )
        val typeAdapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            types.map { it.second }
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner?.adapter = typeAdapter
        val typePosition = types.indexOfFirst { it.first == app.type }.coerceAtLeast(0)
        typeSpinner?.setSelection(typePosition)

        // Handle SaaS config visibility
        typeSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                saasConfigCard?.visibility = if (types[position].first == "saas") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Set SaaS values if applicable
        if (app.type == "saas" && app.saasApp != null) {
            saasConfigCard?.visibility = View.VISIBLE
            saasConsumerUrlInput?.setText(app.saasApp.consumerServiceUrl ?: "")
            saasSpEntityIdInput?.setText(app.saasApp.spEntityId ?: "")
            saasNameIdFormatInput?.setText(app.saasApp.nameIdFormat ?: "")
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_access_edit_app_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput?.text?.toString()
                val domain = domainInput?.text?.toString()
                val type = types[typeSpinner?.selectedItemPosition ?: 0].first
                val sessionDuration = sessionDurationInput?.text?.toString()

                if (name.isNullOrBlank()) {
                    android.widget.Toast.makeText(requireContext(), R.string.msg_app_name_empty, android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (type != "app_launcher" && type != "bookmark" && domain.isNullOrBlank()) {
                    android.widget.Toast.makeText(requireContext(), R.string.msg_domain_empty, android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val saasApp = if (type == "saas") {
                    SaasApplication(
                        consumerServiceUrl = saasConsumerUrlInput?.text?.toString(),
                        spEntityId = saasSpEntityIdInput?.text?.toString(),
                        nameIdFormat = saasNameIdFormatInput?.text?.toString()
                    )
                } else null

                val updateRequest = AccessApplicationRequest(
                    name = name,
                    domain = domain?.takeIf { it.isNotBlank() },
                    type = type,
                    sessionDuration = sessionDuration?.takeIf { it.isNotBlank() },
                    appLauncherVisible = appLauncherSwitch?.isChecked ?: app.appLauncherVisible,
                    autoRedirectToIdentity = autoRedirectSwitch?.isChecked ?: app.autoRedirectToIdentity,
                    enableBindingCookie = app.enableBindingCookie,
                    skipInterstitial = app.skipInterstitial,
                    saasApp = saasApp
                )

                viewModel.updateApplication(account, app.id, updateRequest)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
