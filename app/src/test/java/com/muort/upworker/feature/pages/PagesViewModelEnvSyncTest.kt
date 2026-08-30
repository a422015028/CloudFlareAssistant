package com.muort.upworker.feature.pages

import android.content.Context
import com.muort.upworker.R
import com.muort.upworker.core.model.Account
import com.muort.upworker.core.model.EnvironmentConfig
import com.muort.upworker.core.model.PagesEnvSyncResult
import com.muort.upworker.core.model.UiMessage
import com.muort.upworker.core.repository.PagesRepository
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

@OptIn(ExperimentalCoroutinesApi::class)
class PagesViewModelEnvSyncTest {

    @MockK(relaxed = true) lateinit var mockContext: Context
    @MockK(relaxed = true) lateinit var mockRepo: PagesRepository
    private lateinit var vm: PagesViewModel
    private val dispatcher = StandardTestDispatcher()
    private val testAccount = Account(name = "test", accountId = "acc_x", token = "tok1")

    private fun emptyEnv(): EnvironmentConfig = EnvironmentConfig(
        envVars = emptyMap(),
        kvNamespaces = emptyMap(),
        r2Buckets = emptyMap(),
        d1Databases = emptyMap(),
        durableObjects = emptyMap(),
        services = emptyMap(),
        compatibilityDate = null,
        compatibilityFlags = null,
        placement = null
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
        vm = PagesViewModel(
            appContext = mockContext,
            pagesRepository = mockRepo
        )
    }

    @After fun tear() = Dispatchers.resetMain()

    // ------------------------------------------------------------------
    // Test (a): sync both OK → UiMessage pages_env_sync_ok_format args [3,2,1,2,4]
    // ------------------------------------------------------------------
    @Test
    fun `syncDualEnvConfigs success emits pages_env_sync_ok_format with 5 counts`() = runTest {
        coEvery {
            mockRepo.syncDualEnvConfigs(any(), any(), any())
        } returns PagesEnvSyncResult.Success(
            envVarsCount = 3, kvCount = 2, d1Count = 1, r2Count = 2, servicesCount = 4
        )

        val emitted = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.message.collect { emitted.add(it) }
        }

        vm.syncDualEnvConfigs(testAccount, "proj", emptyEnv())
        advanceUntilIdle()

        val okMsgs = emitted.filterIsInstance<UiMessage.ResourceString>()
            .filter { it.resId == R.string.pages_env_sync_ok_format }
        assertTrue(
            "pages_env_sync_ok_format present (got resIds: ${emitted.map { (it as? UiMessage.ResourceString)?.resId }})",
            okMsgs.isNotEmpty()
        )
        val args = okMsgs.first().args
        assertEquals(
            "pages_env_sync_ok_format 5 format args match [3,2,1,2,4]",
            listOf(3, 2, 1, 2, 4),
            args.toList()
        )
    }

    // ------------------------------------------------------------------
    // Test (b): ProductionFail HTTP 500 → pages_env_sync_production_fail_format substring "500"
    // ------------------------------------------------------------------
    @Test
    fun `syncDualEnvConfigs ProductionFail 500 emits pages_env_sync_production_fail_format with 500`() = runTest {
        coEvery {
            mockRepo.syncDualEnvConfigs(any(), any(), any())
        } returns PagesEnvSyncResult.ProductionFail("HTTP 500 Internal Server Error")

        val emitted = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.message.collect { emitted.add(it) }
        }

        vm.syncDualEnvConfigs(testAccount, "proj", emptyEnv())
        advanceUntilIdle()

        val failMsgs = emitted.filterIsInstance<UiMessage.ResourceString>()
            .filter { it.resId == R.string.pages_env_sync_production_fail_format }
        assertTrue(
            "pages_env_sync_production_fail_format present (got resIds: ${emitted.map { (it as? UiMessage.ResourceString)?.resId }})",
            failMsgs.isNotEmpty()
        )
        val rendered = failMsgs.first().asString(mockContext)
        assertTrue(
            "rendered production_fail message contains '500': $rendered",
            rendered.contains("500")
        )
    }

    // ------------------------------------------------------------------
    // Test (c): PreviewFail HTTP 400 → pages_env_sync_preview_fail_format substring "400"
    // ------------------------------------------------------------------
    @Test
    fun `syncDualEnvConfigs PreviewFail 400 emits pages_env_sync_preview_fail_format with 400`() = runTest {
        coEvery {
            mockRepo.syncDualEnvConfigs(any(), any(), any())
        } returns PagesEnvSyncResult.PreviewFail("HTTP 400 Bad Request")

        val emitted = mutableListOf<UiMessage>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.message.collect { emitted.add(it) }
        }

        vm.syncDualEnvConfigs(testAccount, "proj", emptyEnv())
        advanceUntilIdle()

        val failMsgs = emitted.filterIsInstance<UiMessage.ResourceString>()
            .filter { it.resId == R.string.pages_env_sync_preview_fail_format }
        assertTrue(
            "pages_env_sync_preview_fail_format present (got resIds: ${emitted.map { (it as? UiMessage.ResourceString)?.resId }})",
            failMsgs.isNotEmpty()
        )
        val rendered = failMsgs.first().asString(mockContext)
        assertTrue(
            "rendered preview_fail message contains '400': $rendered",
            rendered.contains("400")
        )
    }
}
