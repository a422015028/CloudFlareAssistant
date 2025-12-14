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
                        binding.zoneIdEditText.setText(it.zoneId ?: "")
                        binding.r2AccessKeyIdEditText.setText(it.r2AccessKeyId ?: "")
                        binding.r2SecretAccessKeyEditText.setText(it.r2SecretAccessKey ?: "")
                        binding.defaultSwitch.isChecked = it.isDefault
                    }
                }
            }
        }
    }
    
    private fun setupSaveButton() {
        binding.saveBtn.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val accountId = binding.accountIdEditText.text.toString().trim()
            val token = binding.tokenEditText.text.toString().trim()
            val zoneId = binding.zoneIdEditText.text.toString().trim()
            val r2AccessKeyId = binding.r2AccessKeyIdEditText.text.toString().trim()
            val r2SecretAccessKey = binding.r2SecretAccessKeyEditText.text.toString().trim()
            val isDefault = binding.defaultSwitch.isChecked
            
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
                    zoneId.ifEmpty { null }, 
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
                                zoneId = zoneId.ifEmpty { null },
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
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
