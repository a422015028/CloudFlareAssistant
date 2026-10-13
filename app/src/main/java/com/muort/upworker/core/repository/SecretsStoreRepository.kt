package com.muort.upworker.core.repository

import android.content.Context
import com.muort.upworker.R
import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.resolveApiError
import com.muort.upworker.core.util.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secrets Store 仓库
 * 文档: https://developers.cloudflare.com/api/resources/secrets_store/
 *
 * 覆盖：Stores（list/get/create/delete）、Secrets
 * （list/get/create/edit/delete/bulk_delete/duplicate）、Quota。
 */
@Singleton
class SecretsStoreRepository @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val api: CloudFlareApi
) {

    private fun errorMessage(response: retrofit2.Response<*>): String {
        val bodyMsg = response.body()?.let { body ->
            (body as? CloudFlareResponse<*>)?.errors?.firstOrNull()?.message
        }
        return resolveApiError(bodyMsg, response)
    }

    // ==================== Stores ====================

    suspend fun listStores(account: Account): Resource<List<SecretStore>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                // 自动聚合分页（per_page 上限 100）
                val all = mutableListOf<SecretStore>()
                var page = 1
                var totalPages = 1
                do {
                    val response = api.listSecretStores(
                        token = AuthHelper.getBearerToken(account),
                        email = AuthHelper.getEmail(account),
                        apiKey = AuthHelper.getGlobalApiKey(account),
                        accountId = account.accountId,
                        page = page,
                        order = "name",
                        direction = "asc"
                    )
                    if (!response.isSuccessful || response.body()?.success != true) {
                        return@safeApiCall Resource.Error(
                            appContext.getString(R.string.repo_ss_store_list_failed_format, errorMessage(response))
                        )
                    }
                    response.body()?.result?.let { all.addAll(it) }
                    totalPages = response.body()?.resultInfo?.totalPages ?: 1
                } while (++page <= totalPages && page <= MAX_PAGES)

                Resource.Success(all)
            }
        }

    suspend fun getStore(account: Account, storeId: String): Resource<SecretStore> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getSecretStore(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    storeId = storeId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.result?.let { Resource.Success(it) }
                        ?: Resource.Error(appContext.getString(R.string.repo_ss_store_no_result))
                } else {
                    Resource.Error(appContext.getString(R.string.repo_ss_store_get_failed_format, errorMessage(response)))
                }
            }
        }

    suspend fun createStore(account: Account, name: String): Resource<SecretStore> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.createSecretStore(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    request = SecretStoreCreateRequest(name)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.result?.let {
                        Timber.d("Secrets store created: %s", it.id)
                        Resource.Success(it)
                    } ?: Resource.Error(appContext.getString(R.string.repo_generic_create_no_result))
                } else {
                    Resource.Error(appContext.getString(R.string.repo_ss_store_create_failed_format, errorMessage(response)))
                }
            }
        }

    /**
     * 删除密钥库。非空库需 force=true 级联删除全部密钥。
     * 错误消息中保留 store_not_empty 供 UI 二次确认。
     */
    suspend fun deleteStore(account: Account, storeId: String, force: Boolean): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteSecretStore(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    storeId = storeId,
                    force = if (force) true else null
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error(appContext.getString(R.string.repo_ss_store_delete_failed_format, errorMessage(response)))
                }
            }
        }

    // ==================== Secrets ====================

    suspend fun listSecrets(
        account: Account,
        storeId: String,
        scopes: List<String>? = null
    ): Resource<List<StoredSecret>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val all = mutableListOf<StoredSecret>()
            var page = 1
            var totalPages = 1
            do {
                val response = api.listStoreSecrets(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    storeId = storeId,
                    page = page,
                    order = "name",
                    direction = "asc",
                    scopes = scopes?.takeIf { it.isNotEmpty() }
                )
                if (!response.isSuccessful || response.body()?.success != true) {
                    return@safeApiCall Resource.Error(
                        appContext.getString(R.string.repo_ss_secret_list_failed_format, errorMessage(response))
                    )
                }
                response.body()?.result?.let { all.addAll(it) }
                totalPages = response.body()?.resultInfo?.totalPages ?: 1
            } while (++page <= totalPages && page <= MAX_PAGES)

            Resource.Success(all)
        }
    }

    suspend fun getSecret(account: Account, storeId: String, secretId: String): Resource<StoredSecret> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getStoreSecret(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    storeId = storeId,
                    secretId = secretId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.result?.let { Resource.Success(it) }
                        ?: Resource.Error(appContext.getString(R.string.repo_ss_secret_no_result))
                } else {
                    Resource.Error(appContext.getString(R.string.repo_ss_secret_get_failed_format, errorMessage(response)))
                }
            }
        }

    /** 创建密钥（接口接收数组，这里一次创建一个，返回首个结果） */
    suspend fun createSecret(
        account: Account,
        storeId: String,
        body: StoredSecretCreateBody
    ): Resource<StoredSecret> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.createStoreSecrets(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                storeId = storeId,
                body = listOf(body)
            )
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.firstOrNull()?.let {
                    Timber.d("Stored secret created: %s", it.id)
                    Resource.Success(it)
                } ?: Resource.Error(appContext.getString(R.string.repo_generic_create_no_result))
            } else {
                Resource.Error(appContext.getString(R.string.repo_ss_secret_create_failed_format, errorMessage(response)))
            }
        }
    }

    suspend fun editSecret(
        account: Account,
        storeId: String,
        secretId: String,
        body: StoredSecretEditBody
    ): Resource<StoredSecret> = withContext(Dispatchers.IO) {
        safeApiCall {
            // 全局 Gson 开启了 serializeNulls，这里手工构造 PATCH 载荷：
            // value 留空（不修改）时必须完全省略，不能发送 null；
            // comment 传 JsonNull 表示用户主动清空备注。
            val payload = com.google.gson.JsonObject().apply {
                body.value?.let { addProperty("value", it) }
                if (body.comment != null) addProperty("comment", body.comment)
                else add("comment", com.google.gson.JsonNull.INSTANCE)
                val scopesArray = com.google.gson.JsonArray()
                body.scopes?.forEach { scopesArray.add(it) }
                add("scopes", scopesArray)
            }
            val response = api.editStoreSecret(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                storeId = storeId,
                secretId = secretId,
                body = payload
            )
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let { Resource.Success(it) }
                    ?: Resource.Error(appContext.getString(R.string.repo_generic_update_no_result))
            } else {
                Resource.Error(appContext.getString(R.string.repo_ss_secret_update_failed_format, errorMessage(response)))
            }
        }
    }

    suspend fun deleteSecret(account: Account, storeId: String, secretId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteStoreSecret(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    storeId = storeId,
                    secretId = secretId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error(appContext.getString(R.string.repo_ss_secret_delete_failed_format, errorMessage(response)))
                }
            }
        }

    /** 清空密钥库中的全部密钥（DELETE collection，无请求体） */
    suspend fun bulkDeleteSecrets(account: Account, storeId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.bulkDeleteStoreSecrets(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    storeId = storeId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error(appContext.getString(R.string.repo_ss_secret_bulk_delete_failed_format, errorMessage(response)))
                }
            }
        }

    suspend fun duplicateSecret(
        account: Account,
        storeId: String,
        secretId: String,
        body: StoredSecretDuplicateBody
    ): Resource<StoredSecret> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.duplicateStoreSecret(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                storeId = storeId,
                secretId = secretId,
                body = body
            )
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Timber.d("Stored secret duplicated: %s", it.id)
                    Resource.Success(it)
                } ?: Resource.Error(appContext.getString(R.string.repo_ss_secret_duplicate_no_result))
            } else {
                Resource.Error(appContext.getString(R.string.repo_ss_secret_duplicate_failed_format, errorMessage(response)))
            }
        }
    }

    // ==================== Quota ====================

    suspend fun getQuota(account: Account): Resource<SecretQuota> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getSecretsStoreQuota(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.result?.secrets?.let { Resource.Success(it) }
                        ?: Resource.Error(appContext.getString(R.string.repo_ss_quota_no_result))
                } else {
                    Resource.Error(appContext.getString(R.string.repo_ss_quota_failed_format, errorMessage(response)))
                }
            }
        }

    companion object {
        /** 分页聚合安全上限，避免异常 total_pages 导致死循环 */
        private const val MAX_PAGES = 50
    }
}
