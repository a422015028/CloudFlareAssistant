package com.muort.upworker.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.DashboardMetrics
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.TimeRange
import com.muort.upworker.core.model.UiMessage
import com.muort.upworker.core.repository.AnalyticsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {
    
    private val _dashboardState = MutableStateFlow<DashboardState>(DashboardState.Idle)
    val dashboardState: StateFlow<DashboardState> = _dashboardState.asStateFlow()
    
    private val _metrics = MutableStateFlow<DashboardMetrics?>(null)
    val metrics: StateFlow<DashboardMetrics?> = _metrics.asStateFlow()
    
    private val _currentTimeRange = MutableStateFlow(TimeRange.ONE_DAY)
    val currentTimeRange: StateFlow<TimeRange> = _currentTimeRange.asStateFlow()
    
    /**
     * 加载域名分析数据
     */
    fun loadDashboard(account: Account?, zoneId: String, timeRange: TimeRange = TimeRange.ONE_DAY) {
        if (account == null) {
            _dashboardState.value = DashboardState.Error(UiMessage.of(R.string.msg_please_select_account_first))
            return
        }
        if (zoneId.isBlank()) {
            _dashboardState.value = DashboardState.Error(UiMessage.of(R.string.vm_msg_dash_zone_id_empty))
            return
        }
        
        _currentTimeRange.value = timeRange
        
        viewModelScope.launch {
            _dashboardState.value = DashboardState.Loading
            
            when (val result = analyticsRepository.getDashboardMetrics(account, zoneId, timeRange)) {
                is Resource.Success -> {
                    _metrics.value = result.data
                    _dashboardState.value = DashboardState.Success(result.data)
                    @Suppress("DEPRECATION") // deprecated displayName is the static fallback intended for logs
                    Timber.d("Dashboard loaded successfully for zone $zoneId, ${timeRange.displayName}: ${result.data}")
                }
                is Resource.Error -> {
                    _dashboardState.value = DashboardState.Error(UiMessage.RawString(result.message))
                    Timber.e("Failed to load dashboard: ${result.message}")
                }
                is Resource.Loading -> {
                    // Already in loading state
                }
            }
        }
    }
    
    /**
     * 刷新数据
     */
    fun refresh(account: Account?, zoneId: String) {
        loadDashboard(account, zoneId, _currentTimeRange.value)
    }
    
    /**
     * 切换时间范围
     */
    fun changeTimeRange(account: Account?, zoneId: String, timeRange: TimeRange) {
        loadDashboard(account, zoneId, timeRange)
    }
    
    /**
     * 重置状态
     */
    fun resetState() {
        _dashboardState.value = DashboardState.Idle
    }
}

/**
 * 仪表盘状态
 */
sealed class DashboardState {
    object Idle : DashboardState()
    object Loading : DashboardState()
    data class Success(val metrics: DashboardMetrics) : DashboardState()
    data class Error(val message: UiMessage) : DashboardState()
}
