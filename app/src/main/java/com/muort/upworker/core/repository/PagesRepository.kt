package com.muort.upworker.core.repository

import com.muort.upworker.core.model.*
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.safeApiCall
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PagesRepository @Inject constructor(
    private val api: CloudFlareApi
) {
    
    suspend fun listProjects(account: Account): Resource<List<PagesProject>> = 
        withContext(Dispatchers.IO) {
            safeApiCall {
                val response = api.listPagesProjects(
                    token = "Bearer ${account.token}",
                    accountId = account.accountId
                )
                
                if (response.isSuccessful && response.body()?.success == true) {
                    Resource.Success(response.body()?.result ?: emptyList())
                } else {
                    val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                        ?: response.message()
                    Resource.Error("Failed to list projects: $errorMsg")
                }
            }
        }
    
    suspend fun createProject(
        account: Account,
        name: String,
        productionBranch: String = "main"
    ): Resource<PagesProject> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.createPagesProject(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                project = PagesProjectRequest(
                    name = name,
                    productionBranch = productionBranch
                )
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Project created but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to create project: $errorMsg")
            }
        }
    }
    
    suspend fun deleteProject(
        account: Account,
        projectName: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deletePagesProject(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                projectName = projectName
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to delete project: $errorMsg")
            }
        }
    }
    
    suspend fun getProject(
        account: Account,
        projectName: String
    ): Resource<PagesProjectDetail> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.getPagesProject(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                projectName = projectName
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Project not found")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to get project: $errorMsg")
            }
        }
    }
    
    suspend fun listDeployments(
        account: Account,
        projectName: String
    ): Resource<List<PagesDeployment>> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.listPagesDeployments(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                projectName = projectName
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(response.body()?.result ?: emptyList())
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to list deployments: $errorMsg")
            }
        }
    }
    
    suspend fun retryDeployment(
        account: Account,
        projectName: String,
        deploymentId: String
    ): Resource<PagesDeployment> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.retryPagesDeployment(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                projectName = projectName,
                deploymentId = deploymentId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                response.body()?.result?.let {
                    Resource.Success(it)
                } ?: Resource.Error("Deployment retried but no result returned")
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to retry deployment: $errorMsg")
            }
        }
    }
    
    suspend fun deleteDeployment(
        account: Account,
        projectName: String,
        deploymentId: String
    ): Resource<Unit> = withContext(Dispatchers.IO) {
        safeApiCall {
            val response = api.deletePagesDeployment(
                token = "Bearer ${account.token}",
                accountId = account.accountId,
                projectName = projectName,
                deploymentId = deploymentId
            )
            
            if (response.isSuccessful && response.body()?.success == true) {
                Resource.Success(Unit)
            } else {
                val errorMsg = response.body()?.errors?.firstOrNull()?.message 
                    ?: response.message()
                Resource.Error("Failed to delete deployment: $errorMsg")
            }
        }
    }
}
