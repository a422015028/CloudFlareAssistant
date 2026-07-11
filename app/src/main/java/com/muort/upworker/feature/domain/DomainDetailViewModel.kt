package com.muort.upworker.feature.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.Zone
import com.muort.upworker.core.repository.ZoneRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DomainDetailViewModel @Inject constructor(
    private val zoneRepository: ZoneRepository,
) : ViewModel() {

    private val zoneId = MutableStateFlow<String?>(null)

    val zone: StateFlow<Zone?> = zoneId
        .flatMapLatest { id -> if (id == null) flowOf(null) else zoneRepository.observeZone(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun bind(id: String) {
        if (zoneId.value != id) zoneId.value = id
    }
}
