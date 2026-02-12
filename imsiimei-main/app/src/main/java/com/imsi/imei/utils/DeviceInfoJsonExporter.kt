package com.imsi.imei.utils

import android.content.Context
import android.util.Log
import ga.mdm.DeviceInfoManager
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 设备信息JSON导出工具类
 * 用于将收集到的设备信息导出为JSON文件
 */
class DeviceInfoJsonExporter {
    companion object {
        private const val TAG = "DeviceInfoJsonExporter"

        /**
         * 收集设备信息并保存为JSON文件
         * @param context 应用上下文
         * @return 是否成功保存
         */
        fun exportDeviceInfoToJson(context: Context): Boolean {
            return try {
                val deviceInfoManager = DeviceInfoManager.getInstance()
                val deviceInfo = deviceInfoManager.getDeviceInfo()

                if (deviceInfo == null) {
                    Log.e(TAG, "设备信息获取失败")
                    return false
                }

                // 创建JSON对象
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

                // 添加时间戳和采集类型
                jsonObject.put("采集时间", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                jsonObject.put("采集类型", "手动采集")

                // 使用新的文件管理器保存数据
                val success = FileManager.saveDataToFile(context, jsonObject)

                if (success) {
                    Log.i(TAG, "设备信息已成功保存为JSON文件")
                    // 上传到服务器
                    DataTransmitter.sendDeviceInfo(context)
                }

                success
            } catch (e: Exception) {
                Log.e(TAG, "导出设备信息失败: ${e.message}", e)
                false
            }
        }
    }
}