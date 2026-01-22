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
import com.google.android.material.snackbar.Snackbar
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
        loadDevices()
    }

    private fun setupRecyclerView() {
        deviceAdapter = DeviceAdapter(
            onRevokeClick = { device ->
                confirmRevokeDevice(device.id, device.name ?: device.model ?: "设备")
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

    private fun loadDevices() {
        accountViewModel.defaultAccount.value?.let { account ->
            viewModel.loadDevices(account)
        }
    }

    private fun showDeviceDetailDialog(device: Device) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_device_detail, null)
        
        // Device Name
        dialogView.findViewById<TextView>(R.id.deviceNameText).text = 
            device.name ?: device.model ?: "未知设备"
        
        // Device Type Chip
        val deviceType = device.type ?: device.deviceType
        val typeChip = dialogView.findViewById<Chip>(R.id.deviceTypeChip)
        typeChip.text = getDeviceTypeLabel(deviceType)
        
        // Status Chip
        val isRevoked = device.revokedAt != null
        val statusChip = dialogView.findViewById<Chip>(R.id.statusChip)
        statusChip.text = if (isRevoked) "已撤销" else "活跃"
        statusChip.setChipBackgroundColorResource(
            if (isRevoked) android.R.color.holo_red_light else android.R.color.holo_green_light
        )
        
        // User Info
        dialogView.findViewById<TextView>(R.id.userEmailText).text = 
            device.user?.email ?: device.user?.name ?: "未知用户"
        dialogView.findViewById<TextView>(R.id.userIdText).text = 
            "ID: ${device.user?.id ?: "N/A"}"
        
        // Device Info
        dialogView.findViewById<TextView>(R.id.modelText).text = device.model ?: "N/A"
        dialogView.findViewById<TextView>(R.id.manufacturerText).text = device.manufacturer ?: "N/A"
        dialogView.findViewById<TextView>(R.id.osVersionText).text = device.osVersion ?: "N/A"
        dialogView.findViewById<TextView>(R.id.serialText).text = device.serialNumber ?: "N/A"
        dialogView.findViewById<TextView>(R.id.macAddressText).text = device.macAddress ?: "N/A"
        
        // Network Info
        dialogView.findViewById<TextView>(R.id.ipAddressText).text = 
            "IP: ${device.ip ?: "未知"}"
        
        // Time Info
        dialogView.findViewById<TextView>(R.id.createdAtText).text = 
            "创建时间: ${formatDateTime(device.created)}"
        dialogView.findViewById<TextView>(R.id.updatedAtText).text = 
            "更新时间: ${formatDateTime(device.updated)}"
        
        // Revoked Info
        val revokedText = dialogView.findViewById<TextView>(R.id.revokedAtText)
        if (isRevoked) {
            revokedText.visibility = View.VISIBLE
            revokedText.text = "撤销时间: ${formatDateTime(device.revokedAt)}"
        } else {
            revokedText.visibility = View.GONE
        }
        
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle("设备详情")
            .setView(dialogView)
            .setNegativeButton("关闭", null)
        
        if (!isRevoked) {
            builder.setPositiveButton("撤销设备") { _, _ ->
                confirmRevokeDevice(device.id, device.name ?: device.model ?: "设备")
            }
        }
        
        builder.show()
    }

    private fun confirmRevokeDevice(deviceId: String, deviceName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("撤销设备")
            .setMessage("确定要撤销设备 \"$deviceName\" 吗？撤销后该设备将无法通过 WARP 连接。")
            .setPositiveButton("撤销") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    viewModel.revokeDevice(account, deviceId)
                }
            }
            .setNegativeButton("取消", null)
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
            else -> type?.uppercase() ?: "未知"
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
