package com.muort.upworker.feature.d1

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muort.upworker.core.model.*
import com.muort.upworker.core.repository.D1Repository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


@HiltViewModel
class D1ViewModel @Inject constructor(
    private val d1Repository: D1Repository
) : ViewModel() {

    suspend fun deleteDatabase(account: Account, databaseId: String): Boolean {
        Timber.d("Deleting D1 database: accountId=%s, databaseId=%s", account.accountId, databaseId)
        val success = d1Repository.deleteDatabase(account, databaseId) is Resource.Success
        if (success) Timber.d("D1 database deleted: databaseId=%s", databaseId)
        else Timber.e("Failed to delete D1 database: databaseId=%s", databaseId)
        return success
    }


    suspend fun createDatabase(account: Account, name: String): Boolean {
        Timber.d("Creating D1 database: accountId=%s, name=%s", account.accountId, name)
        val success = d1Repository.createDatabase(account, name) is Resource.Success
        if (success) Timber.d("D1 database created: name=%s", name)
        else Timber.e("Failed to create D1 database: name=%s", name)
        return success
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
                is Resource.Success -> {
                    Timber.d("Loaded %d D1 databases for accountId=%s", result.data.size, account.accountId)
                    UiState.Success(result.data)
                }
                is Resource.Error -> {
                    Timber.e("Failed to load D1 databases: accountId=%s, error=%s", account.accountId, result.message)
                    UiState.Error(result.message, result.exception)
                }
                else -> UiState.Idle
            }
        }
    }

    fun loadTables(account: Account, databaseId: String) {
        _tables.value = UiState.Loading
        viewModelScope.launch {
            _tables.value = when (val result = d1Repository.listTables(account, databaseId)) {
                is Resource.Success -> {
                    Timber.d("Loaded %d D1 tables: databaseId=%s", result.data.size, databaseId)
                    UiState.Success(result.data)
                }
                is Resource.Error -> {
                    Timber.e("Failed to load D1 tables: databaseId=%s, error=%s", databaseId, result.message)
                    UiState.Error(result.message, result.exception)
                }
                else -> UiState.Idle
            }
        }
    }

    fun executeQuery(account: Account, databaseId: String, sql: String, params: List<Any>? = null) {
        Timber.d("Executing D1 query: databaseId=%s, sql=%s", databaseId, sql.take(100))
        _queryResult.value = UiState.Loading
        viewModelScope.launch {
            val result = d1Repository.executeQuery(account, databaseId, sql, params)
            _queryResult.value = when (result) {
                is Resource.Success -> {
                    Timber.d("D1 query executed successfully: databaseId=%s", databaseId)
                    UiState.Success(result.data)
                }
                is Resource.Error -> {
                    Timber.e("D1 query failed: databaseId=%s, error=%s", databaseId, result.message)
                    UiState.Error(result.message, result.exception)
                }
                else -> UiState.Idle
            }
        }
    }
}
