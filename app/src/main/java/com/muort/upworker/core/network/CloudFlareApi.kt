package com.muort.upworker.core.network

import com.muort.upworker.core.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * Cloudflare API service interface
 * API Documentation: https://developers.cloudflare.com/api/
 *
 * 认证方式支持：
 * 1. API Token: 使用 Authorization header (Bearer token)
 * 2. Global API Key: 使用 X-Auth-Email 和 X-Auth-Key headers
 *
 * 注意：所有方法的认证参数均为 nullable，当使用 Global API Key 时，
 * Authorization 应为 null，email 和 apiKey 应为实际值；
 * 当使用 API Token 时，email 和 apiKey 应为 null。
 */
interface CloudFlareApi {
        /**
         * 更新自定义域名
         * PATCH /accounts/{account_id}/workers/domains/{domain_id}
         */
        @PATCH("accounts/{account_id}/workers/domains/{domain_id}")
        suspend fun updateCustomDomain(
            @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
            @Path("account_id") accountId: String,
            @Path("domain_id") domainId: String,
            @Body request: CustomDomainRequest
        ): Response<CloudFlareResponse<CustomDomain>>

    // ==================== Zones ====================

    /**
     * List all zones for the account
     * https://developers.cloudflare.com/api/operations/zones-get
     */
    @GET("zones")
    suspend fun listZones(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1
    ): Response<CloudFlareResponse<List<ZoneInfo>>>

    /** 新建 Zone（添加域名，full setup）。响应含 CF 分配的 name_servers。 */
    @POST("zones")
    suspend fun createZone(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Body request: CreateZoneRequest
    ): Response<CloudFlareResponse<ZoneInfo>>
    
    // ==================== Accounts ====================
    
    /**
     * List all accounts
     * https://developers.cloudflare.com/api/operations/accounts-list-accounts
     */
    @GET("accounts")
    suspend fun listAccounts(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1
    ): Response<CloudFlareResponse<List<AccountInfo>>>
    
    // ==================== Workers ====================
    
    /**
     * Upload Worker Script using multipart/form-data (Recommended)
     * Supports metadata and module files
     * https://developers.cloudflare.com/api/operations/worker-script-upload-worker-module
     */
    @Multipart
    @PUT("accounts/{account_id}/workers/scripts/{script_name}")
    suspend fun uploadWorkerScriptMultipart(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Part("metadata") metadata: RequestBody,
        @Part script: MultipartBody.Part
    ): Response<CloudFlareResponse<WorkerScript>>
    
    /**
     * Upload Worker Script content only (without touching config/metadata)
     * https://developers.cloudflare.com/api/operations/worker-script-put-content
     */
    @PUT("accounts/{account_id}/workers/scripts/{script_name}/content")
    @Headers("Content-Type: application/javascript")
    suspend fun uploadWorkerScriptContent(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Body script: RequestBody
    ): Response<CloudFlareResponse<WorkerScript>>
    
    /**
     * Upload Worker Script (Legacy/Simple method)
     * Kept for backward compatibility
     */
    @PUT("accounts/{account_id}/workers/scripts/{script_name}")
    suspend fun uploadWorkerScript(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Body script: RequestBody
    ): Response<CloudFlareResponse<WorkerScript>>
    
