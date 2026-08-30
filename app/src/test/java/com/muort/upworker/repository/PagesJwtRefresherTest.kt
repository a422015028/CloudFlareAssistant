package com.muort.upworker.repository

import com.muort.upworker.core.repository.PagesJwtRefresher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PagesJwtRefresher 单测：parseExp / isNearExpiry。
 * 算法向量：用 java.util.Base64 url-unsafe 编码构造 JSON payload。
 */
class PagesJwtRefresherTest {

    // ------------------------------------------------------------------
    // 1. parseExp 正向案例
    // ------------------------------------------------------------------
    @Test fun parseExp_standardJwt_returnsExact() {
        val exp = 1_800_000_000L // 2027-01-15 approx
        val payload = """{"sub":"pages-upload","exp":$exp,"project":"demo"}"""
        val jwt = buildJwt(payload)
        assertEquals(exp, PagesJwtRefresher.parseExp(jwt))
    }

    @Test fun parseExp_base64url_dashAndUnderscore_ok() {
        // 寻找 payload 末尾补 padding 的边界 + -_ 替换
        val exp = 1_800_000_001L
        val payload = """{"exp":$exp,"pad":"abc?+/"}""" // 含 base64 的 +/，URL-safe 变体 =-_
        val jwt = buildJwtUrlSafe(payload)
        assertEquals(exp, PagesJwtRefresher.parseExp(jwt))
    }

    @Test fun parseExp_acceptsMissingPadding() {
        val exp = 1_700_000_000L
        val payload = """{"exp":$exp}"""
        val jwt = buildJwt(payload, stripPad = true)
        assertEquals(exp, PagesJwtRefresher.parseExp(jwt))
    }

    // ------------------------------------------------------------------
    // 2. parseExp 负向案例
    // ------------------------------------------------------------------
    @Test fun parseExp_notThreeParts_null() {
        assertNull(PagesJwtRefresher.parseExp("a.b"))
        assertNull(PagesJwtRefresher.parseExp("a"))
        assertNull(PagesJwtRefresher.parseExp("a.b.c.d"))
    }

    @Test fun parseExp_badBase64_null() {
        assertNull(PagesJwtRefresher.parseExp("x.###!!not-b64.y"))
    }

    @Test fun parseExp_noExpField_null() {
        val jwt = buildJwt("""{"sub":"x"}""")
        assertNull(PagesJwtRefresher.parseExp(jwt))
    }

    @Test fun parseExp_expNotNumeric_null() {
        val jwt = buildJwt("""{"exp":"not-a-long"}""")
        assertNull(PagesJwtRefresher.parseExp(jwt))
    }

    // ------------------------------------------------------------------
    // 3. isNearExpiry
    // ------------------------------------------------------------------
    @Test fun isNearExpiry_31sAhead_false() {
        val now = 1_000_000_000L
        val exp = now + 31
        val jwt = buildJwt("""{"exp":$exp}""")
        assertFalse(PagesJwtRefresher.isNearExpiry(jwt, now))
    }

    @Test fun isNearExpiry_30sAhead_true() {
        val now = 1_000_000_000L
        val exp = now + 30
        val jwt = buildJwt("""{"exp":$exp}""")
        assertTrue(PagesJwtRefresher.isNearExpiry(jwt, now))
    }

    @Test fun isNearExpiry_alreadyExpired_true() {
        val now = 1_000_000_000L
        val exp = now - 1
        val jwt = buildJwt("""{"exp":$exp}""")
        assertTrue(PagesJwtRefresher.isNearExpiry(jwt, now))
    }

    @Test fun isNearExpiry_malformedJwt_false() {
        // 解析失败 → 不阻塞主流程，返回 false（交由 401 触发 force 刷新）
        assertFalse(PagesJwtRefresher.isNearExpiry("definitely.not.jwt"))
    }

    @Test fun parseExp_expAtStartOfJson_ok() {
        val exp = 1_600_000_000L
        val payload = """{"exp":$exp,"a":1}"""
        assertEquals(exp, PagesJwtRefresher.parseExp(buildJwt(payload)))
    }

    @Test fun parseExp_expAtEndOfJson_ok() {
        val exp = 1_500_000_000L
        val payload = """{"a":1,"b":2,"exp":$exp}"""
        assertEquals(exp, PagesJwtRefresher.parseExp(buildJwt(payload)))
    }

    @Test fun parseExp_expNegative_noCrash_returnsNull() {
        // 我们的 regex 只匹配纯数字，不识别负号；负数 exp 本就非法，当作 null。
        val jwt = buildJwt("""{"exp":-1}""")
        assertNull(PagesJwtRefresher.parseExp(jwt))
    }

    // ==================================================================
    // helpers
    // ==================================================================
    private val jvmEnc = java.util.Base64.getEncoder()
    private val jvmUrlEnc = java.util.Base64.getUrlEncoder()

    private fun buildJwt(
        payloadJson: String,
        headerJson: String = """{"alg":"RS256","typ":"JWT"}""",
        stripPad: Boolean = false,
    ): String {
        val h = stripPadIf(jvmEnc.encodeToString(headerJson.toByteArray(Charsets.UTF_8)), stripPad)
        val p = stripPadIf(jvmEnc.encodeToString(payloadJson.toByteArray(Charsets.UTF_8)), stripPad)
        val s = "FAKESIG"
        return "$h.$p.$s"
    }

    private fun buildJwtUrlSafe(payloadJson: String): String {
        val h = stripPadIf(jvmUrlEnc.encodeToString("""{"alg":"none"}""".toByteArray()), true)
        val p = stripPadIf(jvmUrlEnc.encodeToString(payloadJson.toByteArray()), true)
        return "$h.$p.SIG"
    }

    private fun stripPadIf(s: String, strip: Boolean): String =
        if (!strip) s else s.trimEnd('=')
}
