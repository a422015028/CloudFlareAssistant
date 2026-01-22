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
        loadLocations()
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
                    viewModel.loadingState.collect { isLoading ->
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

    private fun showCreateLocationDialog(existingLocation: GatewayLocation? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_location, null)
        
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.locationNameInput)
        val networksInput = dialogView.findViewById<TextInputEditText>(R.id.networksInput)

        // Populate existing location
        existingLocation?.let { location ->
            nameInput.setText(location.name)
            networksInput.setText(location.networks?.joinToString("\n") ?: "")
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

                if (networksText.isNullOrBlank()) {
                    Snackbar.make(binding.root, "网络不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val networks = networksText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                
                val request = GatewayLocationRequest(
                    name = name,
                    networks = networks.map { LocationNetwork(network = it) },
                    clientDefault = false
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