    @GET("accounts/{account_id}/workers/scripts")
    suspend fun listWorkerScripts(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<WorkerScript>>>
    
    @GET("accounts/{account_id}/workers/scripts/{script_name}")
    suspend fun getWorkerScript(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String
    ): Response<ResponseBody>
    
    /**
     * Get Worker Script settings (includes bindings and other configuration)
     * https://developers.cloudflare.com/api/operations/worker-script-get-settings
     */
    @GET("accounts/{account_id}/workers/scripts/{script_name}/settings")
    suspend fun getWorkerSettings(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String
    ): Response<CloudFlareResponse<WorkerScript>>
    
    @DELETE("accounts/{account_id}/workers/scripts/{script_name}")
    suspend fun deleteWorkerScript(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String
    ): Response<CloudFlareResponse<Unit>>
    
    /**
     * Update Worker Script settings (bindings, etc.) without uploading script content
     * https://developers.cloudflare.com/api/operations/worker-script-update-settings
     */
    @Multipart
    @PATCH("accounts/{account_id}/workers/scripts/{script_name}/settings")
    suspend fun updateWorkerSettings(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Part("settings") settings: RequestBody
    ): Response<CloudFlareResponse<WorkerScript>>

    /**
     * Bulk update secrets (create/update/delete in a single request)
     * https://developers.cloudflare.com/api/resources/workers/subresources/scripts/subresources/secrets/methods/bulk_update/
     * - Set secret object to create/update
     * - Set null to delete
     * - Omit to leave unchanged
     */
    @Headers("Content-Type: application/json")
    @PATCH("accounts/{account_id}/workers/scripts/{script_name}/secrets-bulk")
    suspend fun updateSecretsBulk(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Body body: RequestBody
    ): Response<CloudFlareResponse<Unit>>

    /**
     * List Worker Script versions (beta)
     * https://developers.cloudflare.com/api/resources/workers/subresources/beta/subresources/workers/subresources/versions/methods/list
     */
    @Headers("Accept: application/json")
    @GET("accounts/{account_id}/workers/scripts/{script_name}/versions")
    suspend fun listWorkerVersions(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Query("per_page") perPage: Int = 50
    ): Response<CloudFlareResponse<WorkerVersionsResult>>

    /**
     * Get a specific Worker Script version (beta)
     * https://developers.cloudflare.com/api/resources/workers/subresources/beta/subresources/workers/subresources/versions/methods/get
     */
    @Headers("Accept: application/json")
    @GET("accounts/{account_id}/workers/scripts/{script_name}/versions/{version_id}")
    suspend fun getWorkerVersion(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Path("version_id") versionId: String
    ): Response<CloudFlareResponse<WorkerVersion>>

    /**
     * Deploy a Worker Script version (rollback) (beta)
     */
    @Headers("Accept: application/json")
    @POST("accounts/{account_id}/workers/scripts/{script_name}/versions/{version_id}/deploy")
    suspend fun deployWorkerVersion(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Path("version_id") versionId: String
    ): Response<CloudFlareResponse<WorkerVersion>>

    /**
     * Delete a Worker Script version (beta)
     */
    @Headers("Accept: application/json")
    @DELETE("accounts/{account_id}/workers/workers/{worker_id}/versions/{version_id}")
    suspend fun deleteWorkerVersion(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("worker_id") workerId: String,
        @Path("version_id") versionId: String
    ): Response<CloudFlareResponse<Void>>

    /**
     * List Worker Script deployments
     * GET /accounts/{account_id}/workers/scripts/{script_name}/deployments
     */
    @Headers("Accept: application/json")
    @GET("accounts/{account_id}/workers/scripts/{script_name}/deployments")
    suspend fun listWorkerDeployments(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String
    ): Response<CloudFlareResponse<List<WorkerDeployment>>>

    /**
     * Get a specific Worker Script deployment
     * GET /accounts/{account_id}/workers/scripts/{script_name}/deployments/{deployment_id}
     */
    @Headers("Accept: application/json")
    @GET("accounts/{account_id}/workers/scripts/{script_name}/deployments/{deployment_id}")
    suspend fun getWorkerDeployment(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Path("deployment_id") deploymentId: String
    ): Response<CloudFlareResponse<WorkerDeployment>>

    // ==================== Workers Tails (Real-time Logs) ====================
    
    @Headers("Accept: application/json")
    @GET("accounts/{account_id}/workers/scripts/{script_name}/tails")
    suspend fun listTails(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String
    ): Response<CloudFlareResponse<List<TailResult>>>
    
    @Headers("Accept: application/json")
    @POST("accounts/{account_id}/workers/scripts/{script_name}/tails")
    suspend fun createTail(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Body body: CreateTailRequest
    ): Response<CloudFlareResponse<TailResult>>
    
    @Headers("Accept: application/json")
    @DELETE("accounts/{account_id}/workers/scripts/{script_name}/tails/{id}")
    suspend fun deleteTail(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Path("id") id: String
    ): Response<CloudFlareResponse<Void>>
    
    // ==================== Workers Schedules ====================
    
    @Headers("Accept: application/json")
    @GET("accounts/{account_id}/workers/scripts/{script_name}/schedules")
    suspend fun listSchedules(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String
    ): Response<CloudFlareResponse<SchedulesResponse>>
    
    @Headers("Accept: application/json")
    @PUT("accounts/{account_id}/workers/scripts/{script_name}/schedules")
    suspend fun updateSchedules(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Body schedules: List<ScheduleRequest>
    ): Response<CloudFlareResponse<SchedulesResponse>>
    
    // ==================== Routes ====================
    
    @GET("zones/{zone_id}/workers/routes")
    suspend fun listRoutes(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String
    ): Response<CloudFlareResponse<List<Route>>>
    
    @POST("zones/{zone_id}/workers/routes")
    suspend fun createRoute(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Body route: RouteRequest
    ): Response<CloudFlareResponse<Route>>
    
    @PUT("zones/{zone_id}/workers/routes/{route_id}")
    suspend fun updateRoute(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("route_id") routeId: String,
        @Body route: RouteRequest
    ): Response<CloudFlareResponse<Route>>
    
    @DELETE("zones/{zone_id}/workers/routes/{route_id}")
    suspend fun deleteRoute(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("route_id") routeId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Custom Domains ====================
    
    @GET("accounts/{account_id}/workers/domains")
    suspend fun listCustomDomains(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<CustomDomain>>>
    
    @PUT("accounts/{account_id}/workers/domains")
    suspend fun addCustomDomain(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body request: CustomDomainRequest
    ): Response<CloudFlareResponse<CustomDomain>>
    
    @DELETE("accounts/{account_id}/workers/domains/{domain_id}")
    suspend fun deleteCustomDomain(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("domain_id") domainId: String
    ): Response<Unit>
    
    // ==================== DNS ====================
    
    @GET("zones/{zone_id}/dns_records")
    suspend fun listDnsRecords(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Query("type") type: String? = null,
        @Query("name") name: String? = null
    ): Response<CloudFlareResponse<List<DnsRecord>>>
    
    @POST("zones/{zone_id}/dns_records")
    suspend fun createDnsRecord(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Body record: DnsRecordRequest
    ): Response<CloudFlareResponse<DnsRecord>>
    
    @PUT("zones/{zone_id}/dns_records/{record_id}")
    suspend fun updateDnsRecord(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("record_id") recordId: String,
        @Body record: DnsRecordRequest
    ): Response<CloudFlareResponse<DnsRecord>>
    
    @DELETE("zones/{zone_id}/dns_records/{record_id}")
    suspend fun deleteDnsRecord(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("record_id") recordId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== KV ====================
    
    @GET("accounts/{account_id}/storage/kv/namespaces")
    suspend fun listKvNamespaces(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<KvNamespace>>>
    
    @POST("accounts/{account_id}/storage/kv/namespaces")
    suspend fun createKvNamespace(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body namespace: KvNamespaceRequest
    ): Response<CloudFlareResponse<KvNamespace>>
    
    @DELETE("accounts/{account_id}/storage/kv/namespaces/{namespace_id}")
    suspend fun deleteKvNamespace(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("namespace_id") namespaceId: String
    ): Response<CloudFlareResponse<Unit>>
    
    @GET("accounts/{account_id}/storage/kv/namespaces/{namespace_id}/keys")
    suspend fun listKvKeys(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("namespace_id") namespaceId: String
    ): Response<CloudFlareResponse<List<KvKey>>>
    
    @GET("accounts/{account_id}/storage/kv/namespaces/{namespace_id}/values/{key_name}")
    suspend fun getKvValue(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("namespace_id") namespaceId: String,
        @Path("key_name") keyName: String
    ): Response<ResponseBody>
    
    @PUT("accounts/{account_id}/storage/kv/namespaces/{namespace_id}/values/{key_name}")
    suspend fun putKvValue(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("namespace_id") namespaceId: String,
        @Path("key_name") keyName: String,
        @Body value: RequestBody
    ): Response<CloudFlareResponse<Unit>>
    
    @DELETE("accounts/{account_id}/storage/kv/namespaces/{namespace_id}/values/{key_name}")
    suspend fun deleteKvValue(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("namespace_id") namespaceId: String,
        @Path("key_name") keyName: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Pages ====================
    
    @GET("accounts/{account_id}/pages/projects")
    suspend fun listPagesProjects(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<PagesProject>>>
    
    @POST("accounts/{account_id}/pages/projects")
    suspend fun createPagesProject(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body project: PagesProjectRequest
    ): Response<CloudFlareResponse<PagesProject>>
    
    @DELETE("accounts/{account_id}/pages/projects/{project_name}")
    suspend fun deletePagesProject(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String
    ): Response<CloudFlareResponse<Unit>>
    
    @GET("accounts/{account_id}/pages/projects/{project_name}")
    suspend fun getPagesProject(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String
    ): Response<CloudFlareResponse<PagesProjectDetail>>
    
    @GET("accounts/{account_id}/pages/projects/{project_name}/deployments")
    suspend fun listPagesDeployments(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String
    ): Response<CloudFlareResponse<List<PagesDeployment>>>
    
    @POST("accounts/{account_id}/pages/projects/{project_name}/deployments/{deployment_id}/retry")
    suspend fun retryPagesDeployment(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Path("deployment_id") deploymentId: String
    ): Response<CloudFlareResponse<PagesDeployment>>
    
    @DELETE("accounts/{account_id}/pages/projects/{project_name}/deployments/{deployment_id}")
    suspend fun deletePagesDeployment(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Path("deployment_id") deploymentId: String
    ): Response<CloudFlareResponse<Unit>>
    
    @POST("accounts/{account_id}/pages/projects/{project_name}/deployments/{deployment_id}/rollback")
    suspend fun rollbackPagesDeployment(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Path("deployment_id") deploymentId: String
    ): Response<CloudFlareResponse<PagesDeployment>>
    
    @GET("accounts/{account_id}/pages/projects/{project_name}/deployments/{deployment_id}/history/logs")
    suspend fun getPagesDeploymentLogs(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Path("deployment_id") deploymentId: String
    ): Response<CloudFlareResponse<PagesDeploymentLogs>>
    
    /**
     * Update Pages project configuration (environment variables, bindings, etc.)
     * https://developers.cloudflare.com/api/operations/pages-project-update-project
     */
    @PATCH("accounts/{account_id}/pages/projects/{project_name}")
    suspend fun updatePagesProject(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Body updateRequest: PagesProjectUpdateRequest
    ): Response<CloudFlareResponse<PagesProjectDetail>>
    
    // 1. 获取上传资产专用的临时 JWT Token
    @GET("accounts/{account_id}/pages/projects/{project_name}/upload-token")
    suspend fun getPagesUploadToken(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String
    ): Response<CloudFlareResponse<PagesTokenPayload>>

    // 2. 将文件转换为 Base64 独立上载到 Cloudflare 的资产原子库中（注意：此处使用专属的全局全路径 URL）
    @POST("https://api.cloudflare.com/client/v4/pages/assets/upload")
    suspend fun uploadPagesAssets(
        @Header("Authorization") jwtToken: String, // 格式必须为: "Bearer <返回的jwt>"
        @Body assets: List<PagesAssetPayload>
    ): Response<ResponseBody>

    // 2.b 更新资产哈希列表（upsert-hashes），即使没有静态资产也需要调用以初始化部署会话
    @POST("https://api.cloudflare.com/client/v4/pages/assets/upsert-hashes")
    suspend fun upsertPagesAssetHashes(
        @Header("Authorization") jwtToken: String,
        @Body body: PagesUpsertHashesPayload
    ): Response<ResponseBody>

    // 3. 完美的最终盖章接口：只提交 Manifest 清单映射，不携带任何实体文件
    @Multipart
    @POST("accounts/{account_id}/pages/projects/{project_name}/deployments")
    suspend fun createPagesDeploymentManifestOnly(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Part("manifest") manifest: RequestBody
    ): Response<CloudFlareResponse<PagesDeployment>>

    // 3.b 支持 _worker.bundle (Advanced Mode) 的部署：manifest + _worker.bundle
    @Multipart
    @POST("accounts/{account_id}/pages/projects/{project_name}/deployments")
    suspend fun createPagesDeploymentWithWorker(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Part("manifest") manifest: RequestBody,
        @Part workerBundle: MultipartBody.Part
    ): Response<CloudFlareResponse<PagesDeployment>>
    
    // ==================== Pages Domains ====================
    
    /**
     * Get custom domains for Pages project
     * https://developers.cloudflare.com/api/operations/pages-domains-get-domains
     */
    @GET("accounts/{account_id}/pages/projects/{project_name}/domains")
    suspend fun listPagesDomains(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String
    ): Response<CloudFlareResponse<List<PagesDomain>>>
    
    /**
     * Add a custom domain to Pages project
     * https://developers.cloudflare.com/api/operations/pages-domains-add-domain
     */
    @POST("accounts/{account_id}/pages/projects/{project_name}/domains")
    suspend fun addPagesDomain(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Body request: PagesDomainRequest
    ): Response<CloudFlareResponse<PagesDomain>>
    
    /**
     * Delete a custom domain from Pages project
     * https://developers.cloudflare.com/api/operations/pages-domains-delete-domain
     */
    @DELETE("accounts/{account_id}/pages/projects/{project_name}/domains/{domain_name}")
    suspend fun deletePagesDomain(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Path("domain_name") domainName: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== R2 ====================
    
    @GET("accounts/{account_id}/r2/buckets")
    suspend fun listR2Buckets(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<R2BucketsResponse>>
    
    @POST("accounts/{account_id}/r2/buckets")
    suspend fun createR2Bucket(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body bucket: R2BucketRequest
    ): Response<CloudFlareResponse<R2Bucket>>
    
    @DELETE("accounts/{account_id}/r2/buckets/{bucket_name}")
    suspend fun deleteR2Bucket(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("bucket_name") bucketName: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== D1 ====================
    
    @GET("accounts/{account_id}/d1/database")
    suspend fun listD1Databases(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<D1Database>>>
    
    @POST("accounts/{account_id}/d1/database")
    suspend fun createD1Database(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body database: D1DatabaseRequest
    ): Response<CloudFlareResponse<D1Database>>
    
    @DELETE("accounts/{account_id}/d1/database/{database_id}")
    suspend fun deleteD1Database(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("database_id") databaseId: String
    ): Response<CloudFlareResponse<Unit>>

    // ==================== D1 Table & SQL ====================

    /**
     * 列出数据库下所有表
     * GET /accounts/{account_id}/d1/database/{database_id}/tables
     */
    @GET("accounts/{account_id}/d1/database/{database_id}/tables")
    suspend fun listD1Tables(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("database_id") databaseId: String
    ): Response<CloudFlareResponse<List<D1Table>>>

    /**
     * 执行 SQL 语句（支持查询/增删改/DDL）
     * POST /accounts/{account_id}/d1/database/{database_id}/query
     * body: { "sql": "...", "params": [...] }
     */
    @POST("accounts/{account_id}/d1/database/{database_id}/query")
    suspend fun executeD1Query(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("database_id") databaseId: String,
        @Body query: D1QueryRequest
    ): Response<Map<String, @JvmSuppressWildcards Any>>

    /**
     * 导出数据库
     * GET /accounts/{account_id}/d1/database/{database_id}/backup
     */
    @GET("accounts/{account_id}/d1/database/{database_id}/backup")
    suspend fun exportD1Database(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("database_id") databaseId: String
    ): Response<ResponseBody>

    /**
     * 导入数据库
     * POST /accounts/{account_id}/d1/database/{database_id}/restore
     * body: Multipart sqlite file
     */
    @Multipart
    @POST("accounts/{account_id}/d1/database/{database_id}/restore")
    suspend fun importD1Database(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("database_id") databaseId: String,
        @Part file: MultipartBody.Part
    ): Response<CloudFlareResponse<Unit>>

    // ==================== R2 Custom Domains ====================
    
    @GET("accounts/{account_id}/r2/buckets/{bucket_name}/custom_domains")
    suspend fun listR2CustomDomains(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("bucket_name") bucketName: String
    ): Response<CloudFlareResponse<R2CustomDomainsResponse>>
    
    @POST("accounts/{account_id}/r2/buckets/{bucket_name}/domains/custom")
    suspend fun createR2CustomDomain(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("bucket_name") bucketName: String,
        @Body request: R2CustomDomainRequest
    ): Response<CloudFlareResponse<R2CustomDomain>>
    
    @DELETE("accounts/{account_id}/r2/buckets/{bucket_name}/custom_domains/{domain}")
    suspend fun deleteR2CustomDomain(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("bucket_name") bucketName: String,
        @Path("domain") domain: String
    ): Response<CloudFlareResponse<Unit>>
    
    // Note: R2 object operations use S3 API, not Cloudflare REST API
    // ListObjects must be done through S3-compatible endpoint with AWS signatures
    // Use direct OkHttp calls with S3 SDK instead
    
    // Note: R2 object upload/download/delete must use S3 API
    // These operations require AWS S3 signature v4 authentication
    // Use S3 SDK or manual signature generation instead
    
    // ==================== Analytics ====================
    
    /**
     * GraphQL Analytics API
     * https://developers.cloudflare.com/analytics/graphql-api/
     */
    @POST("graphql")
    suspend fun queryAnalytics(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Body request: AnalyticsGraphQLRequest
    ): Response<AnalyticsGraphQLResponse>
    
    // ==================== Zero Trust - Access Applications ====================
    
    /**
     * List Access Applications
     * https://developers.cloudflare.com/api/resources/zero_trust/subresources/access/subresources/applications/
     */
    @GET("accounts/{account_id}/access/apps")
    suspend fun listAccessApplications(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1
    ): Response<CloudFlareResponse<List<AccessApplication>>>
    
    /**
     * Get Access Application
     */
    @GET("accounts/{account_id}/access/apps/{app_id}")
    suspend fun getAccessApplication(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("app_id") appId: String
    ): Response<CloudFlareResponse<AccessApplication>>
    
    /**
     * Create Access Application
     */
    @POST("accounts/{account_id}/access/apps")
    suspend fun createAccessApplication(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body application: AccessApplicationRequest
    ): Response<CloudFlareResponse<AccessApplication>>
    
    /**
     * Update Access Application
     */
    @PUT("accounts/{account_id}/access/apps/{app_id}")
    suspend fun updateAccessApplication(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("app_id") appId: String,
        @Body application: AccessApplicationRequest
    ): Response<CloudFlareResponse<AccessApplication>>
    
    /**
     * Delete Access Application
     */
    @DELETE("accounts/{account_id}/access/apps/{app_id}")
    suspend fun deleteAccessApplication(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("app_id") appId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Access Policies ====================
    
    /**
     * List Access Policies
     */
    @GET("accounts/{account_id}/access/policies")
    suspend fun listAccessPolicies(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<AccessPolicy>>>
    
    /**
     * Create Access Policy
     */
    @POST("accounts/{account_id}/access/policies")
    suspend fun createAccessPolicy(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body policy: AccessPolicyRequest
    ): Response<CloudFlareResponse<AccessPolicy>>
    
    /**
     * List Application Policies
     */
    @GET("accounts/{account_id}/access/apps/{app_id}/policies")
    suspend fun listAppPolicies(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("app_id") appId: String
    ): Response<CloudFlareResponse<List<AccessPolicy>>>
    
    /**
     * Create Application Policy
     */
    @POST("accounts/{account_id}/access/apps/{app_id}/policies")
    suspend fun createAppPolicy(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("app_id") appId: String,
        @Body policy: AccessPolicyRequest
    ): Response<CloudFlareResponse<AccessPolicy>>
    
    /**
     * Update Application Policy
     */
    @PUT("accounts/{account_id}/access/apps/{app_id}/policies/{policy_id}")
    suspend fun updateAppPolicy(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("app_id") appId: String,
        @Path("policy_id") policyId: String,
        @Body policy: AccessPolicyRequest
    ): Response<CloudFlareResponse<AccessPolicy>>
    
    /**
     * Delete Application Policy
     */
    @DELETE("accounts/{account_id}/access/apps/{app_id}/policies/{policy_id}")
    suspend fun deleteAppPolicy(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("app_id") appId: String,
        @Path("policy_id") policyId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Access Groups ====================
    
    /**
     * List Access Groups
     */
    @GET("accounts/{account_id}/access/groups")
    suspend fun listAccessGroups(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<AccessGroup>>>
    
    /**
     * Get Access Group
     */
    @GET("accounts/{account_id}/access/groups/{group_id}")
    suspend fun getAccessGroup(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("group_id") groupId: String
    ): Response<CloudFlareResponse<AccessGroup>>
    
    /**
     * Create Access Group
     */
    @POST("accounts/{account_id}/access/groups")
    suspend fun createAccessGroup(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body group: AccessGroupRequest
    ): Response<CloudFlareResponse<AccessGroup>>
    
    /**
     * Update Access Group
     */
    @PUT("accounts/{account_id}/access/groups/{group_id}")
    suspend fun updateAccessGroup(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("group_id") groupId: String,
        @Body group: AccessGroupRequest
    ): Response<CloudFlareResponse<AccessGroup>>
    
    /**
     * Delete Access Group
     */
    @DELETE("accounts/{account_id}/access/groups/{group_id}")
    suspend fun deleteAccessGroup(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("group_id") groupId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Gateway Rules ====================
    
    /**
     * List Gateway Rules
     */
    @GET("accounts/{account_id}/gateway/rules")
    suspend fun listGatewayRules(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<GatewayRule>>>
    
    /**
     * Create Gateway Rule
     */
    @POST("accounts/{account_id}/gateway/rules")
    suspend fun createGatewayRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body rule: GatewayRuleRequest
    ): Response<CloudFlareResponse<GatewayRule>>
    
    /**
     * Update Gateway Rule
     */
    @PUT("accounts/{account_id}/gateway/rules/{rule_id}")
    suspend fun updateGatewayRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("rule_id") ruleId: String,
        @Body rule: GatewayRuleRequest
    ): Response<CloudFlareResponse<GatewayRule>>
    
    /**
     * Delete Gateway Rule
     */
    @DELETE("accounts/{account_id}/gateway/rules/{rule_id}")
    suspend fun deleteGatewayRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("rule_id") ruleId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Gateway Lists ====================
    
    /**
     * List Gateway Lists
     */
    @GET("accounts/{account_id}/gateway/lists")
    suspend fun listGatewayLists(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<GatewayList>>>
    
    /**
     * Get Gateway List
     */
    @GET("accounts/{account_id}/gateway/lists/{list_id}")
    suspend fun getGatewayList(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("list_id") listId: String
    ): Response<CloudFlareResponse<GatewayList>>

    /**
     * Get Gateway List Items
     */
    @GET("accounts/{account_id}/gateway/lists/{list_id}/items")
    suspend fun listGatewayListItems(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("list_id") listId: String,
        @Query("per_page") perPage: Int = 10000
    ): Response<CloudFlareResponse<List<GatewayListItem>>>
    
    /**
     * Create Gateway List
     */
    @POST("accounts/{account_id}/gateway/lists")
    suspend fun createGatewayList(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body list: GatewayListRequest
    ): Response<CloudFlareResponse<GatewayList>>
    
    /**
     * Update Gateway List
     */
    @PUT("accounts/{account_id}/gateway/lists/{list_id}")
    suspend fun updateGatewayList(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("list_id") listId: String,
        @Body list: GatewayListRequest
    ): Response<CloudFlareResponse<GatewayList>>
    
    /**
     * Patch Gateway List (Append/Remove items)
     */
    @PATCH("accounts/{account_id}/gateway/lists/{list_id}")
    suspend fun patchGatewayList(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("list_id") listId: String,
        @Body patch: GatewayListPatchRequest
    ): Response<CloudFlareResponse<GatewayList>>
    
    /**
     * Delete Gateway List
     */
    @DELETE("accounts/{account_id}/gateway/lists/{list_id}")
    suspend fun deleteGatewayList(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("list_id") listId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Gateway Locations ====================
    
    /**
     * List Gateway Locations
     */
    @GET("accounts/{account_id}/gateway/locations")
    suspend fun listGatewayLocations(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<GatewayLocation>>>
    
    /**
     * Get Gateway Location
     */
    @GET("accounts/{account_id}/gateway/locations/{location_id}")
    suspend fun getGatewayLocation(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("location_id") locationId: String
    ): Response<CloudFlareResponse<GatewayLocation>>
    
    /**
     * Create Gateway Location
     */
    @POST("accounts/{account_id}/gateway/locations")
    suspend fun createGatewayLocation(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body location: GatewayLocationRequest
    ): Response<CloudFlareResponse<GatewayLocation>>
    
    /**
     * Update Gateway Location
     */
    @PUT("accounts/{account_id}/gateway/locations/{location_id}")
    suspend fun updateGatewayLocation(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("location_id") locationId: String,
        @Body location: GatewayLocationRequest
    ): Response<CloudFlareResponse<GatewayLocation>>
    
    /**
     * Delete Gateway Location
     */
    @DELETE("accounts/{account_id}/gateway/locations/{location_id}")
    suspend fun deleteGatewayLocation(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("location_id") locationId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Devices ====================
    
    /**
     * List Devices
     */
    @GET("accounts/{account_id}/devices/physical-devices")
    suspend fun listDevices(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Query("include") include: String = "last_seen_registration.policy"
    ): Response<CloudFlareResponse<List<Device>>>

    /**
     * Get Device
     */
    @GET("accounts/{account_id}/devices/physical-devices/{device_id}")
    suspend fun getDevice(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("device_id") deviceId: String,
        @Query("include") include: String = "last_seen_registration.policy"
    ): Response<CloudFlareResponse<Device>>

    /**
     * Revoke Device (POST revoke endpoint)
     */
    @POST("accounts/{account_id}/devices/physical-devices/{device_id}/revoke")
    suspend fun revokeDevice(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("device_id") deviceId: String
    ): Response<CloudFlareResponse<Unit>>

    /**
     * Delete Device
     */
    @DELETE("accounts/{account_id}/devices/physical-devices/{device_id}")
    suspend fun deleteDevice(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("device_id") deviceId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Device Policies ====================
    
    /**
     * List Device Policies
     */
    @GET("accounts/{account_id}/devices/policies")
    suspend fun listDevicePolicies(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<DeviceSettingsPolicy>>>
    
    /**
     * Get Default Device Policy
     */
    @GET("accounts/{account_id}/devices/policy")
    suspend fun getDefaultDevicePolicy(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<DeviceSettingsPolicy>>
    
    /**
     * Update Default Device Policy
     */
    @PATCH("accounts/{account_id}/devices/policy")
    suspend fun updateDefaultDevicePolicy(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body policy: DevicePolicyUpdate
    ): Response<CloudFlareResponse<DeviceSettingsPolicy>>
    
    /**
     * Create Device Settings Profile
     */
    @POST("accounts/{account_id}/devices/policy")
    suspend fun createDevicePolicy(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body policy: DeviceSettingsPolicyRequest
    ): Response<CloudFlareResponse<DeviceSettingsPolicy>>
    
    /**
     * Update Device Settings Profile
     */
    @PATCH("accounts/{account_id}/devices/policy/{policy_id}")
    suspend fun updateDevicePolicy(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("policy_id") policyId: String,
        @Body policy: DeviceSettingsPolicyRequest
    ): Response<CloudFlareResponse<DeviceSettingsPolicy>>
    
    /**
     * Delete Device Settings Profile
     */
    @DELETE("accounts/{account_id}/devices/policy/{policy_id}")
    suspend fun deleteDevicePolicy(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("policy_id") policyId: String
    ): Response<CloudFlareResponse<List<DeviceSettingsPolicy>>>
    
    /**
     * Set Split Tunnel exclude list for default policy
     */
    @PUT("accounts/{account_id}/devices/policy/exclude")
    suspend fun setDefaultSplitTunnelExclude(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body items: List<SplitTunnel>
    ): Response<CloudFlareResponse<List<SplitTunnel>>>
    
    /**
     * Set Split Tunnel include list for default policy
     */
    @PUT("accounts/{account_id}/devices/policy/include")
    suspend fun setDefaultSplitTunnelInclude(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body items: List<SplitTunnel>
    ): Response<CloudFlareResponse<List<SplitTunnel>>>
    
    /**
     * Set Split Tunnel exclude list for a device policy
     */
    @PUT("accounts/{account_id}/devices/policy/{policy_id}/exclude")
    suspend fun setSplitTunnelExclude(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("policy_id") policyId: String,
        @Body items: List<SplitTunnel>
    ): Response<CloudFlareResponse<List<SplitTunnel>>>
    
    /**
     * Set Split Tunnel include list for a device policy
     */
    @PUT("accounts/{account_id}/devices/policy/{policy_id}/include")
    suspend fun setSplitTunnelInclude(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("policy_id") policyId: String,
        @Body items: List<SplitTunnel>
    ): Response<CloudFlareResponse<List<SplitTunnel>>>
    
    // ==================== Zero Trust - Tunnels ====================
    
    /**
     * List Cloudflare Tunnels
     */
    @GET("accounts/{account_id}/cfd_tunnel")
    suspend fun listCloudflaredTunnels(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1
    ): Response<CloudFlareResponse<List<CloudflareTunnel>>>
    
    /**
     * Get Cloudflare Tunnel
     */
    @GET("accounts/{account_id}/cfd_tunnel/{tunnel_id}")
    suspend fun getCloudflaredTunnel(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("tunnel_id") tunnelId: String
    ): Response<CloudFlareResponse<CloudflareTunnel>>
    
    /**
     * Create Cloudflare Tunnel
     */
    @POST("accounts/{account_id}/cfd_tunnel")
    suspend fun createCloudflaredTunnel(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body tunnel: TunnelCreateRequest
    ): Response<CloudFlareResponse<CloudflareTunnel>>
    
    /**
     * Delete Cloudflare Tunnel
     */
    @DELETE("accounts/{account_id}/cfd_tunnel/{tunnel_id}")
    suspend fun deleteCloudflaredTunnel(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("tunnel_id") tunnelId: String
    ): Response<CloudFlareResponse<Unit>>
    
    /**
     * List Tunnel Connections
     */
    @GET("accounts/{account_id}/cfd_tunnel/{tunnel_id}/connections")
    suspend fun listTunnelConnections(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("tunnel_id") tunnelId: String
    ): Response<CloudFlareResponse<List<TunnelConnection>>>
    
    /**
     * Get Tunnel Configuration
     */
    @GET("accounts/{account_id}/cfd_tunnel/{tunnel_id}/configurations")
    suspend fun getTunnelConfiguration(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("tunnel_id") tunnelId: String
    ): Response<CloudFlareResponse<TunnelConfiguration>>
    
    /**
     * Update Tunnel Configuration
     */
    @PUT("accounts/{account_id}/cfd_tunnel/{tunnel_id}/configurations")
    suspend fun updateTunnelConfiguration(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("tunnel_id") tunnelId: String,
        @Body config: TunnelConfigurationRequest
    ): Response<CloudFlareResponse<TunnelConfiguration>>
    
    @GET("accounts/{account_id}/cfd_tunnel/{tunnel_id}/token")
    suspend fun getTunnelToken(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("tunnel_id") tunnelId: String
    ): Response<CloudFlareResponse<String>>
    
    // ==================== Zero Trust - Service Tokens ====================
    
    /**
     * List Service Tokens
     */
    @GET("accounts/{account_id}/access/service_tokens")
    suspend fun listServiceTokens(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<ServiceToken>>>
    
    /**
     * Create Service Token
     */
    @POST("accounts/{account_id}/access/service_tokens")
    suspend fun createServiceToken(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body request: ServiceTokenRequest
    ): Response<CloudFlareResponse<ServiceToken>>
    
    /**
     * Update Service Token
     */
    @PUT("accounts/{account_id}/access/service_tokens/{token_id}")
    suspend fun updateServiceToken(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("token_id") tokenId: String,
        @Body request: ServiceTokenRequest
    ): Response<CloudFlareResponse<ServiceToken>>
    
    /**
     * Delete Service Token
     */
    @DELETE("accounts/{account_id}/access/service_tokens/{token_id}")
    suspend fun deleteServiceToken(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("token_id") tokenId: String
    ): Response<CloudFlareResponse<Unit>>

    // ==================== Zone Rulesets（WAF / Cache / Rate Limit / Transform 共用 phase 入口） ====================

    /** GET /zones/{zone_id}/rulesets/phases/{phase}/entrypoint —— 取某 phase 的 entrypoint ruleset。 */
    @GET("zones/{zone_id}/rulesets/phases/{phase}/entrypoint")
    suspend fun getRulesetEntrypoint(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("phase") phase: String
    ): Response<CloudFlareResponse<WafRuleset>>

    /** PUT /zones/{zone_id}/rulesets/phases/{phase}/entrypoint —— 创建 entrypoint（首条规则时）。 */
    @PUT("zones/{zone_id}/rulesets/phases/{phase}/entrypoint")
    suspend fun createRulesetEntrypoint(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("phase") phase: String,
        @Body body: WafEntrypointUpdate
    ): Response<CloudFlareResponse<WafRuleset>>

    /** POST /zones/{zone_id}/rulesets/{ruleset_id}/rules —— 向已有规则集追加规则，返回完整 ruleset。 */
    @POST("zones/{zone_id}/rulesets/{ruleset_id}/rules")
    suspend fun addRulesetRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("ruleset_id") rulesetId: String,
        @Body rule: WafRuleCreate
    ): Response<CloudFlareResponse<WafRuleset>>

    /** PATCH /zones/{zone_id}/rulesets/{ruleset_id}/rules/{rule_id} —— 启停 / 整条更新规则，返回完整 ruleset。 */
    @PATCH("zones/{zone_id}/rulesets/{ruleset_id}/rules/{rule_id}")
    suspend fun updateRulesetRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("ruleset_id") rulesetId: String,
        @Path("rule_id") ruleId: String,
        @Body body: Any
    ): Response<CloudFlareResponse<WafRuleset>>

    /** DELETE /zones/{zone_id}/rulesets/{ruleset_id}/rules/{rule_id} —— 删除单条规则。 */
    @DELETE("zones/{zone_id}/rulesets/{ruleset_id}/rules/{rule_id}")
    suspend fun deleteRulesetRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("ruleset_id") rulesetId: String,
        @Path("rule_id") ruleId: String
    ): Response<CloudFlareResponse<Unit>>

    // ---- Cache Rules 专用（返回 CacheRuleset 以保留 action_parameters） ----

    /** GET /zones/{zone_id}/rulesets/phases/{phase}/entrypoint —— 取缓存规则 entrypoint。 */
    @GET("zones/{zone_id}/rulesets/phases/{phase}/entrypoint")
    suspend fun getCacheRulesetEntrypoint(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("phase") phase: String
    ): Response<CloudFlareResponse<CacheRuleset>>

    /** PUT /zones/{zone_id}/rulesets/phases/{phase}/entrypoint —— 创建缓存规则 entrypoint。 */
    @PUT("zones/{zone_id}/rulesets/phases/{phase}/entrypoint")
    suspend fun createCacheRulesetEntrypoint(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("phase") phase: String,
        @Body body: CacheEntrypointUpdate
    ): Response<CloudFlareResponse<CacheRuleset>>

    /** POST /zones/{zone_id}/rulesets/{ruleset_id}/rules —— 向缓存规则集追加规则。 */
    @POST("zones/{zone_id}/rulesets/{ruleset_id}/rules")
    suspend fun addCacheRulesetRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("ruleset_id") rulesetId: String,
        @Body rule: CacheRuleCreate
    ): Response<CloudFlareResponse<CacheRuleset>>

    /** PATCH /zones/{zone_id}/rulesets/{ruleset_id}/rules/{rule_id} —— 启停/更新缓存规则。 */
    @PATCH("zones/{zone_id}/rulesets/{ruleset_id}/rules/{rule_id}")
    suspend fun updateCacheRulesetRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("ruleset_id") rulesetId: String,
        @Path("rule_id") ruleId: String,
        @Body body: Any
    ): Response<CloudFlareResponse<CacheRuleset>>

    // ---- Rate Limiting 专用（返回 RateLimitRuleset 以保留 ratelimit 配置） ----

    @GET("zones/{zone_id}/rulesets/phases/{phase}/entrypoint")
    suspend fun getRateLimitRulesetEntrypoint(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("phase") phase: String
    ): Response<CloudFlareResponse<RateLimitRuleset>>

    @PUT("zones/{zone_id}/rulesets/phases/{phase}/entrypoint")
    suspend fun createRateLimitEntrypoint(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("phase") phase: String,
        @Body body: RateLimitEntrypointUpdate
    ): Response<CloudFlareResponse<RateLimitRuleset>>

    @POST("zones/{zone_id}/rulesets/{ruleset_id}/rules")
    suspend fun addRateLimitRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("ruleset_id") rulesetId: String,
        @Body rule: RateLimitRuleCreate
    ): Response<CloudFlareResponse<RateLimitRuleset>>

    @PATCH("zones/{zone_id}/rulesets/{ruleset_id}/rules/{rule_id}")
    suspend fun updateRateLimitRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("ruleset_id") rulesetId: String,
        @Path("rule_id") ruleId: String,
        @Body body: Any
    ): Response<CloudFlareResponse<RateLimitRuleset>>

    // ==================== IP 访问规则（firewall/access_rules/rules） ====================

    // ---- Transform Rules 专用（返回 TransformRuleset 以保留 action_parameters） ----

    @GET("zones/{zone_id}/rulesets/phases/{phase}/entrypoint")
    suspend fun getTransformRulesetEntrypoint(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("phase") phase: String
    ): Response<CloudFlareResponse<TransformRuleset>>

    @PUT("zones/{zone_id}/rulesets/phases/{phase}/entrypoint")
    suspend fun createTransformEntrypoint(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("phase") phase: String,
        @Body body: TransformEntrypointUpdate
    ): Response<CloudFlareResponse<TransformRuleset>>

    @POST("zones/{zone_id}/rulesets/{ruleset_id}/rules")
    suspend fun addTransformRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("ruleset_id") rulesetId: String,
        @Body body: TransformRuleCreate
    ): Response<CloudFlareResponse<TransformRuleset>>

    @PATCH("zones/{zone_id}/rulesets/{ruleset_id}/rules/{rule_id}")
    suspend fun updateTransformRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("ruleset_id") rulesetId: String,
        @Path("rule_id") ruleId: String,
        @Body body: TransformRuleCreate
    ): Response<CloudFlareResponse<TransformRuleset>>

    @DELETE("zones/{zone_id}/rulesets/{ruleset_id}/rules/{rule_id}")
    suspend fun deleteTransformRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("ruleset_id") rulesetId: String,
        @Path("rule_id") ruleId: String
    ): Response<CloudFlareResponse<Unit>>

    // ==================== IP 访问规则（account 级 firewall/access_rules/rules） ====================

    @GET("accounts/{account_id}/firewall/access_rules/rules")
    suspend fun listAccessRules(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Query("per_page") perPage: Int = 100
    ): Response<CloudFlareResponse<List<FirewallAccessRule>>>

    @POST("accounts/{account_id}/firewall/access_rules/rules")
    suspend fun createAccessRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body rule: AccessRuleCreate
    ): Response<CloudFlareResponse<FirewallAccessRule>>

    @PATCH("accounts/{account_id}/firewall/access_rules/rules/{rule_id}")
    suspend fun updateAccessRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("rule_id") ruleId: String,
        @Body update: AccessRuleUpdate
    ): Response<CloudFlareResponse<FirewallAccessRule>>

    @DELETE("accounts/{account_id}/firewall/access_rules/rules/{rule_id}")
    suspend fun deleteAccessRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("rule_id") ruleId: String
    ): Response<CloudFlareResponse<Unit>>

    // ==================== Email Routing ====================

    @GET("zones/{zone_id}/email/routing")
    suspend fun getEmailRoutingSettings(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String
    ): Response<CloudFlareResponse<EmailRoutingSettings>>

    @POST("zones/{zone_id}/email/routing/{action}")
    suspend fun setEmailRoutingEnabled(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("action") action: String,
        @Body body: Map<String, String>
    ): Response<CloudFlareResponse<Unit>>

    @GET("zones/{zone_id}/email/routing/rules")
    suspend fun listEmailRoutingRules(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String
    ): Response<CloudFlareResponse<List<EmailRoutingRule>>>

    @POST("zones/{zone_id}/email/routing/rules")
    suspend fun createEmailRoutingRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Body rule: EmailRoutingRuleInput
    ): Response<CloudFlareResponse<EmailRoutingRule>>

    @PUT("zones/{zone_id}/email/routing/rules/{rule_id}")
    suspend fun updateEmailRoutingRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("rule_id") ruleId: String,
        @Body rule: EmailRoutingRuleInput
    ): Response<CloudFlareResponse<EmailRoutingRule>>

    @DELETE("zones/{zone_id}/email/routing/rules/{rule_id}")
    suspend fun deleteEmailRoutingRule(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("rule_id") ruleId: String
    ): Response<CloudFlareResponse<Unit>>

    @GET("accounts/{account_id}/email/routing/addresses")
    suspend fun listEmailDestinationAddresses(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<EmailDestinationAddress>>>

    @POST("accounts/{account_id}/email/routing/addresses")
    suspend fun createEmailDestinationAddress(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Body body: EmailDestinationCreate
    ): Response<CloudFlareResponse<EmailDestinationAddress>>

    @DELETE("accounts/{account_id}/email/routing/addresses/{address_id}")
    suspend fun deleteEmailDestinationAddress(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String,
        @Path("address_id") addressId: String
    ): Response<CloudFlareResponse<Unit>>

    // ==================== SSL / 证书 ====================

    @GET("zones/{zone_id}/ssl/certificate_packs")
    suspend fun listCertificatePacks(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Query("status") status: String = "all"
    ): Response<CloudFlareResponse<List<SslCertificatePack>>>

    @GET("zones/{zone_id}/ssl/universal/settings")
    suspend fun getUniversalSslSettings(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String
    ): Response<CloudFlareResponse<UniversalSslSettings>>

    @PATCH("zones/{zone_id}/ssl/universal/settings")
    suspend fun setUniversalSslSettings(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Body settings: UniversalSslSettings
    ): Response<CloudFlareResponse<UniversalSslSettings>>

    @DELETE("zones/{zone_id}/ssl/certificate_packs/{pack_id}")
    suspend fun deleteCertificatePack(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("pack_id") packId: String
    ): Response<CloudFlareResponse<Unit>>

    // ==================== Zone Settings（设置 / 性能 / SSL 模式） ====================

    @GET("zones/{zone_id}/settings/{setting}")
    suspend fun getZoneSetting(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("setting") setting: String
    ): Response<CloudFlareResponse<ZoneSetting>>

    @PATCH("zones/{zone_id}/settings/{setting}")
    suspend fun setZoneSetting(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("setting") setting: String,
        @Body update: ZoneSettingUpdate
    ): Response<CloudFlareResponse<ZoneSetting>>

    @POST("zones/{zone_id}/purge_cache")
    suspend fun purgeAllCache(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Body request: PurgeRequest
    ): Response<CloudFlareResponse<PurgeResult>>

    @POST("zones/{zone_id}/purge_cache")
    suspend fun purgeFilesCache(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Body request: PurgeFilesRequest
    ): Response<CloudFlareResponse<PurgeResult>>

    // ==================== Load Balancer ====================

    @GET("zones/{zone_id}/load_balancers")
    suspend fun listLoadBalancers(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String
    ): Response<CloudFlareResponse<List<LoadBalancer>>>

    @PATCH("zones/{zone_id}/load_balancers/{lb_id}")
    suspend fun toggleLoadBalancer(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("lb_id") lbId: String,
        @Body body: LoadBalancerToggle
    ): Response<CloudFlareResponse<LoadBalancer>>

    @DELETE("zones/{zone_id}/load_balancers/{lb_id}")
    suspend fun deleteLoadBalancer(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("lb_id") lbId: String
    ): Response<CloudFlareResponse<Unit>>

    @GET("accounts/{account_id}/load_balancers/pools")
    suspend fun listLoadBalancerPools(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<Pool>>>

    @GET("accounts/{account_id}/load_balancers/monitors")
    suspend fun listLoadBalancerMonitors(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<Monitor>>>

    // ==================== Snippets ====================

    @GET("zones/{zone_id}/snippets")
    suspend fun listSnippets(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String
    ): Response<CloudFlareResponse<List<Snippet>>>

    @GET("zones/{zone_id}/snippets/{snippet_name}/content")
    suspend fun getSnippetContent(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("snippet_name") snippetName: String
    ): Response<ResponseBody>

    @Multipart
    @PUT("zones/{zone_id}/snippets/{snippet_name}")
    suspend fun putSnippet(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("snippet_name") snippetName: String,
        @Part("metadata") metadata: RequestBody,
        @Part script: MultipartBody.Part
    ): Response<CloudFlareResponse<Snippet>>

    @DELETE("zones/{zone_id}/snippets/{snippet_name}")
    suspend fun deleteSnippet(
        @Header("Authorization") token: String?,
        @Header("X-Auth-Email") email: String?,
        @Header("X-Auth-Key") apiKey: String?,
        @Path("zone_id") zoneId: String,
        @Path("snippet_name") snippetName: String
    ): Response<CloudFlareResponse<Unit>>
}
