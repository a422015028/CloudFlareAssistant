package com.muort.upworker.core.repository

import android.content.Context
import com.google.gson.GsonBuilder
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.WorkerScript
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.util.AuthHelper
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerStageDisabledTest {

    @MockK(relaxed = true)
    lateinit var mockContext: Context

    @MockK(relaxed = true)
    lateinit var mockApi: CloudFlareApi

    private lateinit var repository: WorkerRepository

    private val testAccount = Account(
        name = "test",
        accountId = "accx",
        token = "tok"
    )
    private val scriptName = "my-worker"
    private val fakeScript = WorkerScript(
        id = scriptName,
        createdOn = null,
        modifiedOn = null,
        etag = "etag1",
        size = 1024L
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        mockkObject(AuthHelper)
        every { AuthHelper.getBearerToken(testAccount) } returns "Bearer tok"
        every { AuthHelper.getEmail(testAccount) } returns null
        every { AuthHelper.getGlobalApiKey(testAccount) } returns null

        every { mockContext.getString(any<Int>()) } answers { "str:${firstArg<Int>()}" }
        every { mockContext.getString(any<Int>(), *anyVararg<Any>()) } answers {
            val id = firstArg<Int>()
            val args = invocation.args.drop(1).joinToString(",") { it.toString() }
            "str:$id|args=$args"
        }

        val gson = GsonBuilder()
            .setObjectToNumberStrategy(com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE)
            .create()
        repository = WorkerRepository(appContext = mockContext, api = mockApi, gson = gson)
    }

    private fun <T : Any> cfOk(t: T): Response<com.muort.upworker.core.model.CloudFlareResponse<T>> {
        val body = com.muort.upworker.core.model.CloudFlareResponse(
            success = true,
            errors = emptyList(),
            messages = emptyList(),
            result = t
        )
        return Response.success(body)
    }

    @Test
    fun `afterUpload with Observability disabled skips observability HTTP and stage`() = runTest {
        // Arrange: Observability disabled; Subdomain + Deployment enabled
        val enabledStages: Set<WorkerPostStageKind> = setOf(
            WorkerPostStageKind.Subdomain,
            WorkerPostStageKind.Deployment
        )

        // Subdomain enable succeeds
        coEvery {
            mockApi.enableWorkerSubdomain(any(), any(), any(), any(), any(), any())
        } returns cfOk(mapOf("enabled" to true))

        // Observability PATCH via updateWorkerSettings — should NOT be called
        // (Deployment with versionId=null is a no-op, does not call HTTP)

        // Act
        val result = repository.afterUpload(
            account = testAccount,
            uploadResult = Resource.Success(fakeScript),
            scriptName = scriptName,
            versionId = null,
            percentage = 100,
            enabledStages = enabledStages
        )

        // Assert: Observability stage absent; Subdomain and Deployment present
        val observabilityCount = result.stages.count { it.kind == WorkerPostStageKind.Observability }
        val subdomainCount = result.stages.count { it.kind == WorkerPostStageKind.Subdomain }
        val deploymentCount = result.stages.count { it.kind == WorkerPostStageKind.Deployment }

        assertEquals("Observability stage should be skipped (0 stages)", 0, observabilityCount)
        assertEquals("Subdomain stage should run (1 stage)", 1, subdomainCount)
        assertEquals("Deployment stage should run (1 stage)", 1, deploymentCount)
        assertEquals("Total stages should be 2 (Subdomain + Deployment only)", 2, result.stages.size)

        // Assert: Observability PATCH HTTP NEVER invoked
        coVerify(exactly = 0) {
            mockApi.updateWorkerSettings(any(), any(), any(), any(), any(), any())
        }

        // Sanity: Subdomain HTTP was actually invoked (exactly=1)
        coVerify(exactly = 1) {
            mockApi.enableWorkerSubdomain(any(), any(), any(), any(), any(), any())
        }

        // Upload result preserved
        assertTrue(
            "Upload result should still be Success",
            result.overallUpload is Resource.Success
        )
    }
}
