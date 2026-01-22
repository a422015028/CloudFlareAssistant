package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val apps = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${apps.size} Access applications")
                    Resource.Success(apps)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    appId = appId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val app = response.body()!!.result!!
                    Timber.d("Loaded Access application: ${app.name}")
                    Resource.Success(app)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    application = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val app = response.body()!!.result!!
                    Timber.d("Created Access application: ${app.name}")
                    Resource.Success(app)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    appId = appId,
                    application = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val app = response.body()!!.result!!
                    Timber.d("Updated Access application: ${app.name}")
                    Resource.Success(app)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    appId = appId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted Access application: $appId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policies = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${policies.size} Access policies")
                    Resource.Success(policies)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    appId = appId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policies = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${policies.size} policies for app $appId")
                    Resource.Success(policies)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    appId = appId,
                    policy = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policy = response.body()!!.result!!
                    Timber.d("Created policy: ${policy.name}")
                    Resource.Success(policy)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
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
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    appId = appId,
                    policyId = policyId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted policy: $policyId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val groups = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${groups.size} Access groups")
                    Resource.Success(groups)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    groupId = groupId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val group = response.body()!!.result!!
                    Timber.d("Loaded Access group: ${group.name}")
                    Resource.Success(group)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    group = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val group = response.body()!!.result!!
                    Timber.d("Created Access group: ${group.name}")
                    Resource.Success(group)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    groupId = groupId,
                    group = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val group = response.body()!!.result!!
                    Timber.d("Updated Access group: ${group.name}")
                    Resource.Success(group)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    groupId = groupId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted Access group: $groupId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val rules = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${rules.size} Gateway rules")
                    Resource.Success(rules)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    rule = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val rule = response.body()!!.result!!
                    Timber.d("Created Gateway rule: ${rule.name}")
                    Resource.Success(rule)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    ruleId = ruleId,
                    rule = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val rule = response.body()!!.result!!
                    Timber.d("Updated Gateway rule: ${rule.name}")
                    Resource.Success(rule)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    ruleId = ruleId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted Gateway rule: $ruleId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val lists = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${lists.size} Gateway lists")
                    Resource.Success(lists)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    listId = listId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val list = response.body()!!.result!!
                    Timber.d("Loaded Gateway list: ${list.name}")
                    Resource.Success(list)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
                        ?: "Failed to get list"
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    list = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val list = response.body()!!.result!!
                    Timber.d("Created Gateway list: ${list.name}")
                    Resource.Success(list)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    listId = listId,
                    list = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val list = response.body()!!.result!!
                    Timber.d("Updated Gateway list: ${list.name}")
                    Resource.Success(list)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    listId = listId,
                    patch = patch
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val list = response.body()!!.result!!
                    Timber.d("Patched Gateway list: ${list.name}")
                    Resource.Success(list)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    listId = listId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted Gateway list: $listId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val locations = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${locations.size} Gateway locations")
                    Resource.Success(locations)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    locationId = locationId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val location = response.body()!!.result!!
                    Timber.d("Loaded Gateway location: ${location.name}")
                    Resource.Success(location)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    location = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val location = response.body()!!.result!!
                    Timber.d("Created Gateway location: ${location.name}")
                    Resource.Success(location)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    locationId = locationId,
                    location = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val location = response.body()!!.result!!
                    Timber.d("Updated Gateway location: ${location.name}")
                    Resource.Success(location)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    locationId = locationId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted Gateway location: $locationId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
                        ?: "Failed to delete location"
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val devices = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${devices.size} devices")
                    Resource.Success(devices)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    deviceId = deviceId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val device = response.body()!!.result!!
                    Timber.d("Loaded device: ${device.name}")
                    Resource.Success(device)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    deviceId = deviceId
                )
                if (response.isSuccessful) {
                    Timber.d("Revoked device: $deviceId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
                        ?: "Failed to revoke device"
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policies = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${policies.size} device policies")
                    Resource.Success(policies)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policy = response.body()!!.result!!
                    Timber.d("Loaded default device policy")
                    Resource.Success(policy)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    policy = update
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policy = response.body()!!.result!!
                    Timber.d("Updated default device policy")
                    Resource.Success(policy)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    policy = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policy = response.body()!!.result!!
                    Timber.d("Created device policy: ${policy.name}")
                    Resource.Success(policy)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    policyId = policyId,
                    policy = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val policy = response.body()!!.result!!
                    Timber.d("Updated device policy: ${policy.name}")
                    Resource.Success(policy)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
                        ?: "Failed to update policy"
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    policyId = policyId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    Timber.d("Deleted device policy: $policyId")
                    Resource.Success(true)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val tunnels = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${tunnels.size} Cloudflare Tunnels")
                    Resource.Success(tunnels)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    tunnelId = tunnelId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val tunnel = response.body()!!.result!!
                    Timber.d("Loaded tunnel: ${tunnel.name}")
                    Resource.Success(tunnel)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                val response = api.createCloudflaredTunnel(
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    tunnel = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val tunnel = response.body()!!.result!!
                    Timber.d("Created tunnel: ${tunnel.name}")
                    Resource.Success(tunnel)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    tunnelId = tunnelId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted tunnel: $tunnelId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    tunnelId = tunnelId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val connections = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${connections.size} connections for tunnel $tunnelId")
                    Resource.Success(connections)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    tunnelId = tunnelId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val config = response.body()!!.result!!
                    Timber.d("Loaded configuration for tunnel $tunnelId")
                    Resource.Success(config)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    tunnelId = tunnelId,
                    config = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val config = response.body()!!.result!!
                    Timber.d("Updated configuration for tunnel $tunnelId")
                    Resource.Success(config)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
                        ?: "Failed to update configuration"
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val tokens = response.body()!!.result ?: emptyList()
                    Timber.d("Loaded ${tokens.size} service tokens")
                    Resource.Success(tokens)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
                        ?: "Failed to list service tokens"
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    request = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val serviceToken = response.body()!!.result!!
                    Timber.d("Created service token: ${serviceToken.name}")
                    Resource.Success(serviceToken)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    tokenId = tokenId,
                    request = request
                )
                if (response.isSuccessful && response.body()?.success == true) {
                    val serviceToken = response.body()!!.result!!
                    Timber.d("Updated service token: ${serviceToken.name}")
                    Resource.Success(serviceToken)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
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
                    token = "Bearer ${account.token}",
                    accountId = account.accountId,
                    tokenId = tokenId
                )
                if (response.isSuccessful) {
                    Timber.d("Deleted service token: $tokenId")
                    Resource.Success(Unit)
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message
                        ?: "Failed to delete service token"
                    Resource.Error(errorMsg)
                }
            }
        }
}
