package com.imsi.imei.utils

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File

/**
 * 数据对比管理器
 * 负责比较新旧数据，判断是否需要更新文件
 */
class DataComparisonManager {
    companion object {
        private const val TAG = "DataComparisonManager"
        
        /**
         * 比较新旧数据是否发生变化
         * @param newData 新采集的数据
         * @param oldData 本地存储的旧数据
         * @return 是否发生变化
         */
        fun hasDataChanged(newData: JSONObject, oldData: JSONObject): Boolean {
            try {
                // 比较硬件信息
                val newHardware = newData.getJSONObject("硬件信息")
                val oldHardware = oldData.getJSONObject("硬件信息")
                if (isHardwareChanged(newHardware, oldHardware)) {
                    return true
                }
                
                // 比较软件信息
                val newSoftware = newData.getJSONObject("软件信息")
                val oldSoftware = oldData.getJSONObject("软件信息")
                if (isSoftwareChanged(newSoftware, oldSoftware)) {
                    return true
                }
                
                // 比较SIM卡信息
                val newSim = newData.getJSONObject("SIM卡信息")
                val oldSim = oldData.getJSONObject("SIM卡信息")
                if (isSimInfoChanged(newSim, oldSim)) {
                    return true
                }
                
                return false
            } catch (e: Exception) {
                Log.e(TAG, "数据对比失败: ${e.message}", e)
                return true // 如果对比失败，默认认为数据发生变化
            }
        }
        
        /**
         * 比较硬件信息是否变化
         */
        private fun isHardwareChanged(new: JSONObject, old: JSONObject): Boolean {
            val keys = arrayOf("设备名称", "设备型号", "硬件序列号", "IMEI主卡", "IMEI副卡", "CPU架构", "内存大小", "基带版本")
            return isAnyKeyChanged(new, old, keys)
        }
        
        /**
         * 比较软件信息是否变化
         */
        private fun isSoftwareChanged(new: JSONObject, old: JSONObject): Boolean {
            val keys = arrayOf("操作系统名称", "操作系统版本", "Android_ID", "内核版本")
            return isAnyKeyChanged(new, old, keys)
        }
        
        /**
         * 比较SIM卡信息是否变化
         */
        private fun isSimInfoChanged(new: JSONObject, old: JSONObject): Boolean {
            val keys = arrayOf("卡1手机号", "卡2手机号", "卡1IMSI", "卡2IMSI", "卡1ICCID", "卡2ICCID")
            return isAnyKeyChanged(new, old, keys)
        }
        
        /**
         * 检查指定键的值是否发生变化
         */
        private fun isAnyKeyChanged(new: JSONObject, old: JSONObject, keys: Array<String>): Boolean {
            for (key in keys) {
                val newValue = new.optString(key, "")
                val oldValue = old.optString(key, "")
                //if (newValue != oldValue && newValue.isNotEmpty() && oldValue.isNotEmpty()) {
                if (newValue != oldValue) {    // 移除 newValue.isNotEmpty() && oldValue.isNotEmpty() 的限制
                    Log.d(TAG, "检测到数据变化 - $key: $oldValue -> $newValue")
                    return true
                }
            }
            return false
        }
        
        /**
         * 获取最新的本地数据文件
         */
        fun getLatestDataFile(context: Context): File? {
            val externalDir = context.getExternalFilesDir(null)
            val files = externalDir?.listFiles { file -> 
                file.name.startsWith("device_info_") && file.name.endsWith(".json")
            }
            
            return files?.maxByOrNull { it.lastModified() }
        }
        
        /**
         * 读取本地数据文件内容
         */
        fun readDataFile(file: File): JSONObject? {
            return try {
                val content = file.readText()
                JSONObject(content)
            } catch (e: Exception) {
                Log.e(TAG, "读取数据文件失败: ${e.message}", e)
                null
            }
        }
    }
}