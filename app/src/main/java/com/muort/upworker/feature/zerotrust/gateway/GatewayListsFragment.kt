package com.muort.upworker.feature.zerotrust.gateway

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
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
import com.muort.upworker.core.model.GatewayList
import com.muort.upworker.core.model.GatewayListItem
import com.muort.upworker.core.model.GatewayListRequest
import com.muort.upworker.core.model.Resource
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
            }
        }
    }

    private fun loadLists() {
        val account = accountViewModel.defaultAccount.value
        if (account == null) {
            android.widget.Toast.makeText(requireContext(), getString(R.string.msg_no_account_selected), android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val result = viewModel.loadLists(account)
            if (result is Resource.Error) {
                android.widget.Toast.makeText(
                    requireContext(),
                    getString(R.string.msg_load_lists_failed, result.message),
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadLists()
    }

    private fun showCreateListDialog(existingList: GatewayList? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_create_gateway_list, null)

        val nameInput = dialogView.findViewById<TextInputEditText>(R.id.listNameInput)
        val typeSpinner = dialogView.findViewById<Spinner>(R.id.listTypeSpinner)
        val descriptionInput = dialogView.findViewById<TextInputEditText>(R.id.descriptionInput)
        val itemsInput = dialogView.findViewById<TextInputEditText>(R.id.itemsInput)

        val templateDomainBtn = dialogView.findViewById<Button>(R.id.templateDomainBtn)
        val templateIpBtn = dialogView.findViewById<Button>(R.id.templateIpBtn)
        val templateUrlBtn = dialogView.findViewById<Button>(R.id.templateUrlBtn)

        val types = listOf(
            "DOMAIN" to getString(R.string.zt_list_type_domain),
            "IP" to getString(R.string.zt_list_type_ip),
            "URL" to getString(R.string.zt_list_type_url)
        )
        val typeAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            types.map { it.second }
        )
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        typeSpinner.adapter = typeAdapter

        templateDomainBtn.setOnClickListener {
            typeSpinner.setSelection(0)
            nameInput.setText(getString(R.string.zt_list_template_domain_name))
        }

        templateIpBtn.setOnClickListener {
            typeSpinner.setSelection(1)
            nameInput.setText(getString(R.string.zt_list_template_ip_name))
        }

        templateUrlBtn.setOnClickListener {
            typeSpinner.setSelection(2)
            nameInput.setText(getString(R.string.zt_list_template_url_name))
        }

        existingList?.let { list ->
            nameInput.setText(list.name)
            descriptionInput.setText(list.description ?: "")

            val typeIndex = types.indexOfFirst { it.first == list.type }
            if (typeIndex >= 0) typeSpinner.setSelection(typeIndex)

            val items = list.items
            if (items != null && items.isNotEmpty()) {
                val text = items.joinToString("\n") { it.value }
                itemsInput.setText(text)
                adjustItemsInputHeight(itemsInput, text)
            } else {
                itemsInput.hint = getString(R.string.zt_list_loading_hint)
                val account = accountViewModel.defaultAccount.value
                if (account != null) {
                    viewModel.loadListItems(account, list.id) { loadedItems ->
                        val text = loadedItems.joinToString("\n") { it.value }
                        itemsInput.setText(text)
                        itemsInput.hint = getString(R.string.zt_list_items_hint)
                        adjustItemsInputHeight(itemsInput, text)
                    }
                }
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existingList == null) R.string.zt_list_create_title else R.string.zt_list_edit_title)
            .setView(dialogView)
            .setPositiveButton(if (existingList == null) R.string.dialog_create else R.string.save) { _, _ ->
                val account = accountViewModel.defaultAccount.value ?: return@setPositiveButton
                val name = nameInput.text?.toString()
                val itemsText = itemsInput.text?.toString()

                if (name.isNullOrBlank()) {
                    android.widget.Toast.makeText(requireContext(), getString(R.string.msg_list_name_empty), android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                if (itemsText.isNullOrBlank()) {
                    android.widget.Toast.makeText(requireContext(), getString(R.string.msg_list_items_empty), android.widget.Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val listType = types[typeSpinner.selectedItemPosition].first
                var items = itemsText.split(Regex("[\n,;]")).map { it.trim() }.filter { it.isNotBlank() }

                if (listType == "DOMAIN") {
                    items = items.map { item ->
                        item.replace("*\\.", "").replace(".*\\.", "").replace(".*", "").replace("~", "").trim()
                    }.filter { it.isNotBlank() }

                    if (items.isEmpty()) {
                        android.widget.Toast.makeText(requireContext(), getString(R.string.msg_list_items_empty), android.widget.Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                }

                val request = GatewayListRequest(
                    name = name,
                    type = listType,
                    description = descriptionInput.text?.toString()?.takeIf { it.isNotBlank() },
                    items = items.map { GatewayListItem(value = it) }
                )

                if (existingList == null) {
                    lifecycleScope.launch {
                        val result = viewModel.createList(account, request)
                        val msg = when (result) {
                            is Resource.Success -> getString(R.string.msg_list_create_success, result.data.name)
                            is Resource.Error -> getString(R.string.msg_list_create_failed, result.message)
                            else -> return@launch
                        }
                        android.widget.Toast.makeText(requireContext(), msg, if (result is Resource.Error) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    lifecycleScope.launch {
                        val result = viewModel.updateList(account, existingList.id, request)
                        val msg = when (result) {
                            is Resource.Success -> getString(R.string.msg_list_update_success, result.data.name)
                            is Resource.Error -> getString(R.string.msg_list_update_failed, result.message)
                            else -> return@launch
                        }
                        android.widget.Toast.makeText(
                            requireContext(),
                            msg,
                            if (result is Resource.Error) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun adjustItemsInputHeight(input: TextInputEditText, text: String) {
        val lineCount = text.split("\n").size
        input.minLines = lineCount.coerceIn(4, 25)
        input.requestLayout()
    }

    private fun confirmDeleteList(listId: String, listName: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.zt_list_delete_title)
            .setMessage(getString(R.string.zt_list_delete_confirm, listName))
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteList(listId)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteList(listId: String) {
        val account = accountViewModel.defaultAccount.value ?: return
        lifecycleScope.launch {
            val result = viewModel.deleteList(account, listId)
            val msg = when (result) {
                is Resource.Success -> getString(R.string.msg_list_delete_success)
                is Resource.Error -> getString(R.string.msg_list_delete_failed, result.message)
                else -> return@launch
                }
            android.widget.Toast.makeText(requireContext(), msg, if (result is Resource.Error) android.widget.Toast.LENGTH_LONG else android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}