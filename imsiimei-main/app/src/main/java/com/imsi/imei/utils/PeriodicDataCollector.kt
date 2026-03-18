package com.imsi.imei.utils

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import ga.mdm.DeviceInfoManager
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 周期性数据采集器
 * 使用 WorkManager 实现可靠的24小时周期数据采集
 */
class PeriodicDataCollector {
    companion object {
        private const val TAG = "PeriodicDataCollector"
        private const val COLLECTION_INTERVAL_HOURS = 24L // 24小时
        
        /**
         * 启动周期性数据采集（使用 WorkManager）
         * @param context 应用上下文
         */
        fun startPeriodicCollection(context: Context) {
            try {
                // 创建24小时周期的 WorkRequest
                val periodicWorkRequest = PeriodicWorkRequestBuilder<DataCollectionWorker>(
                    COLLECTION_INTERVAL_HOURS, TimeUnit.HOURS
                ).build()

                // 使用唯一工作名称，避免重复创建
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    DataCollectionWorker.WORK_NAME_PERIODIC,
                    ExistingPeriodicWorkPolicy.KEEP,  // 如果已存在则保持
                    periodicWorkRequest
                )
                
                Log.i(TAG, "WorkManager 周期性数据采集已启动，间隔: ${COLLECTION_INTERVAL_HOURS}小时")
            } catch (e: Exception) {
                Log.e(TAG, "启动 WorkManager 周期性采集失败: ${e.message}", e)
            }
        }
        
        /**
         * 停止周期性数据采集
         * @param context 应用上下文
         */
        fun stopPeriodicCollection(context: Context) {
            try {
                WorkManager.getInstance(context).cancelUniqueWork(DataCollectionWorker.WORK_NAME_PERIODIC)
                Log.i(TAG, "WorkManager 周期性数据采集已停止")
            } catch (e: Exception) {
                Log.e(TAG, "停止 WorkManager 周期性采集失败: ${e.message}", e)
            }
        }
        
        /**
         * 执行周期性数据采集任务
         * @param context 应用上下文
         * @return 是否采集成功
         */
        fun executePeriodicCollection(context: Context): Boolean {
            return try {
                Log.i(TAG, "开始执行周期性数据采集")
                
                // 采集设备信息
                val newData = collectDeviceInfo()
                if (newData == null) {
                    Log.e(TAG, "设备信息采集失败")
                    return false
                }
                
                // 检查是否是首次采集
                if (FileManager.isFirstCollection(context)) {
                    Log.i(TAG, "首次采集，直接保存数据")
                    val saveSuccess = FileManager.saveDataToFile(context, newData)
                    if (saveSuccess) {
                        // 上传到服务器
                        DataTransmitter.sendDeviceInfo(context)
                    }
                    return saveSuccess
                }
                
                // 非首次采集，进行数据对比
                val latestFile = DataComparisonManager.getLatestDataFile(context)
                if (latestFile == null) {
                    Log.w(TAG, "未找到历史数据文件，按首次采集处理")
                    return FileManager.saveDataToFile(context, newData)
                }
                
                val oldData = DataComparisonManager.readDataFile(latestFile)
                if (oldData == null) {
                    Log.w(TAG, "读取历史数据失败，按首次采集处理")
                    return FileManager.saveDataToFile(context, newData)
                }
                
                // 比较数据是否发生变化
                if (DataComparisonManager.hasDataChanged(newData, oldData)) {
                    Log.i(TAG, "检测到数据变化，保存新数据")
                    val saveSuccess = FileManager.saveDataToFile(context, newData)
                    if (saveSuccess) {
                        // 删除旧文件
                        latestFile.delete()
                        Log.i(TAG, "已删除旧数据文件: ${latestFile.name}")
                        // 上传新数据到服务器
                        DataTransmitter.sendDeviceInfo(context)
                    }
                    return saveSuccess
                } else {
                    Log.i(TAG, "数据无变化，不进行文件更新")
                    // 即使数据无变化，也更新采集时间并上传（用于服务器记录）
                    newData.put("采集时间", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                    DataTransmitter.sendDeviceInfo(context)
                    return true
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "周期性数据采集失败: ${e.message}", e)
                return false
            }
        }
        
        /**
         * 采集设备信息
         */
        private fun collectDeviceInfo(): JSONObject? {
            return try {
                val deviceInfoManager = DeviceInfoManager.getInstance()
                val deviceInfo = deviceInfoManager.getDeviceInfo()

                if (deviceInfo == null) {
                    Log.e(TAG, "设备信息获取失败")
                    return null
                }

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
                jsonObject.put("采集类型", "周期性采集")

                jsonObject
            } catch (e: Exception) {
                Log.e(TAG, "采集设备信息失败: ${e.message}", e)
                null
            }
        }
    }
}