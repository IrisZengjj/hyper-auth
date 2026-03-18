package com.imsi.imei.utils

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.*

/**
 * WorkManager 数据采集工作器
 * 负责执行周期性数据采集任务，即使应用被杀死也能继续工作
 */
class DataCollectionWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        private const val TAG = "DataCollectionWorker"
        const val WORK_NAME_PERIODIC = "periodic_data_collection"
    }

    override fun doWork(): Result {
        return try {
            Log.i(TAG, "WorkManager 开始执行周期性数据采集任务")

            // 执行数据采集逻辑
            val success = PeriodicDataCollector.executePeriodicCollection(applicationContext)

            if (success) {
                Log.i(TAG, "WorkManager 数据采集任务执行成功")
                Result.success()
            } else {
                Log.w(TAG, "WorkManager 数据采集任务执行失败")
                Result.retry() // 失败时重试
            }
        } catch (e: Exception) {
            Log.e(TAG, "WorkManager 数据采集任务异常: ${e.message}", e)
            Result.failure() // 异常时标记为失败
        }
    }
}