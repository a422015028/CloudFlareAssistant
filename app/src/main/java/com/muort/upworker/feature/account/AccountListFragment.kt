package com.muort.upworker.feature.account

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.util.showToast
import com.muort.upworker.databinding.FragmentAccountListBinding
import com.muort.upworker.databinding.ItemAccountBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AccountListFragment : Fragment() {
    
    private var _binding: FragmentAccountListBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: AccountViewModel by activityViewModels()
    private val adapter = AccountAdapter()
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAccountListBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        setupFab()
        observeViewModel()
    }
    
    private fun setupRecyclerView() {
        binding.accountsRecyclerView.adapter = adapter
        
        adapter.onItemClick = { account ->
            // Navigate to edit
            val action = AccountListFragmentDirections
                .actionAccountsToAdd(account.id)
            findNavController().navigate(action)
        }
        
        adapter.onMenuClick = { account, view ->
            showAccountMenu(account, view)
        }
    }
    
    private fun setupFab() {
        binding.addAccountFab.setOnClickListener {
            findNavController().navigate(R.id.action_accounts_to_add)
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is AccountUiState.Loading -> {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.emptyStateLayout.visibility = View.GONE
                            }
                            is AccountUiState.Empty -> {
                                binding.progressBar.visibility = View.GONE
                                binding.emptyStateLayout.visibility = View.VISIBLE
                                adapter.submitList(emptyList())
                            }
                            is AccountUiState.Success -> {
                                binding.progressBar.visibility = View.GONE
                                binding.emptyStateLayout.visibility = View.GONE
                                adapter.submitList(state.accounts)
                            }
                            is AccountUiState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                showToast(state.message)
                            }
                        }
                    }
                }
                
                launch {
                    viewModel.message.collect { message ->
                        showToast(message)
                    }
                }
            }
        }
    }
    
    private fun showAccountMenu(account: Account, view: View) {
        val popup = PopupMenu(requireContext(), view)
        popup.inflate(R.menu.menu_account)
        
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_set_default -> {
                    viewModel.setDefaultAccount(account.id)
                    true
                }
                R.id.action_edit -> {
                    val action = AccountListFragmentDirections
                        .actionAccountsToAdd(account.id)
                    findNavController().navigate(action)
                    true
                }
                R.id.action_delete -> {
                    showDeleteConfirmation(account)
                    true
                }
                else -> false
            }
        }
        
        popup.show()
    }
    
    private fun showDeleteConfirmation(account: Account) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除账号")
            .setMessage("确定要删除账号 ${account.name} 吗？")
            .setPositiveButton("删除") { _, _ ->
                viewModel.deleteAccount(account)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class AccountAdapter : RecyclerView.Adapter<AccountAdapter.AccountViewHolder>() {
    
    private var accounts = listOf<Account>()
    var onItemClick: ((Account) -> Unit)? = null
    var onMenuClick: ((Account, View) -> Unit)? = null
    
    fun submitList(newAccounts: List<Account>) {
        accounts = newAccounts
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemAccountBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AccountViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        holder.bind(accounts[position])
    }
    
    override fun getItemCount() = accounts.size
    
    inner class AccountViewHolder(
        private val binding: ItemAccountBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        fun bind(account: Account) {
            binding.accountNameText.text = account.name
            binding.accountIdText.text = "Account ID: ${account.accountId}"
            
            if (account.zoneId.isNullOrBlank()) {
                binding.zoneIdText.visibility = View.GONE
            } else {
                binding.zoneIdText.visibility = View.VISIBLE
                binding.zoneIdText.text = "Zone ID: ${account.zoneId}"
            }
            
            binding.defaultChip.visibility = if (account.isDefault) View.VISIBLE else View.GONE
            
            binding.root.setOnClickListener {
                onItemClick?.invoke(account)
            }
            
            binding.moreBtn.setOnClickListener {
                onMenuClick?.invoke(account, it)
            }
        }
    }
}
