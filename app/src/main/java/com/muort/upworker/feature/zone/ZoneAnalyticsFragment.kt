package com.muort.upworker.feature.zone

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.TimeRange
import com.muort.upworker.feature.dashboard.DashboardState
import com.muort.upworker.databinding.FragmentZoneAnalyticsBinding
import com.muort.upworker.feature.account.AccountViewModel
import com.muort.upworker.feature.dashboard.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber

@AndroidEntryPoint
class ZoneAnalyticsFragment : Fragment() {

    private var _binding: FragmentZoneAnalyticsBinding? = null
    private val binding get() = _binding!!

    private val accountViewModel: AccountViewModel by activityViewModels()
    private val dashboardViewModel: DashboardViewModel by viewModels()

    private val zoneId: String by lazy { arguments?.getString("zoneId") ?: "" }
    private val zoneName: String by lazy { arguments?.getString("zoneName") ?: "" }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentZoneAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity?.setTitle(R.string.zone_analytics_title)

        // 隐藏仪表盘卡片的开关控件（独立页面不需要）
        binding.dashboardCard.hideToggle()

        setupCardListeners()
        observeViewModel()

        // 自动加载数据
        accountViewModel.defaultAccount.value?.let { account ->
            dashboardViewModel.loadDashboard(account, zoneId)
        }
    }

    private fun setupCardListeners() {
        binding.dashboardCard.onRefreshClick = {
            accountViewModel.defaultAccount.value?.let { account ->
                dashboardViewModel.refresh(account, zoneId)
            }
        }

        binding.dashboardCard.onTimeRangeChanged = { timeRange ->
            accountViewModel.defaultAccount.value?.let { account ->
                dashboardViewModel.changeTimeRange(account, zoneId, timeRange)
            }
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    dashboardViewModel.dashboardState.collect { state ->
                        when (state) {
                            is DashboardState.Idle -> {}
                            is DashboardState.Loading -> binding.dashboardCard.showLoading()
                            is DashboardState.Success -> binding.dashboardCard.showData(state.metrics)
                            is DashboardState.Error -> {
                                binding.dashboardCard.showError(state.message.asString(requireContext()))
                                Timber.e("Zone analytics error: ${state.message}")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
