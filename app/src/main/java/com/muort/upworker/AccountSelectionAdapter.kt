package com.muort.upworker

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.muort.upworker.core.model.Account
import com.muort.upworker.databinding.ItemAccountSelectionBinding

class AccountSelectionAdapter(
    private val accounts: List<Account>,
    private val currentAccountId: Long?,
    private val onAccountSelected: (Account) -> Unit
) : RecyclerView.Adapter<AccountSelectionAdapter.AccountViewHolder>() {

    inner class AccountViewHolder(
        private val binding: ItemAccountSelectionBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(account: Account, isSelected: Boolean) {
            binding.accountName.text = account.name
            binding.accountId.text = account.accountId
            binding.accountInitial.text = account.name.firstOrNull()?.uppercase() ?: "A"
            binding.accountRadioButton.isChecked = isSelected
            
            binding.accountCard.setOnClickListener {
                onAccountSelected(account)
            }
            
            // Update card appearance based on selection
            binding.accountCard.isChecked = isSelected
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AccountViewHolder {
        val binding = ItemAccountSelectionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AccountViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AccountViewHolder, position: Int) {
        val account = accounts[position]
        holder.bind(account, account.id == currentAccountId)
    }

    override fun getItemCount() = accounts.size
}
