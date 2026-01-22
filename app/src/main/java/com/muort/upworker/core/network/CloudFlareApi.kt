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
 */
interface CloudFlareApi {
        /**
         * 更新自定义域名
         * PATCH /accounts/{account_id}/workers/domains/{domain_id}
         */
        @PATCH("accounts/{account_id}/workers/domains/{domain_id}")
        suspend fun updateCustomDomain(
            @Header("Authorization") token: String,
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
        @Header("Authorization") token: String,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1
    ): Response<CloudFlareResponse<List<ZoneInfo>>>
    
    // ==================== Workers ====================
    
    /**
     * Upload Worker Script using multipart/form-data (Recommended)
     * Supports metadata and module files
     * https://developers.cloudflare.com/api/operations/worker-script-upload-worker-module
     */
    @Multipart
    @PUT("accounts/{account_id}/workers/scripts/{script_name}")
    suspend fun uploadWorkerScriptMultipart(
        @Header("Authorization") token: String,
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
        @Header("Authorization") token: String,
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
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Body script: RequestBody
    ): Response<CloudFlareResponse<WorkerScript>>
    
    @GET("accounts/{account_id}/workers/scripts")
    suspend fun listWorkerScripts(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<WorkerScript>>>
    
    @GET("accounts/{account_id}/workers/scripts/{script_name}")
    suspend fun getWorkerScript(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String
    ): Response<ResponseBody>
    
    /**
     * Get Worker Script settings (includes bindings and other configuration)
     * https://developers.cloudflare.com/api/operations/worker-script-get-settings
     */
    @GET("accounts/{account_id}/workers/scripts/{script_name}/settings")
    suspend fun getWorkerSettings(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String
    ): Response<CloudFlareResponse<WorkerScript>>
    
    @DELETE("accounts/{account_id}/workers/scripts/{script_name}")
    suspend fun deleteWorkerScript(
        @Header("Authorization") token: String,
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
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("script_name") scriptName: String,
        @Part("settings") settings: RequestBody
    ): Response<CloudFlareResponse<WorkerScript>>
    
    // ==================== Routes ====================
    
    @GET("zones/{zone_id}/workers/routes")
    suspend fun listRoutes(
        @Header("Authorization") token: String,
        @Path("zone_id") zoneId: String
    ): Response<CloudFlareResponse<List<Route>>>
    
    @POST("zones/{zone_id}/workers/routes")
    suspend fun createRoute(
        @Header("Authorization") token: String,
        @Path("zone_id") zoneId: String,
        @Body route: RouteRequest
    ): Response<CloudFlareResponse<Route>>
    
    @PUT("zones/{zone_id}/workers/routes/{route_id}")
    suspend fun updateRoute(
        @Header("Authorization") token: String,
        @Path("zone_id") zoneId: String,
        @Path("route_id") routeId: String,
        @Body route: RouteRequest
    ): Response<CloudFlareResponse<Route>>
    
    @DELETE("zones/{zone_id}/workers/routes/{route_id}")
    suspend fun deleteRoute(
        @Header("Authorization") token: String,
        @Path("zone_id") zoneId: String,
        @Path("route_id") routeId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Custom Domains ====================
    
    @GET("accounts/{account_id}/workers/domains")
    suspend fun listCustomDomains(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<CustomDomain>>>
    
    @PUT("accounts/{account_id}/workers/domains")
    suspend fun addCustomDomain(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body request: CustomDomainRequest
    ): Response<CloudFlareResponse<CustomDomain>>
    
    @DELETE("accounts/{account_id}/workers/domains/{domain_id}")
    suspend fun deleteCustomDomain(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("domain_id") domainId: String
    ): Response<Unit>
    
    // ==================== DNS ====================
    
    @GET("zones/{zone_id}/dns_records")
    suspend fun listDnsRecords(
        @Header("Authorization") token: String,
        @Path("zone_id") zoneId: String,
        @Query("type") type: String? = null,
        @Query("name") name: String? = null
    ): Response<CloudFlareResponse<List<DnsRecord>>>
    
    @POST("zones/{zone_id}/dns_records")
    suspend fun createDnsRecord(
        @Header("Authorization") token: String,
        @Path("zone_id") zoneId: String,
        @Body record: DnsRecordRequest
    ): Response<CloudFlareResponse<DnsRecord>>
    
    @PUT("zones/{zone_id}/dns_records/{record_id}")
    suspend fun updateDnsRecord(
        @Header("Authorization") token: String,
        @Path("zone_id") zoneId: String,
        @Path("record_id") recordId: String,
        @Body record: DnsRecordRequest
    ): Response<CloudFlareResponse<DnsRecord>>
    
    @DELETE("zones/{zone_id}/dns_records/{record_id}")
    suspend fun deleteDnsRecord(
        @Header("Authorization") token: String,
        @Path("zone_id") zoneId: String,
        @Path("record_id") recordId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== KV ====================
    
    @GET("accounts/{account_id}/storage/kv/namespaces")
    suspend fun listKvNamespaces(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<KvNamespace>>>
    
    @POST("accounts/{account_id}/storage/kv/namespaces")
    suspend fun createKvNamespace(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body namespace: KvNamespaceRequest
    ): Response<CloudFlareResponse<KvNamespace>>
    
    @DELETE("accounts/{account_id}/storage/kv/namespaces/{namespace_id}")
    suspend fun deleteKvNamespace(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("namespace_id") namespaceId: String
    ): Response<CloudFlareResponse<Unit>>
    
    @GET("accounts/{account_id}/storage/kv/namespaces/{namespace_id}/keys")
    suspend fun listKvKeys(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("namespace_id") namespaceId: String
    ): Response<CloudFlareResponse<List<KvKey>>>
    
    @GET("accounts/{account_id}/storage/kv/namespaces/{namespace_id}/values/{key_name}")
    suspend fun getKvValue(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("namespace_id") namespaceId: String,
        @Path("key_name") keyName: String
    ): Response<ResponseBody>
    
    @PUT("accounts/{account_id}/storage/kv/namespaces/{namespace_id}/values/{key_name}")
    suspend fun putKvValue(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("namespace_id") namespaceId: String,
        @Path("key_name") keyName: String,
        @Body value: RequestBody
    ): Response<CloudFlareResponse<Unit>>
    
    @DELETE("accounts/{account_id}/storage/kv/namespaces/{namespace_id}/values/{key_name}")
    suspend fun deleteKvValue(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("namespace_id") namespaceId: String,
        @Path("key_name") keyName: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Pages ====================
    
    @GET("accounts/{account_id}/pages/projects")
    suspend fun listPagesProjects(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<PagesProject>>>
    
    @POST("accounts/{account_id}/pages/projects")
    suspend fun createPagesProject(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body project: PagesProjectRequest
    ): Response<CloudFlareResponse<PagesProject>>
    
    @DELETE("accounts/{account_id}/pages/projects/{project_name}")
    suspend fun deletePagesProject(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String
    ): Response<CloudFlareResponse<Unit>>
    
    @GET("accounts/{account_id}/pages/projects/{project_name}")
    suspend fun getPagesProject(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String
    ): Response<CloudFlareResponse<PagesProjectDetail>>
    
    @GET("accounts/{account_id}/pages/projects/{project_name}/deployments")
    suspend fun listPagesDeployments(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String
    ): Response<CloudFlareResponse<List<PagesDeployment>>>
    
    @POST("accounts/{account_id}/pages/projects/{project_name}/deployments/{deployment_id}/retry")
    suspend fun retryPagesDeployment(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Path("deployment_id") deploymentId: String
    ): Response<CloudFlareResponse<PagesDeployment>>
    
    @DELETE("accounts/{account_id}/pages/projects/{project_name}/deployments/{deployment_id}")
    suspend fun deletePagesDeployment(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Path("deployment_id") deploymentId: String
    ): Response<CloudFlareResponse<Unit>>
    
    /**
     * Update Pages project configuration (environment variables, bindings, etc.)
     * https://developers.cloudflare.com/api/operations/pages-project-update-project
     */
    @PATCH("accounts/{account_id}/pages/projects/{project_name}")
    suspend fun updatePagesProject(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Body updateRequest: PagesProjectUpdateRequest
    ): Response<CloudFlareResponse<PagesProjectDetail>>
    
    /**
     * Create a Pages deployment via Direct Upload
     * https://developers.cloudflare.com/api/operations/pages-deployment-create-deployment
     * Direct Upload 需要 manifest 字段描述文件结构
     */
    @Multipart
    @POST("accounts/{account_id}/pages/projects/{project_name}/deployments")
    suspend fun createPagesDeployment(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Part("manifest") manifest: RequestBody,
        @Part file: MultipartBody.Part
    ): Response<CloudFlareResponse<PagesDeployment>>
    
    // ==================== Pages Domains ====================
    
    /**
     * Get custom domains for Pages project
     * https://developers.cloudflare.com/api/operations/pages-domains-get-domains
     */
    @GET("accounts/{account_id}/pages/projects/{project_name}/domains")
    suspend fun listPagesDomains(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String
    ): Response<CloudFlareResponse<List<PagesDomain>>>
    
    /**
     * Add a custom domain to Pages project
     * https://developers.cloudflare.com/api/operations/pages-domains-add-domain
     */
    @POST("accounts/{account_id}/pages/projects/{project_name}/domains")
    suspend fun addPagesDomain(
        @Header("Authorization") token: String,
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
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("project_name") projectName: String,
        @Path("domain_name") domainName: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== R2 ====================
    
    @GET("accounts/{account_id}/r2/buckets")
    suspend fun listR2Buckets(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<R2BucketsResponse>>
    
    @POST("accounts/{account_id}/r2/buckets")
    suspend fun createR2Bucket(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body bucket: R2BucketRequest
    ): Response<CloudFlareResponse<R2Bucket>>
    
    @DELETE("accounts/{account_id}/r2/buckets/{bucket_name}")
    suspend fun deleteR2Bucket(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("bucket_name") bucketName: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== D1 ====================
    
    @GET("accounts/{account_id}/d1/database")
    suspend fun listD1Databases(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<D1Database>>>
    
    @POST("accounts/{account_id}/d1/database")
    suspend fun createD1Database(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body database: D1DatabaseRequest
    ): Response<CloudFlareResponse<D1Database>>
    
    @DELETE("accounts/{account_id}/d1/database/{database_id}")
    suspend fun deleteD1Database(
        @Header("Authorization") token: String,
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
        @Header("Authorization") token: String,
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
        @Header("Authorization") token: String,
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
        @Header("Authorization") token: String,
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
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("database_id") databaseId: String,
        @Part file: MultipartBody.Part
    ): Response<CloudFlareResponse<Unit>>

    // ==================== R2 Custom Domains ====================
    
    @GET("accounts/{account_id}/r2/buckets/{bucket_name}/custom_domains")
    suspend fun listR2CustomDomains(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("bucket_name") bucketName: String
    ): Response<CloudFlareResponse<R2CustomDomainsResponse>>
    
    @POST("accounts/{account_id}/r2/buckets/{bucket_name}/domains/custom")
    suspend fun createR2CustomDomain(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("bucket_name") bucketName: String,
        @Body request: R2CustomDomainRequest
    ): Response<CloudFlareResponse<R2CustomDomain>>
    
    @DELETE("accounts/{account_id}/r2/buckets/{bucket_name}/custom_domains/{domain}")
    suspend fun deleteR2CustomDomain(
        @Header("Authorization") token: String,
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
        @Header("Authorization") token: String,
        @Body request: AnalyticsGraphQLRequest
    ): Response<AnalyticsGraphQLResponse>
    
    // ==================== Zero Trust - Access Applications ====================
    
    /**
     * List Access Applications
     * https://developers.cloudflare.com/api/resources/zero_trust/subresources/access/subresources/applications/
     */
    @GET("accounts/{account_id}/access/apps")
    suspend fun listAccessApplications(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1
    ): Response<CloudFlareResponse<List<AccessApplication>>>
    
    /**
     * Get Access Application
     */
    @GET("accounts/{account_id}/access/apps/{app_id}")
    suspend fun getAccessApplication(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("app_id") appId: String
    ): Response<CloudFlareResponse<AccessApplication>>
    
    /**
     * Create Access Application
     */
    @POST("accounts/{account_id}/access/apps")
    suspend fun createAccessApplication(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body application: AccessApplicationRequest
    ): Response<CloudFlareResponse<AccessApplication>>
    
    /**
     * Update Access Application
     */
    @PUT("accounts/{account_id}/access/apps/{app_id}")
    suspend fun updateAccessApplication(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("app_id") appId: String,
        @Body application: AccessApplicationRequest
    ): Response<CloudFlareResponse<AccessApplication>>
    
    /**
     * Delete Access Application
     */
    @DELETE("accounts/{account_id}/access/apps/{app_id}")
    suspend fun deleteAccessApplication(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("app_id") appId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Access Policies ====================
    
    /**
     * List Access Policies
     */
    @GET("accounts/{account_id}/access/policies")
    suspend fun listAccessPolicies(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<AccessPolicy>>>
    
    /**
     * Create Access Policy
     */
    @POST("accounts/{account_id}/access/policies")
    suspend fun createAccessPolicy(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body policy: AccessPolicyRequest
    ): Response<CloudFlareResponse<AccessPolicy>>
    
    /**
     * List Application Policies
     */
    @GET("accounts/{account_id}/access/apps/{app_id}/policies")
    suspend fun listAppPolicies(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("app_id") appId: String
    ): Response<CloudFlareResponse<List<AccessPolicy>>>
    
    /**
     * Create Application Policy
     */
    @POST("accounts/{account_id}/access/apps/{app_id}/policies")
    suspend fun createAppPolicy(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("app_id") appId: String,
        @Body policy: AccessPolicyRequest
    ): Response<CloudFlareResponse<AccessPolicy>>
    
    /**
     * Update Application Policy
     */
    @PUT("accounts/{account_id}/access/apps/{app_id}/policies/{policy_id}")
    suspend fun updateAppPolicy(
        @Header("Authorization") token: String,
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
        @Header("Authorization") token: String,
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
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<AccessGroup>>>
    
    /**
     * Get Access Group
     */
    @GET("accounts/{account_id}/access/groups/{group_id}")
    suspend fun getAccessGroup(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("group_id") groupId: String
    ): Response<CloudFlareResponse<AccessGroup>>
    
    /**
     * Create Access Group
     */
    @POST("accounts/{account_id}/access/groups")
    suspend fun createAccessGroup(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body group: AccessGroupRequest
    ): Response<CloudFlareResponse<AccessGroup>>
    
    /**
     * Update Access Group
     */
    @PUT("accounts/{account_id}/access/groups/{group_id}")
    suspend fun updateAccessGroup(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("group_id") groupId: String,
        @Body group: AccessGroupRequest
    ): Response<CloudFlareResponse<AccessGroup>>
    
    /**
     * Delete Access Group
     */
    @DELETE("accounts/{account_id}/access/groups/{group_id}")
    suspend fun deleteAccessGroup(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("group_id") groupId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Gateway Rules ====================
    
    /**
     * List Gateway Rules
     */
    @GET("accounts/{account_id}/gateway/rules")
    suspend fun listGatewayRules(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<GatewayRule>>>
    
    /**
     * Create Gateway Rule
     */
    @POST("accounts/{account_id}/gateway/rules")
    suspend fun createGatewayRule(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body rule: GatewayRuleRequest
    ): Response<CloudFlareResponse<GatewayRule>>
    
    /**
     * Update Gateway Rule
     */
    @PUT("accounts/{account_id}/gateway/rules/{rule_id}")
    suspend fun updateGatewayRule(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("rule_id") ruleId: String,
        @Body rule: GatewayRuleRequest
    ): Response<CloudFlareResponse<GatewayRule>>
    
    /**
     * Delete Gateway Rule
     */
    @DELETE("accounts/{account_id}/gateway/rules/{rule_id}")
    suspend fun deleteGatewayRule(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("rule_id") ruleId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Gateway Lists ====================
    
    /**
     * List Gateway Lists
     */
    @GET("accounts/{account_id}/gateway/lists")
    suspend fun listGatewayLists(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<GatewayList>>>
    
    /**
     * Get Gateway List
     */
    @GET("accounts/{account_id}/gateway/lists/{list_id}")
    suspend fun getGatewayList(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("list_id") listId: String
    ): Response<CloudFlareResponse<GatewayList>>
    
    /**
     * Create Gateway List
     */
    @POST("accounts/{account_id}/gateway/lists")
    suspend fun createGatewayList(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body list: GatewayListRequest
    ): Response<CloudFlareResponse<GatewayList>>
    
    /**
     * Update Gateway List
     */
    @PUT("accounts/{account_id}/gateway/lists/{list_id}")
    suspend fun updateGatewayList(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("list_id") listId: String,
        @Body list: GatewayListRequest
    ): Response<CloudFlareResponse<GatewayList>>
    
    /**
     * Patch Gateway List (Append/Remove items)
     */
    @PATCH("accounts/{account_id}/gateway/lists/{list_id}")
    suspend fun patchGatewayList(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("list_id") listId: String,
        @Body patch: GatewayListPatchRequest
    ): Response<CloudFlareResponse<GatewayList>>
    
    /**
     * Delete Gateway List
     */
    @DELETE("accounts/{account_id}/gateway/lists/{list_id}")
    suspend fun deleteGatewayList(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("list_id") listId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Gateway Locations ====================
    
    /**
     * List Gateway Locations
     */
    @GET("accounts/{account_id}/gateway/locations")
    suspend fun listGatewayLocations(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<GatewayLocation>>>
    
    /**
     * Get Gateway Location
     */
    @GET("accounts/{account_id}/gateway/locations/{location_id}")
    suspend fun getGatewayLocation(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("location_id") locationId: String
    ): Response<CloudFlareResponse<GatewayLocation>>
    
    /**
     * Create Gateway Location
     */
    @POST("accounts/{account_id}/gateway/locations")
    suspend fun createGatewayLocation(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body location: GatewayLocationRequest
    ): Response<CloudFlareResponse<GatewayLocation>>
    
    /**
     * Update Gateway Location
     */
    @PUT("accounts/{account_id}/gateway/locations/{location_id}")
    suspend fun updateGatewayLocation(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("location_id") locationId: String,
        @Body location: GatewayLocationRequest
    ): Response<CloudFlareResponse<GatewayLocation>>
    
    /**
     * Delete Gateway Location
     */
    @DELETE("accounts/{account_id}/gateway/locations/{location_id}")
    suspend fun deleteGatewayLocation(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("location_id") locationId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Devices ====================
    
    /**
     * List Devices
     */
    @GET("accounts/{account_id}/devices")
    suspend fun listDevices(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1
    ): Response<CloudFlareResponse<List<Device>>>
    
    /**
     * Get Device
     */
    @GET("accounts/{account_id}/devices/{device_id}")
    suspend fun getDevice(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("device_id") deviceId: String
    ): Response<CloudFlareResponse<Device>>
    
    /**
     * Revoke Device
     */
    @DELETE("accounts/{account_id}/devices/{device_id}")
    suspend fun revokeDevice(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("device_id") deviceId: String
    ): Response<CloudFlareResponse<Unit>>
    
    // ==================== Zero Trust - Device Policies ====================
    
    /**
     * List Device Policies
     */
    @GET("accounts/{account_id}/devices/policies")
    suspend fun listDevicePolicies(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<DeviceSettingsPolicy>>>
    
    /**
     * Get Default Device Policy
     */
    @GET("accounts/{account_id}/devices/policy")
    suspend fun getDefaultDevicePolicy(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<DeviceSettingsPolicy>>
    
    /**
     * Update Default Device Policy
     */
    @PATCH("accounts/{account_id}/devices/policy")
    suspend fun updateDefaultDevicePolicy(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body policy: DevicePolicyUpdate
    ): Response<CloudFlareResponse<DeviceSettingsPolicy>>
    
    /**
     * Create Device Settings Profile
     */
    @POST("accounts/{account_id}/devices/policy")
    suspend fun createDevicePolicy(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body policy: DeviceSettingsPolicyRequest
    ): Response<CloudFlareResponse<DeviceSettingsPolicy>>
    
    // ==================== Zero Trust - Tunnels ====================
    
    /**
     * List Cloudflare Tunnels
     */
    @GET("accounts/{account_id}/cfd_tunnel")
    suspend fun listCloudflaredTunnels(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Query("per_page") perPage: Int = 50,
        @Query("page") page: Int = 1
    ): Response<CloudFlareResponse<List<CloudflareTunnel>>>
    
    /**
     * Get Cloudflare Tunnel
     */
    @GET("accounts/{account_id}/cfd_tunnel/{tunnel_id}")
    suspend fun getCloudflaredTunnel(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("tunnel_id") tunnelId: String
    ): Response<CloudFlareResponse<CloudflareTunnel>>
    
    /**
     * Create Cloudflare Tunnel
     */
    @POST("accounts/{account_id}/cfd_tunnel")
    suspend fun createCloudflaredTunnel(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body tunnel: TunnelCreateRequest
    ): Response<CloudFlareResponse<CloudflareTunnel>>
    
    /**
     * Delete Cloudflare Tunnel
     */
    @DELETE("accounts/{account_id}/cfd_tunnel/{tunnel_id}")
    suspend fun deleteCloudflaredTunnel(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("tunnel_id") tunnelId: String
    ): Response<CloudFlareResponse<Unit>>
    
    /**
     * List Tunnel Connections
     */
    @GET("accounts/{account_id}/cfd_tunnel/{tunnel_id}/connections")
    suspend fun listTunnelConnections(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("tunnel_id") tunnelId: String
    ): Response<CloudFlareResponse<List<TunnelConnection>>>
    
    /**
     * Get Tunnel Configuration
     */
    @GET("accounts/{account_id}/cfd_tunnel/{tunnel_id}/configurations")
    suspend fun getTunnelConfiguration(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("tunnel_id") tunnelId: String
    ): Response<CloudFlareResponse<TunnelConfiguration>>
    
    /**
     * Update Tunnel Configuration
     */
    @PUT("accounts/{account_id}/cfd_tunnel/{tunnel_id}/configurations")
    suspend fun updateTunnelConfiguration(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("tunnel_id") tunnelId: String,
        @Body config: TunnelConfigurationRequest
    ): Response<CloudFlareResponse<TunnelConfiguration>>
    
    // ==================== Zero Trust - Service Tokens ====================
    
    /**
     * List Service Tokens
     */
    @GET("accounts/{account_id}/access/service_tokens")
    suspend fun listServiceTokens(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String
    ): Response<CloudFlareResponse<List<ServiceToken>>>
    
    /**
     * Create Service Token
     */
    @POST("accounts/{account_id}/access/service_tokens")
    suspend fun createServiceToken(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Body request: ServiceTokenRequest
    ): Response<CloudFlareResponse<ServiceToken>>
    
    /**
     * Update Service Token
     */
    @PUT("accounts/{account_id}/access/service_tokens/{token_id}")
    suspend fun updateServiceToken(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("token_id") tokenId: String,
        @Body request: ServiceTokenRequest
    ): Response<CloudFlareResponse<ServiceToken>>
    
    /**
     * Delete Service Token
     */
    @DELETE("accounts/{account_id}/access/service_tokens/{token_id}")
    suspend fun deleteServiceToken(
        @Header("Authorization") token: String,
        @Path("account_id") accountId: String,
        @Path("token_id") tokenId: String
    ): Response<CloudFlareResponse<Unit>>
}
