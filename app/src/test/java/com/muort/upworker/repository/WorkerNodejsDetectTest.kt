package com.muort.upworker.repository

import android.content.Context
import com.muort.upworker.R
import com.muort.upworker.core.network.CloudFlareApi
import com.muort.upworker.core.repository.WorkerNodejsDetectResult
import com.muort.upworker.core.repository.WorkerRepository
import com.muort.upworker.core.util.AuthHelper
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P1-3 RED failing tests for WorkerRepository.detectAndAppendNodejsCompat.
 *
 * 7+1 detect patterns per P1-3 DETECT spec:
 *   #1 __commonJS
 *   #2 require(" (CJS require call)
 *   #3 require("node: (built-in require)
 *   #4 process.
 *   #5 globalThis.process
 *   #6 global.process
 *   #7 Buffer.
 *   #8 node:async_hooks
 *
 * 行为契约 (对齐 worker_nodejs_* 7 strings):
 *  - at least one hit + nodejs_compat NOT in existing flags
 *          → logResId = worker_nodejs_detect_hit_hint_format
 *          → logArgs  = [friendly-names-comma-separated]
 *          → finalFlags += "nodejs_compat" appended
 *  - at least one hit + nodejs_compat ALREADY in flags
 *          → logResId = worker_nodejs_flag_dup_skip_format
 *          → logArgs  = ["nodejs_compat"]
 *          → finalFlags unchanged
 *  - zero hits
 *          → logResId = worker_nodejs_detect_no_hit
 *          → logArgs  = []
 *          → finalFlags unchanged
 */
class WorkerNodejsDetectTest {

    private lateinit var repository: WorkerRepository

