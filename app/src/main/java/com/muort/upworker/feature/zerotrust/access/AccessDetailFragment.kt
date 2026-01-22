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

        // Advanced config switches
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

                // Observe loading state
                launch {
                    viewModel.loadingState.collect { isLoading ->
                        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
                    }
                }

                // Observe messages
                launch {
                    viewModel.message.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }

                // Observe errors
                launch {
                    viewModel.error.collect { error ->
                        Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun loadAppDetail() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            Snackbar.make(binding.root, "未选择账户", Snackbar.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        viewModel.loadAppDetail(account, args.appId)
        viewModel.loadGroups(account) // Load groups for policy rule selector
    }

    private fun displayAppDetails(app: com.muort.upworker.core.model.AccessApplication) {
        binding.appNameText.text = app.name
        binding.domainText.text = app.domain ?: "未设置"
        binding.typeChip.text = getTypeLabel(app.type)
        binding.sessionDurationText.text = app.sessionDuration ?: "默认"
        binding.createdAtText.text = app.createdAt ?: "未知"

        // Advanced config
        binding.appLauncherSwitch.isChecked = app.appLauncherVisible ?: false
        binding.autoRedirectSwitch.isChecked = app.autoRedirectToIdentity ?: false
        binding.bindingCookieSwitch.isChecked = app.enableBindingCookie ?: false
        binding.skipInterstitialSwitch.isChecked = app.skipInterstitial ?: false

        // SaaS config
        if (app.type == "saas" && app.saasApp != null) {
            binding.saasConfigCard.visibility = View.VISIBLE
            binding.saasConsumerUrlText.text = app.saasApp.consumerServiceUrl ?: "未设置"
            binding.saasSpEntityIdText.text = app.saasApp.spEntityId ?: "未设置"
            binding.saasNameIdFormatText.text = app.saasApp.nameIdFormat ?: "未设置"
        } else {
            binding.saasConfigCard.visibility = View.GONE
        }
    }

    private fun getTypeLabel(type: String): String {
        return when (type) {
            "self_hosted" -> "自托管应用"
            "saas" -> "SaaS 应用"
            "ssh" -> "SSH"
            "vnc" -> "VNC"
            "app_launcher" -> "应用启动器"
            "warp" -> "WARP"
            "biso" -> "浏览器隔离"
            "bookmark" -> "书签"
            else -> type
        }
    }

    private fun updateAppConfig(field: String, value: Boolean) {
        val account = accountViewModel.defaultAccount.value ?: return
        val app = viewModel.selectedApp.value ?: return

        val updateRequest = when (field) {
            "appLauncherVisible" -> AccessApplicationRequest(
                name = app.name,
                domain = app.domain,
                type = app.type,
                appLauncherVisible = value
            )
            "autoRedirectToIdentity" -> AccessApplicationRequest(
                name = app.name,
                domain = app.domain,
                type = app.type,
                autoRedirectToIdentity = value
            )
            "enableBindingCookie" -> AccessApplicationRequest(
                name = app.name,
                domain = app.domain,
                type = app.type,
                enableBindingCookie = value
            )
            "skipInterstitial" -> AccessApplicationRequest(
                name = app.name,
                domain = app.domain,
                type = app.type,
                skipInterstitial = value
            )
            else -> {
                Snackbar.make(binding.root, "未知配置字段", Snackbar.LENGTH_SHORT).show()
                return
            }
        }

        viewModel.updateApplication(account, app.id, updateRequest)
        Timber.d("Update $field = $value for app ${app.id}")
    }

    private fun confirmDeletePolicy(policyId: String, policyName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除策略")
            .setMessage("确定要删除策略 \"$policyName\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                deletePolicy(policyId)
            }
            .setNegativeButton("取消", null)
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
            .setTitle("删除应用")
            .setMessage("确定要删除应用 \"${app.name}\" 吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                deleteApplication()
            }
            .setNegativeButton("取消", null)
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
        val types = listOf("self_hosted", "saas", "ssh", "vnc", "app_launcher", "warp", "biso", "bookmark")
        val typePosition = types.indexOf(app.type).coerceAtLeast(0)
        typeSpinner?.setSelection(typePosition)

        // Handle SaaS config visibility
        typeSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                saasConfigCard?.visibility = if (types[position] == "saas") View.VISIBLE else View.GONE
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
            .setTitle("编辑应用")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val name = nameInput?.text?.toString()
                val domain = domainInput?.text?.toString()
                val type = types[typeSpinner?.selectedItemPosition ?: 0]
                val sessionDuration = sessionDurationInput?.text?.toString()

                if (name.isNullOrBlank()) {
                    Snackbar.make(binding.root, "应用名称不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (type != "app_launcher" && type != "bookmark" && domain.isNullOrBlank()) {
                    Snackbar.make(binding.root, "域名不能为空", Snackbar.LENGTH_SHORT).show()
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
                    appLauncherVisible = appLauncherSwitch?.isChecked,
                    autoRedirectToIdentity = autoRedirectSwitch?.isChecked,
                    saasApp = saasApp
                )

                viewModel.updateApplication(account, app.id, updateRequest)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
