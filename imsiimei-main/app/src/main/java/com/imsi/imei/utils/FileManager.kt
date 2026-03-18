package com.imsi.imei.utils

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * 文件管理器
 * 负责本地文件的存储、更新和删除操作
 */
class FileManager {
    companion object {
        private const val TAG = "FileManager"
        private const val FILE_NAME_PREFIX = "device_info_"
        
        /**
         * 保存数据到文件
         * @param context 应用上下文
         * @param data 要保存的数据
         * @return 是否保存成功
         */
        fun saveDataToFile(context: Context, data: JSONObject): Boolean {
            return try {
                val fileName = "${FILE_NAME_PREFIX}${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
                val file = File(context.getExternalFilesDir(null), fileName)
                
                FileWriter(file).use { writer ->
                    writer.write(data.toString(4))
                }
                Log.i(TAG, "数据已保存到: ${file.absolutePath}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "保存数据失败: ${e.message}", e)
                false
            }
        }
        
        /**
         * 删除旧的数据文件
         * @param context 应用上下文
         * @param keepLatest 保留最新的几个文件
         */
        fun deleteOldFiles(context: Context, keepLatest: Int = 5) {
            try {
                val externalDir = context.getExternalFilesDir(null)
                val files = externalDir?.listFiles { file -> 
                    file.name.startsWith(FILE_NAME_PREFIX) && file.name.endsWith(".json")
                }
                
                files?.let {
                    if (it.size > keepLatest) {
                        // 按修改时间排序，删除最旧的文件
                        val sortedFiles = it.sortedBy { file -> file.lastModified() }
                        val filesToDelete = sortedFiles.take(it.size - keepLatest)
                        
                        filesToDelete.forEach { file ->
                            if (file.delete()) {
                                Log.i(TAG, "已删除旧文件: ${file.name}")
                            } else {
                                Log.w(TAG, "删除文件失败: ${file.name}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "删除旧文件失败: ${e.message}", e)
            }
        }
        
        /**
         * 检查是否是首次采集
         * @param context 应用上下文
         * @return 是否是首次采集
         */
        fun isFirstCollection(context: Context): Boolean {
            val externalDir = context.getExternalFilesDir(null)
            val files = externalDir?.listFiles { file -> 
                file.name.startsWith(FILE_NAME_PREFIX) && file.name.endsWith(".json")
            }
            return files.isNullOrEmpty()
        }
        
        /**
         * 获取数据文件数量
         */
        fun getDataFileCount(context: Context): Int {
            val externalDir = context.getExternalFilesDir(null)
            val files = externalDir?.listFiles { file -> 
                file.name.startsWith(FILE_NAME_PREFIX) && file.name.endsWith(".json")
            }
            return files?.size ?: 0
        }
    }
}