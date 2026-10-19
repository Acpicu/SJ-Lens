package com.sj.followclip.util

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 敏感小数据的落盘加密（目前只用于 B站 登录 Cookie）。
 *
 * 为什么自己写：工程约定零第三方库，`security-crypto` 会带进一批依赖。
 * 这里只用 Android Keystore 里的 AES-256-GCM：密钥由系统托管、不可导出，
 * 拿到应用私有目录的文件也解不开。
 *
 * 设计要点：密钥别名固定；每条记录独立随机 IV，密文与 IV 一起 Base64 存；
 * Keystore 不可用时**不落盘**（写入被忽略），绝不退回明文。
 */
object SecureStore {

    private const val PREF = "sj_secure"
    private const val KEY_ALIAS = "sjlens_secure_v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val TAG_BITS = 128

    private fun secretKey(): SecretKey? = runCatching {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: generateKey()
    }.getOrNull()

    private fun generateKey(): SecretKey? = runCatching {
        val generator = KeyGenerator.getInstance("AES", KEYSTORE)
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        generator.generateKey()
    }.getOrNull()

    fun put(context: Context, key: String, value: String) {
        val key0 = secretKey() ?: return
        val encrypted = runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key0)
            val body = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(body, Base64.NO_WRAP)
        }.getOrNull() ?: return
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(key, encrypted).apply()
    }

    fun get(context: Context, key: String): String? = runCatching {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(key, null) ?: return null
        val parts = raw.split(":", limit = 2)
        if (parts.size != 2) return null
        val iv = Base64.decode(parts[0], Base64.NO_WRAP)
        val body = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey() ?: return null, GCMParameterSpec(TAG_BITS, iv))
        String(cipher.doFinal(body), Charsets.UTF_8)
    }.getOrNull()

    fun remove(context: Context, key: String) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(key).apply()
    }
}
