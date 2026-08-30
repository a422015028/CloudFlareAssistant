package com.muort.upworker.repository

import android.content.Context
import com.muort.upworker.R
import com.muort.upworker.core.repository.PagesRepository
import com.muort.upworker.core.repository.PagesSpecialFormDataResult
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
import kotlinx.coroutines.runBlocking
import okhttp3.MultipartBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * P1-2 RED failing tests for PagesRepository.buildSpecialFormData.
 *
 * Coverage → pages_formdata_* 11 keys:
 *  Test 1: _worker.js only       → nested wrapped; log: workerjs_nested_building / compat_date_auto
 *  Test 2: _worker.bundle + .js  → prefer bundle; skip .js;  log: bundle_applied + special_skipped_format
 *  Test 3: _headers              → attached;                   log: headers_applied
 *  Test 4: _redirects            → attached;                   log: redirects_applied
 *  Test 5: _routes.json + no custom date → routes applied + compat auto injected;
 *                                      log: routes_json_applied + compat_date_auto_format
 */
class PagesFormDataTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var repository: PagesRepository

    @MockK lateinit var mockContext: Context
    @MockK(relaxed = true) lateinit var mockApi: com.muort.upworker.core.network.CloudFlareApi
    @MockK(relaxed = true) lateinit var mockSucrase: SucraseTransformer
    @MockK(relaxed = true) lateinit var mockEsbuild: EsbuildBundler

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
            val argsStr = flat.joinToString(",") { it.toString() }
            "str:$id|args=$argsStr"
        }
        repository = PagesRepository(
            appContext = mockContext,
            api = mockApi,
            sucraseTransformer = mockSucrase,
            esbuildBundler = mockEsbuild
        )
    }

    @After
    fun tearDown() { /* jvm teardown */ }

    // ================== Helpers ==================

    private fun writeFile(dir: File, name: String, content: String): File {
        val f = File(dir, name)
        f.parentFile?.mkdirs()
        f.writeText(content)
        return f
    }

    /** Extract just the R.string ids from result.logEvents (drop formatArgs). */
    private fun logIds(r: PagesSpecialFormDataResult): List<Int> = r.logEvents.map { it.first }

    /** File names of special parts, matched 1:1 to result.specialFileNames. */
    private fun specialNames(r: PagesSpecialFormDataResult): List<String> = r.specialFileNames

    // ========================================================================
    // Test 1: _worker.js (alone) → nested wrapped, NOT flat _worker.bundle
    // ========================================================================
    @Test
    fun `buildSpecialFormData wraps _worker_worker js into nested multipart when only _worker dot js present`() {
        val base = tmp.newFolder("ziproot")
        writeFile(base, "_worker.js", "export default {\n  async fetch(req) { return new Response('hi') }\n}")
        val result = runBlocking {
            repository.buildSpecialFormData(baseDir = base, compatibilityDate = null)
        }
        assertNotNull("workerBody should not be null for _worker.js", result.workerBody)
        assertEquals("_worker.js", result.workerName)
        // Nested-building log must be emitted
        assertTrue(
            "log must contain workerjs_nested_building (${logIds(result).map { "R#$it" }})",
            R.string.pages_formdata_workerjs_nested_building in logIds(result)
        )
        // No bundle preference log
        assertFalse(R.string.pages_formdata_bundle_applied in logIds(result))
    }

    // ========================================================================
    // Test 2: _worker.bundle + _worker.js BOTH present → prefer bundle, skip .js
    // ========================================================================
    @Test
    fun `buildSpecialFormData prefers _worker_worker bundle over _worker_worker js and emits skip log`() {
        val base = tmp.newFolder("ziproot2")
        writeFile(base, "_worker.js", "should be skipped")
        writeFile(base, "_worker.bundle", "BUNDLE BINARY CONTENT")
        val result = runBlocking {
            repository.buildSpecialFormData(baseDir = base, compatibilityDate = "2026-08-31")
        }
        assertNotNull("workerBody should not be null when bundle exists", result.workerBody)
        assertEquals("_worker.bundle", result.workerName)
        assertTrue(
            "log must contain pages_formdata_bundle_applied",
            R.string.pages_formdata_bundle_applied in logIds(result)
        )
        // special_skipped_format must be emitted — with args [_worker.js, _worker.bundle]
        val skipped = result.logEvents.firstOrNull {
            it.first == R.string.pages_formdata_special_skipped_format
        }
        assertNotNull("log must contain pages_formdata_special_skipped_format", skipped)
        // formatArgs must be two strings: "_worker.js", "_worker.bundle"
        val args = skipped!!.second
        assertEquals("skipped format expects 2 args", 2, args.size)
        assertEquals("_worker.js", args[0])
        assertEquals("_worker.bundle", args[1])
        // nested-building log must NOT be emitted
        assertFalse(
            "workerjs_nested_building must NOT be in log when bundle preferred",
            R.string.pages_formdata_workerjs_nested_building in logIds(result)
        )
    }

    // ========================================================================
    // Test 3: _headers present → attached + headers_applied log
    // ========================================================================
    @Test
    fun `buildSpecialFormData includes _headers part and emits headers_applied log`() {
        val base = tmp.newFolder("ziproot3")
        writeFile(base, "_headers", "/*\n  X-Robots-Tag: none\n")
        val result = runBlocking {
            repository.buildSpecialFormData(baseDir = base, compatibilityDate = null)
        }
        val names = specialNames(result)
        assertTrue("_headers part must exist; got names=$names", "_headers" in names)
        assertTrue(R.string.pages_formdata_headers_applied in logIds(result))
    }

    // ========================================================================
    // Test 4: _redirects present → attached + redirects_applied log
    // ========================================================================
    @Test
    fun `buildSpecialFormData includes _redirects part and emits redirects_applied log`() {
        val base = tmp.newFolder("ziproot4")
        writeFile(base, "_redirects", "/old /new 301\n")
        val result = runBlocking {
            repository.buildSpecialFormData(baseDir = base, compatibilityDate = null)
        }
        val names = specialNames(result)
        assertTrue("_redirects part must exist; got names=$names", "_redirects" in names)
        assertTrue(R.string.pages_formdata_redirects_applied in logIds(result))
    }

    // ========================================================================
    // Test 5: _routes.json + NO custom compatibilityDate → routes applied
    //         + auto compat date injected
    // ========================================================================
    @Test
    fun `buildSpecialFormData includes _routes_worker json and auto-injects compatibility_date when missing custom date`() {
        val base = tmp.newFolder("ziproot5")
        // Simulate _worker.js to trigger compat date auto injection (per pages_formdata_compat_date_auto_format)
        writeFile(base, "_worker.js", "export default { fetch() { return new Response('') } }")
        writeFile(base, "_routes.json", "{\"version\":1,\"rules\":[{\"path\":\"/api/*\",\"middleware\":\"_worker.js\"}]}")
        val result = runBlocking {
            repository.buildSpecialFormData(baseDir = base, compatibilityDate = null)
        }
        val names = specialNames(result)
        assertTrue(
            "_routes.json part must exist; got names=$names",
            "_routes.json" in names
        )
        assertTrue(
            "routes_json_applied must be in log",
            R.string.pages_formdata_routes_json_applied in logIds(result)
        )
        // appliedCompatDate must NOT be null when _worker.js exists and no custom date
        assertNotNull(
            "compatDate must be auto-injected when _worker.js exists and no custom provided",
            result.appliedCompatDate
        )
        // compat_date_auto_format log event must be present, args: ["_worker.js", actual date]
        val evt = result.logEvents.firstOrNull {
            it.first == R.string.pages_formdata_compat_date_auto_format
        }
        assertNotNull("log must contain compat_date_auto_format event", evt)
        val args = evt!!.second
        assertEquals(2, args.size)
        assertEquals("_worker.js", args[0])
        assertFalse("injected date must not be empty", args[1].toString().isBlank())
    }
}
