package com.muort.upworker.util

import com.muort.upworker.core.util.ExponentialRetry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExponentialRetryTest {

    @Test fun success_noRetry() = runTest {
        var calls = 0
        val r = ExponentialRetry.withRetry(attempts = 3, baseDelayMs = 1, onBeforeRetry = { _, _ -> fail("should not retry") }) {
            calls++
            "ok"
        }
        assertEquals("ok", r)
        assertEquals(1, calls)
    }

    @Test fun onceFail_thenSucceed_secondAttempt() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()
        val r = ExponentialRetry.withRetry(
            attempts = 3,
            baseDelayMs = 2,
            multiplier = 2.0,
            onBeforeRetry = { _, d -> delays.add(d) }
        ) {
            calls++
            if (calls == 1) error("boom-1")
            "v$calls"
        }
        assertEquals("v2", r)
        assertEquals(2, calls)
        assertEquals(1, delays.size)
        assertEquals(2L, delays[0]) // baseDelayMs * 2^0
    }

    @Test fun twoFails_thenSucceed_thirdAttempt_delaysExponential() = runTest {
        var calls = 0
        val delays = mutableListOf<Long>()
        val r = ExponentialRetry.withRetry(
            attempts = 3,
            baseDelayMs = 10,
            multiplier = 2.0,
            onBeforeRetry = { _, d -> delays.add(d) }
        ) {
            calls++
            if (calls <= 2) error("boom-$calls")
            "done"
        }
        assertEquals("done", r)
        assertEquals(3, calls)
        assertEquals(listOf(10L, 20L), delays) // i=0: 10*2^0=10, i=1: 10*2^1=20
    }

    @Test fun allAttemptsFail_rethrowsLast() = runTest {
        var calls = 0
        try {
            ExponentialRetry.withRetry(attempts = 3, baseDelayMs = 1) {
                calls++
                throw IllegalStateException("fail$calls")
            }
            fail("should have thrown")
        } catch (e: IllegalStateException) {
            assertEquals("fail3", e.message)
        }
        assertEquals(3, calls)
    }

    @Test fun attemptsEq1_noRetry() = runTest {
        var called = 0
        try {
            ExponentialRetry.withRetry(attempts = 1, baseDelayMs = 1,
                onBeforeRetry = { _, _ -> fail("no retry") }) {
                called++
                error("x")
            }
        } catch (_: Throwable) { /* ok */ }
        assertEquals(1, called)
    }

    @Test fun attemptsZero_throwsIllegalArgument() = runTest {
        var thrown: IllegalArgumentException? = null
        try {
            ExponentialRetry.withRetry<Unit>(attempts = 0) { }
        } catch (e: IllegalArgumentException) {
            thrown = e
        }
        assertTrue("expect IAE for attempts==0", thrown != null)
    }
}
