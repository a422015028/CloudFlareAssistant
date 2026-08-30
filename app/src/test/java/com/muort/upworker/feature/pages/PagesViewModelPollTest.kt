package com.muort.upworker.feature.pages

import android.content.Context
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.PagesDeployment
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.UiMessage
import com.muort.upworker.core.repository.PagesPollResult
import com.muort.upworker.core.repository.PagesRepository
import com.muort.upworker.core.util.EsbuildBundler
import com.muort.upworker.core.util.SucraseTransformer
import com.muort.upworker.core.network.CloudFlareApi
import io.mockk.impl.annotations.MockK
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PagesViewModelPollTest {

    @MockK(relaxed = true) lateinit var mockContext: Context
    @MockK(relaxed = true) lateinit var mockApi: CloudFlareApi
    @MockK(relaxed = true) lateinit var mockSucrase: SucraseTransformer
    @MockK(relaxed = true) lateinit var mockEsbuild: EsbuildBundler
    @MockK(relaxed = true) lateinit var mockRepo: PagesRepository
    private lateinit var vm: PagesViewModel
    private val dispatcher = StandardTestDispatcher()
    private val testAccount = Account(name = "test", accountId = "acc_x", token = "tok1")

    private fun fakeDeployment(id: String, projectName: String = "p"): PagesDeployment =
        PagesDeployment(
            id = id,
            shortId = id.take(8),
            projectName = projectName,
            projectId = "proj_x",
            environment = "production",
            url = "https://$projectName.pages.dev",
            aliases = listOf("$projectName.pages.dev"),
            createdOn = null,
            modifiedOn = null,
            latestStage = null,
            deploymentTrigger = null,
            stages = null,
            isSkipped = null,
            usesFunctions = null,
            envVars = null,
            buildConfig = null,
            source = null
        )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(dispatcher)
        every { mockContext.getString(any<Int>()) } answers { "str:${firstArg<Int>()}" }
        every { mockContext.getString(any<Int>(), *anyVararg<Any>()) } answers {
            val id = firstArg<Int>()
            val tail = invocation.args.drop(1)
            val flat = if (tail.size == 1 && tail[0] is Array<*>) (tail[0] as Array<*>).toList() else tail
            "str:$id|" + flat.joinToString(",")
        }
        // Build repository with mockContext so that test-controlled string flattening works.
        // NOTE: we use the mocked PagesRepository directly via coEvery stubbing below, so the
        // constructor-injected PagesRepository is the mock instance.
        vm = PagesViewModel(
            appContext = mockContext,
            pagesRepository = mockRepo
        )
    }

    @After fun tear() = Dispatchers.resetMain()

    // ------------------------------------------------------------------
    // Test (a): poll success → UiMessage pages_poll_success_format with projectName + aliases
    // ------------------------------------------------------------------
    @Test
    fun `poll success emits pages_poll_success_format with projectName and aliases`() = runTest {
        val zipFile = File.createTempFile("app_", ".zip")
        val fakeDep = fakeDeployment(id = "dep_123", projectName = "p")
        coEvery {
            mockRepo.createDeployment(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Resource.Success(fakeDep)
        coEvery {
            mockRepo.pollDeployment(any(), any(), any(), any(), any(), any(), any(), any())
        } returns PagesPollResult.Success(
            deploymentId = "dep_123",
            projectName = "p",
            aliases = listOf("a.pages.dev")
        )

        val emitted = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.message.collect { emitted.add(it) }
        }

        vm.createDeploymentWithLogs(
            account = testAccount,
            projectName = "p",
            baseDir = zipFile,
            prodBranch = "main",
            compatDate = null,
            compatFlags = null,
            buildMode = null,
            extraEnvVars = null,
            onLog = {}
        )
        advanceUntilIdle()

        assertTrue(
            "pages_poll_success_format present in emitted UiMessages",
            emitted.any { msg ->
                msg is UiMessage.ResourceString &&
                    msg.resId == R.string.pages_poll_success_format
            }
        )
    }

    // ------------------------------------------------------------------
    // Test (b): poll HTTP 500 Failure → pages_poll_failed_format contains "500"
    // ------------------------------------------------------------------
    @Test
    fun `poll Aborted with 500 cause emits pages_poll_failed_format containing 500`() = runTest {
        val zipFile = File.createTempFile("app_", ".zip")
        val fakeDep = fakeDeployment(id = "dep_456")
        coEvery {
            mockRepo.createDeployment(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Resource.Success(fakeDep)
        coEvery {
            mockRepo.pollDeployment(any(), any(), any(), any(), any(), any(), any(), any())
        } returns PagesPollResult.Aborted(RuntimeException("HTTP 500 Internal Server Error"))

        val emitted = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.message.collect { emitted.add(it) }
        }

        vm.createDeploymentWithLogs(
            account = testAccount,
            projectName = "p",
            baseDir = zipFile,
            prodBranch = "main",
            compatDate = null,
            compatFlags = null,
            buildMode = null,
            extraEnvVars = null,
            onLog = {}
        )
        advanceUntilIdle()

        val failedMsgs = emitted.filterIsInstance<UiMessage.ResourceString>()
            .filter { it.resId == R.string.pages_poll_failed_format || it.resId == R.string.pages_poll_aborted_format }
        assertTrue(
            "poll failure message emitted (found ${emitted.map { it.javaClass.simpleName }}): $failedMsgs",
            failedMsgs.isNotEmpty()
        )
        // Render with mockContext so "500" is visible in the formatted string.
        val rendered = failedMsgs.map { it.asString(mockContext) }
        assertTrue(
            "rendered failure messages contain '500': $rendered",
            rendered.any { it.contains("500") }
        )
    }

    // ------------------------------------------------------------------
    // Test (c): progressListener with backoff → onLog callback gets 2 messages
    //           (progress_format + backoff_format)
    // ------------------------------------------------------------------
    @Test
    fun `progressListener with backoff calls onLog with progress and backoff format strings`() = runTest {
        val zipFile = File.createTempFile("app_", ".zip")
        val fakeDep = fakeDeployment(id = "dep_789")
        coEvery {
            mockRepo.createDeployment(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Resource.Success(fakeDep)
        val onLogCalls: MutableList<String> = mutableListOf()
        coEvery {
            mockRepo.pollDeployment(any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            val onProgress = invocation.args[7] as? ((String, Int, Int, Long?) -> Unit)
            // Call progressListener with stageText="Building", poll=2, maxPolls=30, backoff=1500ms
            onProgress?.invoke("Building", 2, 30, 1500L)
            PagesPollResult.Success("dep_789", "p", listOf("p.pages.dev"))
        }

        vm.createDeploymentWithLogs(
            account = testAccount,
            projectName = "p",
            baseDir = zipFile,
            prodBranch = "main",
            compatDate = null,
            compatFlags = null,
            buildMode = null,
            extraEnvVars = null,
            onLog = { line -> onLogCalls.add(line) }
        )
        advanceUntilIdle()

        val progressStr = mockContext.getString(R.string.pages_poll_progress_format, 2, 30, "Building")
        val backoffStr = mockContext.getString(R.string.pages_poll_backoff_format, 1500L)
        assertTrue(
            "progress_format string in onLog calls (got: $onLogCalls)",
            onLogCalls.contains(progressStr)
        )
        assertTrue(
            "backoff_format string in onLog calls (got: $onLogCalls)",
            onLogCalls.contains(backoffStr)
        )
    }

    // ------------------------------------------------------------------
    // Test (d): buildSpecialFormData logEvents order preserved via
    //           Repository.createDeployment internal onLog forwarding.
    // ------------------------------------------------------------------
    @Test
    fun `buildSpecialFormData logEvents forwarded through onLog in order`() = runTest {
        val zipFile = File.createTempFile("app_", ".zip")
        val fakeDep = fakeDeployment(id = "dep_abc")
        val onLogCalls: MutableList<String> = mutableListOf()

        // Stub pollDeployment to return quick success so we only care about createDeployment flow.
        coEvery {
            mockRepo.pollDeployment(any(), any(), any(), any(), any(), any(), any(), any())
        } returns PagesPollResult.Success("dep_abc", "p", emptyList())

        // Simulate PagesRepository.createDeployment internally calling onLog 3 times
        // (representing 3 buildSpecialFormData logEvents forwarded in order).
        // Repository.createDeployment's onLog arg index = 8 (NOT suspend).
        coEvery {
            mockRepo.createDeployment(any(), any(), any(), any(), any(), any(), any(), any(), any())
        } coAnswers {
            @Suppress("UNCHECKED_CAST")
            val onLogFn = invocation.args[8] as ((String) -> Unit)
            onLogFn("log1")
            onLogFn("log2")
            onLogFn("log3")
            Resource.Success(fakeDep)
        }

        vm.createDeploymentWithLogs(
            account = testAccount,
            projectName = "p",
            baseDir = zipFile,
            prodBranch = "main",
            compatDate = null,
            compatFlags = null,
            buildMode = null,
            extraEnvVars = null,
            onLog = { line -> onLogCalls.add(line) }
        )
        advanceUntilIdle()

        // Assert onLog captured order preserves log1, log2, log3
        val sliced = onLogCalls.takeLast(3)
        assertEquals(
            "last 3 onLog invocations preserve log1,log2,log3 (got: $onLogCalls)",
            listOf("log1", "log2", "log3"),
            sliced
        )
    }
}
