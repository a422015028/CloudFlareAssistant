package com.muort.upworker.feature.dns

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.muort.upworker.R
import com.muort.upworker.core.model.DnsRecord
import com.muort.upworker.databinding.DialogDnsRecordInputBinding
import com.muort.upworker.databinding.FragmentDnsBinding
import com.muort.upworker.databinding.ItemDnsRecordBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DnsFragment : Fragment() {
    
    private var _binding: FragmentDnsBinding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val dnsViewModel: DnsViewModel by viewModels()
    
    private lateinit var dnsAdapter: DnsAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDnsBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupAdapter()
        setupClickListeners()
        observeViewModel()

        // 优先从导航参数获取 zoneId，回退到账号默认 zoneId
        val zoneId = arguments?.getString("zoneId") ?: accountViewModel.defaultAccount.value?.zoneId
        if (!zoneId.isNullOrBlank()) {
            dnsViewModel.setZoneId(zoneId)
            timber.log.Timber.d("DNS Fragment: Loading records for zoneId: $zoneId")
            accountViewModel.defaultAccount.value?.let { dnsViewModel.loadDnsRecords(it) }
        } else {
            timber.log.Timber.w("DNS Fragment: No zoneId available")
            Snackbar.make(binding.root, "请先选择或添加域名", Snackbar.LENGTH_LONG).show()
        }
    }
    
    private fun setupAdapter() {
        dnsAdapter = DnsAdapter(
            onEditClick = { record ->
                showEditRecordDialog(record)
            },
            onDeleteClick = { record ->
                showDeleteRecordDialog(record)
            }
        )
        binding.dnsRecyclerView.adapter = dnsAdapter
    }
    
    private fun setupClickListeners() {
        binding.fabAddRecord.setOnClickListener {
            showAddRecordDialog()
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dnsViewModel.dnsRecords.collect { records ->
                        dnsAdapter.submitList(records)
                        binding.emptyText.visibility = 
                            if (records.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    dnsViewModel.loadingState.collect { isLoading ->
                        binding.progressBar.visibility = 
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    dnsViewModel.message.collect { message ->
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
                    }
                }
                
                launch {
                    accountViewModel.defaultAccount.collect { account ->
                        if (account != null) {
                            dnsViewModel.loadDnsRecords(account)
                        }
                    }
                }
            }
        }
    }
    
    private fun showAddRecordDialog() {
        val dialogBinding = DialogDnsRecordInputBinding.inflate(layoutInflater)
        
        // 设置 DNS 类型下拉选择
        val types = resources.getStringArray(R.array.dns_types)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        dialogBinding.dnsType.setAdapter(adapter)
        dialogBinding.dnsType.setText(types[0], false) // 默认选择 A
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val type = dialogBinding.dnsType.text.toString()
                val name = dialogBinding.dnsName.text.toString()
                val content = dialogBinding.dnsContent.text.toString()
                val ttl = dialogBinding.dnsTtl.text.toString().toIntOrNull() ?: 1
                val proxied = dialogBinding.dnsProxied.isChecked
                
                accountViewModel.defaultAccount.value?.let { account ->
                    dnsViewModel.createDnsRecord(account, type, name, content, ttl, proxied)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showEditRecordDialog(record: DnsRecord) {
        val dialogBinding = DialogDnsRecordInputBinding.inflate(layoutInflater)
        
        // 设置 DNS 类型下拉选择
        val types = resources.getStringArray(R.array.dns_types)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, types)
        dialogBinding.dnsType.setAdapter(adapter)
        
        // 填充现有数据
        dialogBinding.dnsType.setText(record.type, false)
        dialogBinding.dnsName.setText(record.name)
        dialogBinding.dnsContent.setText(record.content)
        dialogBinding.dnsTtl.setText(record.ttl.toString())
        dialogBinding.dnsProxied.isChecked = record.proxied
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("保存") { _, _ ->
                val type = dialogBinding.dnsType.text.toString()
                val name = dialogBinding.dnsName.text.toString()
                val content = dialogBinding.dnsContent.text.toString()
                val ttl = dialogBinding.dnsTtl.text.toString().toIntOrNull() ?: 1
                val proxied = dialogBinding.dnsProxied.isChecked
                
                accountViewModel.defaultAccount.value?.let { account ->
                    dnsViewModel.updateDnsRecord(
                        account,
                        record.id,
                        type,
                        name,
                        content,
                        ttl,
                        proxied
                    )
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showDeleteRecordDialog(record: DnsRecord) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除 DNS 记录")
            .setMessage("确定要删除记录 \"${record.name}\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    dnsViewModel.deleteDnsRecord(account, record.id)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    private class DnsAdapter(
        private val onEditClick: (DnsRecord) -> Unit,
        private val onDeleteClick: (DnsRecord) -> Unit
    ) : RecyclerView.Adapter<DnsAdapter.ViewHolder>() {
        
        private var records = listOf<DnsRecord>()
        
        fun submitList(newList: List<DnsRecord>) {
            records = newList
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemDnsRecordBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(records[position])
        }
        
        override fun getItemCount() = records.size
        
        inner class ViewHolder(
            private val binding: ItemDnsRecordBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(record: DnsRecord) {
                binding.dnsNameText.text = record.name
                binding.dnsTypeText.text = record.type
                binding.dnsContentText.text = "→ ${record.content}"
                binding.dnsTtlText.text = "TTL: ${record.ttl}"
                binding.dnsProxiedText.text = if (record.proxied) "🟠 已代理" else "⚪ 仅 DNS"
                
                // 点击列表项直接显示编辑窗口
                binding.root.setOnClickListener {
                    onEditClick(record)
                }
                
                binding.dnsMenuButton.setOnClickListener { view ->
                    PopupMenu(view.context, view).apply {
                        inflate(R.menu.menu_account)
                        menu.findItem(R.id.action_set_default)?.isVisible = false
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_edit -> {
                                    onEditClick(record)
                                    true
                                }
                                R.id.action_delete -> {
                                    onDeleteClick(record)
                                    true
                                }
                                else -> false
                            }
                        }
                        show()
                    }
                }
            }
        }
    }
}
