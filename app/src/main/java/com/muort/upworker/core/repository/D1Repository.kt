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
    suspend fun listTables(account: Account, databaseId: String): Resource<List<D1Table>> = withContext(Dispatchers.IO) {
        // 查询所有表名，并为每个表查询字段信息
        safeApiCall {
            val sql = "SELECT name FROM sqlite_master WHERE type='table'"
            val response = api.executeD1Query(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                databaseId = databaseId,
                query = D1QueryRequest(sql)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val resultList = body["result"] as? List<*> ?: emptyList<Any>()
                val firstResult = resultList.firstOrNull() as? Map<*, *>
                val results = firstResult?.get("results") as? List<*> ?: emptyList<Any>()
                val success = firstResult?.get("success") as? Boolean ?: false
                if (success) {
                    val tables = results.mapNotNull { row ->
                        val name = (row as? Map<*, *>)?.get("name") as? String
                        name
                    }.filterNot { it.startsWith("_cf_") || it.startsWith("sqlite_") }

                    // 查询每个表的字段信息
                    val d1Tables = tables.map { tableName ->
                        // PRAGMA table_info 返回字段信息
                        val pragmaSql = "PRAGMA table_info('$tableName')"
                        val pragmaResp = api.executeD1Query(
                            token = "Bearer ${account.token}",
                            accountId = account.accountId,
                            databaseId = databaseId,
                            query = D1QueryRequest(pragmaSql)
                        )
                        val pragmaBody = pragmaResp.body()
                        val pragmaResultList = pragmaBody?.get("result") as? List<*> ?: emptyList<Any>()
                        val pragmaFirstResult = pragmaResultList.firstOrNull() as? Map<*, *>
                        val pragmaResults = pragmaFirstResult?.get("results") as? List<*> ?: emptyList<Any>()
                        val columns = pragmaResults.mapNotNull { colRow ->
                            val colMap = colRow as? Map<*, *>
                            val colName = colMap?.get("name") as? String
                            val colType = colMap?.get("type") as? String
                            if (colName != null) D1Column(colName, colType) else null
                        }
                        D1Table(tableName, columns)
                    }
                    Resource.Success(d1Tables)
                } else {
                    val errorMsg = firstResult?.get("error") as? String ?: response.message()
                    Resource.Error("Failed to list tables: $errorMsg")
                }
            } else {
                Resource.Error("Failed to list tables: ${response.message()}")
            }
        }
    }

    suspend fun executeQuery(account: Account, databaseId: String, sql: String, params: List<Any>? = null): Resource<D1QueryResult> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.executeD1Query(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                databaseId = databaseId,
                query = D1QueryRequest(sql, params)
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                val success = body["success"] as? Boolean ?: false
                if (success) {
                    val resultArray = body["result"] as? List<*> ?: emptyList<Any>()
                    val firstResult = resultArray.firstOrNull() as? Map<String, Any?>
                    val results = firstResult?.get("results") as? List<*> ?: emptyList<Any>()
                    val meta = firstResult?.get("meta")
                    val d1Result = com.muort.upworker.core.model.D1QueryResult(
                        results = results.mapNotNull { it as? Map<String, Any?> },
                        success = true,
                        error = null,
                        meta = meta
                    )
                    Resource.Success(d1Result)
                } else {
                    val errorMsg = body["error"] as? String ?: response.message()
                    Resource.Error("Failed to execute query: $errorMsg")
                }
            } else {
                Resource.Error("Failed to execute query: ${response.message()}")
            }
        }
    }

    suspend fun exportDatabase(account: Account, databaseId: String) = withContext(Dispatchers.IO) {
        api.exportD1Database(
            token = "Bearer ${account.token}",
            accountId = account.accountId,
            databaseId = databaseId
        )
    }

    suspend fun importDatabase(account: Account, databaseId: String, file: okhttp3.MultipartBody.Part): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.importD1Database(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                databaseId = databaseId,
                file = file
            )
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message ?: response.message()
                Resource.Error("Failed to import D1 database: $errorMsg")
            }
        }
    }
}