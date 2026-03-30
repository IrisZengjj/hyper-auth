package com.imsi.imei.utils

import com.imsi.imei.utils.SecureDataUploader
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import com.imsi.imei.utils.ServerConfig

/**
 * 数据传输工具类
 * 实现设备信息的加密传输
 */
class DataTransmitter {
    companion object {
        private const val TAG = "DataTransmitter"
        private const val PREFS_NAME = "server_config"
        private const val SERVER_URL_KEY = "server_url"
        // 实际的服务器IP地址
        private val DEFAULT_SERVER_URL: String
            get() = ServerConfig.API_URL
       // 从共享偏好设置获取服务器URL
        private fun getServerUrl(context: Context? = null): String {
            return if(context != null) {
                ServerConfig.init(context)
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.getString(SERVER_URL_KEY, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
            } else {
                // 当无法获取context时，使用默认值或环境变量
                DEFAULT_SERVER_URL
            }
        }
        
        private fun getEndpoints(context: Context): Triple<String, String, String> {
            val baseUrl = getServerUrl(context)
            return Triple("$baseUrl/configured-public-key", "$baseUrl/upload", "$baseUrl/auth-url")
        }

        private val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        private val gson = Gson()
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /**
         * 获取服务器RSA公钥
         */
        private fun fetchPublicKey(context: Context): String? {
            try {
                val (publicKeyEndpoint, _, _) = getEndpoints(context)
                val request = Request.Builder()
                    .url(publicKeyEndpoint)
                    .get()
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("获取公钥失败: ${response.code}")
                    val publicKey = response.body?.string()
                    Log.d(TAG, "成功获取公钥，长度: ${publicKey?.length ?: 0}")
                    return publicKey ?: throw IOException("响应体为空")
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取公钥失败: ${e.message}", e)
                return null
            }
        }

        /**
         * 发送加密的设备信息到服务器
         */
        fun sendDeviceInfo(context: Context): Boolean {
            return sendDeviceInfoInternal(context, "常规采集")
        }

        /**
         * 发送认证数据到服务器（用于认证时的实时采集）
         * @param context 应用上下文
         * @param authData 认证数据
         * @param onComplete 完成回调，参数为是否成功、消息和凭证哈希
         */
        fun sendDeviceInfoForAuth(context: Context, authData: JSONObject, onComplete: (Boolean, String, String?) -> Unit) {
            Thread {
                sendDeviceInfoInternalWithAuthResult(context, "实时认证", authData, onComplete)
            }.start()
        }

        /**
         * 获取认证URL
         * @param context 应用上下文
         * @param finalCredentialHash 最终凭证哈希
         * @param onAuthUrlResult 回调函数，通知获取认证URL的结果
         */
        fun getAuthUrl(context: Context, finalCredentialHash: String, onAuthUrlResult: (Boolean, String, String?) -> Unit) {
            Thread {
                getAuthUrlInternal(context, finalCredentialHash, onAuthUrlResult)
            }.start()
        }

        /**
         * 内部方法：发送设备信息到服务器
         */
        private fun sendDeviceInfoInternal(
            context: Context,
            collectionType: String,
            customData: JSONObject? = null
        ): Boolean {
            try {
                Log.d(TAG, "开始发送设备信息...类型: $collectionType")

                // 获取RSA公钥
                val publicKey = fetchPublicKey(context) ?: return false

                val deviceInfo = if (customData != null) {
                    // 使用传入的自定义数据
                    customData
                } else {
                    // 获取设备信息
                    val deviceInfoManager = ga.mdm.DeviceInfoManager.getInstance()
                    val deviceInfoArray = deviceInfoManager.getDeviceInfo()

                    if (deviceInfoArray == null) {
                        Log.e(TAG, "设备信息获取失败")
                        return false
                    }

                    // 构建设备信息JSON
                    val info = JSONObject()

                    // 添加硬件信息
                    val hardwareJson = JSONObject()
                    hardwareJson.put("设备名称", if (deviceInfoArray.size > 4) deviceInfoArray[4]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("设备型号", if (deviceInfoArray.size > 5) deviceInfoArray[5]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("硬件序列号", if (deviceInfoArray.size > 9) deviceInfoArray[9]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("IMEI主卡", if (deviceInfoArray.size > 0) deviceInfoArray[0]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("IMEI副卡", if (deviceInfoArray.size > 1) deviceInfoArray[1]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("CPU架构", if (deviceInfoArray.size > 15) deviceInfoArray[15]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("内存大小", if (deviceInfoArray.size > 2) deviceInfoArray[2]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("基带版本", if (deviceInfoArray.size > 7) deviceInfoArray[7]?.toString() ?: "未收集到信息" else "未收集到信息")
                    info.put("硬件信息", hardwareJson)

                    // 添加软件信息
                    val softwareJson = JSONObject()
                    softwareJson.put("操作系统名称", if (deviceInfoArray.size > 6) deviceInfoArray[6]?.toString() ?: "未收集到信息" else "未收集到信息")
                    softwareJson.put("操作系统版本", if (deviceInfoArray.size > 8) deviceInfoArray[8]?.toString() ?: "未收集到信息" else "未收集到信息")
                    softwareJson.put("Android_ID", if (deviceInfoArray.size > 16) deviceInfoArray[16]?.toString() ?: "未收集到信息" else "未收集到信息")
                    softwareJson.put("内核版本", if (deviceInfoArray.size > 7) deviceInfoArray[7]?.toString() ?: "未收集到信息" else "未收集到信息")
                    info.put("软件信息", softwareJson)

                    // 添加SIM卡信息
                    val simJson = JSONObject()
                    simJson.put("卡1手机号", if (deviceInfoArray.size > 17) deviceInfoArray[17]?.toString() ?: "未收集到信息" else "未收集到信息")
                    simJson.put("卡2手机号", if (deviceInfoArray.size > 18) deviceInfoArray[18]?.toString() ?: "未收集到信息" else "未收集到信息")
                    simJson.put("卡1IMSI", if (deviceInfoArray.size > 13) deviceInfoArray[13]?.toString() ?: "未收集到信息" else "未收集到信息")
                    simJson.put("卡2IMSI", if (deviceInfoArray.size > 14) deviceInfoArray[14]?.toString() ?: "未收集到信息" else "未收集到信息")
                    simJson.put("卡1ICCID", if (deviceInfoArray.size > 11) deviceInfoArray[11]?.toString() ?: "未收集到信息" else "未收集到信息")
                    simJson.put("卡2ICCID", if (deviceInfoArray.size > 12) deviceInfoArray[12]?.toString() ?: "未收集到信息" else "未收集到信息")
                    info.put("SIM卡信息", simJson)

                    // 添加时间戳和采集类型
                    info.put("采集时间", System.currentTimeMillis())
                    info.put("采集类型", collectionType)

                    info
                }

                // 加密数据
                val deviceInfoJson = deviceInfo.toString()
                Log.d(TAG, "设备信息JSON: $deviceInfoJson")
                val (publicKeyEndpoint, dataUploadEndpoint, _) = getEndpoints(context)
                Log.d(TAG, "准备上传到端点: $dataUploadEndpoint")
                Log.d(TAG, "公钥端点: $publicKeyEndpoint")

                val plainJson = deviceInfo
                SecureDataUploader.uploadData(
                    dataUploadEndpoint,
                    deviceInfo,
                    publicKey
                ) { success ->
                    if (success) {
                        Log.d(TAG, "设备信息上传成功 - 类型: $collectionType")
                    } else {
                        Log.e(TAG, "设备信息上传失败 - 类型: $collectionType")
                    }
                }
                return true
            } catch (e: Exception) {
                Log.e(TAG, "上传设备信息失败: ${e.message}", e)
                return false
            }
        }
        
        /**
         * 内部方法：发送设备信息到服务器并获取认证结果
         */
        private fun sendDeviceInfoInternalWithAuthResult(
            context: Context,
            collectionType: String,
            customData: JSONObject? = null,
            onAuthResult: (Boolean, String, String?) -> Unit
        ): Boolean {
            try {
                Log.d(TAG, "开始发送设备信息...类型: $collectionType")

                // 获取RSA公钥
                val publicKey = fetchPublicKey(context) ?: run {
                    onAuthResult(false, "无法获取服务器公钥", null)
                    return false
                }

                val deviceInfo = if (customData != null) {
                    // 使用传入的自定义数据
                    customData
                } else {
                    // 获取设备信息
                    val deviceInfoManager = ga.mdm.DeviceInfoManager.getInstance()
                    val deviceInfoArray = deviceInfoManager.getDeviceInfo()

                    if (deviceInfoArray == null) {
                        Log.e(TAG, "设备信息获取失败")
                        onAuthResult(false, "设备信息获取失败", null)
                        return false
                    }

                    // 构建设备信息JSON
                    val info = JSONObject()

                    // 添加硬件信息
                    val hardwareJson = JSONObject()
                    hardwareJson.put("设备名称", if (deviceInfoArray.size > 4) deviceInfoArray[4]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("设备型号", if (deviceInfoArray.size > 5) deviceInfoArray[5]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("硬件序列号", if (deviceInfoArray.size > 9) deviceInfoArray[9]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("IMEI主卡", if (deviceInfoArray.size > 0) deviceInfoArray[0]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("IMEI副卡", if (deviceInfoArray.size > 1) deviceInfoArray[1]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("CPU架构", if (deviceInfoArray.size > 15) deviceInfoArray[15]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("内存大小", if (deviceInfoArray.size > 2) deviceInfoArray[2]?.toString() ?: "未收集到信息" else "未收集到信息")
                    hardwareJson.put("基带版本", if (deviceInfoArray.size > 7) deviceInfoArray[7]?.toString() ?: "未收集到信息" else "未收集到信息")
                    info.put("硬件信息", hardwareJson)

                    // 添加软件信息
                    val softwareJson = JSONObject()
                    softwareJson.put("操作系统名称", if (deviceInfoArray.size > 6) deviceInfoArray[6]?.toString() ?: "未收集到信息" else "未收集到信息")
                    softwareJson.put("操作系统版本", if (deviceInfoArray.size > 8) deviceInfoArray[8]?.toString() ?: "未收集到信息" else "未收集到信息")
                    softwareJson.put("Android_ID", if (deviceInfoArray.size > 16) deviceInfoArray[16]?.toString() ?: "未收集到信息" else "未收集到信息")
                    softwareJson.put("内核版本", if (deviceInfoArray.size > 7) deviceInfoArray[7]?.toString() ?: "未收集到信息" else "未收集到信息")
                    info.put("软件信息", softwareJson)

                    // 添加SIM卡信息
                    val simJson = JSONObject()
                    simJson.put("卡1手机号", if (deviceInfoArray.size > 17) deviceInfoArray[17]?.toString() ?: "未收集到信息" else "未收集到信息")
                    simJson.put("卡2手机号", if (deviceInfoArray.size > 18) deviceInfoArray[18]?.toString() ?: "未收集到信息" else "未收集到信息")
                    simJson.put("卡1IMSI", if (deviceInfoArray.size > 13) deviceInfoArray[13]?.toString() ?: "未收集到信息" else "未收集到信息")
                    simJson.put("卡2IMSI", if (deviceInfoArray.size > 14) deviceInfoArray[14]?.toString() ?: "未收集到信息" else "未收集到信息")
                    simJson.put("卡1ICCID", if (deviceInfoArray.size > 11) deviceInfoArray[11]?.toString() ?: "未收集到信息" else "未收集到信息")
                    simJson.put("卡2ICCID", if (deviceInfoArray.size > 12) deviceInfoArray[12]?.toString() ?: "未收集到信息" else "未收集到信息")
                    info.put("SIM卡信息", simJson)

                    // 添加时间戳和采集类型
                    info.put("采集时间", System.currentTimeMillis())
                    info.put("采集类型", collectionType)

                    info
                }

                // 加密数据
                val deviceInfoJson = deviceInfo.toString()
                Log.d(TAG, "设备信息JSON: $deviceInfoJson")
                val (publicKeyEndpoint, dataUploadEndpoint, _) = getEndpoints(context)
                Log.d(TAG, "准备上传到端点: $dataUploadEndpoint")
                Log.d(TAG, "公钥端点: $publicKeyEndpoint")

                val plainJson = deviceInfo
                // 使用新的带认证结果的方法，该方法会返回凭证哈希
                SecureDataUploader.uploadDataWithAuthResult(
                    dataUploadEndpoint,
                    deviceInfo,
                    publicKey
                ) { success, message, finalCredentialHash ->
                    // 在实际实现中，服务器应该返回凭证哈希
                    // 这里返回从服务器响应中获取的凭证哈希
                    if (success) {
                        Log.d(TAG, "设备信息上传成功 - 类型: $collectionType, 凭证哈希: $finalCredentialHash")
                        onAuthResult(true, message, finalCredentialHash)
                    } else {
                        Log.e(TAG, "设备信息上传失败 - 类型: $collectionType - $message")
                        onAuthResult(false, message, null)
                    }
                }
                return true
            } catch (e: Exception) {
                Log.e(TAG, "上传设备信息失败: ${e.message}", e)
                onAuthResult(false, "上传设备信息失败: ${e.message}", null)
                return false
            }
        }
        
        /**
         * 内部方法：获取认证URL
         */
        private fun getAuthUrlInternal(
            context: Context,
            finalCredentialHash: String,
            onAuthUrlResult: (Boolean, String, String?) -> Unit
        ) {
            try {
                Log.d(TAG, "开始获取认证URL，凭证哈希: $finalCredentialHash")
                
                val (_, _, authUrlEndpoint) = getEndpoints(context)
                Log.d(TAG, "认证URL端点: $authUrlEndpoint")
                
                SecureDataUploader.getAuthUrl(
                    authUrlEndpoint,
                    finalCredentialHash,
                    onAuthUrlResult
                )
            } catch (e: Exception) {
                Log.e(TAG, "获取认证URL失败: ${e.message}", e)
                onAuthUrlResult(false, "获取认证URL失败: ${e.message}", null)
            }
        }
        
        /**
         * 更新服务器URL配置
         */
        fun updateServerUrl(context: Context, newUrl: String): Boolean {
            return try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putString(SERVER_URL_KEY, newUrl)
                editor.apply()
                Log.d(TAG, "服务器URL已更新为: $newUrl")
                true
            } catch (e: Exception) {
                Log.e(TAG, "更新服务器URL失败: ${e.message}", e)
                false
            }
        }
        
        /**
         * 获取当前服务器URL
         */
        fun getCurrentServerUrl(context: Context): String {
            return getServerUrl(context)
        }
    }
}