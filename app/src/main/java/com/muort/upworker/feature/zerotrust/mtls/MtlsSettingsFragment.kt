package com.muort.upworker.feature.zerotrust.mtls

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.MtlsCertificateSetting
import com.muort.upworker.databinding.FragmentMtlsSettingsBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MtlsSettingsFragment : Fragment() {

    private var _binding: FragmentMtlsSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MtlsViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    private lateinit var settingsAdapter: MtlsSettingsAdapter

    /** Local working copy; the PUT endpoint replaces the whole list. */
    private val currentSettings = mutableListOf<MtlsCertificateSetting>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMtlsSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        settingsAdapter = MtlsSettingsAdapter(
            onEditClick = { setting -> showEditDialog(setting) },
            onDeleteClick = { setting -> confirmDelete(setting) }
        )
        binding.settingsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = settingsAdapter
        }
        binding.createSettingButton.setOnClickListener { showCreateDialog() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.settings.collect { settings ->
                    currentSettings.clear()
                    currentSettings.addAll(settings)
                    settingsAdapter.submitList(settings.toList())
                    binding.settingsEmptyView.visibility =
                        if (settings.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    // ==================== Add / Edit ====================

    private fun showCreateDialog() {
        val account = accountViewModel.defaultAccount.value ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_mtls_setting_edit, null)
        val hostnameInput = dialogView.findViewById<TextInputEditText>(R.id.settingHostnameInput)
        val forwardingSwitch = dialogView.findViewById<SwitchMaterial>(R.id.settingForwardingSwitch)
        val chinaSwitch = dialogView.findViewById<SwitchMaterial>(R.id.settingChinaSwitch)
        forwardingSwitch.isChecked = true

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_mtls_setting_create_title)
            .setView(dialogView)
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val hostname = hostnameInput.text?.toString()?.trim().orEmpty()
                if (!validateHostname(hostname)) return@setPositiveButton

                val newList = currentSettings.toMutableList().apply {
                    add(
                        MtlsCertificateSetting(
                            hostname = hostname,
                            clientCertificateForwarding = forwardingSwitch.isChecked,
                            chinaNetwork = chinaSwitch.isChecked
                        )
                    )
                }
                viewModel.saveSettings(account, newList)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showEditDialog(setting: MtlsCertificateSetting) {
        val account = accountViewModel.defaultAccount.value ?: return

        val dialogView = layoutInflater.inflate(R.layout.dialog_mtls_setting_edit, null)
        val hostnameInput = dialogView.findViewById<TextInputEditText>(R.id.settingHostnameInput)
        val forwardingSwitch = dialogView.findViewById<SwitchMaterial>(R.id.settingForwardingSwitch)
        val chinaSwitch = dialogView.findViewById<SwitchMaterial>(R.id.settingChinaSwitch)

        hostnameInput.setText(setting.hostname)
        forwardingSwitch.isChecked = setting.clientCertificateForwarding == true
        chinaSwitch.isChecked = setting.chinaNetwork == true

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_mtls_setting_edit_title)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { _, _ ->
                val hostname = hostnameInput.text?.toString()?.trim().orEmpty()
                if (!validateHostname(hostname, setting.hostname)) return@setPositiveButton

                val newList = currentSettings.map {
                    if (it.hostname == setting.hostname) {
                        MtlsCertificateSetting(
                            hostname = hostname,
                            clientCertificateForwarding = forwardingSwitch.isChecked,
                            chinaNetwork = chinaSwitch.isChecked
                        )
                    } else {
                        it
                    }
                }
                viewModel.saveSettings(account, newList)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(setting: MtlsCertificateSetting) {
        val account = accountViewModel.defaultAccount.value ?: return

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_mtls_setting_delete_title)
            .setMessage(getString(R.string.zt_mtls_setting_delete_confirm, setting.hostname))
            .setPositiveButton(R.string.delete) { _, _ ->
                val newList = currentSettings.filterNot { it.hostname == setting.hostname }
                viewModel.saveSettings(account, newList)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * @param originalHostname when editing, the hostname being edited (excluded from duplicate check)
     */
    private fun validateHostname(hostname: String, originalHostname: String? = null): Boolean {
        if (hostname.isEmpty()) {
            Toast.makeText(requireContext(), R.string.zt_mtls_setting_hostname_empty, Toast.LENGTH_SHORT).show()
            return false
        }
        if (currentSettings.any { it.hostname == hostname && it.hostname != originalHostname }) {
            Toast.makeText(requireContext(), R.string.zt_mtls_setting_hostname_duplicate, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
