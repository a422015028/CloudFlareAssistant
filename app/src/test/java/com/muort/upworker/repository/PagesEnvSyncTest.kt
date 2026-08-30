package com.muort.upworker.repository

import android.content.Context
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.CloudFlareError
import com.muort.upworker.core.model.CloudFlareResponse
import com.muort.upworker.core.model.EnvVar
import com.muort.upworker.core.model.EnvironmentConfig
import com.muort.upworker.core.model.KvBinding
import com.muort.upworker.core.model.PagesEnvSyncResult
import com.muort.upworker.core.model.PagesProjectDetail
import com.muort.upworker.core.model.PagesProjectUpdateRequest
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.repository.PagesRepository
import com.muort.upworker.core.util.EsbuildBundler
import com.muort.upworker.core.util.SucraseTransformer
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

/**
 * P1-4 RED failing tests for PagesRepository.syncDualEnvConfigs.
 *
 * 语义：两次独立 PATCH 分别写入 production / preview deployment_configs:
 *  ① 先 PATCH production → fail → PagesEnvSyncResult.ProductionFail（不再调 preview）
 *  ② PATCH production OK → PATCH preview → fail → PagesEnvSyncResult.PreviewFail
 *  ③ 两次 OK → PagesEnvSyncResult.Success（env/kv/d1/r2/service 5 个 count 由
 *     sharedEnvConfig 实际非 null map size 决定）
 *
 * 对应字符串：pages_env_sync_production_fail_format / preview_fail_format / ok_format
 */
class PagesEnvSyncTest {

    private lateinit var repository: PagesRepository

    @MockK(relaxed = true) lateinit var mockContext: Context
    @MockK(relaxed = true) lateinit var mockApi: CloudFlareApi
    @MockK(relaxed = true) lateinit var mockSucrase: SucraseTransformer
    @MockK(relaxed = true) lateinit var mockEsbuild: EsbuildBundler

