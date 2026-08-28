package com.muort.upworker.feature.kv

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.muort.upworker.core.model.KvKey
import com.muort.upworker.core.model.KvNamespace
import com.muort.upworker.databinding.DialogKvInputBinding
import com.muort.upworker.databinding.DialogNamespaceInputBinding
import com.muort.upworker.databinding.FragmentKvBinding
import com.muort.upworker.databinding.ItemKvKeyBinding
import com.muort.upworker.databinding.ItemNamespaceBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class KvFragment : Fragment() {
    
    private var _binding: FragmentKvBinding? = null
    private val binding get() = _binding!!
    
    private val accountViewModel: AccountViewModel by activityViewModels()
    private val kvViewModel: KvViewModel by viewModels()
    
    private lateinit var namespaceAdapter: NamespaceAdapter
    private lateinit var keyAdapter: KeyAdapter
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentKvBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupAdapters()
        setupClickListeners()
        observeViewModel()
        
        // 加载命名空间
        accountViewModel.defaultAccount.value?.let { account ->
            kvViewModel.loadNamespaces(account)
        }
    }
    
    private fun setupAdapters() {
        namespaceAdapter = NamespaceAdapter(
            onNamespaceClick = { namespace ->
                kvViewModel.selectNamespace(namespace)
                accountViewModel.defaultAccount.value?.let { account ->
                    kvViewModel.loadKeys(account, namespace.id)
                }
            },
            onDeleteClick = { namespace ->
                showDeleteNamespaceDialog(namespace)
            }
        )
        
        keyAdapter = KeyAdapter(
            onKeyClick = { key ->
                showKeyValueDialog(key)
            },
            onDeleteClick = { key ->
                showDeleteKeyDialog(key)
            }
        )
        
        binding.namespaceRecyclerView.adapter = namespaceAdapter
        binding.keyRecyclerView.adapter = keyAdapter
    }
    
    private fun setupClickListeners() {
        binding.fabAddNamespace.setOnClickListener {
            showAddNamespaceDialog()
        }
        
        binding.fabAddKey.setOnClickListener {
            showAddKeyDialog()
        }
    }
    
    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    kvViewModel.namespaces.collect { namespaces ->
                        namespaceAdapter.submitList(namespaces)
                        binding.namespaceEmptyText.visibility = 
                            if (namespaces.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    kvViewModel.selectedNamespace.collect { namespace ->
                        binding.fabAddKey.visibility = if (namespace != null) View.VISIBLE else View.GONE
                        binding.keyEmptyText.text = if (namespace != null) getString(R.string.kv_no_key_yet) else getString(R.string.kv_please_select_namespace_first)
                        // 动态更新右侧标题栏，格式：键值对（namespaceName）
                        binding.keyTitleText.text = namespace?.let { getString(R.string.kv_key_title_with_namespace, it.title) } ?: getString(R.string.kv_key_value)
                    }
                }
                
                launch {
                    kvViewModel.keys.collect { keys ->
                        keyAdapter.submitList(keys)
                        val hasKeys = keys.isNotEmpty()
                        binding.keyEmptyText.visibility = 
                            if (!hasKeys && kvViewModel.selectedNamespace.value != null) View.VISIBLE 
                            else if (kvViewModel.selectedNamespace.value == null) View.VISIBLE
                            else View.GONE
                    }
                }
                
                launch {
                    kvViewModel.loadingState.collect { isLoading ->
                        binding.namespaceProgressBar.visibility = 
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    kvViewModel.keysLoadingState.collect { isLoading ->
                        binding.keyProgressBar.visibility = 
                            if (isLoading) View.VISIBLE else View.GONE
                    }
                }
                
                launch {
                    kvViewModel.message.collect { message ->
                        Snackbar.make(binding.root, message.asString(requireContext()), Snackbar.LENGTH_SHORT).show()
                    }
                }
                
                launch {
                    accountViewModel.defaultAccount.collect { account ->
                        if (account != null) {
                            kvViewModel.loadNamespaces(account)
                        }
                    }
                }
            }
        }
    }
    
    private fun showAddNamespaceDialog() {
        val dialogBinding = DialogNamespaceInputBinding.inflate(layoutInflater)
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.dialog_create) { _, _ ->
                val title = dialogBinding.namespaceTitle.text.toString()
                accountViewModel.defaultAccount.value?.let { account ->
                    kvViewModel.createNamespace(account, title)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showDeleteNamespaceDialog(namespace: KvNamespace) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.kv_delete_namespace_title)
            .setMessage(getString(R.string.kv_delete_namespace_message, namespace.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    kvViewModel.deleteNamespace(account, namespace.id)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showAddKeyDialog() {
        val dialogBinding = DialogKvInputBinding.inflate(layoutInflater)
        
        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val keyName = dialogBinding.keyName.text.toString()
                val keyValue = dialogBinding.keyValue.text.toString()
                
                accountViewModel.defaultAccount.value?.let { account ->
                    kvViewModel.selectedNamespace.value?.let { namespace ->
                        kvViewModel.putValue(account, namespace.id, keyName, keyValue)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showKeyValueDialog(key: KvKey) {
        val dialogBinding = DialogKvInputBinding.inflate(layoutInflater)
        dialogBinding.keyName.setText(key.name)
        dialogBinding.keyName.isEnabled = false
        dialogBinding.keyValue.setText(key.value ?: "")
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.kv_edit_key_title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val keyValue = dialogBinding.keyValue.text.toString()
                accountViewModel.defaultAccount.value?.let { account ->
                    kvViewModel.selectedNamespace.value?.let { namespace ->
                        kvViewModel.putValue(account, namespace.id, key.name, keyValue)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    private fun showDeleteKeyDialog(key: KvKey) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.kv_delete_key_title)
            .setMessage(getString(R.string.kv_delete_key_message, key.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                accountViewModel.defaultAccount.value?.let { account ->
                    kvViewModel.selectedNamespace.value?.let { namespace ->
                        kvViewModel.deleteValue(account, namespace.id, key.name)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // RecyclerView Adapters
    private class NamespaceAdapter(
        private val onNamespaceClick: (KvNamespace) -> Unit,
        private val onDeleteClick: (KvNamespace) -> Unit
    ) : RecyclerView.Adapter<NamespaceAdapter.ViewHolder>() {
        
        private var namespaces = listOf<KvNamespace>()
        
        fun submitList(newList: List<KvNamespace>) {
            namespaces = newList
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemNamespaceBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(namespaces[position])
        }
        
        override fun getItemCount() = namespaces.size
        
        inner class ViewHolder(
            private val binding: ItemNamespaceBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(namespace: KvNamespace) {
                binding.namespaceTitleText.text = namespace.title
                binding.namespaceIdText.text = "ID: ${namespace.id}"
                
                binding.root.setOnClickListener {
                    onNamespaceClick(namespace)
                }
                
                binding.namespaceMenuButton.setOnClickListener { view ->
                    PopupMenu(view.context, view).apply {
                        inflate(R.menu.menu_account)
                        menu.findItem(R.id.action_set_default)?.isVisible = false
                        menu.findItem(R.id.action_edit)?.isVisible = false
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_delete -> {
                                    onDeleteClick(namespace)
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
    
    private class KeyAdapter(
        private val onKeyClick: (KvKey) -> Unit,
        private val onDeleteClick: (KvKey) -> Unit
    ) : RecyclerView.Adapter<KeyAdapter.ViewHolder>() {
        
        private var keys = listOf<KvKey>()
        
        fun submitList(newList: List<KvKey>) {
            keys = newList
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemKvKeyBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(keys[position])
        }
        
        override fun getItemCount() = keys.size
        
        inner class ViewHolder(
            private val binding: ItemKvKeyBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            
            fun bind(key: KvKey) {
                binding.keyNameText.text = key.name
                val ctx = binding.root.context
                binding.keyMetadataText.text = key.value?.let { ctx.getString(R.string.kv_value_label, it) } ?: ctx.getString(R.string.kv_value_loading)
                
                binding.root.setOnClickListener {
                    onKeyClick(key)
                }
                
                binding.keyMenuButton.setOnClickListener { view ->
                    PopupMenu(view.context, view).apply {
                        inflate(R.menu.menu_account)
                        menu.findItem(R.id.action_set_default)?.isVisible = false
                        menu.findItem(R.id.action_edit)?.isVisible = false
                        setOnMenuItemClickListener { item ->
                            when (item.itemId) {
                                R.id.action_delete -> {
                                    onDeleteClick(key)
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
