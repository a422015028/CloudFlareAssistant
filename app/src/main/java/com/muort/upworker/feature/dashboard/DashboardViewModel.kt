package com.muort.upworker.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.DashboardMetrics
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.TimeRange
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
     * 加载仪表盘数据
     */
    fun loadDashboard(account: Account?, timeRange: TimeRange = TimeRange.ONE_DAY) {
        if (account == null) {
            _dashboardState.value = DashboardState.Error("请先选择账号")
            return
        }
        
        _currentTimeRange.value = timeRange
        
        viewModelScope.launch {
            _dashboardState.value = DashboardState.Loading
            
            when (val result = analyticsRepository.getDashboardMetrics(account, timeRange)) {
                is Resource.Success -> {
                    _metrics.value = result.data
                    _dashboardState.value = DashboardState.Success(result.data)
                    Timber.d("Dashboard loaded successfully for ${timeRange.displayName}: ${result.data}")
                }
                is Resource.Error -> {
                    val errorMsg = result.message
                    _dashboardState.value = DashboardState.Error(errorMsg)
                    Timber.e("Failed to load dashboard: $errorMsg")
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
    fun refresh(account: Account?) {
        loadDashboard(account, _currentTimeRange.value)
    }
    
    /**
     * 切换时间范围
     */
    fun changeTimeRange(account: Account?, timeRange: TimeRange) {
        loadDashboard(account, timeRange)
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
    data class Error(val message: String) : DashboardState()
}
