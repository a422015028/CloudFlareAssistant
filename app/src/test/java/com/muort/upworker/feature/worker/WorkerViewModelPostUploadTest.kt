package com.muort.upworker.feature.worker

import android.content.Context
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.Resource
import com.muort.upworker.core.model.UiMessage
import com.muort.upworker.core.model.WorkerMetadata
import com.muort.upworker.core.model.WorkerScript
import com.muort.upworker.core.model.WorkerSettingsRequest
import com.muort.upworker.core.repository.WorkerAfterUploadResult
import com.muort.upworker.core.repository.WorkerNodejsDetectResult
import com.muort.upworker.core.repository.WorkerPostActionStage
import com.muort.upworker.core.repository.WorkerPostStageKind
import com.muort.upworker.core.repository.WorkerRepository
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
class WorkerViewModelPostUploadTest {

    @MockK(relaxed = true) lateinit var mockRepo: WorkerRepository
    @MockK(relaxed = true) lateinit var mockCtx: Context
    private lateinit var vm: WorkerViewModel
    private val dispatcher = StandardTestDispatcher()
    private val testAccount = Account(name = "test", accountId = "acc_x", token = "tok1")
    private val fakeScript = WorkerScript(
        id = "sc-name",
        createdOn = null,
        modifiedOn = null,
        etag = "etag_x",
        size = 128L
    )

