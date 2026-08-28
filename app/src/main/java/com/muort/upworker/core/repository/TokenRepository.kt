package com.muort.upworker.core.repository

import android.content.Context
import com.muort.upworker.R
import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 用户 API 令牌仓库
 * 文档: https://developers.cloudflare.com/api/resources/user/subresources/tokens/
 */
@Singleton
class TokenRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val api: CloudFlareApi
) {

    private fun errorMessage(response: retrofit2.Response<*>): String {
        return response.body()?.let { body ->
            (body as? CloudFlareResponse<*>)?.errors?.firstOrNull()?.message
        } ?: response.message()
    }

    suspend fun listTokens(account: Account): Resource<List<ApiToken>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listUserTokens(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account)
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result ?: emptyList())
                } else {
                    Resource.Error(appContext.getString(R.string.repo_token_list_failed_format, errorMessage(response)))
                }
            }
        }

    suspend fun getToken(account: Account, tokenId: String): Resource<ApiToken> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getUserToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    tokenId = tokenId
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.result?.let {
                        Resource.Success(it)
                    } ?: Resource.Error(appContext.getString(R.string.repo_token_detail_no_result))
                } else {
                    Resource.Error(appContext.getString(R.string.repo_token_detail_failed_format, errorMessage(response)))
                }
            }
        }

    suspend fun verifyToken(account: Account): Resource<TokenVerifyResult> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                // 用原始 token 判断类型（getBearerToken 返回 "Bearer xxx"，不能直接前缀匹配）
                val useAccountVerify = account.token.startsWith("cfat_")
                var response = if (useAccountVerify) {
                    api.verifyAccountToken(
                        token = AuthHelper.getBearerToken(account),
                        email = AuthHelper.getEmail(account),
                        apiKey = AuthHelper.getGlobalApiKey(account),
                        accountId = account.accountId
                    )
                } else {
                    api.verifyUserToken(
                        token = AuthHelper.getBearerToken(account),
                        email = AuthHelper.getEmail(account),
                        apiKey = AuthHelper.getGlobalApiKey(account)
                    )
                }

                // 旧格式账户令牌（无 cfat_ 前缀）在用户级端点报 Invalid，回退账户级端点
                if (!useAccountVerify &&
                    !(response.isSuccessful && response.body()?.success == true)
                ) {
                    response = api.verifyAccountToken(
                        token = AuthHelper.getBearerToken(account),
                        email = AuthHelper.getEmail(account),
                        apiKey = AuthHelper.getGlobalApiKey(account),
                        accountId = account.accountId
                    )
                }

                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.result?.let {
                        Resource.Success(it)
                    } ?: Resource.Error(appContext.getString(R.string.repo_token_verify_no_result))
                } else {
                    Resource.Error(appContext.getString(R.string.repo_token_verify_failed_format, errorMessage(response)))
                }
            }
        }

    suspend fun listPermissionGroups(account: Account): Resource<List<PermissionGroup>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listPermissionGroups(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account)
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result ?: emptyList())
                } else {
                    Resource.Error(appContext.getString(R.string.repo_token_pg_list_failed_format, errorMessage(response)))
                }
            }
        }

    /**
     * 账户所属令牌可用的权限组（GET /accounts/{account_id}/tokens/permission_groups）
     */
    suspend fun listAccountPermissionGroups(account: Account): Resource<List<PermissionGroup>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listAccountPermissionGroups(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result ?: emptyList())
                } else {
                    Resource.Error(appContext.getString(R.string.repo_token_account_pg_list_failed_format, errorMessage(response)))
                }
            }
        }

    suspend fun getUserId(account: Account): Resource<String> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getUser(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account)
                )

                val uid = response.body()?.result?.id
                if (response.isSuccessful && response.body()?.success == true && !uid.isNullOrBlank()) {
                    Resource.Success(uid)
                } else {
                    Resource.Error(appContext.getString(R.string.repo_token_user_info_failed_format, errorMessage(response)))
                }
            }
        }

    suspend fun createToken(
        account: Account,
        request: TokenUpsertRequest
    ): Resource<ApiToken> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.createUserToken(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                request = request
            )

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Timber.d("Token created: ${it.id}")
                    Resource.Success(it)
                } ?: Resource.Error(appContext.getString(R.string.repo_generic_create_no_result))
            } else {
                Resource.Error(appContext.getString(R.string.repo_token_create_failed_format, errorMessage(response)))
            }
        }
    }

    suspend fun updateToken(
        account: Account,
        tokenId: String,
        request: TokenUpsertRequest
    ): Resource<ApiToken> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.updateUserToken(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                tokenId = tokenId,
                request = request
            )

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error(appContext.getString(R.string.repo_generic_update_no_result))
            } else {
                Resource.Error(appContext.getString(R.string.repo_token_update_failed_format, errorMessage(response)))
            }
        }
    }

    suspend fun deleteToken(account: Account, tokenId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteUserToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    tokenId = tokenId
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error(appContext.getString(R.string.repo_token_delete_failed_format, errorMessage(response)))
                }
            }
        }

    /**
     * 更换账户级令牌 secret（PUT /accounts/{id}/tokens/{tid}/value）
     * 返回新 secret，旧值立即失效
     */
    suspend fun rollAccountToken(account: Account, tokenId: String): Resource<String> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.rollAccountToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tokenId = tokenId,
                    body = emptyMap()
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.result?.let {
                        Resource.Success(it)
                    } ?: Resource.Error(appContext.getString(R.string.repo_token_roll_no_result))
                } else {
                    Resource.Error(appContext.getString(R.string.repo_token_roll_failed_format, errorMessage(response)))
                }
            }
        }

    /**
     * 更换用户级令牌 secret（PUT /user/tokens/{tid}/value）
     * 返回新 secret，旧值立即失效
     */
    suspend fun rollUserToken(account: Account, tokenId: String): Resource<String> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.rollUserToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    tokenId = tokenId,
                    body = emptyMap()
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.result?.let {
                        Resource.Success(it)
                    } ?: Resource.Error(appContext.getString(R.string.repo_token_roll_no_result))
                } else {
                    Resource.Error(appContext.getString(R.string.repo_token_roll_failed_format, errorMessage(response)))
                }
            }
        }

    // ==================== 账户级 API 令牌 ====================
    // 文档: https://developers.cloudflare.com/api/resources/accounts/subresources/tokens/

    suspend fun listAccountTokens(account: Account): Resource<List<ApiToken>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listAccountTokens(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result ?: emptyList())
                } else {
                    Resource.Error(appContext.getString(R.string.repo_token_account_list_failed_format, errorMessage(response)))
                }
            }
        }

    suspend fun getAccountToken(account: Account, tokenId: String): Resource<ApiToken> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getAccountToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tokenId = tokenId
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    response.body()?.result?.let {
                        Resource.Success(it)
                    } ?: Resource.Error(appContext.getString(R.string.repo_token_detail_no_result))
                } else {
                    Resource.Error(appContext.getString(R.string.repo_token_detail_failed_format, errorMessage(response)))
                }
            }
        }

    suspend fun createAccountToken(
        account: Account,
        request: TokenUpsertRequest
    ): Resource<ApiToken> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.createAccountToken(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                request = request
            )

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Timber.d("Account token created: ${it.id}")
                    Resource.Success(it)
                } ?: Resource.Error(appContext.getString(R.string.repo_generic_create_no_result))
            } else {
                Resource.Error(appContext.getString(R.string.repo_token_account_create_failed_format, errorMessage(response)))
            }
        }
    }

    suspend fun updateAccountToken(
        account: Account,
        tokenId: String,
        request: TokenUpsertRequest
    ): Resource<ApiToken> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.updateAccountToken(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                accountId = account.accountId,
                tokenId = tokenId,
                request = request
            )

            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error(appContext.getString(R.string.repo_generic_update_no_result))
            } else {
                Resource.Error(appContext.getString(R.string.repo_token_account_update_failed_format, errorMessage(response)))
            }
        }
    }

    suspend fun deleteAccountToken(account: Account, tokenId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteAccountToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tokenId = tokenId
                )

                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(Unit)
                } else {
                    Resource.Error(appContext.getString(R.string.repo_token_account_delete_failed_format, errorMessage(response)))
                }
            }
        }
}
