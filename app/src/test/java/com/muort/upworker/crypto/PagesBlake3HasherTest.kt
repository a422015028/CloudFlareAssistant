package com.muort.upworker.crypto

import com.muort.upworker.core.crypto.PagesBlake3Hasher
import org.bouncycastle.crypto.digests.Blake3Digest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

/**
 * P0-1 PagesBlake3Hasher 单元测试。
 *
 * 对齐 cf-manager Be() / wrangler v4 Pages Direct Upload 资产哈希算法：
 *   input = Base64(NO_WRAP bytes) + "." + lowercaseExtension
 *   hash  = lowercase_hex( Blake3-128(input.toByteArray(UTF_8)) )  // 16 bytes = 32 hex chars
 */
class PagesBlake3HasherTest {

    /**
     * 使用 BouncyCastle Blake3Digest 独立计算参考向量（不依赖被测 PagesBlake3Hasher）。
     */
    private fun blake3Ref(bytes: ByteArray, ext: String): String {
        val base64 = Base64.getEncoder().encodeToString(bytes)
        val payload = "$base64.$ext"
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val digest = Blake3Digest(128)
        digest.update(payloadBytes, 0, payloadBytes.size)
        val out = ByteArray(16)
        digest.doFinal(out, 0)
        return out.joinToString("") { "%02x".format(it) }
    }

    /** 项目旧算法（SHA-256(base64(bytes)+ext)[:32]） — 用作 fallback 的对照。 */
    private fun sha256Legacy(bytes: ByteArray, ext: String): String {
        val base64 = Base64.getEncoder().encodeToString(bytes)
        val payload = base64 + ext
        val md = MessageDigest.getInstance("SHA-256")
        val hash = md.digest(payload.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }.substring(0, 32)
    }

    private lateinit var vec1: Pair<String, Triple<ByteArray, String, String>>
    private lateinit var vec2: Pair<String, Triple<ByteArray, String, String>>

    @Before
    fun setUp() {
        val b1 = "A".toByteArray(Charsets.UTF_8)
        val e1 = "js"
        val b2 = "Hello World".toByteArray(Charsets.UTF_8)
        val e2 = "html"
        vec1 = "vec1(bytes='A',ext='js')" to Triple(b1, e1, blake3Ref(b1, e1))
        vec2 = "vec2(bytes='Hello World',ext='html')" to Triple(b2, e2, blake3Ref(b2, e2))

        // 预先确认参考值长度=32 并且是小写 hex
        listOf(vec1.second.third, vec2.second.third).forEach {
            assertEquals(32, it.length)
            assertTrue(it.matches(Regex("^[0-9a-f]{32}$")))
        }
        // 确认新旧算法不一致（证明：如果仍然在跑 SHA-256，会与 Blake3 结果不同）
        listOf(vec1 to sha256Legacy(b1, e1), vec2 to sha256Legacy(b2, e2)).forEach { (v, legacy) ->
            assertTrue("新旧算法结果应不同 (${v.first})", legacy != v.second.third)
        }
    }

    @Test fun vec1_blake3_equals_reference() {
        val (label, triple) = vec1
        val (bytes, ext, expected) = triple
        assertEquals("PagesBlake3Hasher.hash($label) 与 bcprov 参考不一致",
            expected, PagesBlake3Hasher.hash(bytes, ext))
    }

    @Test fun vec2_blake3_equals_reference() {
        val (label, triple) = vec2
        val (bytes, ext, expected) = triple
        assertEquals("PagesBlake3Hasher.hash($label) 与 bcprov 参考不一致",
            expected, PagesBlake3Hasher.hash(bytes, ext))
    }

    @Test fun blake3_hash_is_always_32_lowercase_hex_for_random_payloads() {
        val rnd = java.util.Random(42L)
        repeat(32) {
            val size = rnd.nextInt(16 * 1024) + 1
            val arr = ByteArray(size).also(rnd::nextBytes)
            val ext = listOf("js", "html", "css", "png", "woff2")[rnd.nextInt(5)]
            val actual = PagesBlake3Hasher.hash(arr, ext)
            val ref = blake3Ref(arr, ext)
            assertEquals("随机负载大小=$size ext=$ext", ref, actual)
            assertEquals(32, actual.length)
            assertTrue(actual.matches(Regex("^[0-9a-f]{32}$")))
        }
    }
}