    @Before
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        Dispatchers.setMain(dispatcher)
        every { mockCtx.getString(any<Int>()) } answers { "str:${firstArg<Int>()}" }
        every { mockCtx.getString(any<Int>(), *anyVararg<Any>()) } answers {
            val id = firstArg<Int>()
            val tail = invocation.args.drop(1)
            val flat = if (tail.size == 1 && tail[0] is Array<*>) (tail[0] as Array<*>).toList() else tail
            "str:$id|" + flat.joinToString(",")
        }
        vm = WorkerViewModel(appContext = mockCtx, workerRepository = mockRepo)
    }

    @After fun tear() = Dispatchers.resetMain()

    // ------------------------------------------------------------------
    // Test 1: observability stage Success → UiMessage matches worker_post_observability_applying
    // ------------------------------------------------------------------
    @Test
    fun `uploadWithPostFlow observability stage Success emits ResourceString`() = runTest {
        val scriptFile = File.createTempFile("scr_", ".js")
        // Stub getWorkerSettings as Resource.Success (preserve path)
        val emptySettings = WorkerScript(
            id = "sc-name", createdOn = null, modifiedOn = null, etag = "e", size = 128L,
            bindings = emptyList(), compatibilityDate = null, compatibilityFlags = null
        )
        coEvery { mockRepo.getWorkerSettings(any(), any()) } returns Resource.Success(emptySettings)
        coEvery { mockRepo.uploadWorkerScriptMultipart(any(), any(), any(), any()) } returns Resource.Success(fakeScript)
        coEvery { mockRepo.detectAndAppendNodejsCompat(any(), any()) } returns WorkerNodejsDetectResult(
            finalFlags = emptyList(),
            hitPatterns = emptyList(),
            logResId = R.string.worker_nodejs_detect_no_hit,
            logFormatArgs = emptyArray()
        )
        coEvery { mockRepo.afterUpload(any(), any(), any(), any(), any()) } returns WorkerAfterUploadResult(
            overallUpload = Resource.Success(fakeScript),
            stages = listOf(
                WorkerPostActionStage.Success(
                    kind = WorkerPostStageKind.Observability,
                    messageResId = R.string.worker_post_observability_applying,
                    formatArgs = emptyArray()
                ),
                WorkerPostActionStage.Success(
                    kind = WorkerPostStageKind.Subdomain,
                    messageResId = R.string.worker_post_subdomain_already,
                    formatArgs = emptyArray()
                ),
                WorkerPostActionStage.Success(
                    kind = WorkerPostStageKind.Deployment,
                    messageResId = R.string.worker_post_deploy_deploying,
                    formatArgs = arrayOf(100)
                )
            )
        )

        val emitted = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.message.collect { emitted.add(it) } }
        vm.uploadWorkerScriptWithBindings(testAccount, "sc-name", scriptFile)
        advanceUntilIdle()

        assertTrue(
            "stage observability applying in emitted",
            emitted.any {
                it is UiMessage.ResourceString && it.resId == R.string.worker_post_observability_applying
            }
        )
    }

    // ------------------------------------------------------------------
    // Test 2: subdomain 403 failure → UiMessage contains hint; subsequent stage still runs
    // ------------------------------------------------------------------
    @Test
    fun `subdomain 403 failure stage emits hint without aborting subsequent deployment`() = runTest {
        val scriptFile = File.createTempFile("scr_", ".js")
        val emptySettings = WorkerScript(
            id = "sc-name", createdOn = null, modifiedOn = null, etag = "e", size = 128L,
            bindings = emptyList(), compatibilityDate = null, compatibilityFlags = null
        )
        coEvery { mockRepo.getWorkerSettings(any(), any()) } returns Resource.Success(emptySettings)
        coEvery { mockRepo.uploadWorkerScriptMultipart(any(), any(), any(), any()) } returns Resource.Success(fakeScript)
        coEvery { mockRepo.detectAndAppendNodejsCompat(any(), any()) } returns WorkerNodejsDetectResult(
            finalFlags = emptyList(),
            hitPatterns = emptyList(),
            logResId = R.string.worker_nodejs_detect_no_hit,
            logFormatArgs = emptyArray()
        )
        val errorMsg403 = "HTTP 403 — " +
            mockCtx.getString(R.string.worker_post_subdomain_403_dashboard_hint)
        coEvery { mockRepo.afterUpload(any(), any(), any(), any(), any()) } returns WorkerAfterUploadResult(
            overallUpload = Resource.Success(fakeScript),
            stages = listOf(
                WorkerPostActionStage.Success(
                    kind = WorkerPostStageKind.Observability,
                    messageResId = R.string.worker_post_observability_ok,
                    formatArgs = emptyArray()
                ),
                WorkerPostActionStage.Failure(
                    kind = WorkerPostStageKind.Subdomain,
                    messageResId = R.string.worker_post_subdomain_fail_format,
                    formatArgs = arrayOf(errorMsg403)
                ),
                WorkerPostActionStage.Success(
                    kind = WorkerPostStageKind.Deployment,
                    messageResId = R.string.worker_post_deploy_deploying,
                    formatArgs = arrayOf(100)
                )
            )
        )

        val emitted = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.message.collect { emitted.add(it) } }
        vm.uploadWorkerScriptWithBindings(testAccount, "sc-name", scriptFile)
        advanceUntilIdle()

        // Failure stage UiMessage emitted (subdomain_fail_format resId present)
        assertTrue(
            "subdomain fail format resId emitted",
            emitted.any {
                it is UiMessage.ResourceString && it.resId == R.string.worker_post_subdomain_fail_format
            }
        )
        // Failure formatArgs contains 403 dashboard hint (verify via asString on mockCtx)
        val failMsg = emitted.filterIsInstance<UiMessage.ResourceString>()
            .first { it.resId == R.string.worker_post_subdomain_fail_format }
        val rendered = failMsg.asString(mockCtx)
        assertTrue(
            "rendered fail msg contains worker_post_subdomain_403_dashboard_hint string. Got: $rendered",
            rendered.contains("str:" + R.string.worker_post_subdomain_403_dashboard_hint)
        )
        // Subsequent deployment stage is NOT skipped — deployment stage message present
        assertTrue(
            "deployment stage still emitted after subdomain failure",
            emitted.any {
                it is UiMessage.ResourceString && it.resId == R.string.worker_post_deploy_deploying
            }
        )
    }

    // ------------------------------------------------------------------
    // Test 3: nodejs detect flags changed → second uploadMultipart called with appended flag
    // ------------------------------------------------------------------
    @Test
    fun `nodejs detect flag changed triggers second uploadMultipart with appended flags`() = runTest {
        val scriptFile = File.createTempFile("scr_", ".js")
        @Suppress("DEPRECATION")
        scriptFile.writeText("const fs = require('node:fs')")
        val settingsWithNoFlags = WorkerScript(
            id = "sc-name", createdOn = null, modifiedOn = null, etag = "e", size = 128L,
            bindings = emptyList(), compatibilityDate = "2026-06-16", compatibilityFlags = emptyList()
        )
        coEvery { mockRepo.getWorkerSettings(any(), any()) } returns Resource.Success(settingsWithNoFlags)
        // Return different fakeScript for each call
        coEvery { mockRepo.uploadWorkerScriptMultipart(any(), any(), any(), any()) } returnsMany listOf(
            Resource.Success(fakeScript),
            Resource.Success(fakeScript.copy(etag = "etag_after_flag"))
        )
        // Changed case: original=[], final=["nodejs_compat"], logResId=hit_hint_format
        coEvery { mockRepo.detectAndAppendNodejsCompat(any(), any()) } returns WorkerNodejsDetectResult(
            finalFlags = listOf("nodejs_compat"),
            hitPatterns = listOf("""require("node:" (内置模块)"""),
            logResId = R.string.worker_nodejs_detect_hit_hint_format,
            logFormatArgs = arrayOf("""require("node:" (内置模块)""")
        )
        coEvery { mockRepo.afterUpload(any(), any(), any(), any(), any()) } returns WorkerAfterUploadResult(
            overallUpload = Resource.Success(fakeScript),
            stages = listOf(
                WorkerPostActionStage.Success(
                    kind = WorkerPostStageKind.Observability,
                    messageResId = R.string.worker_post_observability_ok,
                    formatArgs = emptyArray()
                ),
                WorkerPostActionStage.Success(
                    kind = WorkerPostStageKind.Subdomain,
                    messageResId = R.string.worker_post_subdomain_already,
                    formatArgs = emptyArray()
                ),
                WorkerPostActionStage.Success(
                    kind = WorkerPostStageKind.Deployment,
                    messageResId = R.string.worker_post_deploy_deploying,
                    formatArgs = arrayOf(100)
                )
            )
        )

        val emitted = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.message.collect { emitted.add(it) } }
        vm.uploadWorkerScriptWithBindings(testAccount, "sc-name", scriptFile)
        advanceUntilIdle()

        // Assert uploadWorkerScriptMultipart called exactly TWICE
        coVerify(exactly = 2) { mockRepo.uploadWorkerScriptMultipart(any(), any(), any(), any()) }

        // Assert second invocation metadata has compatibilityFlags = ["nodejs_compat"]
        val metadatas = mutableListOf<WorkerMetadata?>()
        coVerify(exactly = 2) {
            mockRepo.uploadWorkerScriptMultipart(any(), any(), any(), captureNullable(metadatas))
        }
        assertEquals("2 upload calls → 2 captured metadatas", 2, metadatas.size)
        // First call: compatibilityFlags=[] (original from settings)
        assertTrue(
            "first upload metadata flags empty or null. actual: ${metadatas[0]?.compatibilityFlags}",
            metadatas[0]?.compatibilityFlags.isNullOrEmpty()
        )
        // Second call: compatibilityFlags must contain appended nodejs_compat
        assertEquals(
            "second upload metadata flags = ['nodejs_compat']",
            listOf("nodejs_compat"),
            metadatas[1]?.compatibilityFlags
        )

        // hit log event emitted: worker_nodejs_detect_hit_hint_format message
        assertTrue(
            "log event hit_hint_format emitted",
            emitted.any {
                it is UiMessage.ResourceString && it.resId == R.string.worker_nodejs_detect_hit_hint_format
            }
        )
    }

    // ------------------------------------------------------------------
    // Test 4: three stages independent — stage1 failure → still runs stage2 & stage3
    // ------------------------------------------------------------------
    @Test
    fun `three stages independent — stage1 failure still runs stage2 and stage3`() = runTest {
        val scriptFile = File.createTempFile("scr_", ".js")
        val emptySettings = WorkerScript(
            id = "sc-name", createdOn = null, modifiedOn = null, etag = "e", size = 128L,
            bindings = emptyList(), compatibilityDate = null, compatibilityFlags = null
        )
        coEvery { mockRepo.getWorkerSettings(any(), any()) } returns Resource.Success(emptySettings)
        coEvery { mockRepo.uploadWorkerScriptMultipart(any(), any(), any(), any()) } returns Resource.Success(fakeScript)
        coEvery { mockRepo.detectAndAppendNodejsCompat(any(), any()) } returns WorkerNodejsDetectResult(
            finalFlags = emptyList(),
            hitPatterns = emptyList(),
            logResId = R.string.worker_nodejs_detect_no_hit,
            logFormatArgs = emptyArray()
        )
        // stage1=Failure, stage2=Success, stage3=Success → all 3 stages emitted independently
        coEvery { mockRepo.afterUpload(any(), any(), any(), any(), any()) } returns WorkerAfterUploadResult(
            overallUpload = Resource.Success(fakeScript),
            stages = listOf(
                WorkerPostActionStage.Failure(
                    kind = WorkerPostStageKind.Observability,
                    messageResId = R.string.worker_post_observability_fail_format,
                    formatArgs = arrayOf("PATCH 500 internal error")
                ),
                WorkerPostActionStage.Success(
                    kind = WorkerPostStageKind.Subdomain,
                    messageResId = R.string.worker_post_subdomain_already,
                    formatArgs = emptyArray()
                ),
                WorkerPostActionStage.Success(
                    kind = WorkerPostStageKind.Deployment,
                    messageResId = R.string.worker_post_deploy_ok_format,
                    formatArgs = arrayOf(7, 100)
                )
            )
        )

        val emitted = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.message.collect { emitted.add(it) } }
        vm.uploadWorkerScriptWithBindings(testAccount, "sc-name", scriptFile)
        advanceUntilIdle()

        val resStrings = emitted.filterIsInstance<UiMessage.ResourceString>()
        // Stage 1 failure present
        assertTrue(
            "stage1 observability failure emitted",
            resStrings.any { it.resId == R.string.worker_post_observability_fail_format }
        )
        // Stage 2 subdomain still emitted (not aborted)
        assertTrue(
            "stage2 subdomain emitted after stage1 failure",
            resStrings.any { it.resId == R.string.worker_post_subdomain_already }
        )
        // Stage 3 deployment still emitted (not aborted)
        assertTrue(
            "stage3 deployment emitted after stage1 failure",
            resStrings.any { it.resId == R.string.worker_post_deploy_ok_format }
        )
        // Exactly 3 post-stage messages + 1 detect log message + 1 original upload success = >=5.
        // The ordering: upload success message → detect log → observability_fail → subdomain → deploy_ok.
        // We check that observability_fail (from afterUpload) appears BEFORE deploy_ok to
        // confirm forEach order-preservation.
        val idxFail = resStrings.indexOfFirst { it.resId == R.string.worker_post_observability_fail_format }
        val idxDeploy = resStrings.indexOfFirst { it.resId == R.string.worker_post_deploy_ok_format }
        assertTrue(
            "stage observability_fail ($idxFail) must appear before deploy_ok ($idxDeploy) in message order",
            idxFail in 0 until idxDeploy
        )
    }
}
