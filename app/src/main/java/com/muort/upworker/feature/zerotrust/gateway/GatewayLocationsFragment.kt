package com.muort.upworker.feature.zerotrust.gateway

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
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.GatewayLocation
import com.muort.upworker.core.model.GatewayLocationRequest
import com.muort.upworker.core.model.LocationNetwork
import com.muort.upworker.databinding.FragmentGatewayLocationsBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GatewayLocationsFragment : Fragment() {

    private var _binding: FragmentGatewayLocationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GatewayViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    
    private lateinit var locationAdapter: GatewayLocationAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGatewayLocationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupRecyclerView() {
        locationAdapter = GatewayLocationAdapter(
            onEditClick = { location ->
                showCreateLocationDialog(location)
            },
            onDeleteClick = { location ->
                confirmDeleteLocation(location.id, location.name)
            }
        )

        binding.locationsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = locationAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddLocation.setOnClickListener {
            showCreateLocationDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.locations.collect { locations ->
                        locationAdapter.submitList(locations)
                        binding.emptyText.visibility = 
                            if (locations.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.loadingState.collect { _ ->
                        // Loading state handled by ViewModel
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

    private fun loadLocations() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            Snackbar.make(binding.root, "未选择账户", Snackbar.LENGTH_SHORT).show()
            return
        }

        viewModel.loadLocations(account)
    }
    
    override fun onResume() {
        super.onResume()
        loadLocations()
    }

    private fun showCreateLocationDialog(existingLocation: GatewayLocation? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_location, null)
        
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.locationNameInput)
        val networksInput = dialogView.findViewById<TextInputEditText>(R.id.networksInput)
        val defaultSwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.defaultSwitch)
        val ecsSwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.ecsSwitch)

        // Populate existing location
        existingLocation?.let { location ->
            nameInput.setText(location.name)
            networksInput.setText(location.networks?.joinToString("\n") { it.network } ?: "")
            defaultSwitch.isChecked = location.clientDefault ?: false
            ecsSwitch.isChecked = location.ecsSupport ?: false
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingLocation == null) "创建位置" else "编辑位置")
            .setView(dialogView)
            .setPositiveButton(if (existingLocation == null) "创建" else "保存") { _, _ ->
                val account = accountViewModel.defaultAccount.value ?: return@setPositiveButton
                val name = nameInput.text?.toString()
                val networksText = networksInput.text?.toString()
                
                if (name.isNullOrBlank()) {
                    Snackbar.make(binding.root, "位置名称不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val networks = networksText?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                
                for (network in networks) {
                    val validation = validateNetwork(network)
                    if (validation.isNotEmpty()) {
                        Snackbar.make(binding.root, "网络 $network $validation", Snackbar.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                }
                
                val request = GatewayLocationRequest(
                    name = name,
                    clientDefault = if (defaultSwitch.isChecked) true else null,
                    ecsSupport = if (ecsSwitch.isChecked) true else null,
                    networks = if (networks.isNotEmpty()) networks.map { LocationNetwork(network = normalizeCidr(it)) } else null
                )

                if (existingLocation == null) {
                    viewModel.createLocation(account, request)
                } else {
                    viewModel.updateLocation(account, existingLocation.id, request)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteLocation(locationId: String, locationName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除位置")
            .setMessage("确定要删除位置 \"$locationName\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteLocation(locationId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteLocation(locationId: String) {
        val account = accountViewModel.defaultAccount.value ?: return
        viewModel.deleteLocation(account, locationId)
    }

    private fun validateNetwork(cidr: String): String {
        val parts = cidr.split("/")
        if (parts.size != 2) {
            return "格式错误，需要 CIDR 格式（如 123.45.67.89/32）"
        }
        
        val ip = parts[0]
        val prefix = parts[1].toIntOrNull()
        
        if (prefix == null || prefix < 8 || prefix > 32) {
            return "前缀必须在 8-32 之间"
        }
        
        if (prefix < 24) {
            return "网络太大，最大支持 /24（如 /24, /28, /32）"
        }
        
        val ipParts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (ipParts.size != 4 || ipParts.any { it < 0 || it > 255 }) {
            return "IP 地址格式错误"
        }
        
        val firstOctet = ipParts[0]
        val secondOctet = ipParts[1]
        
        if (firstOctet == 10 ||
            (firstOctet == 172 && secondOctet in 16..31) ||
            (firstOctet == 192 && secondOctet == 168)) {
            return "请填写公网 IP，私有地址（10.x.x.x、172.16-31.x.x、192.168.x.x）不支持"
        }
        
        if (firstOctet == 0 || firstOctet == 127 || firstOctet == 169) {
            return "保留地址不支持，请使用公网 IP 地址"
        }
        
        if (firstOctet >= 224) {
            return "组播/特殊地址不支持，请使用公网 IP 地址"
        }
        
        return ""
    }

    private fun normalizeCidr(cidr: String): String {
        val parts = cidr.split("/")
        if (parts.size != 2) return cidr
        
        val ip = parts[0]
        val prefix = parts[1].toIntOrNull() ?: return cidr
        
        val ipParts = ip.split(".").map { it.toInt() }.toMutableList()
        
        val fullBytes = prefix / 8
        val remainingBits = prefix % 8
        
        for (i in fullBytes until 4) {
            ipParts[i] = 0
        }
        
        if (fullBytes < 4 && remainingBits < 8) {
            ipParts[fullBytes] = ipParts[fullBytes] and (0xFF shl (8 - remainingBits))
        }
        
        return "${ipParts.joinToString(".")}/$prefix"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
