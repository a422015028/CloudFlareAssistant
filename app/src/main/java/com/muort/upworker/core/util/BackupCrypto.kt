package com.muort.upworker.core.util

import android.content.Context
import android.util.Base64
import com.muort.upworker.R
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * 备份加密工具
 * 使用 AES-256-CBC + PBKDF2 密码派生密钥
 * 加密格式：salt (16 bytes) + iv (16 bytes) + ciphertext，Base64 编码
 */
object BackupCrypto {

    private const val PBKDF2_ITERATIONS = 10000
    private const val KEY_LENGTH_BITS = 256
    private const val SALT_LENGTH = 16
    private const val IV_LENGTH = 16
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val SECRET_KEY_FACTORY = "PBKDF2WithHmacSHA256"

    /**
     * 加密文本
     * @param plaintext 明文
     * @param password 密码
     * @return Base64 编码的加密数据（含 salt 和 iv）
     */
    fun encrypt(plaintext: String, password: String): String {
        val salt = ByteArray(SALT_LENGTH).also { Random.nextBytes(it) }
        val iv = ByteArray(IV_LENGTH).also { Random.nextBytes(it) }

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // 拼接: salt + iv + ciphertext
        val combined = ByteArray(salt.size + iv.size + ciphertext.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(ciphertext, 0, combined, salt.size + iv.size, ciphertext.size)

        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * 解密文本
     * @param encrypted Base64 编码的加密数据
     * @param password 密码
     * @param context 用于获取本地化错误消息
     * @return 明文字符串
     * @throws IllegalArgumentException 密码错误或数据损坏
     */
    fun decrypt(encrypted: String, password: String, context: Context): String {
        val combined = Base64.decode(encrypted, Base64.NO_WRAP)

        if (combined.size < SALT_LENGTH + IV_LENGTH) {
            throw IllegalArgumentException(context.getString(R.string.repo_crypto_invalid_format))
        }

        val salt = combined.copyOfRange(0, SALT_LENGTH)
        val iv = combined.copyOfRange(SALT_LENGTH, SALT_LENGTH + IV_LENGTH)
        val ciphertext = combined.copyOfRange(SALT_LENGTH + IV_LENGTH, combined.size)

        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))

        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext, Charsets.UTF_8)
    }

    /**
     * 判断数据是否为加密格式（通过尝试 Base64 解码和长度检查）
     */
    fun isEncrypted(data: String): Boolean {
        return try {
            val decoded = Base64.decode(data.trim(), Base64.NO_WRAP)
            decoded.size > SALT_LENGTH + IV_LENGTH && !data.trim().startsWith("{")
        } catch (e: Exception) {
            false
        }
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val keySpec = PBEKeySpec(
            password.toCharArray(),
            salt,
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BITS
        )
        val factory = SecretKeyFactory.getInstance(SECRET_KEY_FACTORY)
        val keyBytes = factory.generateSecret(keySpec).encoded
        return SecretKeySpec(keyBytes, ALGORITHM)
    }
}
