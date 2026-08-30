package com.muort.upworker.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.muort.upworker.core.net.security.RemoteFileDownloadSecurityException
import com.muort.upworker.core.net.security.RemoteSecurityCode
import com.muort.upworker.core.net.security.RemoteUrlSecurity
import com.muort.upworker.core.util.RemoteFileResolver
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetAddress

/**
 * P0-2 RemoteFileResolver 集成测试（Robolectric + MockWebServer）。
 * 测试 redirect 手动 follow 5 跳/6 跳超限、redirect→HTTP 拒绝、脚本/zip 大小、ZIP 魔数。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RemoteFileResolverIntegrationTest {

    private lateinit var server: MockWebServer
    private lateinit var context: Context

    @Before fun setUp() {
        server = MockWebServer()
        // MockWebServer 明文 HTTP（https 校验只在 production 严格执行，
        // test 下通过 RemoteFileResolver.allowInsecureProtocolForTests 放行，
        // 其它所有安全检查（重定向/SSRF/allowlist/CT/size/ZIP 魔数）仍然真实执行）
        server.start()
        context = ApplicationProvider.getApplicationContext()
        assertNotNull(context.cacheDir)

        RemoteFileResolver.messageResolverForTests = { _, code, a1, a2 ->
            when {
                a1 != null && a2 != null -> "$code($a1,$a2)"
                a1 != null -> "$code($a1)"
                else -> code.name
            }
        }
        // 因为 server.hostName 是 localhost，DNS 解析结果是 127.0.0.1（私网段）
        // 这里加 SSRF bypass（仅 test 生效，production 为 null）
        RemoteFileResolver.ssrfBypassHostsForTests = setOf(server.hostName)
        RemoteFileResolver.allowInsecureProtocolForTests = true
    }

    @After fun tearDown() {
        RemoteFileResolver.messageResolverForTests = null
        RemoteFileResolver.ssrfBypassHostsForTests = null
        RemoteFileResolver.allowInsecureProtocolForTests = false
        server.shutdown()
    }

    // ======================================================================
    // 辅助：构造 Dns + OkHttpClient。
    //   - DNS：用系统 Dns（localhost→127.0.0.1，SSRF 通过 ssrfBypassHostsForTests 豁免）
    //   - OkHttpClient：默认配置 + followRedirects(false) 手动重定向
    // ======================================================================
    private fun buildHarness(): Harness {
        val dns = Dns.SYSTEM
        val client = OkHttpClient.Builder()
            .dns(dns)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        return Harness(dns, client, server.url("/"))
    }

    private data class Harness(
        val dns: Dns,
        val client: OkHttpClient,
        val baseUrl: HttpUrl,
    )

    // ------- 基础测试：1 跳 200，脚本下载成功 -------
    @Test fun oneHop_200_textJs_success() = runTest {
        val h = buildHarness()
        val js = "console.log('hello from P0-2 integration');"
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/javascript")
            .setBody(js))
        val url = h.baseUrl.newBuilder().addPathSegment("a.js").build().toString()
        val result = RemoteFileResolver.resolve(
            context = context,
            url = url,
            maxSizeBytes = RemoteUrlSecurity.SCRIPT_MAX_BYTES,
            dns = h.dns,
            okHttpClientSupplier = { h.client },
        )
        assertTrue("expect success, actual=$result", result.isSuccess)
        val f = result.getOrThrow()
        assertEquals(js, f.readText())
        f.delete()
    }

    // ------- 重定向测试：5 跳成功（MAX_REDIRECTS==5，remaining 初始=5，每次减 1，第 6 跳 <0 抛）
    //  remaining=5 → 302(1)→rem=4 →302(2)→3 →302(3)→2 →302(4)→1 →302(5)→0 →200(6th request) OK
    //  6 次 302 然后 200： rem 会到 -1 → 抛 TOO_MANY_REDIRECTS
    // -------
    @Test fun redirects_5hops_ok_then_200() = runTest {
        val h = buildHarness()
        // 5 次 302，最后 1 次 200
        repeat(5) { i ->
            server.enqueue(MockResponse()
                .setResponseCode(302)
                .addHeader("Location", h.baseUrl.newBuilder()
                    .addPathSegment("hop${i + 2}.js").build().toString()))
        }
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/javascript")
            .setBody("OK"))
        val url = h.baseUrl.newBuilder().addPathSegment("hop1.js").build().toString()
        val r = RemoteFileResolver.resolve(
            context, url, dns = h.dns, okHttpClientSupplier = { h.client }
        )
        assertTrue("5 hops + 200 should succeed", r.isSuccess)
        assertEquals(6, server.requestCount)
        r.getOrThrow().delete()
    }

    @Test fun redirects_6hops_throws_TOO_MANY() = runTest {
        val h = buildHarness()
        // 6 次 302（每次都能循环回到服务器），第 7 次请求才 200。
        // MAX_REDIRECTS=5：第 1 次请求减到 4；第 6 次响应 302 时 rem=4-5=-1 → throw。
        // 所以请求次数：≤6 request 就抛。
        repeat(6) { i ->
            server.enqueue(MockResponse()
                .setResponseCode(302)
                .addHeader("Location", h.baseUrl.newBuilder()
                    .addPathSegment("r$i.js").build().toString()))
        }
        server.enqueue(MockResponse().setResponseCode(200)
            .addHeader("Content-Type", "application/javascript").setBody("body"))
        val url = h.baseUrl.newBuilder().addPathSegment("start.js").build().toString()
        val r = RemoteFileResolver.resolve(
            context, url, dns = h.dns, okHttpClientSupplier = { h.client }
        )
        assertTrue("expect failure", r.isFailure)
        val ex = r.exceptionOrNull() as RemoteFileDownloadSecurityException
        assertEquals(RemoteSecurityCode.TOO_MANY_REDIRECTS, ex.code)
    }

    @Test fun redirects_target_http_throws() = runTest {
        // 此测试需要**初始 URL HTTPS** + **重定向 Location HTTP**，才能触发
        // REDIRECT_TO_NON_HTTPS（而不是初始 NOT_HTTPS）。
        // 所以单独起一个自签 TLS 的 MockWebServer（非顶层共享 server）。
        val (sslCtx, tm) = singleTlsMockServer()
        val tlsServer = MockWebServer()
        tlsServer.useHttps(sslCtx.socketFactory, false)
        tlsServer.start()
        val tlsDns = Dns.SYSTEM
        val tlsClient = OkHttpClient.Builder()
            .sslSocketFactory(sslCtx.socketFactory, tm)
            .hostnameVerifier { _, _ -> true }
            .dns(tlsDns)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        // 因为是 https://localhost → SSRF 私网段，这个测试专用 host 也要临时加入 bypass
        val prevBypass = RemoteFileResolver.ssrfBypassHostsForTests.orEmpty().toMutableSet()
        prevBypass.add(tlsServer.hostName)
        RemoteFileResolver.ssrfBypassHostsForTests = prevBypass
        val prevHttp = RemoteFileResolver.allowInsecureProtocolForTests
        RemoteFileResolver.allowInsecureProtocolForTests = false
        try {
            tlsServer.enqueue(MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "http://insecure.example.com/a.js"))
            val url = tlsServer.url("/start.js").toString()
            val r = RemoteFileResolver.resolve(
                context, url, dns = tlsDns, okHttpClientSupplier = { tlsClient }
            )
            assertTrue(r.isFailure)
            val ex = r.exceptionOrNull() as RemoteFileDownloadSecurityException
            assertEquals(RemoteSecurityCode.REDIRECT_TO_NON_HTTPS, ex.code)
        } finally {
            RemoteFileResolver.allowInsecureProtocolForTests = prevHttp
            RemoteFileResolver.ssrfBypassHostsForTests = RemoteFileResolver.ssrfBypassHostsForTests
                ?.minus(tlsServer.hostName)?.ifEmpty { null }
            tlsServer.shutdown()
        }
    }

    private fun singleTlsMockServer(): Pair<javax.net.ssl.SSLContext, javax.net.ssl.X509TrustManager> {
        // okhttp-tls: HeldCertificate 默认生成 localhost RSA 自签证书（keyManager+trustManager 都已配好）
        val held = okhttp3.tls.HeldCertificate.Builder()
            .commonName("localhost")
            .addSubjectAlternativeName("localhost")
            .build()
        val serverCerts = okhttp3.tls.HandshakeCertificates.Builder()
            .heldCertificate(held)
            .build()
        val clientCerts = okhttp3.tls.HandshakeCertificates.Builder()
            .addTrustedCertificate(held.certificate)
            .build()
        // 服务端：必须拿 serverCerts.keyManager 做 TLS (握有私钥)，trustManager 可以随便（我们不做 mTLS）
        // 客户端：拿 clientCerts.trustManager (信任该自签)
        val sc = javax.net.ssl.SSLContext.getInstance("TLS")
        val dummyTm = clientCerts.trustManager
        sc.init(arrayOf(serverCerts.keyManager), arrayOf(dummyTm), java.security.SecureRandom())
        return sc to dummyTm
    }

    // ------- binary/octet-stream (legacy CT) ZIP → OK (cf.muort.com 实测场景) -------
    @Test fun zip_contentType_binaryOctetStream_ok() = runTest {
        val h = buildHarness()
        val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x00, 0x00)
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "binary/octet-stream")
            .setBody(okio.Buffer().write(zipBytes)))
        val url = h.baseUrl.newBuilder().addPathSegment("cloudflare-assistant-2608301.zip").build().toString()
        val r = RemoteFileResolver.resolve(
            context, url, dns = h.dns, okHttpClientSupplier = { h.client }
        )
        if (!r.isSuccess) {
            r.exceptionOrNull()?.printStackTrace()
            org.junit.Assert.fail("expected success but: ${r.exceptionOrNull()}")
        }
        val f = r.getOrThrow()
        assertEquals(zipBytes.size.toLong(), f.length())
        assertTrue(f.delete())
    }

    // ------- ZIP 魔数 -------
    @Test fun zip_first2bytes_not_PK_throws() = runTest {
        val h = buildHarness()
        // 返回不是 zip 魔数的 body
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/zip")
            .setBody("<!DOCTYPE html><html>"))
        val url = h.baseUrl.newBuilder().addPathSegment("a.zip").build().toString()
        val r = RemoteFileResolver.resolve(
            context, url, dns = h.dns, okHttpClientSupplier = { h.client }
        )
        assertTrue(r.isFailure)
        val ex = r.exceptionOrNull() as RemoteFileDownloadSecurityException
        assertEquals(RemoteSecurityCode.ZIP_MAGIC_MISMATCH, ex.code)
    }

    // ------- Script size > 5MiB streaming throw -------
    @Test fun script_streaming_6MB_throws_5MiB_limit() = runTest {
        val h = buildHarness()
        // 6 MiB body；用 okio.Buffer 填充 6MB
        val size = 6 * 1024 * 1024
        val buf = okio.Buffer()
        val chunk = ByteArray(64 * 1024) // 64KB
        var remaining = size
        while (remaining > 0) {
            val w = minOf(chunk.size, remaining)
            buf.write(chunk, 0, w)
            remaining -= w
        }
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/javascript")
            .setBody(buf))
        val url = h.baseUrl.newBuilder().addPathSegment("big.js").build().toString()
        val r = RemoteFileResolver.resolve(
            context, url, dns = h.dns, okHttpClientSupplier = { h.client }
        )
        assertTrue(r.isFailure)
        val ex = r.exceptionOrNull() as RemoteFileDownloadSecurityException
        assertEquals(RemoteSecurityCode.SCRIPT_SIZE_EXCEEDED_5MIB, ex.code)
    }

    // ------- HTTP 404 → HTTP_STATUS_ERROR -------
    @Test fun http_404_throws() = runTest {
        val h = buildHarness()
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))
        val url = h.baseUrl.newBuilder().addPathSegment("x.js").build().toString()
        val r = RemoteFileResolver.resolve(
            context, url, dns = h.dns, okHttpClientSupplier = { h.client }
        )
        assertTrue(r.isFailure)
        val ex = r.exceptionOrNull() as RemoteFileDownloadSecurityException
        assertEquals(RemoteSecurityCode.HTTP_STATUS_ERROR, ex.code)
    }

    // ======================================================================
    // Local 模式 (allowInsecureProtocol + allowPrivateIp)：
    //   用户需要访问局域网/NAS/本地回环的 http:// 地址；以下 tests 强制不传
    //   test hook（setUp 里的 ssrfBypass + allowInsecureForTests 在这些 tests 开头临时还原）
    // ======================================================================

    /** 关闭全局 test hooks，让 tests 真实模拟 strict 模式 */
    private fun disableGlobalTestHooks() {
        RemoteFileResolver.ssrfBypassHostsForTests = null
        RemoteFileResolver.allowInsecureProtocolForTests = false
    }

    @Test fun localMode_http_protocol_and_127_0_0_1_private_success() = runTest {
        disableGlobalTestHooks()
        val h = buildHarness()
        val body = "// local worker script"
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/javascript")
            .setBody(body))
        // 直接用 server.url(...) 构造 http://127.0.0.1:<port>/local.js
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("127.0.0.1")
            .port(server.port)
            .addPathSegment("local.js")
            .build().toString()
        // 不传 local flags → 必须 FAIL（NOT_HTTPS 或 SSRF_BLOCKED）
        val rStrict = RemoteFileResolver.resolve(
            context, url, dns = h.dns, okHttpClientSupplier = { h.client }
        )
        assertTrue("strict mode MUST fail for http://127.0.0.1", rStrict.isFailure)

        // 传 local flags → 必须 PASS
        val r = RemoteFileResolver.resolve(
            context, url, dns = h.dns, okHttpClientSupplier = { h.client },
            allowInsecureProtocol = true, allowPrivateIp = true
        )
        if (!r.isSuccess) {
            r.exceptionOrNull()?.printStackTrace()
            fail("localMode expected success: ${r.exceptionOrNull()}")
        }
        val f = r.getOrThrow()
        assertEquals(body.length.toLong(), f.length())
        assertTrue(f.delete())
    }

    @Test fun localMode_redirect_private_http_to_private_http_success() = runTest {
        disableGlobalTestHooks()
        // 跳1: 302 → 跳2: 200。两边都用 MockWebServer 真实 host 但 DNS fake 强行解析到 127.0.0.1
        //    → 保证 ① private IP（SSRF 拦截）+ ② HTTP 明文（NOT_HTTPS 拦截） + ③ 重定向到 HTTP
        //    三个 check 都被 allowInsecureProtocol=true / allowPrivateIp=true 放行。
        server.enqueue(MockResponse()
            .setResponseCode(302)
            .addHeader("Location", server.url("/final.zip").toString()))
        val zipBytes = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "binary/octet-stream")
            .setBody(okio.Buffer().write(zipBytes)))
        val dnsFake = object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                listOf(InetAddress.getByName("127.0.0.1"))
        }
        val client = OkHttpClient.Builder()
            .dns(dnsFake)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val startUrl = server.url("/step1.zip").toString() // 已是 http://${server.hostName}:port/step1.zip

        val r = RemoteFileResolver.resolve(
            context, startUrl, dns = dnsFake, okHttpClientSupplier = { client },
            allowInsecureProtocol = true, allowPrivateIp = true
        )
        if (!r.isSuccess) {
            r.exceptionOrNull()?.printStackTrace()
            fail("localMode redirect expected success: ${r.exceptionOrNull()}")
        }
        val f = r.getOrThrow()
        assertEquals(zipBytes.size.toLong(), f.length())
        assertTrue(f.delete())
    }

    @Test fun localMode_zip_magic_still_enforced() = runTest {
        disableGlobalTestHooks()
        val h = buildHarness()
        // body 不是 ZIP 魔数 → 即使 localMode=true 也要拒绝
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/octet-stream")
            .setBody("<html>Not a zip</html>"))
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("127.0.0.1")
            .port(server.port)
            .addPathSegment("notzip.zip")
            .build().toString()
        val r = RemoteFileResolver.resolve(
            context, url, dns = h.dns, okHttpClientSupplier = { h.client },
            allowInsecureProtocol = true, allowPrivateIp = true
        )
        assertTrue(r.isFailure)
        val ex = r.exceptionOrNull() as RemoteFileDownloadSecurityException
        assertEquals(RemoteSecurityCode.ZIP_MAGIC_MISMATCH, ex.code)
    }

    @Test fun localMode_strict_flag_off_still_blocks_http() = runTest {
        disableGlobalTestHooks()
        val h = buildHarness()
        server.enqueue(MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/javascript")
            .setBody("hello"))
        val url = HttpUrl.Builder()
            .scheme("http")
            .host("127.0.0.1")
            .port(server.port)
            .addPathSegment("a.js")
            .build().toString()
        // allowPrivateIp=true 但 allowInsecureProtocol=false → 仍必须因 NOT_HTTPS fail
        val r = RemoteFileResolver.resolve(
            context, url, dns = h.dns, okHttpClientSupplier = { h.client },
            allowInsecureProtocol = false, allowPrivateIp = true
        )
        assertTrue("http MUST fail unless allowInsecureProtocol=true", r.isFailure)
        val ex = r.exceptionOrNull() as RemoteFileDownloadSecurityException
        assertTrue(
            "expected NOT_HTTPS or SSRF, got ${ex.code}",
            ex.code == RemoteSecurityCode.NOT_HTTPS || ex.code == RemoteSecurityCode.SSRF_BLOCKED
        )
    }
}
