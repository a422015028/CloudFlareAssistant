package com.muort.upworker.feature.domain

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.content.ContextCompat
import com.muort.upworker.R
import com.muort.upworker.core.model.Zone
import com.muort.upworker.core.util.AnimationHelper
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.DialogAddDomainBinding
import com.muort.upworker.databinding.FragmentDomainListBinding
import com.muort.upworker.databinding.ItemDomainBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DomainListFragment : Fragment() {

    private var _binding: FragmentDomainListBinding? = null
    private val binding get() = _binding!!

    private val accountViewModel: AccountViewModel by activityViewModels()
    private val domainListViewModel: DomainListViewModel by viewModels()

    private lateinit var domainAdapter: DomainAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDomainListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapter()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupAdapter() {
        domainAdapter = DomainAdapter(
            onItemClick = { zone ->
                AnimationHelper.scaleDown(binding.root)
                binding.root.postDelayed({
                    val args = Bundle().apply {
                        putString("zoneId", zone.id)
                        putString("zoneName", zone.name)
                    }
                    findNavController().navigate(R.id.action_domainList_to_domainDetail, args)
                }, 150)
            }
        )
        binding.domainsRecyclerView.adapter = domainAdapter
    }

    private fun setupClickListeners() {
        binding.addDomainFab.setOnClickListener {
            accountViewModel.defaultAccount.value?.let { showAddDomainDialog() }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    accountViewModel.defaultAccount.collect { account ->
                        if (account != null) {
                            domainListViewModel.bind(account)
                        }
                    }
                }
                launch {
                    domainListViewModel.uiState.collect { state ->
                        domainAdapter.submitList(state.zones)
                        binding.emptyStateLayout.visibility =
                            if (state.zones.isEmpty() && !state.isLoading) View.VISIBLE else View.GONE
                        binding.progressBar.visibility =
                            if (state.isLoading && state.zones.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private var addDialogBinding: DialogAddDomainBinding? = null
    private var addDialog: androidx.appcompat.app.AlertDialog? = null

    private fun showAddDomainDialog() {
        domainListViewModel.resetAddState()
        addDialogBinding = DialogAddDomainBinding.inflate(layoutInflater)

        addDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(addDialogBinding!!.root)
            .setPositiveButton(R.string.domain_add_title, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        addDialog!!.setOnShowListener {
            addDialog!!.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener {
                    val domain = addDialogBinding!!.domainInput.text.toString().trim().lowercase()
                    if (isValidDomain(domain)) {
                        accountViewModel.defaultAccount.value?.let { account ->
                            domainListViewModel.createZone(account, domain)
                        }
                    } else {
                        addDialogBinding!!.errorText.visibility = View.VISIBLE
                        addDialogBinding!!.errorText.text = getString(R.string.dns_invalid_format)
                    }
                }
        }

        observeAddState()
        addDialog!!.show()
    }

    private fun observeAddState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                domainListViewModel.addState.collect { state ->
                    val db = addDialogBinding ?: return@collect
                    db.savingProgress.visibility = if (state.isSaving) View.VISIBLE else View.GONE

                    state.error?.let { err ->
                        db.errorText.visibility = View.VISIBLE
                        db.errorText.text = err.asString(requireContext())
                    }

                    val zone = state.createdZone
                    if (zone != null) {
                        showAddResult(zone, db)
                    }
                }
            }
        }
    }

    private fun showAddResult(zone: Zone, db: DialogAddDomainBinding) {
        db.formLayout.visibility = View.GONE
        db.resultLayout.visibility = View.VISIBLE
        db.resultDomainName.text = zone.name

        val servers = zone.nameServerList()
        db.nameServersContainer.removeAllViews()
        servers.forEach { server ->
            val tv = android.widget.TextView(requireContext()).apply {
                text = server
                textSize = 14f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, 8, 0, 8)
            }
            db.nameServersContainer.addView(tv)
        }

        db.copyButton.setOnClickListener {
            copyToClipboard(servers.joinToString("\n"))
            requireContext().showToast(getString(R.string.msg_nameservers_copied))
        }

        // 切换确定按钮文本
        addDialog?.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE)?.let { btn ->
            btn.text = getString(R.string.done)
            btn.setOnClickListener {
                addDialog?.dismiss()
            }
        }
    }

    private fun isValidDomain(input: String): Boolean {
        if (input.length < 3 || input.contains(' ')) return false
        val labels = input.split('.')
        if (labels.size < 2 || labels.any { it.isEmpty() }) return false
        return (labels.lastOrNull()?.length ?: 0) >= 2
    }

    private fun copyToClipboard(text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("name_servers", text))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        addDialog?.dismiss()
        addDialog = null
        addDialogBinding = null
        _binding = null
    }

    private class DomainAdapter(
        private val onItemClick: (Zone) -> Unit
    ) : RecyclerView.Adapter<DomainAdapter.ViewHolder>() {

        private var zones = listOf<Zone>()

        fun submitList(newList: List<Zone>) {
            zones = newList
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDomainBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(zones[position])
        }

        override fun getItemCount() = zones.size

        inner class ViewHolder(
            private val binding: ItemDomainBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(zone: Zone) {
                binding.domainNameText.text = zone.name
                binding.statusText.text = statusLabel(zone.status)
                binding.statusDot.backgroundTintList =
                    android.content.res.ColorStateList.valueOf(statusColor(zone.status))

                if (!zone.plan.isNullOrBlank()) {
                    binding.planChip.visibility = View.VISIBLE
                    binding.planChip.text = zone.plan.substringBefore(" ")
                } else {
                    binding.planChip.visibility = View.GONE
                }

                binding.root.setOnClickListener { onItemClick(zone) }
            }

            private fun statusLabel(status: String): String = when (status) {
                "active" -> binding.root.context.getString(R.string.status_activated)
                "pending", "initializing" -> binding.root.context.getString(R.string.domain_pending)
                "paused" -> binding.root.context.getString(R.string.status_paused_short)
                else -> status
            }

            private fun statusColor(status: String): Int = when (status) {
                "active" -> ContextCompat.getColor(binding.root.context, R.color.status_success)
                "pending", "initializing" -> ContextCompat.getColor(binding.root.context, R.color.status_warning)
                "paused", "deactivated" -> ContextCompat.getColor(binding.root.context, R.color.status_error)
                else -> ContextCompat.getColor(binding.root.context, R.color.status_neutral)
            }
        }
    }
}
