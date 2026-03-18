package com.imsi.imei.utils

import android.content.Context
import android.util.Base64
import android.util.Log
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * 加密数据上传工具类
 * 实现了完整的AES-256与RSA混合加密方案，并将数据安全传输至服务器。
 * 该工具类现在负责：
 * 1. 使用AES加密数据体。
 * 2. 使用RSA公钥加密AES会话密钥。
 * 3. 构建包含加密数据和加密密钥的JSON载荷。
 * 4. 使用OkHttp将载荷发送到服务器。
 */
class SecureDataUploader {

    companion object {
        private const val TAG = "SecureDataUploader"
        private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        private const val AES_TRANSFORMATION = "AES/ECB/PKCS5Padding"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        /**
         * 上传加密数据到服务器
         * @param serverUrl 服务器API端点
         * @param dataJson 要上传的JSON数据
         * @param rsaPublicKeyString 服务器提供的RSA公钥（Base64编码）
         * @param onComplete 回调函数，通知上传结果（成功或失败）
         */
        fun uploadData(
            serverUrl: String,
            dataJson: JSONObject,
            rsaPublicKeyString: String,
            onComplete: (Boolean) -> Unit
        ) {
            try {
                Log.d(TAG, "开始上传数据到: $serverUrl")

                // Step 1: 使用AES生成会话密钥并加密数据
                val aesKey = generateAesKey()
                val encryptedData = aesEncrypt(dataJson.toString(), aesKey)

                // Step 2: 使用RSA公钥加密AES会话密钥
                val publicKey = getPublicKey(rsaPublicKeyString)
                val encryptedKey = rsaEncrypt(aesKey.encoded, publicKey)

                // Step 3: 构建最终的JSON载荷
                val payloadJson = JSONObject().apply {
                    put("encrypted_data", Base64.encodeToString(encryptedData, Base64.NO_WRAP or Base64.URL_SAFE))
                    put("encrypted_key", Base64.encodeToString(encryptedKey, Base64.NO_WRAP or Base64.URL_SAFE))  //服务器端解码
                }

                Log.d(TAG, "上传的载荷: $payloadJson")
                //Log.d(TAG, "上传的载荷: ${payloadJson.toString()}")

                // val JSON = "application/json; charset=utf-8".toMediaType()
                val requestBody = payloadJson.toString().toRequestBody(JSON)
                val request = Request.Builder()
                    .url(serverUrl)
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "上传失败: ${e.message}", e)
                        onComplete(false)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val responseBody = response.body?.string() // ⚠️ 只能读一次！
                            if (response.isSuccessful) {
                                Log.i(TAG, "数据成功上传！响应: $responseBody")
                                
                                // 尝试解析服务器响应以获取凭证哈希或认证结果
                                try {
                                    val responseJson = JSONObject(responseBody!!)
                                    val success = responseJson.optBoolean("success", false)
                                    val message = responseJson.optString("message", "上传成功")
                                    
                                    if (!success) {
                                        Log.w(TAG, "服务器返回失败状态: $message")
                                        onComplete(false)
                                    } else {
                                        Log.d(TAG, "服务器返回成功状态: $message")
                                        onComplete(true)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "无法解析服务器响应: ${e.message}")
                                    onComplete(true) // 假设上传成功，即使无法解析响应
                                }
                            } else {
                                Log.e(TAG, "上传失败，服务器返回: ${response.code}, 响应: $responseBody") // 打印真实内容
                                onComplete(false)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析响应失败: ${e.message}", e)
                            onComplete(false)
                        }
                    }
                })

            } catch (e: Exception) {
                Log.e(TAG, "上传异常: ${e.message}", e)
                onComplete(false)
            }
        }

        /**
         * 上传加密数据到服务器并获取认证结果
         * @param serverUrl 服务器API端点
         * @param dataJson 要上传的JSON数据
         * @param rsaPublicKeyString 服务器提供的RSA公钥（Base64编码）
         * @param onAuthResult 回调函数，通知认证结果（成功或失败）、消息和凭证哈希
         */
        fun uploadDataWithAuthResult(
            serverUrl: String,
            dataJson: JSONObject,
            rsaPublicKeyString: String,
            onAuthResult: (Boolean, String, String?) -> Unit
        ) {
            try {
                Log.d(TAG, "开始上传数据到: $serverUrl 并获取认证结果")

                // Step 1: 使用AES生成会话密钥并加密数据
                val aesKey = generateAesKey()
                val encryptedData = aesEncrypt(dataJson.toString(), aesKey)

                // Step 2: 使用RSA公钥加密AES会话密钥
                val publicKey = getPublicKey(rsaPublicKeyString)
                val encryptedKey = rsaEncrypt(aesKey.encoded, publicKey)

                // Step 3: 构建最终的JSON载荷
                val payloadJson = JSONObject().apply {
                    put("encrypted_data", Base64.encodeToString(encryptedData, Base64.NO_WRAP or Base64.URL_SAFE))
                    put("encrypted_key", Base64.encodeToString(encryptedKey, Base64.NO_WRAP or Base64.URL_SAFE))  //服务器端解码
                }

                Log.d(TAG, "上传的载荷: $payloadJson")

                val requestBody = payloadJson.toString().toRequestBody(JSON)
                val request = Request.Builder()
                    .url(serverUrl)
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "上传失败: ${e.message}", e)
                        onAuthResult(false, "网络连接失败: ${e.message}", null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val responseBody = response.body?.string() // ⚠️ 只能读一次！
                            if (response.isSuccessful) {
                                Log.i(TAG, "数据成功上传！响应: $responseBody")
                                
                                // 解析服务器响应以获取认证结果和凭证哈希
                                try {
                                    val responseJson = JSONObject(responseBody!!)
                                    val success = responseJson.optBoolean("success", false)
                                    val message = responseJson.optString("message", "认证成功")
                                    val finalCredentialHash = if (responseJson.has("finalCredentialHash")) responseJson.optString("finalCredentialHash") else null // 服务器返回的凭证哈希
                                    
                                    if (!success) {
                                        Log.w(TAG, "服务器返回认证失败状态: $message")
                                        onAuthResult(false, message, null)
                                    } else {
                                        Log.d(TAG, "服务器返回认证成功状态: $message, 凭证哈希: $finalCredentialHash")
                                        onAuthResult(true, message, finalCredentialHash)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "无法解析服务器响应: ${e.message}")
                                    onAuthResult(false, "无法解析服务器响应: ${e.message}", null)
                                }
                            } else {
                                Log.e(TAG, "上传失败，服务器返回: ${response.code}, 响应: $responseBody")
                                onAuthResult(false, "服务器返回错误: ${response.code}", null)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析响应失败: ${e.message}", e)
                            onAuthResult(false, "解析响应失败: ${e.message}", null)
                        }
                    }
                })

            } catch (e: Exception) {
                Log.e(TAG, "上传异常: ${e.message}", e)
                onAuthResult(false, "上传异常: ${e.message}", null)
            }
        }
        
        /**
         * 获取认证URL
         * @param serverUrl 服务器认证URL端点
         * @param finalCredentialHash 最终凭证哈希
         * @param onAuthUrlResult 回调函数，通知获取认证URL的结果
         */
        fun getAuthUrl(
            serverUrl: String,
            finalCredentialHash: String,
            onAuthUrlResult: (Boolean, String, String?) -> Unit
        ) {
            try {
                Log.d(TAG, "开始获取认证URL，凭证哈希: $finalCredentialHash")

                // 构建请求体
                val requestBody = JSONObject().apply {
                    put("finalCredentialHash", finalCredentialHash)
                }.toString().toRequestBody(JSON)

                val request = Request.Builder()
                    .url(serverUrl)
                    .post(requestBody)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "获取认证URL失败: ${e.message}", e)
                        onAuthUrlResult(false, "网络连接失败: ${e.message}", null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        try {
                            val responseBody = response.body?.string() // ⚠️ 只能读一次！
                            if (response.isSuccessful) {
                                Log.i(TAG, "获取认证URL成功！响应: $responseBody")
                                
                                // 解析服务器响应
                                try {
                                    val responseJson = JSONObject(responseBody!!)
                                    val success = responseJson.optBoolean("success", false)
                                    val message = responseJson.optString("message", "获取认证URL成功")
                                    val authUrl = if (responseJson.has("authUrl")) responseJson.optString("authUrl") else null
                                    
                                    if (!success || authUrl.isNullOrEmpty()) {
                                        Log.w(TAG, "服务器返回获取认证URL失败: $message")
                                        onAuthUrlResult(false, message, null)
                                    } else {
                                        Log.d(TAG, "成功获取认证URL: $authUrl")
                                        onAuthUrlResult(true, message, authUrl)
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "无法解析服务器响应: ${e.message}")
                                    onAuthUrlResult(false, "无法解析认证URL响应: ${e.message}", null)
                                }
                            } else {
                                Log.e(TAG, "获取认证URL失败，服务器返回: ${response.code}, 响应: $responseBody")
                                onAuthUrlResult(false, "服务器返回错误: ${response.code}", null)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "解析响应失败: ${e.message}", e)
                            onAuthUrlResult(false, "解析响应失败: ${e.message}", null)
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "获取认证URL异常: ${e.message}", e)
                onAuthUrlResult(false, "获取认证URL异常: ${e.message}", null)
            }
        }

        /**
         * 生成AES会话密钥
         */
        private fun generateAesKey(): SecretKey =
            KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

        /**
         * 使用AES密钥加密数据 - 与服务器端保持一致
         */
        private fun aesEncrypt(plain: String, key: SecretKey): ByteArray {
            Log.d(TAG, "开始AES加密，明文长度: ${plain.length}")
            return Cipher.getInstance(AES_TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key)
            }.doFinal(plain.toByteArray())
        }

        /**
         * 使用RSA公钥加密数据
         */
        private fun rsaEncrypt(data: ByteArray, pub: PublicKey): ByteArray {
            Log.d(TAG, "开始RSA加密，数据长度: ${data.size}")
            return Cipher.getInstance(RSA_TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, pub)
            }.doFinal(data)
        }

        /**
         * 将Base64编码的公钥字符串转换为PublicKey对象
         */
        private fun getPublicKey(b64: String): PublicKey {
            try {
                // 清理空白字符
                val cleaned = b64.trim().replace("\\s+".toRegex(), "")
                Log.d(TAG, "准备解码公钥，长度: ${cleaned.length}, 前50字符: ${cleaned.substring(0, minOf(50, cleaned.length))}")

                val keyBytes = Base64.decode(cleaned, Base64.DEFAULT)
                val keySpec = X509EncodedKeySpec(keyBytes)
                return KeyFactory.getInstance("RSA").generatePublic(keySpec)
            } catch (e: Exception) {
                Log.e(TAG, "解析公钥失败", e)
                throw e
            }
        }
    }
}