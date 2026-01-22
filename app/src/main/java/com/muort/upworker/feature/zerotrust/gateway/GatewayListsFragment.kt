package com.muort.upworker.feature.zerotrust.gateway

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
import com.muort.upworker.core.model.GatewayList
import com.muort.upworker.core.model.GatewayListItem
import com.muort.upworker.core.model.GatewayListRequest
import com.muort.upworker.databinding.FragmentGatewayListsBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GatewayListsFragment : Fragment() {

    private var _binding: FragmentGatewayListsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GatewayViewModel by activityViewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()
    
    private lateinit var listAdapter: GatewayListAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGatewayListsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        loadLists()
    }

    private fun setupRecyclerView() {
        listAdapter = GatewayListAdapter(
            onEditClick = { list ->
                showCreateListDialog(list)
            },
            onDeleteClick = { list ->
                confirmDeleteList(list.id, list.name)
            }
        )

        binding.listsRecyclerView.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = listAdapter
        }
    }

    private fun setupClickListeners() {
        binding.fabAddList.setOnClickListener {
            showCreateListDialog()
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.lists.collect { lists ->
                        listAdapter.submitList(lists)
                        binding.emptyText.visibility = 
                            if (lists.isEmpty()) View.VISIBLE else View.GONE
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

    private fun loadLists() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            Snackbar.make(binding.root, "未选择账户", Snackbar.LENGTH_SHORT).show()
            return
        }

        viewModel.loadLists(account)
    }

    private fun showCreateListDialog(existingList: GatewayList? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_gateway_list, null)
        
        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.listNameInput)
        val typeSpinner = dialogView.findViewById<android.widget.Spinner>(R.id.listTypeSpinner)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.descriptionInput)
        val itemsInput = dialogView.findViewById<TextInputEditText>(R.id.itemsInput)

        // Setup type spinner
        val types = listOf("DOMAIN" to "域名", "IP" to "IP 地址", "URL" to "URL")
        val typeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            types.map { it.second }
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter

        // Populate existing list
        existingList?.let { list ->
            nameInput.setText(list.name)
            descriptionInput.setText(list.description ?: "")
            
            val typeIndex = types.indexOfFirst { it.first == list.type }
            if (typeIndex >= 0) typeSpinner.setSelection(typeIndex)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingList == null) "创建列表" else "编辑列表")
            .setView(dialogView)
            .setPositiveButton(if (existingList == null) "创建" else "保存") { _, _ ->
                val account = accountViewModel.defaultAccount.value ?: return@setPositiveButton
                val name = nameInput.text?.toString()
                val itemsText = itemsInput.text?.toString()
                
                if (name.isNullOrBlank()) {
                    Snackbar.make(binding.root, "列表名称不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (itemsText.isNullOrBlank()) {
                    Snackbar.make(binding.root, "列表项不能为空", Snackbar.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val listType = types[typeSpinner.selectedItemPosition].first
                val items = itemsText.split("\n").map { it.trim() }.filter { it.isNotBlank() }
                
                val request = GatewayListRequest(
                    name = name,
                    type = listType,
                    description = descriptionInput.text?.toString(),
                    items = items.map { GatewayListItem(value = it) }
                )

                if (existingList == null) {
                    viewModel.createList(account, request)
                } else {
                    viewModel.updateList(account, existingList.id, request)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmDeleteList(listId: String, listName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("删除列表")
            .setMessage("确定要删除列表 \"$listName\" 吗？")
            .setPositiveButton("删除") { _, _ ->
                deleteList(listId)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteList(listId: String) {
        val account = accountViewModel.defaultAccount.value ?: return
        viewModel.deleteList(account, listId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
