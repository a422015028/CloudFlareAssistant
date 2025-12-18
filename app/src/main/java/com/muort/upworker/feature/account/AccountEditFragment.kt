package com.muort.upworker.feature.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.FragmentAccountEditBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccountEditFragment : Fragment() {
    
    private var _binding: FragmentAccountEditBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: AccountViewModel by activityViewModels()
    private val args: AccountEditFragmentArgs by navArgs()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountEditBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        loadAccountIfEditing()
        setupSaveButton()
        setupZoneButtons()
        observeViewModel()
    }
    
    private fun loadAccountIfEditing() {
        if (args.accountId != -1L) {
            // Editing existing account
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.accounts.collect { accounts ->
                    val account = accounts.find { it.id == args.accountId }
                    account?.let {
                        binding.nameEditText.setText(it.name)
                        binding.accountIdEditText.setText(it.accountId)
                        binding.tokenEditText.setText(it.token)
                        binding.r2AccessKeyIdEditText.setText(it.r2AccessKeyId ?: "")
                        binding.r2SecretAccessKeyEditText.setText(it.r2SecretAccessKey ?: "")
                        binding.defaultSwitch.isChecked = it.isDefault
                        
                        // Load zones for this account
                        viewModel.loadZonesForAccount(it.id)
                    }
                }
            }
        }
    }
    
    private fun setupZoneButtons() {
        binding.fetchZonesBtn.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val accountId = binding.accountIdEditText.text.toString().trim()
            val token = binding.tokenEditText.text.toString().trim()
            
            if (accountId.isEmpty() || token.isEmpty()) {
                showToast("请先填写 Account ID 和 API Token")
                return@setOnClickListener
            }
            
            // Create temporary account for API call
            val tempAccount = com.muort.upworker.core.model.Account(
                id = if (args.accountId == -1L) 0 else args.accountId,
                name = name.ifEmpty { "临时" },
                accountId = accountId,
                token = token
            )
            
            viewModel.fetchZonesFromApi(tempAccount)
        }
        
        binding.manageZonesBtn.setOnClickListener {
            if (args.accountId == -1L) {
                showToast("请先保存账号")
                return@setOnClickListener
            }
            
            showZoneManagementDialog()
        }
    }
    
    private fun setupSaveButton() {
        binding.saveBtn.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val accountId = binding.accountIdEditText.text.toString().trim()
            val token = binding.tokenEditText.text.toString().trim()
            val r2AccessKeyId = binding.r2AccessKeyIdEditText.text.toString().trim()
            val r2SecretAccessKey = binding.r2SecretAccessKeyEditText.text.toString().trim()
            val isDefault = binding.defaultSwitch.isChecked
            
            // Get selected zone ID from ViewModel (backward compatibility with old zoneId field)
            val selectedZoneId = if (args.accountId != -1L) {
                viewModel.getSelectedZoneId(args.accountId)
            } else null
            
            if (name.isEmpty()) {
                showToast("请输入账号名称")
                return@setOnClickListener
            }
            
            if (accountId.isEmpty()) {
                showToast("请输入 Account ID")
                return@setOnClickListener
            }
            
            if (token.isEmpty()) {
                showToast("请输入 API Token")
                return@setOnClickListener
            }
            
            if (args.accountId == -1L) {
                // Add new account
                viewModel.addAccount(
                    name, 
                    accountId, 
                    token, 
                    selectedZoneId,
                    isDefault,
                    r2AccessKeyId.ifEmpty { null },
                    r2SecretAccessKey.ifEmpty { null }
                )
            } else {
                // Update existing account
                viewLifecycleOwner.lifecycleScope.launch {
                    val accounts = viewModel.accounts.value
                    val existingAccount = accounts.find { it.id == args.accountId }
                    existingAccount?.let { account ->
                        viewModel.updateAccount(
                            account.copy(
                                name = name,
                                accountId = accountId,
                                token = token,
                                zoneId = selectedZoneId,
                                isDefault = isDefault,
                                r2AccessKeyId = r2AccessKeyId.ifEmpty { null },
                                r2SecretAccessKey = r2SecretAccessKey.ifEmpty { null }
                            )
                        )
                    }
                }
            }
            
            findNavController().navigateUp()
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.message.collect { message ->
                    showToast(message)
                }
            }
        }
    }
    
    private fun showZoneManagementDialog() {
        val zones = viewModel.zones.value
        val selectedZone = viewModel.selectedZone.value
        
        if (zones.isEmpty()) {
            showToast("暂无域名，请先从API获取")
            return
        }
        
        val items = zones.map { zone ->
            val status = if (zone.status == "active") "✓" else "○"
            val selected = if (zone.id == selectedZone?.id) " [已选择]" else ""
            "$status ${zone.name}$selected"
        }.toTypedArray()
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("选择域名")
            .setSingleChoiceItems(items, zones.indexOfFirst { it.id == selectedZone?.id }) { dialog, which ->
                val zone = zones[which]
                viewModel.selectZone(args.accountId, zone.id)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
