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
    ): Response<CloudFlareResponse<Unit>>
    
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
}
