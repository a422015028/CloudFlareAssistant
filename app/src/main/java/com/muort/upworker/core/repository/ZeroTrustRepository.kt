package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.resolveApiError
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Cloudflare Zero Trust (Cloudflare One) operations
 * Includes Access, Gateway, Devices, and Tunnels management
 */
@Singleton
class ZeroTrustRepository @Inject constructor(
    private val api: CloudFlareApi
) {
    
    // ==================== Access Applications ====================
    
    /**
     * List all Access applications
     */
    suspend fun listAccessApplications(account: Account): Resource<List<AccessApplication>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listAccessApplications(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val apps = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${apps.size} Access applications")
                    Resource.Success(apps)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list applications"
                    Timber.e("Error listing applications: $errorMsg")
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Get a specific Access application
     */
    suspend fun getAccessApplication(account: Account, appId: String): Resource<AccessApplication> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getAccessApplication(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    appId = appId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val app = response.body()!!.result!!
                    Timber.d("Loaded Access application: ${app.name}")
                    Resource.Success(app)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to get application"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Create a new Access application
     */
    suspend fun createAccessApplication(
        account: Account,
        request: AccessApplicationRequest
    ): Resource<AccessApplication> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.createAccessApplication(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    application = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val app = response.body()!!.result!!
                    Timber.d("Created Access application: ${app.name}")
                    Resource.Success(app)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to create application"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Update an Access application
     */
    suspend fun updateAccessApplication(
        account: Account,
        appId: String,
        request: AccessApplicationRequest
    ): Resource<AccessApplication> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.updateAccessApplication(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    appId = appId,
                    application = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val app = response.body()!!.result!!
                    Timber.d("Updated Access application: ${app.name}")
                    Resource.Success(app)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to update application"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Delete an Access application
     */
    suspend fun deleteAccessApplication(account: Account, appId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteAccessApplication(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    appId = appId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted Access application: $appId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to delete application"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    // ==================== Access Policies ====================
    
    /**
     * List all reusable Access policies
     */
    suspend fun listAccessPolicies(account: Account): Resource<List<AccessPolicy>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listAccessPolicies(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policies = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${policies.size} Access policies")
                    Resource.Success(policies)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list policies"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * List policies for a specific application
     */
    suspend fun listAppPolicies(account: Account, appId: String): Resource<List<AccessPolicy>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listAppPolicies(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    appId = appId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policies = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${policies.size} policies for app $appId")
                    Resource.Success(policies)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list app policies"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Create a policy for an application
     */
    suspend fun createAppPolicy(
        account: Account,
        appId: String,
        request: AccessPolicyRequest
    ): Resource<AccessPolicy> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.createAppPolicy(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    appId = appId,
                    policy = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policy = response.body()!!.result!!
                    Timber.d("Created policy: ${policy.name}")
                    Resource.Success(policy)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to create policy"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Update an application policy
     */
    suspend fun updateAppPolicy(
        account: Account,
        appId: String,
        policyId: String,
        request: AccessPolicyRequest
    ): Resource<AccessPolicy> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.updateAppPolicy(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    appId = appId,
                    policyId = policyId,
                    policy = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policy = response.body()!!.result!!
                    Timber.d("Updated policy: ${policy.name}")
                    Resource.Success(policy)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to update policy"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Delete an application policy
     */
    suspend fun deleteAppPolicy(
        account: Account,
        appId: String,
        policyId: String
    ): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteAppPolicy(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    appId = appId,
                    policyId = policyId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted policy: $policyId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to delete policy"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    // ==================== Access Groups ====================
    
    /**
     * List all Access groups
     */
    suspend fun listAccessGroups(account: Account): Resource<List<AccessGroup>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listAccessGroups(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val groups = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${groups.size} Access groups")
                    Resource.Success(groups)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list groups"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Get a specific Access group
     */
    suspend fun getAccessGroup(account: Account, groupId: String): Resource<AccessGroup> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getAccessGroup(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    groupId = groupId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val group = response.body()!!.result!!
                    Timber.d("Loaded Access group: ${group.name}")
                    Resource.Success(group)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to get group"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Create an Access group
     */
    suspend fun createAccessGroup(
        account: Account,
        request: AccessGroupRequest
    ): Resource<AccessGroup> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.createAccessGroup(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    group = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val group = response.body()!!.result!!
                    Timber.d("Created Access group: ${group.name}")
                    Resource.Success(group)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to create group"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Update an Access group
     */
    suspend fun updateAccessGroup(
        account: Account,
        groupId: String,
        request: AccessGroupRequest
    ): Resource<AccessGroup> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.updateAccessGroup(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    groupId = groupId,
                    group = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val group = response.body()!!.result!!
                    Timber.d("Updated Access group: ${group.name}")
                    Resource.Success(group)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to update group"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Delete an Access group
     */
    suspend fun deleteAccessGroup(account: Account, groupId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteAccessGroup(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    groupId = groupId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted Access group: $groupId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to delete group"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    // ==================== Gateway Rules ====================
    
    /**
     * List all Gateway rules
     */
    suspend fun listGatewayRules(account: Account): Resource<List<GatewayRule>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listGatewayRules(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val rules = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${rules.size} Gateway rules")
                    Resource.Success(rules)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list rules"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Create a Gateway rule
     */
    suspend fun createGatewayRule(
        account: Account,
        request: GatewayRuleRequest
    ): Resource<GatewayRule> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.createGatewayRule(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    rule = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val rule = response.body()!!.result!!
                    Timber.d("Created Gateway rule: ${rule.name}")
                    Resource.Success(rule)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to create rule"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Update a Gateway rule
     */
    suspend fun updateGatewayRule(
        account: Account,
        ruleId: String,
        request: GatewayRuleRequest
    ): Resource<GatewayRule> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.updateGatewayRule(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    ruleId = ruleId,
                    rule = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val rule = response.body()!!.result!!
                    Timber.d("Updated Gateway rule: ${rule.name}")
                    Resource.Success(rule)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to update rule"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Delete a Gateway rule
     */
    suspend fun deleteGatewayRule(account: Account, ruleId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteGatewayRule(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    ruleId = ruleId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted Gateway rule: $ruleId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to delete rule"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    // ==================== Gateway Lists ====================
    
    /**
     * List all Gateway lists
     */
    suspend fun listGatewayLists(account: Account): Resource<List<GatewayList>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listGatewayLists(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val lists = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${lists.size} Gateway lists")
                    Resource.Success(lists)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list gateway lists"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Get a specific Gateway list
     */
    suspend fun getGatewayList(account: Account, listId: String): Resource<GatewayList> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getGatewayList(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    listId = listId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val list = response.body()!!.result!!
                    Timber.d("Loaded Gateway list: ${list.name}")
                    Resource.Success(list)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to get list"
                    Resource.Error(errorMsg)
                }
            }
        }

    /**
     * Get items of a specific Gateway list
     */
    suspend fun getGatewayListItems(account: Account, listId: String): Resource<List<GatewayListItem>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listGatewayListItems(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    listId = listId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val items = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${items.size} items for list $listId")
                    Resource.Success(items)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to get list items"
                    Resource.Error(errorMsg)
                }
            }
        }

    /**
     * Create a Gateway list
     */
    suspend fun createGatewayList(
        account: Account,
        request: GatewayListRequest
    ): Resource<GatewayList> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.createGatewayList(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    list = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val list = response.body()!!.result!!
                    Timber.d("Created Gateway list: ${list.name}")
                    Resource.Success(list)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to create list"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Update a Gateway list
     */
    suspend fun updateGatewayList(
        account: Account,
        listId: String,
        request: GatewayListRequest
    ): Resource<GatewayList> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.updateGatewayList(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    listId = listId,
                    list = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val list = response.body()!!.result!!
                    Timber.d("Updated Gateway list: ${list.name}")
                    Resource.Success(list)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to update list"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Patch a Gateway list (append/remove items)
     */
    suspend fun patchGatewayList(
        account: Account,
        listId: String,
        patch: GatewayListPatchRequest
    ): Resource<GatewayList> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.patchGatewayList(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    listId = listId,
                    patch = patch
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val list = response.body()!!.result!!
                    Timber.d("Patched Gateway list: ${list.name}")
                    Resource.Success(list)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to patch list"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Delete a Gateway list
     */
    suspend fun deleteGatewayList(account: Account, listId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteGatewayList(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    listId = listId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted Gateway list: $listId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to delete list"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    // ==================== Gateway Locations ====================
    
    /**
     * List all Gateway locations
     */
    suspend fun listGatewayLocations(account: Account): Resource<List<GatewayLocation>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listGatewayLocations(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val locations = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${locations.size} Gateway locations")
                    Resource.Success(locations)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list locations"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Get a specific Gateway location
     */
    suspend fun getGatewayLocation(account: Account, locationId: String): Resource<GatewayLocation> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getGatewayLocation(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    locationId = locationId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val location = response.body()!!.result!!
                    Timber.d("Loaded Gateway location: ${location.name}")
                    Resource.Success(location)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to get location"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Create a Gateway location
     */
    suspend fun createGatewayLocation(
        account: Account,
        request: GatewayLocationRequest
    ): Resource<GatewayLocation> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.createGatewayLocation(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    location = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val location = response.body()!!.result!!
                    Timber.d("Created Gateway location: ${location.name}")
                    Resource.Success(location)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to create location"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Update a Gateway location
     */
    suspend fun updateGatewayLocation(
        account: Account,
        locationId: String,
        request: GatewayLocationRequest
    ): Resource<GatewayLocation> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.updateGatewayLocation(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    locationId = locationId,
                    location = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val location = response.body()!!.result!!
                    Timber.d("Updated Gateway location: ${location.name}")
                    Resource.Success(location)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to update location"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Delete a Gateway location
     */
    suspend fun deleteGatewayLocation(account: Account, locationId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteGatewayLocation(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    locationId = locationId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted Gateway location: $locationId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = response.errorBody()?.string()?.take(200)
                        ?: response.message()
                        ?: "Failed to delete location"
                    Resource.Error(errorMsg)
                }
            }
        }

    // ==================== Gateway DNS Analytics ====================

    /**
     * 获取 Gateway DNS 查询分析数据
     * 数据集: gatewayResolverQueriesAdaptiveGroups
     * 包含: DNS 操作(resolverDecision)、国家/地区(srcIpCountry)、DNS 位置(locationName)
     */
    suspend fun getGatewayDnsAnalytics(
        account: Account,
        timeRange: TimeRange = TimeRange.SEVEN_DAYS,
        limit: Int = 5
    ): Resource<GatewayDnsAnalytics> = withContext(Dispatchers.IO) {
        safeApiCall {
            val query = """
                query GatewayDnsAnalytics(${'$'}accountTag: string!, ${'$'}since: Time!, ${'$'}until: Time!, ${'$'}limit: Int!) {
                  viewer {
                    accounts(filter: { accountTag: ${'$'}accountTag }) {
                      ops: gatewayResolverQueriesAdaptiveGroups(
                        filter: { datetime_geq: ${'$'}since, datetime_leq: ${'$'}until }
                        limit: ${'$'}limit
                        orderBy: [count_DESC]
                      ) {
                        count
                        dimensions { resolverDecision }
                      }
                      countries: gatewayResolverQueriesAdaptiveGroups(
                        filter: { datetime_geq: ${'$'}since, datetime_leq: ${'$'}until }
                        limit: ${'$'}limit
                        orderBy: [count_DESC]
                      ) {
                        count
                        dimensions { srcIpCountry }
                      }
                      locations: gatewayResolverQueriesAdaptiveGroups(
                        filter: { datetime_geq: ${'$'}since, datetime_leq: ${'$'}until }
                        limit: ${'$'}limit
                        orderBy: [count_DESC]
                      ) {
                        count
                        dimensions { locationName }
                      }
                      domains: gatewayResolverQueriesAdaptiveGroups(
                        filter: { datetime_geq: ${'$'}since, datetime_leq: ${'$'}until }
                        limit: ${'$'}limit
                        orderBy: [count_DESC]
                      ) {
                        count
                        dimensions { queryName }
                      }
                      policies: gatewayResolverQueriesAdaptiveGroups(
                        filter: { datetime_geq: ${'$'}since, datetime_leq: ${'$'}until }
                        limit: ${'$'}limit
                        orderBy: [count_DESC]
                      ) {
                        count
                        dimensions { policyName }
                      }
                    }
                  }
                }
            """.trimIndent()

            val variables = mapOf(
                "accountTag" to account.accountId,
                "since" to timeRange.getStartDateTime(),
                "until" to timeRange.getEndDateTime(),
                "limit" to limit
            )

            val response = api.queryGatewayDnsAnalytics(
                token = AuthHelper.getBearerToken(account),
                email = AuthHelper.getEmail(account),
                apiKey = AuthHelper.getGlobalApiKey(account),
                request = AnalyticsGraphQLRequest(query = query, variables = variables)
            )

            if (response.isSuccessful) {
                val body = response.body()
                if (body?.errors != null && body.errors.isNotEmpty()) {
                    val errorMsg = body.errors.firstOrNull()?.message ?: "GraphQL query failed"
                    Timber.e("Gateway DNS analytics GraphQL error: $errorMsg")
                    Resource.Error(errorMsg)
                } else {
                    val accountNode = body?.data?.viewer?.accounts?.firstOrNull()
                    val analytics = GatewayDnsAnalytics(
                        operations = accountNode?.ops
                            ?.mapNotNull { g ->
                                g.dimensions?.resolverDecision?.let { DnsOperationItem(it, g.count) }
                            }
                            ?.sortedByDescending { it.count }
                            ?: emptyList(),
                        countries = accountNode?.countries
                            ?.mapNotNull { g ->
                                g.dimensions?.srcIpCountry?.let { DnsCountryItem(it, g.count) }
                            }
                            ?.sortedByDescending { it.count }
                            ?: emptyList(),
                        locations = accountNode?.locations
                            ?.mapNotNull { g ->
                                g.dimensions?.locationName?.takeIf { it.isNotBlank() }
                                    ?.let { DnsLocationItem(it, g.count) }
                            }
                            ?.sortedByDescending { it.count }
                            ?: emptyList(),
                        domains = accountNode?.domains
                            ?.mapNotNull { g ->
                                g.dimensions?.queryName?.takeIf { it.isNotBlank() }
                                    ?.let { DnsDomainItem(it, g.count) }
                            }
                            ?.sortedByDescending { it.count }
                            ?: emptyList(),
                        // 空策略名（未命中任何策略）的分组不展示
                        policies = accountNode?.policies
                            ?.mapNotNull { g ->
                                g.dimensions?.policyName?.takeIf { it.isNotBlank() }
                                    ?.let { DnsPolicyItem(it, g.count) }
                            }
                            ?.sortedByDescending { it.count }
                            ?: emptyList()
                    )
                    Timber.d("Gateway DNS analytics loaded: ops=${analytics.operations.size}, " +
                            "countries=${analytics.countries.size}, locations=${analytics.locations.size}, " +
                            "domains=${analytics.domains.size}, policies=${analytics.policies.size}")
                    Resource.Success(analytics)
                }
            } else {
                val errorMsg = "HTTP ${response.code()}"
                Timber.e("Gateway DNS analytics request failed: $errorMsg")
                Resource.Error(errorMsg)
            }
        }
    }

    // ==================== Devices ====================
    
    /**
     * List all devices
     */
    suspend fun listDevices(account: Account): Resource<List<Device>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listDevices(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val devices = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${devices.size} devices")
                    Resource.Success(devices)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list devices"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Get a specific device
     */
    suspend fun getDevice(account: Account, deviceId: String): Resource<Device> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getDevice(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    deviceId = deviceId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val device = response.body()!!.result!!
                    Timber.d("Loaded device: ${device.name}")
                    Resource.Success(device)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to get device"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Revoke a device
     */
    suspend fun revokeDevice(account: Account, deviceId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.revokeDevice(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    deviceId = deviceId
                )
                if (response.isSuccessful) {
                    Timber.d("Revoked device: $deviceId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to revoke device"
                    Resource.Error(errorMsg)
                }
            }
        }

    /**
     * Delete a device
     */
    suspend fun deleteDevice(account: Account, deviceId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteDevice(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    deviceId = deviceId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted device: $deviceId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to delete device"
                    Resource.Error(errorMsg)
                }
            }
        }

    // ==================== Device Policies ====================
    
    /**
     * List all device policies
     */
    suspend fun listDevicePolicies(account: Account): Resource<List<DeviceSettingsPolicy>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listDevicePolicies(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policies = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${policies.size} device policies")
                    Resource.Success(policies)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list device policies"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Get default device policy
     */
    suspend fun getDefaultDevicePolicy(account: Account): Resource<DeviceSettingsPolicy> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getDefaultDevicePolicy(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policy = response.body()!!.result!!
                    Timber.d("Loaded default device policy")
                    Resource.Success(policy)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to get default policy"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Update default device policy
     */
    suspend fun updateDefaultDevicePolicy(
        account: Account,
        update: DevicePolicyUpdate
    ): Resource<DeviceSettingsPolicy> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.updateDefaultDevicePolicy(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    policy = update
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policy = response.body()!!.result!!
                    Timber.d("Updated default device policy")
                    Resource.Success(policy)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to update default policy"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Create a device policy
     */
    suspend fun createDevicePolicy(
        account: Account,
        request: DeviceSettingsPolicyRequest
    ): Resource<DeviceSettingsPolicy> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.createDevicePolicy(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    policy = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policy = response.body()!!.result!!
                    Timber.d("Created device policy: ${policy.name}")
                    Resource.Success(policy)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to create policy"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Update a device policy
     */
    suspend fun updateDevicePolicy(
        account: Account,
        policyId: String,
        request: DeviceSettingsPolicyRequest
    ): Resource<DeviceSettingsPolicy> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.updateDevicePolicy(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    policyId = policyId,
                    policy = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policy = response.body()!!.result!!
                    Timber.d("Updated device policy: ${policy.name}")
                    Resource.Success(policy)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to update policy"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    suspend fun setDefaultSplitTunnelExclude(
        account: Account,
        items: List<SplitTunnel>
    ): Resource<List<SplitTunnel>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.setDefaultSplitTunnelExclude(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    items = items
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()!!.result!!)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to set split tunnel exclude"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    suspend fun setDefaultSplitTunnelInclude(
        account: Account,
        items: List<SplitTunnel>
    ): Resource<List<SplitTunnel>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.setDefaultSplitTunnelInclude(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    items = items
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()!!.result!!)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to set split tunnel include"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    suspend fun setSplitTunnelExclude(
        account: Account,
        policyId: String,
        items: List<SplitTunnel>
    ): Resource<List<SplitTunnel>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.setSplitTunnelExclude(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    policyId = policyId,
                    items = items
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()!!.result!!)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to set split tunnel exclude"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    suspend fun setSplitTunnelInclude(
        account: Account,
        policyId: String,
        items: List<SplitTunnel>
    ): Resource<List<SplitTunnel>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.setSplitTunnelInclude(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    policyId = policyId,
                    items = items
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()!!.result!!)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to set split tunnel include"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Delete a device policy
     */
    suspend fun deleteDevicePolicy(
        account: Account,
        policyId: String
    ): Resource<Boolean> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteDevicePolicy(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    policyId = policyId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Timber.d("Deleted device policy: $policyId")
                    Resource.Success(true)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to delete policy"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    // ==================== Cloudflare Tunnels ====================
    
    /**
     * List all Cloudflare Tunnels
     */
    suspend fun listTunnels(account: Account): Resource<List<CloudflareTunnel>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listCloudflaredTunnels(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val tunnels = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${tunnels.size} Cloudflare Tunnels")
                    Resource.Success(tunnels)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list tunnels"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Get a specific tunnel
     */
    suspend fun getTunnel(account: Account, tunnelId: String): Resource<CloudflareTunnel> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getCloudflaredTunnel(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tunnelId = tunnelId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val tunnel = response.body()!!.result!!
                    Timber.d("Loaded tunnel: ${tunnel.name}")
                    Resource.Success(tunnel)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to get tunnel"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Create a Cloudflare Tunnel
     */
    suspend fun createTunnel(
        account: Account,
        request: TunnelCreateRequest
    ): Resource<CloudflareTunnel> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                // Generate Cloudflare-style secret if not provided
                val tunnelRequest = if (request.tunnelSecret.isNullOrEmpty()) {
                    request.copy(tunnelSecret = generateTunnelSecret())
                } else {
                    request
                }
                val response = api.createCloudflaredTunnel(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tunnel = tunnelRequest
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val tunnel = response.body()!!.result!!
                    Timber.d("Created tunnel: ${tunnel.name}")
                    Resource.Success(tunnel)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to create tunnel"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Delete a Cloudflare Tunnel
     */
    suspend fun deleteTunnel(account: Account, tunnelId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteCloudflaredTunnel(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tunnelId = tunnelId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted tunnel: $tunnelId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to delete tunnel"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * List tunnel connections
     */
    suspend fun listTunnelConnections(
        account: Account,
        tunnelId: String
    ): Resource<List<TunnelConnection>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listTunnelConnections(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tunnelId = tunnelId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val connections = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${connections.size} connections for tunnel $tunnelId")
                    Resource.Success(connections)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list connections"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Get tunnel configuration
     */
    suspend fun getTunnelConfiguration(
        account: Account,
        tunnelId: String
    ): Resource<TunnelConfiguration> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getTunnelConfiguration(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tunnelId = tunnelId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val config = response.body()!!.result!!
                    Timber.d("Loaded configuration for tunnel $tunnelId")
                    Resource.Success(config)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to get configuration"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Update tunnel configuration
     */
    suspend fun updateTunnelConfiguration(
        account: Account,
        tunnelId: String,
        request: TunnelConfigurationRequest
    ): Resource<TunnelConfiguration> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.updateTunnelConfiguration(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tunnelId = tunnelId,
                    config = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val config = response.body()!!.result!!
                    Timber.d("Updated configuration for tunnel $tunnelId")
                    Resource.Success(config)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to update configuration"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Get tunnel token for running cloudflared service
     */
    suspend fun getTunnelToken(account: Account, tunnelId: String): Resource<String> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getTunnelToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tunnelId = tunnelId
                )
                if (response.isSuccessful && response.body()?.success == true && response.body()?.result != null) {
                    val token = response.body()!!.result!!
                    Timber.d("Got tunnel token for tunnel $tunnelId")
                    Resource.Success(token)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to get tunnel token"
                    Resource.Error(errorMsg)
                }
            }
        }

    /**
     * Refresh / rotate tunnel token - invalidates current token and generates a new one
     * Works by generating a new random tunnel_secret and PATCH-ing the tunnel
     */
    suspend fun refreshTunnelToken(account: Account, tunnelId: String): Resource<String> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val newSecret = generateTunnelSecret()

                // PATCH tunnel with new secret - this invalidates the old token
                val patchResponse = api.updateCloudflaredTunnel(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tunnelId = tunnelId,
                    body = TunnelUpdateRequest(tunnelSecret = newSecret)
                )

                if (patchResponse.isSuccessful && patchResponse.body()?.success == true) {
                    // Get the new token
                    val tokenResponse = api.getTunnelToken(
                        token = AuthHelper.getBearerToken(account),
                        email = AuthHelper.getEmail(account),
                        apiKey = AuthHelper.getGlobalApiKey(account),
                        accountId = account.accountId,
                        tunnelId = tunnelId
                    )
                    if (tokenResponse.isSuccessful && tokenResponse.body()?.success == true && tokenResponse.body()?.result != null) {
                        val token = tokenResponse.body()!!.result!!
                        Timber.d("Refreshed tunnel token for tunnel $tunnelId")
                        Resource.Success(token)
                    } else {
                        val errorMsg = resolveApiError(tokenResponse.body()?.errors?.firstOrNull()?.message, tokenResponse)
                            ?: "Failed to get new tunnel token after refresh"
                        Resource.Error(errorMsg)
                    }
                } else {
                    val errorMsg = resolveApiError(patchResponse.body()?.errors?.firstOrNull()?.message, patchResponse)
                        ?: "Failed to refresh tunnel token"
                    Resource.Error(errorMsg)
                }
            }
        }

    /**
     * Generate a Cloudflare-style tunnel secret: two UUIDs concatenated, base64 encoded.
     * Matches the format Cloudflare uses natively, resulting in consistent token appearance.
     */
    private fun generateTunnelSecret(): String {
        val uuid1 = java.util.UUID.randomUUID().toString()
        val uuid2 = java.util.UUID.randomUUID().toString()
        val secretStr = uuid1 + uuid2
        return android.util.Base64.encodeToString(
            secretStr.toByteArray(Charsets.UTF_8),
            android.util.Base64.NO_WRAP
        )
    }

    // ==================== Teamnet Routes (私有网络路由) ====================

    /**
     * 获取指定隧道的私有网络路由列表
     */
    suspend fun listTeamnetRoutes(account: Account, tunnelId: String): Resource<List<TeamnetRoute>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listTeamnetRoutes(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tunnelId = tunnelId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val routes = response.body()?.result ?: emptyList()
                    Timber.d("Got ${routes.size} teamnet routes for tunnel $tunnelId")
                    Resource.Success(routes)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list teamnet routes"
                    Resource.Error(errorMsg)
                }
            }
        }

    /**
     * 创建私有网络路由
     */
    suspend fun createTeamnetRoute(account: Account, network: String, tunnelId: String, comment: String? = null): Resource<TeamnetRoute> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val request = CreateTeamnetRouteRequest(
                    network = network,
                    tunnelId = tunnelId,
                    comment = comment ?: ""
                )
                val response = api.createTeamnetRoute(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    request = request
                )
                if (response.isSuccessful && response.body()?.success == true && response.body()?.result != null) {
                    val route = response.body()!!.result!!
                    Timber.d("Created teamnet route: ${route.network} for tunnel $tunnelId")
                    Resource.Success(route)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to create teamnet route"
                    Resource.Error(errorMsg)
                }
            }
        }

    /**
     * 删除私有网络路由
     */
    suspend fun deleteTeamnetRoute(account: Account, routeId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteTeamnetRoute(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    routeId = routeId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Timber.d("Deleted teamnet route: $routeId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to delete teamnet route"
                    Resource.Error(errorMsg)
                }
            }
        }

    // ==================== Hostname Routes (主机名路由) ====================

    /**
     * List hostname routes for a tunnel
     */
    suspend fun listHostnameRoutes(
        account: Account,
        tunnelId: String? = null
    ): Resource<List<HostnameRoute>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listHostnameRoutes(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tunnelId = tunnelId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val routes = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${routes.size} hostname routes")
                    Resource.Success(routes)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list hostname routes"
                    Resource.Error(errorMsg)
                }
            }
        }

    /**
     * Create a hostname route
     */
    suspend fun createHostnameRoute(
        account: Account,
        hostname: String,
        tunnelId: String,
        comment: String? = null
    ): Resource<HostnameRoute> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val request = CreateHostnameRouteRequest(
                    hostname = hostname,
                    tunnelId = tunnelId,
                    comment = comment ?: ""
                )
                val response = api.createHostnameRoute(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    request = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val route = response.body()!!.result!!
                    Timber.d("Created hostname route: ${route.hostname}")
                    Resource.Success(route)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to create hostname route"
                    Resource.Error(errorMsg)
                }
            }
        }

    /**
     * Delete a hostname route
     */
    suspend fun deleteHostnameRoute(account: Account, routeId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteHostnameRoute(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    routeId = routeId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Timber.d("Deleted hostname route: $routeId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to delete hostname route"
                    Resource.Error(errorMsg)
                }
            }
        }

    // ==================== Service Tokens ====================
    
    /**
     * List all service tokens
     */
    suspend fun listServiceTokens(account: Account): Resource<List<ServiceToken>> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listServiceTokens(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val tokens = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${tokens.size} service tokens")
                    Resource.Success(tokens)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to list service tokens"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Get a single service token
     */
    suspend fun getServiceToken(account: Account, tokenId: String): Resource<ServiceToken> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.getServiceToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tokenId = tokenId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val serviceToken = response.body()!!.result!!
                    Timber.d("Loaded service token: ${serviceToken.name}")
                    Resource.Success(serviceToken)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to get service token"
                    Resource.Error(errorMsg)
                }
            }
        }

    /**
     * Create a service token
     */
    suspend fun createServiceToken(
        account: Account,
        request: ServiceTokenRequest
    ): Resource<ServiceToken> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.createServiceToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    request = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val serviceToken = response.body()!!.result!!
                    Timber.d("Created service token: ${serviceToken.name}")
                    Resource.Success(serviceToken)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to create service token"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Update a service token
     */
    suspend fun updateServiceToken(
        account: Account,
        tokenId: String,
        request: ServiceTokenRequest
    ): Resource<ServiceToken> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.updateServiceToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tokenId = tokenId,
                    request = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val serviceToken = response.body()!!.result!!
                    Timber.d("Updated service token: ${serviceToken.name}")
                    Resource.Success(serviceToken)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to update service token"
                    Resource.Error(errorMsg)
                }
            }
        }
    
    /**
     * Delete a service token
     */
    suspend fun deleteServiceToken(account: Account, tokenId: String): Resource<Unit> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.deleteServiceToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tokenId = tokenId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted service token: $tokenId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to delete service token"
                    Resource.Error(errorMsg)
                }
            }
        }

    /**
     * Refresh a service token's expiration (extends by the token's configured duration)
     */
    suspend fun refreshServiceToken(account: Account, tokenId: String): Resource<ServiceToken> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.refreshServiceToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tokenId = tokenId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val serviceToken = response.body()!!.result!!
                    Timber.d("Refreshed service token: ${serviceToken.name}")
                    Resource.Success(serviceToken)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to refresh service token"
                    Resource.Error(errorMsg)
                }
            }
        }

    /**
     * Rotate a service token secret.
     * [previousSecretExpiresAt] RFC3339 timestamp; null revokes the old secret immediately.
     */
    suspend fun rotateServiceToken(
        account: Account,
        tokenId: String,
        previousSecretExpiresAt: String?
    ): Resource<ServiceToken> =
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.rotateServiceToken(
                    token = AuthHelper.getBearerToken(account),
                    email = AuthHelper.getEmail(account),
                    apiKey = AuthHelper.getGlobalApiKey(account),
                    accountId = account.accountId,
                    tokenId = tokenId,
                    request = ServiceTokenRotateRequest(previousClientSecretExpiresAt = previousSecretExpiresAt)
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val serviceToken = response.body()!!.result!!
                    Timber.d("Rotated service token secret: ${serviceToken.name}")
                    Resource.Success(serviceToken)
                } else {
                    val errorMsg = resolveApiError(response.body()?.errors?.firstOrNull()?.message, response)
                        ?: "Failed to rotate service token"
                    Resource.Error(errorMsg)
                }
            }
        }
}