    @MockK(relaxed = true) lateinit var mockContext: Context
    @MockK(relaxed = true) lateinit var mockApi: CloudFlareApi
    private lateinit var gson: Gson

    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        gson = GsonBuilder().create()
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
        repository = WorkerRepository(
            appContext = mockContext,
            api = mockApi,
            gson = gson
        )
    }

    @After fun tearDown() { /* no-op */ }

    // ==============================
    // Test 1: pure ESM — 0 hits
    // ==============================
    @Test
    fun `detectAndAppendNodejsCompat returns no_hit log and keeps flags unchanged for pure ESM script`() {
        val script = """
            export default {
              async fetch(request, env) {
                return new Response(JSON.stringify({ hello: env.KEY }), {
                  headers: { "content-type": "application/json" }
                })
              }
            }
        """.trimIndent()
        val existing = listOf("streams_enable_constructors")
        val r = repository.detectAndAppendNodejsCompat(script, existing)
        assertEquals(
            "logResId must be worker_nodejs_detect_no_hit",
            R.string.worker_nodejs_detect_no_hit,
            r.logResId
        )
        assertEquals("logFormatArgs must be empty", 0, r.logFormatArgs.size)
        assertTrue("hitPatterns must be empty", r.hitPatterns.isEmpty())
        assertEquals("finalFlags must equal original", existing, r.finalFlags)
        assertFalse(
            "nodejs_compat must NOT be appended when 0 hits",
            "nodejs_compat" in r.finalFlags
        )
    }

    // ==============================
    // Test 2: require("path") — single hit → append nodejs_compat
    // ==============================
    @Test
    fun `detectAndAppendNodejsCompat appends nodejs_compat flag and emits hit log on require call hit`() {
        val script = """
            const path = require("path");
            export default { fetch() { return new Response(path.resolve('/')); } };
        """.trimIndent()
        val existing = listOf("streams_enable_constructors")
        val r = repository.detectAndAppendNodejsCompat(script, existing)
        // Append happened
        assertTrue(
            "finalFlags must contain nodejs_compat",
            "nodejs_compat" in r.finalFlags
        )
        assertTrue(
            "finalFlags must keep original flags",
            "streams_enable_constructors" in r.finalFlags
        )
        // Hit pattern captured
        assertEquals("hitPatterns size must be 1 (require call)", 1, r.hitPatterns.size)
        assertTrue(
            "hit name should mention 'require' (got ${r.hitPatterns})",
            r.hitPatterns.first().contains("require")
        )
        // Log event: hit_hint_format
        assertEquals(
            "logResId must be hit_hint_format",
            R.string.worker_nodejs_detect_hit_hint_format,
            r.logResId
        )
        val args = r.logFormatArgs
        assertEquals("hit_hint_format expects 1 arg (pattern-names-joined)", 1, args.size)
        assertTrue(
            "log arg0 must contain require-friendly name (got '${args[0]}')",
            args[0].toString().contains("require")
        )
    }

    // ==============================
    // Test 3: Buffer. + node:async_hooks — dual hits → ONE append + 2 pattern names
    // ==============================
    @Test
    fun `detectAndAppendNodejsCompat records multiple hits and de-duplicates single nodejs_compat append`() {
        val script = """
            const { AsyncLocalStorage } = require('node:async_hooks');
            const store = new AsyncLocalStorage();
            const buf = Buffer.from("hello");
            export default { fetch() { return new Response(buf.toString()); } };
        """.trimIndent()
        val r = repository.detectAndAppendNodejsCompat(script, existingFlags = null)
        assertEquals(
            "must have ≥2 distinct hits (require/async_hooks family + Buffer.); actual count was ${r.hitPatterns.size}: ${r.hitPatterns}",
            true,
            r.hitPatterns.size >= 2
        )
        // Check both friendly names represented (case-insensitive contains)
        val joined = r.hitPatterns.joinToString("|")
        assertTrue("one hit must mention Buffer (got '$joined')", joined.contains("Buffer", true))
        assertTrue("one hit must mention async_hooks or node: (got '$joined')",
            joined.contains("async_hooks") || joined.contains("node:"))
        // nodejs_compat appended exactly once
        val count = r.finalFlags.count { it == "nodejs_compat" }
        assertEquals("nodejs_compat must appear exactly once in finalFlags", 1, count)
    }

    // ==============================
    // Test 4: existingFlags already contains "nodejs_compat" → skip dup, emit dup_skip log
    // ==============================
    @Test
    fun `detectAndAppendNodejsCompat emits dup_skip log and does not re-append when flag already present`() {
        val script = """
            const { File } = require("node:buffer");
            console.log(process.version);
            export default { fetch() { return new Response(File.name); } };
        """.trimIndent()
        val existing = listOf("nodejs_compat", "streams_enable_constructors")
        val r = repository.detectAndAppendNodejsCompat(script, existing)
        // Size unchanged (no dup append)
        assertEquals(
            "finalFlags must keep size == original size (no re-append)",
            existing.size,
            r.finalFlags.size
        )
        assertEquals("finalFlags must equal original (order preserved)", existing, r.finalFlags)
        // Log: dup_skip_format
        assertEquals(
            "logResId must be worker_nodejs_flag_dup_skip_format",
            R.string.worker_nodejs_flag_dup_skip_format,
            r.logResId
        )
        val args = r.logFormatArgs
        assertEquals("dup_skip_format expects 1 arg", 1, args.size)
        assertEquals("dup_skip_format arg0 must be 'nodejs_compat'", "nodejs_compat", args[0])
    }

    // ==============================
    // Test 5: globalThis.process + require(node:fs) + global.process → 3 hits
    // ==============================
    @Test
    fun `detectAndAppendNodejsCompat captures all three process-related patterns as distinct hits`() {
        val script = """
            import process from 'node:process';
            function env() {
              console.log(globalThis.process.env.NODE_ENV);
              console.log(global.process.pid);
              // Plain process. usage too
              return process.cwd();
            }
            const fs = require("node:fs");
            export default { fetch() { env(); return new Response(fs.readFileSync('/etc/hosts')); } };
        """.trimIndent()
        val r = repository.detectAndAppendNodejsCompat(script, existingFlags = emptyList())
        // Friendly name of require(node:*): at least 3 distinct patterns expected
        // → require(node: / globalThis.process / global.process / process. / node:process import)
        assertTrue(
            "hitPatterns size must be ≥3 (globalThis.process + global.process + process. + require(node:fs) → ≥3; got ${r.hitPatterns.size}: ${r.hitPatterns})",
            r.hitPatterns.size >= 3
        )
        assertTrue("nodejs_compat in finalFlags", "nodejs_compat" in r.finalFlags)
        assertNotNull("logFormatArgs must not be null", r.logFormatArgs)
        assertEquals(
            "when hit + no dup → logResId == hit_hint_format",
            R.string.worker_nodejs_detect_hit_hint_format,
            r.logResId
        )
    }
}
