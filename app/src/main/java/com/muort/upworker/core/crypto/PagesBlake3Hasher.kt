package com.muort.upworker.core.crypto

import org.bouncycastle.crypto.digests.Blake3Digest
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/**
 * Pages 资产哈希计算。
 *
 * 算法与 wrangler v4 / cf-manager `Be()` 对齐：
 *   input = Base64(NO_WRAP, bytes) + "." + lowercaseExtension
 *   hash  = lowercase_hex( Blake3-128(input.toByteArray(UTF_8)) )  // 16 B = 32 hex
 *
 * 兼容性：如果 BouncyCastle 算法不可用，[hash] 会抛 [IllegalStateException]；
 * 调用方（PagesRepository）需要捕获并回退到旧 SHA-256 实现。
 */
object PagesBlake3Hasher {

    /** 在 Android 运行时与 JVM 单测之间都可用的 Base64 NO_WRAP 编码器。 */
    private val base64EncodeNoWrap: (ByteArray) -> String by lazy {
        try {
            // Android 运行时：注意 unit-test 下 android.jar 是 Stub，调用 encodeToString 会抛 RuntimeException("Stub!")，
            // 所以这里必须"先试调用一次"而不是只看 Class.forName 是否成功。
            val cls = Class.forName("android.util.Base64")
            val NO_WRAP = cls.getField("NO_WRAP").getInt(null)
            val m = cls.getMethod("encodeToString", ByteArray::class.java, Int::class.javaPrimitiveType)
            // 探测：对空数组做一次编码
            val probe = m.invoke(null, ByteArray(0), NO_WRAP) as? String
                ?: error("android.util.Base64 returned null")
            if (probe != "") error("android.util.Base64 probe mismatch")
            Timber.d("PagesBlake3Hasher: using android.util.Base64")
            return@lazy { bytes -> m.invoke(null, bytes, NO_WRAP) as String }
        } catch (t: Throwable) {
            // JVM 单测 / 或 Android stub 探测失败
            Timber.d("PagesBlake3Hasher: using java.util.Base64 (cause=${t.javaClass.simpleName})")
            val enc = java.util.Base64.getEncoder()
            return@lazy { bytes -> enc.encodeToString(bytes) }
        }
    }

    /**
     * @throws IllegalStateException 当 Blake3Digest/BouncyCastle 不可用时
     */
    @JvmStatic
    fun hash(bytes: ByteArray, extension: String): String {
        val b64 = base64EncodeNoWrap(bytes)
        val ext = extension.lowercase()
        val payload = "$b64.$ext".toByteArray(Charsets.UTF_8)

        val digest = try {
            Blake3Digest(128)
        } catch (t: Throwable) {
            Timber.w(t, "PagesBlake3Hasher: Blake3Digest not available")
            throw IllegalStateException("BouncyCastle Blake3-128 unavailable", t)
        }
        digest.update(payload, 0, payload.size)
        val out = ByteArray(16)
        digest.doFinal(out, 0)
        return out.joinToString("") { "%02x".format(it) }
    }

    /** 便捷方法：对文件做 hash。 */
    @JvmStatic
    fun calculate(file: File): String = hash(file.readBytes(), file.extension.lowercase())

    // ---------------------------------------------------------------------------------------
    // 旧 SHA-256 算法（供 fallback，精确复刻 PagesRepository 原有实现）。
    // input = Base64(NO_WRAP, bytes) + lowercaseExtension   (注意：此处没有 ".")
    //   =>  SHA-256[:32] lowercase hex
    // ---------------------------------------------------------------------------------------
    @JvmStatic
    fun legacySha256Truncated(bytes: ByteArray, extension: String): String {
        val b64 = base64EncodeNoWrap(bytes)
        val ext = extension.lowercase()
        val md = MessageDigest.getInstance("SHA-256")
        md.update(b64.toByteArray(Charsets.UTF_8))
        md.update(ext.toByteArray(Charsets.UTF_8))
        val h = md.digest()
        return h.joinToString("") { "%02x".format(it) }.substring(0, 32)
    }
}
