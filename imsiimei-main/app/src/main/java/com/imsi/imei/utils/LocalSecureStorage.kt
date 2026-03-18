package com.imsi.imei.utils

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

/**
 * 本地安全存储工具类，负责使用AES加密、Android Keystore托管密钥。
 */
class LocalSecureStorage {

    private val KEY_ALIAS = "my_app_aes_key_alias"
    private val TRANSFORMATION = "AES/CBC/PKCS7Padding"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val TAG = "LocalSecureStorage"

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
        keyStore.load(null)

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).run {
                setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                setRandomizedEncryptionRequired(false) // For consistent IV, as we're managing it
                build()
            }

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
        val secretKeyEntry = keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry
        return secretKeyEntry.secretKey
    }

    /**
     * 将数据加密后存储到本地私有文件
     * @param context 应用上下文
     * @param fileName 文件名
     * @param data 要存储的数据（JSON字符串）
     * @return 是否成功
     */
    fun encryptAndSave(context: Context, fileName: String, data: String): Boolean {
        try {
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)

            val iv = cipher.iv
            val encryptedData = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

            val file = File(context.filesDir, fileName)
            FileOutputStream(file).use { fos ->
                fos.write(iv) // 将IV作为文件头
                fos.write(encryptedData)
            }
            Log.i(TAG, "数据已成功加密并保存到本地文件: ${file.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "加密并保存数据失败: ${e.message}", e)
            return false
        }
    }

    /**
     * 从本地文件解密并读取数据
     * @param context 应用上下文
     * @param fileName 文件名
     * @return 解密后的JSON字符串，如果失败则返回null
     */
    fun decryptData(context: Context, fileName: String): String? {
        return try {
            val file = File(context.filesDir, fileName)
            val ivSize = 16 // IV for AES/CBC is 16 bytes
            val iv = ByteArray(ivSize)
            val encryptedBytes = ByteArray(file.length().toInt() - ivSize)

            FileInputStream(file).use { fis ->
                fis.read(iv)
                fis.read(encryptedBytes)
            }

            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(encryptedBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "解密数据失败: ${e.message}", e)
            null
        }
    }
}
