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
import android.widget.Toast 
import com.google.android.material.textfield.TextInputEditText
import com.muort.upworker.R
import com.muort.upworker.core.model.DOHEndpoint
import com.muort.upworker.core.model.DOTEndpoint
import com.muort.upworker.core.model.GatewayLocation
import com.muort.upworker.core.model.GatewayLocationRequest
import com.muort.upworker.core.model.IPV4Endpoint
import com.muort.upworker.core.model.IPV6Endpoint
import com.muort.upworker.core.model.LocationEndpoints
import com.muort.upworker.core.model.LocationNetwork
import com.muort.upworker.core.model.Resource
import com.muort.upworker.databinding.FragmentGatewayLocationsBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class GatewayLocationsFragment : Fragment() {

    private var _binding: FragmentGatewayLocationsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GatewayViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    @Inject lateinit var okHttpClient: OkHttpClient
    
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
            }
        }
    }

    private fun loadLocations() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            android.widget.Toast.makeText(requireContext(), getString(R.string.msg_no_account_selected), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = viewModel.loadLocations(account)
            if (result is Resource.Error) {
                android.widget.Toast.makeText(requireContext(), getString(R.string.msg_load_locations_failed, result.message), android.widget.Toast.LENGTH_LONG).show()
            }
        }
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
        val ipv4Switch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.ipv4Switch)
        val ipv6Switch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.ipv6Switch)
        val dotSwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.dotSwitch)
        val dohSwitch = dialogView.findViewById<androidx.appcompat.widget.SwitchCompat>(R.id.dohSwitch)

        // Populate existing location
        existingLocation?.let { location ->
            nameInput.setText(location.name)
            networksInput.setText(location.networks?.joinToString("\n") { it.network } ?: "")
            defaultSwitch.isChecked = location.clientDefault ?: false
            ecsSwitch.isChecked = location.ecsSupport ?: false
            ipv4Switch.isChecked = location.endpoints?.ipv4?.enabled ?: true
            ipv6Switch.isChecked = location.endpoints?.ipv6?.enabled ?: true
            dotSwitch.isChecked = location.endpoints?.dot?.enabled ?: true
            dohSwitch.isChecked = location.endpoints?.doh?.enabled ?: true
        }

        var prefilledIp: String? = null

        fun prefillCurrentNetwork() {
            if (networksInput.text?.toString()?.isNotBlank() == true) return
            prefilledIp?.let { ip ->
                networksInput.setText("$ip/32")
                return
            }
            lifecycleScope.launch {
                val ip = fetchCurrentIpv4()
                if (ipv4Switch.isChecked && networksInput.text?.toString()?.isBlank() == true) {
                    if (ip != null) {
                        prefilledIp = ip
                        networksInput.setText("$ip/32")
                        android.widget.Toast.makeText(requireContext(), getString(R.string.msg_location_filled_current_ip, "$ip/32"), android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        android.widget.Toast.makeText(requireContext(), getString(R.string.msg_location_fetch_ip_failed), android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        ipv4Switch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) prefillCurrentNetwork()
        }
        if (ipv4Switch.isChecked) prefillCurrentNetwork()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingLocation == null) R.string.zt_location_create_title else R.string.zt_location_edit_title)
            .setView(dialogView)
            .setPositiveButton(if (existingLocation == null) R.string.dialog_create else R.string.save) { _, _ ->
                val account = accountViewModel.defaultAccount.value ?: return@setPositiveButton
                val name = nameInput.text?.toString()
                val networksText = networksInput.text?.toString()
                
                if (name.isNullOrBlank()) {
                    android.widget.Toast.makeText(requireContext(), getString(R.string.msg_location_name_empty), android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val networks = networksText?.split("\n")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
                
                for (network in networks) {
                    val validation = validateNetwork(network)
                    if (validation.isNotEmpty()) {
                        android.widget.Toast.makeText(requireContext(), getString(R.string.msg_location_network_invalid, network, validation), android.widget.Toast.LENGTH_LONG).show()
                        return@setPositiveButton
                    }
                }

                if (ipv4Switch.isChecked && networks.isEmpty()) {
                    android.widget.Toast.makeText(
                        requireContext(),
                        getString(R.string.msg_location_ipv4_needs_network),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }

                if (!ipv4Switch.isChecked && !ipv6Switch.isChecked && !dotSwitch.isChecked && !dohSwitch.isChecked) {
                    android.widget.Toast.makeText(requireContext(), getString(R.string.msg_location_need_dns_endpoint), android.widget.Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                if (defaultSwitch.isChecked && !dohSwitch.isChecked) {
                    android.widget.Toast.makeText(requireContext(), getString(R.string.msg_location_default_needs_doh), android.widget.Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                val existingEndpoints = existingLocation?.endpoints
                val request = GatewayLocationRequest(
                    name = name,
                    clientDefault = if (defaultSwitch.isChecked) true else null,
                    ecsSupport = if (ecsSwitch.isChecked) true else null,
                    networks = if (networks.isNotEmpty()) networks.map { LocationNetwork(network = normalizeCidr(it)) } else null,
                    endpoints = LocationEndpoints(
                        ipv4 = IPV4Endpoint(enabled = ipv4Switch.isChecked),
                        ipv6 = IPV6Endpoint(enabled = ipv6Switch.isChecked, networks = existingEndpoints?.ipv6?.networks),
                        dot = DOTEndpoint(enabled = dotSwitch.isChecked, networks = existingEndpoints?.dot?.networks),
                        doh = DOHEndpoint(
                            enabled = dohSwitch.isChecked,
                            networks = existingEndpoints?.doh?.networks,
                            requireToken = existingEndpoints?.doh?.requireToken
                        )
                    ),
                    maxTtl = existingLocation?.maxTtl
                )

                if (existingLocation == null) {
                    lifecycleScope.launch {
                        val result = viewModel.createLocation(account, request)
                        val msg = when (result) {
                            is Resource.Success -> getString(R.string.msg_location_create_success, result.data.name)
                            is Resource.Error -> getString(R.string.msg_location_create_failed, result.message)
                            else -> return@launch
                        }
                        android.widget.Toast.makeText(requireContext(), msg, if (result is Resource.Error) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    lifecycleScope.launch {
                        val result = viewModel.updateLocation(account, existingLocation.id, request)
                        val msg = when (result) {
                            is Resource.Success -> getString(R.string.msg_location_update_success, result.data.name)
                            is Resource.Error -> getString(R.string.msg_location_update_failed, result.message)
                            else -> return@launch
                        }
                        android.widget.Toast.makeText(requireContext(), msg, if (result is Resource.Error) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private suspend fun fetchCurrentIpv4(): String? = withContext(Dispatchers.IO) {
        val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        // 设备网络优先 IPv6 时，trace 会返回 IPv6 出口；强制 IPv4 连接以获取 IPv4 出口
        val ipv4Dns = object : Dns {
            override fun lookup(hostname: String): List<java.net.InetAddress> {
                val v4 = Dns.SYSTEM.lookup(hostname).filterIsInstance<Inet4Address>()
                if (v4.isEmpty()) throw UnknownHostException("No IPv4 address for $hostname")
                return v4
            }
        }
        val probeClient = okHttpClient.newBuilder()
            .dns(ipv4Dns)
            .connectTimeout(4, TimeUnit.SECONDS)
            .callTimeout(8, TimeUnit.SECONDS)
            .build()
        val sources = listOf(
            "https://api.cloudflare.com/cdn-cgi/trace",
            "https://www.cloudflare.com/cdn-cgi/trace",
            "https://1.1.1.1/cdn-cgi/trace"
        )
        for (url in sources) {
            val body = runCatching {
                probeClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (response.isSuccessful) response.body?.string() else null
                }
            }.getOrNull() ?: continue
            val ip = body.lineSequence()
                .firstOrNull { it.startsWith("ip=") }
                ?.removePrefix("ip=")?.trim()
                ?.takeIf { ipv4Regex.matches(it) }
            if (ip != null) return@withContext ip
        }
        null
    }

    private fun confirmDeleteLocation(locationId: String, locationName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_location_delete_title)
            .setMessage(getString(R.string.zt_location_delete_confirm, locationName))
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteLocation(locationId)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteLocation(locationId: String) {
        val account = accountViewModel.defaultAccount.value ?: return
        lifecycleScope.launch {
            val result = viewModel.deleteLocation(account, locationId)
            val msg = when (result) {
                is Resource.Success -> getString(R.string.msg_location_delete_success)
                is Resource.Error -> getString(R.string.msg_location_delete_failed, result.message)
                else -> return@launch
            }
            android.widget.Toast.makeText(requireContext(), msg, if (result is Resource.Error) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateNetwork(cidr: String): String {
        val parts = cidr.split("/")
        if (parts.size != 2) {
            return getString(R.string.zt_validate_cidr_format_error)
        }
        
        val ip = parts[0]
        val prefix = parts[1].toIntOrNull()
        
        if (prefix == null || prefix < 8 || prefix > 32) {
            return getString(R.string.zt_validate_cidr_prefix_range)
        }
        
        if (prefix < 24) {
            return getString(R.string.zt_validate_cidr_network_too_large)
        }
        
        val ipParts = ip.split(".").mapNotNull { it.toIntOrNull() }
        if (ipParts.size != 4 || ipParts.any { it < 0 || it > 255 }) {
            return getString(R.string.zt_validate_ip_format_error)
        }
        
        val firstOctet = ipParts[0]
        val secondOctet = ipParts[1]
        
        if (firstOctet == 10 ||
            (firstOctet == 172 && secondOctet in 16..31) ||
            (firstOctet == 192 && secondOctet == 168)) {
            return getString(R.string.zt_validate_ip_private_not_supported)
        }
        
        if (firstOctet == 0 || firstOctet == 127 || firstOctet == 169) {
            return getString(R.string.zt_validate_ip_reserved_not_supported)
        }
        
        if (firstOctet >= 224) {
            return getString(R.string.zt_validate_ip_multicast_not_supported)
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