    private val testAccount = Account(
        name = "test",
        accountId = "acc_xx",
        token = "tok_x"  // AuthHelper.getBearerToken() 会拼 "Bearer " 前缀
    )

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        every { mockContext.getString(any<Int>()) } answers { "str:${firstArg<Int>()}" }
        every { mockContext.getString(any<Int>(), *anyVararg<Any>()) } answers {
            val id = firstArg<Int>()
            val tail = invocation.args.drop(1)
            val flat = when {
                tail.size == 1 && tail[0] is Array<*> -> (tail[0] as Array<*>).toList()
                else -> tail
            }
            val args = flat.joinToString(",") { it.toString() }
            "str:$id|args=$args"
        }
        repository = PagesRepository(
            appContext = mockContext,
            api = mockApi,
            sucraseTransformer = mockSucrase,
            esbuildBundler = mockEsbuild
        )
        // Global default coEvery: capture every updatePagesProject body into capturedRequests
        // and return a synthetic success. Individual tests override this with their own
        // coEvery (MockK last-writer-wins for identical input signature) — those tests
        // must manually re-add to capturedRequests inside coAnswers if they need bodies.
        capturedRequests.clear()
        val bodySlot = io.mockk.slot<PagesProjectUpdateRequest>()
        coEvery {
            mockApi.updatePagesProject(any(), any(), any(), any(), any(), capture(bodySlot))
        } coAnswers {
            capturedRequests.add(bodySlot.captured)
            cfSuccess(mkProjDet(name = "default-proj", id = "def_$capturedRequests"))
        }
    }

    @After fun tearDown() { /* no-op */ }

    // =================== Helpers ===================

    /** Captures each PagesProjectUpdateRequest body across updatePagesProject calls, in order. */
    private val capturedRequests = mutableListOf<PagesProjectUpdateRequest>()

    private fun <T> cfSuccess(result: T): Response<CloudFlareResponse<T>> =
        Response.success(CloudFlareResponse(success = true, errors = emptyList(), messages = emptyList(), result = result))

    private fun <T> cfError(code: Int, msg: String): Response<CloudFlareResponse<T>> =
        Response.error(code, "CF API error: $msg".toResponseBody())

    /** Build a PagesProjectDetail with only name/id filled; everything else null. */
    private fun mkProjDet(name: String, id: String): PagesProjectDetail = PagesProjectDetail(
        id = id,
        name = name,
        subdomain = null,
        domains = null,
        createdOn = null,
        productionBranch = null,
        framework = null,
        frameworkVersion = null,
        usesFunctions = null,
        previewScriptName = null,
        productionScriptName = null,
        source = null,
        buildConfig = null,
        deploymentConfigs = null,
        latestDeployment = null,
        canonicalDeployment = null,
        previewDeployment = null
    )

    /** Extract body of updatePagesProject sent on n-th call (1-based). */
    private fun captureUpdateRequest(callIndex: Int): PagesProjectUpdateRequest {
        assertTrue(
            "captureUpdateRequest($callIndex) but only ${capturedRequests.size} requests captured",
            capturedRequests.size >= callIndex
        )
        return capturedRequests[callIndex - 1]
    }

    private fun makeEnv(
        vars: Int = 0,
        kv: Int = 0,
        d1: Int = 0,
        r2: Int = 0,
        services: Int = 0
    ): EnvironmentConfig = EnvironmentConfig(
        envVars = (0 until vars).associate { "KEY_$it" to EnvVar(value = "v$it", type = "plain_text") },
        kvNamespaces = (0 until kv).associate { "KV_$it" to KvBinding(namespaceId = "ns_$it") },
        d1Databases = (0 until d1).associate { "DB_$it" to com.muort.upworker.core.model.D1Binding(id = "d1_$it") },
        r2Buckets = (0 until r2).associate { "R2_$it" to com.muort.upworker.core.model.R2Binding(name = "bkt_$it") },
        services = (0 until services).associate { "SVC_$it" to com.muort.upworker.core.model.ServiceBinding(service = "svc_$it", environment = "production") }
    )

    // ========================================================================
    // Test 1: 两次 PATCH 均成功 → Success，格式串 5 个 counts 正确
    // ========================================================================
    @Test
    fun `syncDualEnvConfigs returns Success with five counts when both patches succeed`() = runBlocking {
        // sharedEnv: 3 variables + 2 KV + 1 D1 + 2 R2 + 4 Service
        val shared = makeEnv(vars = 3, kv = 2, d1 = 1, r2 = 2, services = 4)
        coEvery { mockApi.updatePagesProject(any(), any(), any(), any(), any(), any()) } coAnswers {
            capturedRequests.add(args[5] as PagesProjectUpdateRequest)
            cfSuccess(mkProjDet("proj", "proj123"))
        }
        val r = repository.syncDualEnvConfigs(testAccount, "my-proj", shared)
        assertTrue("Result must be Success", r is PagesEnvSyncResult.Success)
        val s = r as PagesEnvSyncResult.Success
        assertEquals("envVars count", 3, s.envVarsCount)
        assertEquals("kv count", 2, s.kvCount)
        assertEquals("d1 count", 1, s.d1Count)
        assertEquals("r2 count", 2, s.r2Count)
        assertEquals("services count", 4, s.servicesCount)
        // Verify 2 distinct updatePagesProject calls were made (1st=prod patch, 2nd=preview patch)
        coVerify(exactly = 2) {
            mockApi.updatePagesProject(
                token = "Bearer tok_x",
                email = null,
                apiKey = null,
                accountId = testAccount.accountId,
                projectName = "my-proj",
                any()
            )
        }
    }

    // ========================================================================
    // Test 2: 首次 PATCH (production) 失败 → ProductionFail + 不调 preview
    // ========================================================================
    @Test
    fun `syncDualEnvConfigs returns ProductionFail and skips preview patch when first PATCH returns HTTP 500`() = runBlocking {
        val shared = makeEnv(vars = 1)
        // 只有第 1 次 PATCH 返回 500；如果实现错误调了第 2 次就会默认 success
        coEvery { mockApi.updatePagesProject(any(), any(), any(), any(), any(), any()) }
            .coAnswers {
                capturedRequests.add(args[5] as PagesProjectUpdateRequest)
                callCounter++
                if (callCounter == 1) cfError(500, "Internal Server Error: DB locked")
                else cfSuccess(mkProjDet("p", "x"))
            }
        callCounter = 0
        val r = repository.syncDualEnvConfigs(testAccount, "my-proj", shared)
        assertTrue("Result must be ProductionFail", r is PagesEnvSyncResult.ProductionFail)
        val p = r as PagesEnvSyncResult.ProductionFail
        assertNotNull("ProductionFail.errorMessage must not be null", p.errorMessage)
        assertTrue("error should mention '500' (got: ${p.errorMessage})", p.errorMessage!!.contains("500"))
        coVerify(exactly = 1) {
            mockApi.updatePagesProject(any(), any(), any(), any(), any(), any())
        }
    }

    // ========================================================================
    // Test 3: production OK / preview FAIL → PreviewFail + 2 calls
    // ========================================================================
    @Test
    fun `syncDualEnvConfigs returns PreviewFail when second PATCH fails but production succeeded`() = runBlocking {
        val shared = makeEnv(vars = 2, services = 1)
        coEvery { mockApi.updatePagesProject(any(), any(), any(), any(), any(), any()) }
            .coAnswers {
                capturedRequests.add(args[5] as PagesProjectUpdateRequest)
                callCounter++
                when (callCounter) {
                    1 -> cfSuccess(mkProjDet("p", "x"))
                    else -> cfError(400, "Preview quota exceeded")
                }
            }
        callCounter = 0
        val r = repository.syncDualEnvConfigs(testAccount, "my-proj", shared)
        assertTrue("Result must be PreviewFail", r is PagesEnvSyncResult.PreviewFail)
        val pv = r as PagesEnvSyncResult.PreviewFail
        assertNotNull(pv.errorMessage)
        assertTrue("error must mention '400' (got: ${pv.errorMessage})", pv.errorMessage!!.contains("400"))
        coVerify(exactly = 2) { mockApi.updatePagesProject(any(), any(), any(), any(), any(), any()) }
    }

    // ========================================================================
    // Test 4: 首次 PATCH body 中 deploymentConfigs.production 非空，preview 为 null
    //         第二次 PATCH body 中 deploymentConfigs.preview 非空，production 为 null
    // ========================================================================
    @Test
    fun `syncDualEnvConfigs sends production-only then preview-only on separate PATCH bodies`() = runBlocking {
        val shared = makeEnv(vars = 2)
        coEvery { mockApi.updatePagesProject(any(), any(), any(), any(), any(), any()) } coAnswers {
            capturedRequests.add(args[5] as PagesProjectUpdateRequest)
            cfSuccess(mkProjDet("p", "y"))
        }
        repository.syncDualEnvConfigs(testAccount, "my-proj", shared)
        // 1st call: production filled, preview null
        val req1 = captureUpdateRequest(1)
        assertNotNull("1st request.deploymentConfigs must not be null", req1.deploymentConfigs)
        assertNotNull("1st request.production must not be null", req1.deploymentConfigs!!.production)
        assertTrue(
            "1st request.preview must be null; got: ${req1.deploymentConfigs!!.preview}",
            req1.deploymentConfigs!!.preview == null
        )
        // 2nd call: preview filled, production null
        val req2 = captureUpdateRequest(2)
        assertNotNull("2nd request.preview must not be null", req2.deploymentConfigs!!.preview)
        assertTrue(
            "2nd request.production must be null; got: ${req2.deploymentConfigs!!.production}",
            req2.deploymentConfigs!!.production == null
        )
    }

    // ========================================================================
    // Test 5: 所有 map 为 null → 5 counts 全部为 0，仍触发两个 PATCH
    // ========================================================================
    @Test
    fun `syncDualEnvConfigs reports zero counts when all bindings maps are null`() = runBlocking {
        // EnvironmentConfig all maps null, no compatibility date etc.
        val empty = EnvironmentConfig(envVars = null)
        coEvery { mockApi.updatePagesProject(any(), any(), any(), any(), any(), any()) } coAnswers {
            cfSuccess(mkProjDet("empty", "empty_proj"))
        }
        val r = repository.syncDualEnvConfigs(testAccount, "empty-proj", empty)
        assertTrue("Result must be Success", r is PagesEnvSyncResult.Success)
        val s = r as PagesEnvSyncResult.Success
        assertEquals("envVarsCount must be 0", 0, s.envVarsCount)
        assertEquals("kvCount must be 0", 0, s.kvCount)
        assertEquals("d1Count must be 0", 0, s.d1Count)
        assertEquals("r2Count must be 0", 0, s.r2Count)
        assertEquals("servicesCount must be 0", 0, s.servicesCount)
        coVerify(exactly = 2) { mockApi.updatePagesProject(any(), any(), any(), any(), any(), any()) }
    }

    // tiny counter shared between the coAnswers closures
    private var callCounter = 0
}
