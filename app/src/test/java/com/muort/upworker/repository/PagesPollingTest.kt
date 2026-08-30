package com.muort.upworker.repository

import android.content.Context
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.CloudFlareError
import com.muort.upworker.core.model.CloudFlareResponse
import com.muort.upworker.core.model.DeploymentStage
import com.muort.upworker.core.model.PagesDeployment
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.repository.PagesPollResult
import com.muort.upworker.core.repository.PagesRepository
import com.muort.upworker.core.util.AuthHelper
import com.muort.upworker.core.util.EsbuildBundler
import com.muort.upworker.core.util.SucraseTransformer
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class PagesPollingTest {

    private lateinit var repository: PagesRepository

    @MockK lateinit var mockContext: Context
    @MockK lateinit var mockApi: CloudFlareApi
    @MockK(relaxed = true) lateinit var mockSucrase: SucraseTransformer
    @MockK(relaxed = true) lateinit var mockEsbuild: EsbuildBundler

    private val testAccount = Account(
        accountId = "acct_1",
        token = "tok_x",
        name = "test"
    )
    private val projectName = "my-project"
    private val deploymentId = "dep_abc123"

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        mockkObject(AuthHelper)
        every { AuthHelper.getBearerToken(testAccount) } returns "Bearer tok_x"
        every { AuthHelper.getEmail(testAccount) } returns null
        every { AuthHelper.getGlobalApiKey(testAccount) } returns null
        // Context.getString fallback: return the formatted res string without Robolectric
        every { mockContext.getString(any<Int>()) } answers { "str:${firstArg<Int>()}" }
        every { mockContext.getString(any<Int>(), *anyVararg<Any>()) } answers {
            val id = firstArg<Int>()
            // MockK stores *anyVararg elements as a single Array<*> in invocation.args[1]
            // (NOT spread), so drop(1) returns [Array<*>]; flatten it.
            val tail = invocation.args.drop(1)
            val flatArgs: List<Any?> = when {
                tail.size == 1 && tail[0] is Array<*> -> (tail[0] as Array<*>).toList()
                else -> tail
            }
            val args = flatArgs.joinToString(",") { it.toString() }
            "str:$id|args=$args"
        }
        repository = PagesRepository(
            appContext = mockContext,
            api = mockApi,
            sucraseTransformer = mockSucrase,
            esbuildBundler = mockEsbuild
        )
    }

    @After
    fun tearDown() { /* jvm shutdown cleanup */ }

    // ===== Helpers =====

    private fun <T : Any> cfOk(t: T): Response<CloudFlareResponse<T>> {
        return Response.success(CloudFlareResponse(success = true, errors = emptyList(),
            messages = emptyList(), result = t))
    }

    private fun <T : Any> cfError(code: Int, msg: String = "boom"): Response<CloudFlareResponse<T>> {
        val body = CloudFlareResponse<T>(success = false,
            errors = listOf(CloudFlareError(code, msg)),
            messages = emptyList(), result = null)
        return Response.error(code,
            "{\"success\":false,\"errors\":[{\"code\":$code,\"message\":\"$msg\"}]}"
                .toResponseBody("application/json".toMediaType()))
    }

    private fun mkStage(name: String): DeploymentStage =
        DeploymentStage(name = name, status = "current", startedOn = null, endedOn = null)

    private fun mkDeployment(deploymentId: String, stageName: String, aliases: List<String> = emptyList()): PagesDeployment =
        PagesDeployment(
            id = deploymentId,
            shortId = deploymentId.take(8),
            projectName = projectName,
            projectId = "proj_x",
            environment = "production",
            url = "https://$projectName.pages.dev",
            aliases = aliases,
            createdOn = "2026-08-31T00:00:00Z",
            modifiedOn = "2026-08-31T00:00:00Z",
            latestStage = mkStage(stageName),
            deploymentTrigger = null,
            stages = null,
            isSkipped = null,
            usesFunctions = null,
            envVars = null,
            buildConfig = null,
            source = null
        )

    // =========================================================================
    // RED 1: Progresses through stages and terminates at Success
    //   Stage sequence: Queued → Building → Success → polling terminates.
    //   onProgress must be called 3 times with matching stage names.
    // =========================================================================
    @Test
    fun `pollDeployment advances Queued then Building then Success and terminates with Success result`() = runTest {
        val slotStageCalls = mutableListOf<String>()
        var polls = 0
        coEvery {
            mockApi.getPagesDeployment(any(), any(), any(), any(), any(), any())
        } answers {
            polls++
            when (polls) {
                1 -> cfOk(mkDeployment(deploymentId, "queued"))
                2 -> cfOk(mkDeployment(deploymentId, "building"))
                else -> cfOk(mkDeployment(deploymentId, "success",
                    aliases = listOf("alias1.example.com", "alias2.pages.dev")))
            }
        }
        val progressEvents = mutableListOf<Triple<String, Int, Long?>>()
        val result = repository.pollDeployment(
            account = testAccount,
            projectName = projectName,
            deploymentId = deploymentId,
            maxPolls = 10,
            initialDelayMs = 100L,
            backoffMultiplier = 1.0,
            capDelayMs = 500L,
            onProgress = { stageText, poll, _, backoff ->
                progressEvents += Triple(stageText, poll, backoff)
            }
        )
        assertTrue("result should be Success; got $result", result is PagesPollResult.Success)
        val s = result as PagesPollResult.Success
        assertEquals(deploymentId, s.deploymentId)
        assertEquals(projectName, s.projectName)
        assertEquals(2, s.aliases.size)
        // Exactly 3 polls (stopped after success)
        assertEquals(3, polls)
        // 3 progress events emitted
        assertEquals(3, progressEvents.size)
        // first/second poll may have backoff (no backoff before first); verify all had increasing poll numbers
        assertEquals(1, progressEvents[0].second)
        assertEquals(2, progressEvents[1].second)
        assertEquals(3, progressEvents[2].second)
        // only network for getPagesDeployment (no listPagesDeployments fallback)
        coVerify(atLeast = 3) {
            mockApi.getPagesDeployment(
                token = "Bearer tok_x",
                email = null,
                apiKey = null,
                accountId = testAccount.accountId,
                projectName = projectName,
                deploymentId = deploymentId
            )
        }
    }

    // =========================================================================
    // RED 2: Poll returns Failure stage → result is PagesPollResult.Failure with stage+error
    // =========================================================================
    @Test
    fun `pollDeployment returns Failure PagesPollResult when deployment reaches failed stage`() = runTest {
        coEvery {
            mockApi.getPagesDeployment(any(), any(), any(), any(), any(), any())
        } returns cfOk(mkDeployment(deploymentId, "failed").copy(
            stages = listOf(
                DeploymentStage(name = "build", status = "failed",
                    startedOn = null, endedOn = null),
                DeploymentStage(name = "failed", status = "current",
                    startedOn = null, endedOn = null)
            )
        ))
        val result = repository.pollDeployment(
            account = testAccount,
            projectName = projectName,
            deploymentId = deploymentId,
            maxPolls = 5,
            initialDelayMs = 50L
        )
        assertTrue("result should be Failure; got $result", result is PagesPollResult.Failure)
        val f = result as PagesPollResult.Failure
        assertEquals("failed", f.latestStageName)
        // Only one HTTP call: failure means immediate terminate
        coVerify(exactly = 1) { mockApi.getPagesDeployment(any(), any(), any(), any(), any(), any()) }
    }

    // =========================================================================
    // RED 3: All polls show same stage (Queued) → backoff grows to cap and eventually
    //   maxPolls exhausted returns Timeout.
    //   We use maxPolls=3 here to keep test fast; backoff 100→150→225 (no cap hit).
    // =========================================================================
    @Test
    fun `pollDeployment returns Timeout PagesPollResult when maxPolls reached with stage stagnant`() = runTest {
        var polls = 0
        coEvery {
            mockApi.getPagesDeployment(any(), any(), any(), any(), any(), any())
        } answers {
            polls++
            cfOk(mkDeployment(deploymentId, "queued"))
        }
        val delaysSeen = mutableListOf<Long?>()
        val result = repository.pollDeployment(
            account = testAccount,
            projectName = projectName,
            deploymentId = deploymentId,
            maxPolls = 3,
            initialDelayMs = 100L,
            backoffMultiplier = 1.5,
            capDelayMs = 500L,
            onProgress = { _, _, _, backoff -> delaysSeen += backoff }
        )
        assertTrue("result should be Timeout; got $result", result is PagesPollResult.Timeout)
        val t = result as PagesPollResult.Timeout
        assertEquals(3, t.maxPolls)
        assertEquals("queued", t.lastStageName)
        assertEquals(3, polls)
        // Expected 3 delay callbacks: after poll 1 (100ms), after poll 2 (150ms), and before poll 3 — 
        // actually the test design emits delay BEFORE each poll except the first → 
        // so for 3 polls: poll1 no-delay emitted; emit delay100 pre-poll2; emit delay150 pre-poll3.
        // We accept: size should be at least 2 (post-poll 1 and post-poll 2 delays).
        assertTrue("expected ≥2 delay events, got ${delaysSeen.filterNotNull()}",
            delaysSeen.filterNotNull().size >= 2)
        // backoff values increase monotonically (100 → 150)
        val nonNull = delaysSeen.filterNotNull()
        for (i in 1 until nonNull.size) {
            assertTrue("backoff must not shrink: ${nonNull[i-1]} → ${nonNull[i]}",
                nonNull[i] >= nonNull[i-1])
        }
    }

    // =========================================================================
    // RED 4: Transient HTTP-500 bubble-up as Aborted PagesPollResult (not crash)
    // =========================================================================
    @Test
    fun `pollDeployment returns Aborted PagesPollResult on HTTP 500`() = runTest {
        coEvery {
            mockApi.getPagesDeployment(any(), any(), any(), any(), any(), any())
        } returns cfError(500, "server broken")
        val result = repository.pollDeployment(
            account = testAccount,
            projectName = projectName,
            deploymentId = deploymentId,
            maxPolls = 3,
            initialDelayMs = 50L
        )
        assertTrue("result should be Aborted; got $result", result is PagesPollResult.Aborted)
    }

    // =========================================================================
    // RED 5: deploymentId=null → use listPagesDeployments first element for stage query
    // =========================================================================
    @Test
    fun `pollDeployment falls back to listPagesDeployments when deploymentId is null`() = runTest {
        val listResp = listOf(mkDeployment("latest_99", "success",
            aliases = listOf("project.pages.dev")))
        coEvery {
            mockApi.listPagesDeployments(any(), any(), any(), any(), any())
        } returns cfOk(listResp)
        val result = repository.pollDeployment(
            account = testAccount,
            projectName = projectName,
            deploymentId = null,
            maxPolls = 5,
            initialDelayMs = 50L
        )
        assertTrue("result should be Success; got $result", result is PagesPollResult.Success)
        assertEquals("latest_99", (result as PagesPollResult.Success).deploymentId)
        coVerify(exactly = 1) { mockApi.listPagesDeployments(any(), any(), any(), any(), any()) }
        // Since deploymentId was resolved from list → we still call listPagesDeployments but NOT
        // the single deployment GET.
        coVerify(exactly = 0) { mockApi.getPagesDeployment(any(), any(), any(), any(), any(), any()) }
    }

    // =========================================================================
    // RED 6: Unknown stage name → use pages_poll_stage_unknown (%1$s = raw name) fallback string
    // =========================================================================
    @Test
    fun `pollDeployment maps unknown stage name to pages_poll_stage_unknown format string`() = runTest {
        var polls = 0
        coEvery {
            mockApi.getPagesDeployment(any(), any(), any(), any(), any(), any())
        } answers {
            polls++
            if (polls < 2) cfOk(mkDeployment(deploymentId, "pre_build_custom"))
            else cfOk(mkDeployment(deploymentId, "success", aliases = listOf("x.pages.dev")))
        }
        val seenStages = mutableListOf<String>()
        repository.pollDeployment(
            account = testAccount,
            projectName = projectName,
            deploymentId = deploymentId,
            maxPolls = 5,
            initialDelayMs = 50L,
            onProgress = { st, _, _, _ -> seenStages += st }
        )
        assertTrue("first stage should include unknown format string; got ${seenStages.firstOrNull()}",
            seenStages.firstOrNull()?.contains("pre_build_custom") == true)
    }
}
