package com.muort.upworker.feature.d1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.UiState
import com.muort.upworker.databinding.FragmentD1DataViewerBinding
import com.muort.upworker.feature.account.AccountViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class D1DataViewerFragment : Fragment() {

    private var _binding: FragmentD1DataViewerBinding? = null
    private val binding get() = _binding!!

    private lateinit var dataAdapter: D1DataAdapter

    private val viewModel: D1ViewModel by viewModels()
    private val accountViewModel: AccountViewModel by activityViewModels()

    private var currentAccount: Account? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentD1DataViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupToolbar()
        observeViewModel()
        loadTableData()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.toolbar.title = arguments?.getString("title") ?: "数据查看器"
    }

    private fun setupRecyclerView() {
        dataAdapter = D1DataAdapter(emptyList(), emptyList())
        binding.recyclerViewData.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = dataAdapter
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            accountViewModel.defaultAccount.collectLatest { account ->
                currentAccount = account
            }
        }

        lifecycleScope.launch {
            viewModel.queryResult.collectLatest { state ->
                when (state) {
                    is UiState.Success -> {
                        binding.progressBar.visibility = View.GONE
                        val result = state.data
                        if (result.success && result.results != null) {
                            val columns = result.results.firstOrNull()?.keys?.toList() ?: emptyList()
                            val rows = result.results
                            dataAdapter.updateData(columns, rows)
                        } else {
                            Snackbar.make(binding.root, result.error ?: "查询失败", Snackbar.LENGTH_SHORT).show()
                        }
                    }
                    is UiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        Snackbar.make(binding.root, "数据加载失败: ${state.message}", Snackbar.LENGTH_SHORT).show()
                    }
                    is UiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                    }
                    is UiState.Idle -> {
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun loadTableData() {
        val databaseId = arguments?.getString("databaseId")
        val tableName = arguments?.getString("tableName")

        if (databaseId != null && tableName != null && currentAccount != null) {
            val sql = "SELECT * FROM $tableName LIMIT 100"
            viewModel.executeQuery(currentAccount!!, databaseId, sql)
        } else {
            Snackbar.make(binding.root, "缺少必要的参数", Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}