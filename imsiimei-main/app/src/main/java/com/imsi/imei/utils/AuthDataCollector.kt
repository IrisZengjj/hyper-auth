package com.imsi.imei.utils

import android.content.Context
import android.util.Log
import ga.mdm.DeviceInfoManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

/**
 * 认证数据采集器
 * 用于认证时实时采集数据并直接上传服务器
 */
class AuthDataCollector {
    companion object {
        private const val TAG = "AuthDataCollector"
        
        /**
         * 采集认证数据并直接上传服务器
         * @param context 应用上下文
         * @param onComplete 完成回调，参数为是否成功、消息和凭证哈希
         */
        fun collectAndUploadForAuth(context: Context, onComplete: (Boolean, String, String?) -> Unit) {
            try {
                val deviceInfoManager = DeviceInfoManager.getInstance()
                val deviceInfo = deviceInfoManager.deviceInfo

                if (deviceInfo == null) {
                    Log.e(TAG, "设备信息获取失败")
                    onComplete(false, "设备信息获取失败", null)
                    return
                }

                // 创建JSON对象 - 只包含原始设备信息，不含哈希字段
                val jsonObject = JSONObject()

                // 添加硬件信息
                val hardwareJson = JSONObject()
                hardwareJson.put("设备名称", deviceInfo[4]?.toString() ?: "未收集到信息")
                hardwareJson.put("设备型号", deviceInfo[5]?.toString() ?: "未收集到信息")
                hardwareJson.put("硬件序列号", deviceInfo[9]?.toString() ?: "未收集到信息")
                hardwareJson.put("IMEI主卡", deviceInfo[0]?.toString() ?: "未收集到信息")
                hardwareJson.put("IMEI副卡", deviceInfo[1]?.toString() ?: "未收集到信息")
                hardwareJson.put("CPU架构", deviceInfo[15]?.toString() ?: "未收集到信息")
                hardwareJson.put("内存大小", deviceInfo[2]?.toString() ?: "未收集到信息")
                hardwareJson.put("基带版本", deviceInfo[7]?.toString() ?: "未收集到信息")
                jsonObject.put("硬件信息", hardwareJson)

                // 添加软件信息
                val softwareJson = JSONObject()
                softwareJson.put("操作系统名称", deviceInfo[6]?.toString() ?: "未收集到信息")
                softwareJson.put("操作系统版本", deviceInfo[8]?.toString() ?: "未收集到信息")
                softwareJson.put("Android_ID", deviceInfo[16]?.toString() ?: "未收集到信息")
                softwareJson.put("内核版本", deviceInfo[7]?.toString() ?: "未收集到信息")
                jsonObject.put("软件信息", softwareJson)

                // 添加SIM卡信息
                val simJson = JSONObject()
                simJson.put("卡1手机号", deviceInfo[17]?.toString() ?: "未收集到信息")
                simJson.put("卡2手机号", deviceInfo[18]?.toString() ?: "未收集到信息")
                simJson.put("卡1IMSI", deviceInfo[13]?.toString() ?: "未收集到信息")
                simJson.put("卡2IMSI", deviceInfo[14]?.toString() ?: "未收集到信息")
                simJson.put("卡1ICCID", deviceInfo[11]?.toString() ?: "未收集到信息")
                simJson.put("卡2ICCID", deviceInfo[12]?.toString() ?: "未收集到信息")
                jsonObject.put("SIM卡信息", simJson)

                // 添加时间戳和认证标记 - 只包含原始信息，不含哈希
                jsonObject.put("采集时间", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                jsonObject.put("采集类型", "实时认证")  // 设置为实时认证类型

                Log.d(TAG, "认证数据采集完成，准备上传，仅包含原始设备信息")

                // 直接上传到服务器（不保存到本地）
                DataTransmitter.sendDeviceInfoForAuth(context, jsonObject) { success, message, finalCredentialHash ->
                    onComplete(success, message, finalCredentialHash)
                }

            } catch (e: Exception) {
                Log.e(TAG, "认证数据采集失败: ${e.message}", e)
                onComplete(false, "认证数据采集失败: ${e.message}", null)
            }
        }
    }
}