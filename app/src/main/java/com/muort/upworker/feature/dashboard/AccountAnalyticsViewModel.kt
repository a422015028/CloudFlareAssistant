package com.muort.upworker.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.AccountAnalyticsOverview
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
class AccountAnalyticsViewModel @Inject constructor(
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _state = MutableStateFlow<AccountAnalyticsState>(AccountAnalyticsState.Idle)
    val state: StateFlow<AccountAnalyticsState> = _state.asStateFlow()

    private val _overview = MutableStateFlow<AccountAnalyticsOverview?>(null)
    val overview: StateFlow<AccountAnalyticsOverview?> = _overview.asStateFlow()

    private val _currentTimeRange = MutableStateFlow(TimeRange.ONE_DAY)
    val currentTimeRange: StateFlow<TimeRange> = _currentTimeRange.asStateFlow()

    fun load(account: Account?, timeRange: TimeRange = TimeRange.ONE_DAY) {
        if (account == null) {
            _state.value = AccountAnalyticsState.Error(UiMessage.of(R.string.msg_please_select_account_first))
            return
        }

        _currentTimeRange.value = timeRange

        viewModelScope.launch {
            _state.value = AccountAnalyticsState.Loading

            when (val result = analyticsRepository.getAccountAnalyticsOverview(account, timeRange)) {
                is Resource.Success -> {
                    _overview.value = result.data
                    _state.value = AccountAnalyticsState.Success(result.data)
                    @Suppress("DEPRECATION") // deprecated displayName is the static fallback intended for logs
                    Timber.d("Account analytics loaded for ${timeRange.displayName}: requests=${result.data.requests}")
                }
                is Resource.Error -> {
                    _state.value = AccountAnalyticsState.Error(UiMessage.RawString(result.message))
                    Timber.e("Failed to load account analytics: ${result.message}")
                }
                is Resource.Loading -> {
                    // Already in loading state
                }
            }
        }
    }

    fun refresh(account: Account?) {
        load(account, _currentTimeRange.value)
    }

    fun changeTimeRange(account: Account?, timeRange: TimeRange) {
        load(account, timeRange)
    }
}

sealed class AccountAnalyticsState {
    object Idle : AccountAnalyticsState()
    object Loading : AccountAnalyticsState()
    data class Success(val overview: AccountAnalyticsOverview) : AccountAnalyticsState()
    data class Error(val message: UiMessage) : AccountAnalyticsState()
}
