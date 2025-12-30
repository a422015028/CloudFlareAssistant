package com.muort.upworker.feature.d1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.*
import com.muort.upworker.core.repository.D1Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
class D1ViewModel @Inject constructor(
    private val d1Repository: D1Repository
) : ViewModel() {

    suspend fun deleteDatabase(account: Account, databaseId: String): Boolean {
        return d1Repository.deleteDatabase(account, databaseId) is Resource.Success
    }


    suspend fun createDatabase(account: Account, name: String): Boolean {
        return d1Repository.createDatabase(account, name) is Resource.Success
    }
    private val _databases = MutableStateFlow<UiState<List<D1Database>>>(UiState.Idle)
    val databases: StateFlow<UiState<List<D1Database>>> = _databases

    private val _tables = MutableStateFlow<UiState<List<D1Table>>>(UiState.Idle)
    val tables: StateFlow<UiState<List<D1Table>>> = _tables

    private val _queryResult = MutableStateFlow<UiState<D1QueryResult>>(UiState.Idle)
    val queryResult: StateFlow<UiState<D1QueryResult>> = _queryResult

    fun loadDatabases(account: Account) {
        _databases.value = UiState.Loading
        viewModelScope.launch {
            _databases.value = when (val result = d1Repository.listDatabases(account)) {
                is Resource.Success -> UiState.Success(result.data)
                is Resource.Error -> UiState.Error(result.message, result.exception)
                else -> UiState.Idle
            }
        }
    }

    fun loadTables(account: Account, databaseId: String) {
        _tables.value = UiState.Loading
        viewModelScope.launch {
            _tables.value = when (val result = d1Repository.listTables(account, databaseId)) {
                is Resource.Success -> UiState.Success(result.data)
                is Resource.Error -> UiState.Error(result.message, result.exception)
                else -> UiState.Idle
            }
        }
    }

    fun executeQuery(account: Account, databaseId: String, sql: String, params: List<Any>? = null) {
        _queryResult.value = UiState.Loading
        viewModelScope.launch {
            val result = d1Repository.executeQuery(account, databaseId, sql, params)
            _queryResult.value = when (result) {
                is Resource.Success -> UiState.Success(result.data)
                is Resource.Error -> UiState.Error(result.message, result.exception)
                else -> UiState.Idle
            }
        }
    }
}
