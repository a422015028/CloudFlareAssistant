package com.muort.upworker.feature.zerotrust.devices

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.Toast
import com.muort.upworker.R
import com.muort.upworker.core.model.Device
import com.muort.upworker.databinding.FragmentDevicesListBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class DevicesListFragment : Fragment() {

    private var _binding: FragmentDevicesListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DevicesViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    
    private lateinit var deviceAdapter: DeviceAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDevicesListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter(
            onRevokeClick = { device ->
                confirmRevokeDevice(device.id, device.name ?: device.model ?: getString(R.string.zt_device_status_unknown))
            },
            onItemClick = { device ->
                showDeviceDetailDialog(device)
            }
        )
        binding.devicesRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = deviceAdapter
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.devices.collect { devices ->
                        deviceAdapter.submitList(devices)
                        binding.emptyText.visibility = 
                            if (devices.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.message.collect { message ->
                        android.widget.Toast.makeText(
                            requireContext(),
                            message.asString(requireContext()),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                launch {
                    viewModel.error.collect { error ->
                        android.widget.Toast.makeText(
                            requireContext(),
                            error.asString(requireContext()),
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    private fun loadDevices() {
        accountViewModel.defaultAccount.value?.let { account ->
            viewModel.loadDevices(account)
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadDevices()
    }

    private fun showDeviceDetailDialog(device: Device) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_device_detail, null)

        // Device Name
        dialogView.findViewById<TextView>(R.id.deviceNameText).text =
            device.name ?: device.model ?: getString(R.string.zt_device_unknown_device)

        // Device Type Chip
        val deviceType = device.type ?: device.deviceType
        val typeChip = dialogView.findViewById<Chip>(R.id.deviceTypeChip)
        typeChip.text = getDeviceTypeLabel(deviceType)

        // Status Chip
        val isRevoked = device.revokedAt != null
        val statusChip = dialogView.findViewById<Chip>(R.id.statusChip)
        statusChip.text = if (isRevoked) getString(R.string.zt_device_status_revoked) else getString(R.string.zt_device_status_active)
        statusChip.setChipBackgroundColorResource(
            if (isRevoked) android.R.color.holo_red_light else android.R.color.holo_green_light
        )

        // Active Registrations Chip
        val activeRegChip = dialogView.findViewById<Chip>(R.id.activeRegChip)
        activeRegChip.text = getString(R.string.zt_device_active_registrations, device.activeRegistrations ?: 0)

        // User Info - last_seen_user preferred
        val user = device.lastSeenUser ?: device.user
        dialogView.findViewById<TextView>(R.id.userEmailText).text =
            user?.email ?: user?.name ?: getString(R.string.zt_device_unknown_user)
        dialogView.findViewById<TextView>(R.id.userIdText).text =
            getString(R.string.zt_device_id_label, user?.id ?: "N/A")

        // Device Info
        dialogView.findViewById<TextView>(R.id.modelText).text = device.model ?: "N/A"
        dialogView.findViewById<TextView>(R.id.manufacturerText).text = device.manufacturer ?: "N/A"
        dialogView.findViewById<TextView>(R.id.osVersionText).text = device.osVersion ?: "N/A"
        dialogView.findViewById<TextView>(R.id.osVersionExtraText).text = device.osVersionExtra ?: "N/A"
        dialogView.findViewById<TextView>(R.id.serialText).text = device.serialNumber ?: "N/A"
        dialogView.findViewById<TextView>(R.id.macAddressText).text = device.macAddress ?: "N/A"
        dialogView.findViewById<TextView>(R.id.hardwareIdText).text = device.hardwareId ?: "N/A"
        dialogView.findViewById<TextView>(R.id.clientVersionText).text =
            device.clientVersion ?: device.version ?: "N/A"
        dialogView.findViewById<TextView>(R.id.deviceIdText).text = device.id

        // Network Info
        val ipAddr = device.publicIp ?: device.ip
        dialogView.findViewById<TextView>(R.id.ipAddressText).text =
            getString(R.string.zt_device_ip_label, ipAddr ?: getString(R.string.zt_device_status_unknown))

        // Policy Info - last_seen_registration.policy
        val policy = device.lastSeenRegistration?.policy
        dialogView.findViewById<TextView>(R.id.policyNameText).text =
            policy?.name ?: device.policyName ?: getString(R.string.zt_access_detail_default)
        dialogView.findViewById<TextView>(R.id.policyDefaultText).text =
            if (policy?.default == true) getString(R.string.status_yes) else getString(R.string.status_no)
        dialogView.findViewById<TextView>(R.id.policyDeletedText).text =
            if (policy?.deleted == true) getString(R.string.status_yes) else getString(R.string.status_no)
        dialogView.findViewById<TextView>(R.id.policyUpdatedAtText).text =
            formatDateTime(policy?.updatedAt)

        // Time Info
        val createdTime = device.createdAt ?: device.created
        val updatedTime = device.updatedAt ?: device.updated
        dialogView.findViewById<TextView>(R.id.createdAtText).text =
            getString(R.string.zt_device_created_label, formatDateTime(createdTime))
        dialogView.findViewById<TextView>(R.id.updatedAtText).text =
            getString(R.string.zt_device_updated_label, formatDateTime(updatedTime))
        dialogView.findViewById<TextView>(R.id.lastSeenAtText).text =
            getString(R.string.zt_device_last_seen_label, formatDateTime(device.lastSeenAt))

        // Revoked Info
        val revokedText = dialogView.findViewById<TextView>(R.id.revokedAtText)
        if (isRevoked) {
            revokedText.visibility = View.VISIBLE
            revokedText.text = getString(R.string.zt_device_revoked_label, formatDateTime(device.revokedAt))
        } else {
            revokedText.visibility = View.GONE
        }

        // Deleted Info
        val deletedText = dialogView.findViewById<TextView>(R.id.deletedAtText)
        if (device.deletedAt != null) {
            deletedText.visibility = View.VISIBLE
            deletedText.text = getString(R.string.zt_device_deleted_label, formatDateTime(device.deletedAt))
        } else {
            deletedText.visibility = View.GONE
        }

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_device_detail_title)
            .setView(dialogView)
            .setNegativeButton(R.string.dialog_close, null)

        if (!isRevoked) {
            builder.setPositiveButton(R.string.zt_device_revoke_button) { _, _ ->
                confirmRevokeDevice(device.id, device.name ?: device.model ?: getString(R.string.zt_device_status_unknown))
            }
        }

        builder.show()
    }

    private fun confirmRevokeDevice(deviceId: String, deviceName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_device_revoke_title)
            .setMessage(getString(R.string.zt_device_revoke_confirm, deviceName))
            .setPositiveButton(getString(R.string.zt_device_revoke_button)) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    viewModel.revokeDevice(account, deviceId)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun getDeviceTypeLabel(type: String?): String {
        return when (type?.lowercase()) {
            "windows" -> "Windows"
            "mac", "macos" -> "macOS"
            "linux" -> "Linux"
            "android" -> "Android"
            "ios" -> "iOS"
            "chromeos" -> "ChromeOS"
            else -> type?.uppercase() ?: getString(R.string.zt_device_status_unknown)
        }
    }

    private fun formatDateTime(dateString: String?): String {
        if (dateString == null) return "N/A"
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = inputFormat.parse(dateString.substringBefore(".").substringBefore("Z"))
            outputFormat.format(date!!)
        } catch (e: Exception) {
            dateString.substringBefore("T")
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
