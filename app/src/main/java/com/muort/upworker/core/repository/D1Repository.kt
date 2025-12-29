package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class D1Repository @Inject constructor(
    private val api: CloudFlareApi
) {

    suspend fun listDatabases(account: Account): Resource<List<D1Database>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                Timber.d("D1Repository: Starting listDatabases for account ${account.accountId}")
                val response = api.listD1Databases(
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )

                Timber.d("D1Repository: API response received. Success: ${response.isSuccessful}, Code: ${response.code()}")
                Timber.d("D1Repository: Response body success: ${response.body()?.success}")
                Timber.d("D1Repository: Response body result: ${response.body()?.result}")
                Timber.d("D1Repository: Response body errors: ${response.body()?.errors}")
                Timber.d("D1Repository: Raw response: ${response.raw()}")

                if (response.isSuccessful && response.body()?.success == true) {
                    val databases = response.body()?.result ?: emptyList()
                    Timber.d("D1Repository: Successfully retrieved ${databases.size} databases")
                    Resource.Success(databases)
                } else if (response.isSuccessful && response.body()?.success == false) {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
                        ?: "API returned success=false"
                    Timber.e("D1Repository: API returned success=false. Error: $errorMsg")
                    Resource.Error(errorMsg)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
                        ?: response.message()
                    Timber.e("D1Repository: HTTP error. Code: ${response.code()}, Message: $errorMsg")
                    Resource.Error("HTTP ${response.code()}: $errorMsg")
                }
            }
        }

    suspend fun createDatabase(
        account: Account,
        name: String
    ): Resource<D1Database> = withContext(Dispatchers.IO) {
        safeApiCall {
            val request = D1DatabaseRequest(name = name)
            val response = api.createD1Database(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                database = request
            )

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Database created but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message
                    ?: response.message()
                Resource.Error("Failed to create D1 database: $errorMsg")
            }
        }
    }

    suspend fun deleteDatabase(
        account: Account,
        databaseId: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deleteD1Database(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                databaseId = databaseId
            )

            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message
                    ?: response.message()
                Resource.Error("Failed to delete D1 database: $errorMsg")
            }
        }
    }
}