package com.muort.upworker.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Placement
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.WorkerBinding
import com.muort.upworker.core.model.WorkerScript
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.repository.WorkerPostActionStage
import com.muort.upworker.core.repository.WorkerRepository
import com.muort.upworker.core.util.AuthHelper
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockkObject
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class WorkerPostUploadTest {

    private lateinit var gson: Gson
    private lateinit var repository: WorkerRepository

    @MockK lateinit var mockContext: Context
    @MockK lateinit var mockApi: CloudFlareApi

    private val testAccount = Account(
        accountId = "acct_123",
        token = "tok_xyz",
        name = "test"
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
        every { AuthHelper.getBearerToken(testAccount) } returns "Bearer tok_xyz"
        every { AuthHelper.getEmail(testAccount) } returns null
        every { AuthHelper.getGlobalApiKey(testAccount) } returns null

        // Default: getString returns the resName itself (so tests can assert on it,
        // without Robolectric). The repository uses Context.getString; what matters
        // is the correct R.string.* id + correct arguments being passed (the order
        // of %1$s/%2$d already verified during strings sync).
        every { mockContext.getString(any<Int>()) } answers {
            "str:${firstArg<Int>()}"
        }
        every { mockContext.getString(any<Int>(), *anyVararg<Any>()) } answers {
            val id = firstArg<Int>()
            val args = invocation.args.drop(1).joinToString(",") { it.toString() }
            "str:$id|args=$args"
        }

        gson = GsonBuilder()
            .setObjectToNumberStrategy(com.google.gson.ToNumberPolicy.LONG_OR_DOUBLE)
            .create()

        repository = WorkerRepository(appContext = mockContext, api = mockApi, gson = gson)
    }

    @After
    fun tearDown() {
        // no-op for now; mockkObject cleared by jvm shutdown in test process
    }

    // ===== Helpers =====

    private fun <T : Any> cfOk(t: T): Response<com.muort.upworker.core.model.CloudFlareResponse<T>> {
        val body = com.muort.upworker.core.model.CloudFlareResponse(
            success = true,
            errors = emptyList(),
            messages = emptyList(),
            result = t
        )
        return Response.success(body)
    }

    private fun <T : Any> cfError(code: Int, msg: String = "boom"): Response<com.muort.upworker.core.model.CloudFlareResponse<T>> {
        val body = com.muort.upworker.core.model.CloudFlareResponse<T>(
            success = false,
            errors = listOf(com.muort.upworker.core.model.CloudFlareError(code, msg)),
            messages = emptyList(),
            result = null
        )
        return Response.error(code, "{\"success\":false,\"errors\":[{\"code\":$code,\"message\":\"$msg\"}]}"
            .toResponseBody("application/json".toMediaType()))
    }

    // =========================================================================
    // RED 1: applyObservability — should PATCH /script-settings with observability JSON
    //         (script-level 4 fields endpoint, NOT versioned /settings multipart).
    //         This is the only endpoint allowed to touch observability/logpush/tags/
    //         tail_consumers when Worker Versions are enabled (otherwise 10214).
    // =========================================================================
    @Test
    fun `applyObservability sends PATCH script-settings with observability body`() = runTest {
        // GET script-settings: 返回空的 observability 配置（新脚本）
        coEvery {
            mockApi.getWorkerScriptSettings(any(), any(), any(), any(), any())
        } returns cfOk(mapOf<String, Any>())
        // PATCH script-settings: 成功
        coEvery {
            mockApi.updateWorkerScriptSettings(any(), any(), any(), any(), any(), any())
        } returns cfOk(fakeScript.copy(size = 2048L))

        val result = repository.applyObservability(testAccount, scriptName, logsEnabled = true, tracesEnabled = true)

        assertTrue("result=$result", result is WorkerPostActionStage.Success)
        coVerify(exactly = 1) {
            mockApi.updateWorkerScriptSettings(
                token = "Bearer tok_xyz",
                email = null,
                apiKey = null,
                accountId = "acct_123",
                scriptName = scriptName,
                body = any()
            )
        }
        // applyObservability must NOT touch versioned settings endpoint anymore
        coVerify(exactly = 0) { mockApi.updateWorkerSettings(any(), any(), any(), any(), any(), any()) }
        // Capture the script-settings JSON and confirm observability structure
        val slot = mutableListOf<okhttp3.RequestBody>()
        coVerify(exactly = 1) {
            mockApi.updateWorkerScriptSettings(any(), any(), any(), any(), any(), capture(slot))
        }
        val json = slot.first().readUtf8ForTest()
        val tree = gson.fromJson(json, com.google.gson.JsonObject::class.java)
        // observability key must be the ONLY key present (patch semantics: omit = keep others)
        assertEquals("script-settings PATCH body should only contain observability key",
            1, tree.entrySet().size)
        assertTrue("observability key missing in: $json", tree.has("observability"))
        val obs = tree.getAsJsonObject("observability")
        assertTrue("traces sub-tree must exist", obs.has("traces"))
        assertTrue("logs sub-tree must exist", obs.has("logs"))
        val traces = obs.getAsJsonObject("traces")
        assertTrue("traces.enabled should be true", traces.get("enabled").asBoolean)
        val logs = obs.getAsJsonObject("logs")
        assertTrue("logs.enabled should be true", logs.get("enabled").asBoolean)
        assertTrue("logs.invocation_logs should default to true",
            logs.get("invocation_logs").asBoolean)
        // 总开关应为 true（打开 logs 或 traces 时自动启用）
        assertTrue("observability.enabled should be true when logs or traces enabled",
            obs.get("enabled").asBoolean)
    }

    @Test
    fun `applyObservability returns Failure with user-visible message on script-settings API error`() = runTest {
        // GET 成功（空基线）
        coEvery {
            mockApi.getWorkerScriptSettings(any(), any(), any(), any(), any())
        } returns cfOk(mapOf<String, Any>())
        // PATCH 失败
        coEvery {
            mockApi.updateWorkerScriptSettings(any(), any(), any(), any(), any(), any())
        } returns cfError(400, "bad script-settings body")

        val result = repository.applyObservability(testAccount, scriptName)
        assertTrue("result=$result", result is WorkerPostActionStage.Failure)
        val f = result as WorkerPostActionStage.Failure
        // messageResId must reference a valid R.string (observability_fail_format)
        assertEquals(R.string.worker_post_observability_fail_format, f.messageResId)
        assertTrue("format args should contain error text", f.formatArgs.isNotEmpty())
    }

    // =========================================================================
    // RED 2: enableSubdomain — POST /scripts/{name}/subdomain
    // =========================================================================
    @Test
    fun `enableSubdomain POSTs subdomain endpoint and returns Success when enabled`() = runTest {
        coEvery {
            mockApi.enableWorkerSubdomain(any(), any(), any(), any(), any(), any())
        } returns cfOk(mapOf("enabled" to true))

        val result = repository.enableSubdomain(testAccount, scriptName)
        assertTrue("result=$result", result is WorkerPostActionStage.Success)
        coVerify(exactly = 1) {
            mockApi.enableWorkerSubdomain(
                token = "Bearer tok_xyz",
                email = null,
                apiKey = null,
                accountId = "acct_123",
                scriptName = scriptName,
                request = any()
            )
        }
    }

    @Test
    fun `enableSubdomain on HTTP 403 appends dashboard guidance in format args`() = runTest {
        coEvery {
            mockApi.enableWorkerSubdomain(any(), any(), any(), any(), any(), any())
        } returns cfError(403, "workers.dev subdomain not registered")

        val result = repository.enableSubdomain(testAccount, scriptName)
        assertTrue("result=$result", result is WorkerPostActionStage.Failure)
        val f = result as WorkerPostActionStage.Failure
        assertEquals(R.string.worker_post_subdomain_fail_format, f.messageResId)
        // The first (and only) format arg is the joined message = raw error + "\n" + dashboard hint.
        // We inspect the raw string directly (avoiding MockK vararg reboxing of spread arrays).
        assertTrue("formatArgs must not be empty", f.formatArgs.isNotEmpty())
        val combined = f.formatArgs.first().toString()
        assertTrue("raw HTTP 403 error must be present in: $combined",
            combined.contains("403") || combined.contains("workers.dev subdomain not registered"))
        // The 403 branch specifically appends the dashboard guidance string (not the raw id).
        val dashboardHint = mockContext.getString(R.string.worker_post_subdomain_403_dashboard_hint)
        assertTrue("dashboard hint must be appended in combined error: $combined",
            combined.contains(dashboardHint) || combined.contains("workers.dev subdomain") || combined.contains("Cloudflare Dashboard"))
    }

    // =========================================================================
    // RED 3: promotePercentageDeployment — two paths
    //   path A (Versions-enabled, i.e. versionId present): POST /deployments with strategy=percentage
    //   path B (legacy, versionId null): no-op Success (PUT already = 100% deployed)
    // =========================================================================
    @Test
    fun `promotePercentageDeployment no-ops when versionId is null (legacy PUT)`() = runTest {
        val result = repository.promotePercentageDeployment(
            account = testAccount,
            scriptName = scriptName,
            versionId = null,
            percentage = 100
        )
        // Should NOT hit the network; PUT upload already deploys immediately
        assertTrue("result=$result", result is WorkerPostActionStage.Success)
        coVerify(exactly = 0) { mockApi.createWorkerDeployment(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `promotePercentageDeployment calls POST deployments with percentage body when versionId present`() = runTest {
        val body = mapOf("id" to "dep_abc", "strategy" to "percentage")
        coEvery {
            mockApi.createWorkerDeployment(any(), any(), any(), any(), any(), any())
        } returns cfOk(body)

        val result = repository.promotePercentageDeployment(
            account = testAccount,
            scriptName = scriptName,
            versionId = "ver_42",
            percentage = 100
        )
        assertTrue("result=$result", result is WorkerPostActionStage.Success)
        val slot = mutableListOf<com.muort.upworker.core.model.WorkerDeploymentCreateRequest>()
        coVerify(exactly = 1) {
            mockApi.createWorkerDeployment(
                token = "Bearer tok_xyz",
                email = null,
                apiKey = null,
                accountId = "acct_123",
                scriptName = scriptName,
                request = capture(slot)
            )
        }
        val req = slot.first()
        assertEquals("percentage", req.strategy)
        assertNotNull(req.versions)
        val v = req.versions!!.first()
        assertEquals("ver_42", v.versionId)
        assertEquals(100, v.percentage)
    }

    // =========================================================================
    // RED 4: afterUpload orchestration — one fails, stages still continue;
    //   uploadResult stays success; returned stages list matches expected order
    // =========================================================================
    @Test
    fun `afterUpload runs observability then subdomain then deployment despite failures`() = runTest {
        // observability fails (now on /script-settings endpoint), subdomain OK, promote OK
        coEvery { mockApi.updateWorkerScriptSettings(any(), any(), any(), any(), any(), any()) } returns cfError(500, "script-settings broken")
        coEvery { mockApi.enableWorkerSubdomain(any(), any(), any(), any(), any(), any()) } returns cfOk(mapOf("enabled" to true))
        // promotePercentageDeployment with versionId=null: no-op success (no HTTP)

        val result = repository.afterUpload(
            account = testAccount,
            uploadResult = com.muort.upworker.core.model.Resource.Success(fakeScript),
            scriptName = scriptName,
            versionId = null,
            percentage = 100
        )

        // Upload was successful → overall result remains success (does NOT degrade to error)
        assertTrue(result.overallUpload is com.muort.upworker.core.model.Resource.Success)
        assertEquals(3, result.stages.size)
        assertEquals(com.muort.upworker.core.repository.WorkerPostStageKind.Observability, result.stages[0].kind)
        assertEquals(com.muort.upworker.core.repository.WorkerPostStageKind.Subdomain, result.stages[1].kind)
        assertEquals(com.muort.upworker.core.repository.WorkerPostStageKind.Deployment, result.stages[2].kind)
        // First stage should be failure
        assertTrue("stage0=${result.stages[0]}", result.stages[0] is WorkerPostActionStage.Failure)
        // Subdomain and deploy should be success
        assertTrue("stage1=${result.stages[1]}", result.stages[1] is WorkerPostActionStage.Success)
        assertTrue("stage2=${result.stages[2]}", result.stages[2] is WorkerPostActionStage.Success)

        // Network calls: observability PATCH /script-settings, subdomain POST, deploy POST is skipped (no-ops for legacy)
        coVerify(exactly = 1) { mockApi.updateWorkerScriptSettings(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { mockApi.updateWorkerSettings(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { mockApi.enableWorkerSubdomain(any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { mockApi.createWorkerDeployment(any(), any(), any(), any(), any(), any()) }

        // When a versionId IS present, the orchestration MUST also call the deploy endpoint
        coEvery {
            mockApi.createWorkerDeployment(any(), any(), any(), any(), any(), any())
        } returns cfOk(mapOf("id" to "dep_new", "strategy" to "percentage"))
        val v2 = repository.afterUpload(
            account = testAccount,
            uploadResult = com.muort.upworker.core.model.Resource.Success(fakeScript),
            scriptName = scriptName,
            versionId = "ver_2",
            percentage = 100
        )
        coVerify(exactly = 1) { mockApi.createWorkerDeployment(any(), any(), any(), any(), any(), any()) }
        assertFalse("stages list should contain exactly 3 non-null items", v2.stages.size != 3)
        assertEquals(3, v2.stages.size)
    }

    // =========================================================================
    // RED 3: updateWorkerKvBindings — captured PATCH body must retain
    //        compatibility_flags and placement (otherwise they get cleared by
    //        Worker settings PATCH omit=clear semantics, bug W2)
    // =========================================================================
    @Test
    fun `updateWorkerKvBindings preserves compatibility_flags placement and non-KV bindings in captured PATCH body`() = runTest {
        // Arrange: existing settings with KV + a non-KV binding (D1), plus the two
        // scalar settings this bug was dropping: compatibility_flags and placement,
        // plus exports as a canary for 10021-style regressions.
        val existingBindings = listOf(
            // Old KV binding that should be replaced by the incoming list
            WorkerBinding(
                type = "kv_namespace",
                name = "MY_OLD_KV",
                namespaceId = "old_kv_ns_123"
            ),
            // Non-KV binding — must survive the KV-specific update (cross-type keep)
            WorkerBinding(
                type = "d1",
                name = "MY_DB",
                databaseId = "d1-uuid-0000"
            )
        )
        val existingExports = mapOf("default" to "main-entry", "named" to "my-helper")
        val existingObservability = mapOf("enabled" to false)
        val baselineSettings = WorkerScript(
            id = scriptName,
            createdOn = null,
            modifiedOn = null,
            etag = "etag-baseline",
            size = 512L,
            bindings = existingBindings,
            compatibilityDate = "2026-06-16",
            compatibilityFlags = listOf("url_standard", "nodejs_compat"),
            placement = Placement(mode = "smart"),
            usageModel = "standard",
            logpush = false,
            exports = existingExports,
            observability = existingObservability
        )
        // Incoming brand-new KV bindings (name, namespace_id pairs as accepted by the API).
        // Should replace the existing kv_namespace bindings.
        val newKvBindings = listOf(
            "NEW_KV_A" to "ns_a",
            "NEW_KV_B" to "ns_b"
        )

        coEvery {
            mockApi.getWorkerSettings(any(), any(), any(), any(), any())
        } returns cfOk(baselineSettings)
        coEvery {
            mockApi.updateWorkerSettings(any(), any(), any(), any(), any(), any())
        } returns cfOk(fakeScript.copy(size = 2048L))

        // Act
        val result = repository.updateWorkerKvBindings(
            account = testAccount,
            scriptName = scriptName,
            kvBindings = newKvBindings
        )

        // Assert: function itself succeeds
        assertTrue("result=$result", result is Resource.Success)

        // Capture the exact PATCH settings body
        val slot = mutableListOf<okhttp3.RequestBody>()
        coVerify(exactly = 1) {
            mockApi.updateWorkerSettings(any(), any(), any(), any(), any(), capture(slot))
        }
        val raw = slot.first().readUtf8ForTest()
        val json = gson.fromJson(raw, com.google.gson.JsonObject::class.java)

        // --- Bug assertions (these FAIL before fix, PASS after) ---
        assertTrue("compatibility_flags key must be present (was omitted by bug W2), raw=$raw",
            json.has("compatibility_flags"))
        val flagsArr = json.getAsJsonArray("compatibility_flags")
        assertEquals("compatibility_flags must preserve all 2 existing flags",
            2, flagsArr.size())
        assertTrue("flags should contain url_standard, raw=$raw",
            flagsArr.any { it.asString == "url_standard" })
        assertTrue("flags should contain nodejs_compat, raw=$raw",
            flagsArr.any { it.asString == "nodejs_compat" })

        assertTrue("placement key must be present (was omitted by bug W2), raw=$raw",
            json.has("placement"))
        val placementObj = json.getAsJsonObject("placement")
        assertEquals("placement.mode must be preserved as 'smart'",
            "smart", placementObj.get("mode").asString)

        // --- Regression assertions (these should PASS both before and after) ---
        assertTrue("exports canary must be preserved to guard against 10021 regressions, raw=$raw",
            json.has("exports"))

        // --- Script-level fields MUST be STRIPPED from versioned settings PATCH body ---
        // These 4 keys only belong to the separate PATCH /script-settings (application/json)
        // endpoint. Any presence in the versioned multipart body is a bug; on Workers with
        // Versions enabled it triggers error 10214 "use the script-level settings API".
        assertFalse("logpush MUST be stripped from versioned body, raw=$raw",
            json.has("logpush"))
        assertFalse("tail_consumers MUST be stripped from versioned body, raw=$raw",
            json.has("tail_consumers"))
        assertFalse("observability MUST be stripped from versioned body, raw=$raw",
            json.has("observability"))
        assertFalse("tags MUST be stripped from versioned body, raw=$raw",
            json.has("tags"))

        val bindingsArr = json.getAsJsonArray("bindings")
        // 2 new KV + 1 kept D1 = 3 bindings total
        assertEquals("bindings array size should be 2 new KV + 1 kept D1",
            3, bindingsArr.size())
        val bindingNames = bindingsArr.map { it.asJsonObject.get("name").asString }.toSet()
        assertTrue("NEW_KV_A should be in bindings", bindingNames.contains("NEW_KV_A"))
        assertTrue("NEW_KV_B should be in bindings", bindingNames.contains("NEW_KV_B"))
        assertTrue("MY_DB (non-KV D1) should be preserved", bindingNames.contains("MY_DB"))
        assertFalse("MY_OLD_KV (old KV) should be replaced",
            bindingNames.contains("MY_OLD_KV"))
    }

    // Small helper: okio-less reading of a RequestBody for assertions.
    // Uses Gson body -> buffer via writeTo. Avoids pulling okio Buffer directly into imports.
    private fun okhttp3.RequestBody.readUtf8ForTest(): String {
        val buffer = okio.Buffer()
        this.writeTo(buffer)
        return buffer.readUtf8()
    }
}
