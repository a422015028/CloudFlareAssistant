package com.muort.upworker.security

import com.muort.upworker.core.net.security.RemoteFileDownloadSecurityException
import com.muort.upworker.core.net.security.RemoteSecurityCode
import com.muort.upworker.core.net.security.RemoteUrlSecurity
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * P0-2 RemoteUrlSecurity 纯函数单元测试（无需网络）。
 * SSRF 场景注入自定义 Dns，返回指定 IP 即可触发。
 */
class RemoteUrlSecurityTest {

    // ======== Helpers 放在最前，防止单行 @Test 函数找不到后向声明 ========

    private inline fun assertCode(expected: RemoteSecurityCode, block: () -> Unit) {
        try {
            block()
            fail("expected RemoteFileDownloadSecurityException(code=$expected) but none thrown")
        } catch (ex: RemoteFileDownloadSecurityException) {
            assertEquals("code mismatch", expected, ex.code)
        }
    }

    private fun assertIpPrivate(ip: String, expect: Boolean) {
        val a = InetAddress.getByName(ip)
        assertEquals("ip=$ip isPrivate=$expect", expect, RemoteUrlSecurity.isPrivateAddress(a))
        val fakeDns = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> = listOf(a)
        }
        val host = "dummy-$ip.local"
        if (expect) {
            assertCode(RemoteSecurityCode.SSRF_BLOCKED) {
                RemoteUrlSecurity.dnsResolveAndCheckPrivate(host, fakeDns)
            }
        } else {
            assertFalse(RemoteUrlSecurity.isPrivateAddress(a))
        }
    }

    // ------------------------------------------------------------------
    // 协议 & 格式
    // ------------------------------------------------------------------
    @Test fun parseUrl_allowsHttps() {
        val u = RemoteUrlSecurity.parseUrlOrThrow("https://safe.com/a.js")
        assertEquals("safe.com", u.host)
        assertEquals("/a.js", u.path)
    }

    @Test fun parseUrl_rejectsHttp() {
        assertCode(RemoteSecurityCode.NOT_HTTPS) {
            RemoteUrlSecurity.parseUrlOrThrow("http://evil.com/a.js")
        }
    }

    @Test fun parseUrl_rejectsJunk() {
        assertCode(RemoteSecurityCode.INVALID_URL_FORMAT) {
            RemoteUrlSecurity.parseUrlOrThrow("not a url at all")
        }
    }

    @Test fun parseUrl_rejectsBlankHost() {
        assertCode(RemoteSecurityCode.INVALID_URL_FORMAT) {
            RemoteUrlSecurity.dnsResolveAndCheckPrivate("", Dns.SYSTEM)
        }
    }

    // ------------------------------------------------------------------
    // Allowlist
    // ------------------------------------------------------------------
    @Test fun allowlist_null_ok() {
        RemoteUrlSecurity.checkHostAllowlist("anything.com", null)
    }
    @Test fun allowlist_empty_ok() {
        RemoteUrlSecurity.checkHostAllowlist("anything.com", emptySet())
    }
    @Test fun allowlist_match_caseInsensitive() {
        RemoteUrlSecurity.checkHostAllowlist("Safe.COM", setOf("safe.com"))
    }
    @Test fun allowlist_blocked() {
        assertCode(RemoteSecurityCode.HOST_NOT_IN_ALLOWLIST) {
            RemoteUrlSecurity.checkHostAllowlist("evil.com", setOf("safe.com"))
        }
    }
    @Test fun allowlist_parseCsv() {
        assertEquals(setOf("a.com", "b.com"), RemoteUrlSecurity.parseAllowlist("a.com , b.com ,"))
        assertEquals(null, RemoteUrlSecurity.parseAllowlist(""))
        assertEquals(null, RemoteUrlSecurity.parseAllowlist(null))
    }

    // ------------------------------------------------------------------
    // SSRF IPv4 私网拦截
    // ------------------------------------------------------------------
    @Test fun ipv4_private_10()          { assertIpPrivate("10.0.0.1",           expect = true) }
    @Test fun ipv4_private_127()         { assertIpPrivate("127.0.0.1",          expect = true) }
    @Test fun ipv4_private_169254()      { assertIpPrivate("169.254.169.254",    expect = true) }
    @Test fun ipv4_private_172_16()      { assertIpPrivate("172.16.0.1",         expect = true) }
    @Test fun ipv4_private_172_31()      { assertIpPrivate("172.31.255.254",     expect = true) }
    @Test fun ipv4_private_172_32_not()  { assertIpPrivate("172.32.0.1",         expect = false) }
    @Test fun ipv4_private_192168()      { assertIpPrivate("192.168.1.1",        expect = true) }
    @Test fun ipv4_public_8888()         { assertIpPrivate("8.8.8.8",            expect = false) }
    @Test fun ipv4_public_cf()           { assertIpPrivate("104.16.132.229",     expect = false) }
    @Test fun ipv4_zero()                { assertIpPrivate("0.0.0.0",            expect = true) }

    // ------------------------------------------------------------------
    // SSRF IPv6 & v4-mapped
    // ------------------------------------------------------------------
    @Test fun ipv6_loopback()   { assertIpPrivate("::1",                  expect = true) }
    @Test fun ipv6_unspec()     { assertIpPrivate("::",                   expect = true) }
    @Test fun ipv6_ula_fc()     { assertIpPrivate("fc00::1",              expect = true) }
    @Test fun ipv6_ula_fd()     { assertIpPrivate("fd00::1",              expect = true) }
    @Test fun ipv6_linklocal()  { assertIpPrivate("fe80::1",              expect = true) }
    @Test fun ipv6_public()     { assertIpPrivate("2606:4700:4700::1111", expect = false) }
    @Test fun ipv6_mapped10()   { assertIpPrivate("::ffff:10.0.0.1",      expect = true) }

    // ------------------------------------------------------------------
    // DNS 注入
    // ------------------------------------------------------------------
    @Test fun dnsInjection_returnsPrivate_throwsSSRF() {
        val fake = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                listOf(InetAddress.getByName("127.0.0.1"))
        }
        assertCode(RemoteSecurityCode.SSRF_BLOCKED) {
            RemoteUrlSecurity.dnsResolveAndCheckPrivate("metadata.internal", fake)
        }
    }
    @Test fun dnsInjection_returnsPublic_ok() {
        val fake = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                listOf(InetAddress.getByName("8.8.8.8"))
        }
        val r = RemoteUrlSecurity.dnsResolveAndCheckPrivate("safe.com", fake)
        assertEquals(1, r.size)
    }
    @Test fun dnsInjection_unknownHost() {
        val fake = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                throw UnknownHostException("boom")
        }
        try {
            RemoteUrlSecurity.dnsResolveAndCheckPrivate("nope", fake)
            fail("expect UnknownHostException")
        } catch (ex: UnknownHostException) {
            // ok
        }
    }

    // ------------------------------------------------------------------
    // Content-Type
    // ------------------------------------------------------------------
    @Test fun ct_script_textPlain() = RemoteUrlSecurity.checkContentTypeScript("text/plain")
    @Test fun ct_script_appJS()     = RemoteUrlSecurity.checkContentTypeScript("application/javascript; charset=utf-8")
    @Test fun ct_script_octet()     = RemoteUrlSecurity.checkContentTypeScript("application/octet-stream")
    @Test fun ct_script_null()      = RemoteUrlSecurity.checkContentTypeScript(null)
    @Test fun ct_script_png_blocked() {
        assertCode(RemoteSecurityCode.CONTENT_TYPE_DISALLOWED) {
            RemoteUrlSecurity.checkContentTypeScript("image/png")
        }
    }
    @Test fun ct_zip_applicationZip() = RemoteUrlSecurity.checkContentTypeZip("application/zip")
    @Test fun ct_zip_xZip()           = RemoteUrlSecurity.checkContentTypeZip("application/x-zip-compressed")
    @Test fun ct_zip_octet()          = RemoteUrlSecurity.checkContentTypeZip("application/octet-stream")
    // 非标准但现实大量出现的老式 binary/octet-stream
    @Test fun ct_zip_binaryOctet()    = RemoteUrlSecurity.checkContentTypeZip("binary/octet-stream")
    @Test fun ct_script_binaryOctet() = RemoteUrlSecurity.checkContentTypeScript("binary/octet-stream; charset=utf-8")
    @Test fun ct_zip_html_blocked() {
        assertCode(RemoteSecurityCode.CONTENT_TYPE_DISALLOWED) {
            RemoteUrlSecurity.checkContentTypeZip("text/html; charset=utf-8")
        }
    }

    // ------------------------------------------------------------------
    // Sizes
    // ------------------------------------------------------------------
    @Test fun size_script_under() = RemoteUrlSecurity.checkScriptSize(5 * 1024 * 1024)
    @Test fun size_script_over() {
        assertCode(RemoteSecurityCode.SCRIPT_SIZE_EXCEEDED_5MIB) {
            RemoteUrlSecurity.checkScriptSize(5 * 1024 * 1024 + 1L)
        }
    }
    @Test fun size_zip_under() = RemoteUrlSecurity.checkZipSize(25 * 1024 * 1024)
    @Test fun size_zip_over() {
        assertCode(RemoteSecurityCode.ZIP_SIZE_EXCEEDED_25MIB) {
            RemoteUrlSecurity.checkZipSize(25 * 1024 * 1024 + 1L)
        }
    }

    // ------------------------------------------------------------------
    // ZIP magic
    // ------------------------------------------------------------------
    @Test fun zip_magic_OK() = RemoteUrlSecurity.checkZipMagic(byteArrayOf(0x50, 0x4B))
    @Test fun zip_magic_bad() {
        assertCode(RemoteSecurityCode.ZIP_MAGIC_MISMATCH) {
            // "<!DOCTYPE..." 模拟把 HTML 429 页面当做 zip
            RemoteUrlSecurity.checkZipMagic(byteArrayOf(0x3C.toByte(), 0x21.toByte()))
        }
    }
    @Test fun zip_magic_short() {
        assertCode(RemoteSecurityCode.ZIP_MAGIC_MISMATCH) {
            RemoteUrlSecurity.checkZipMagic(byteArrayOf(0x50))
        }
    }
}
