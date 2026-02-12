package com.imsi.imei.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 数据采集广播接收器
 * 接收定时任务触发，执行周期性数据采集
 */
class DataCollectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i("DataCollectionReceiver", "接收到周期性数据采集广播")
        
        // 在后台线程执行数据采集
        Thread {
            PeriodicDataCollector.executePeriodicCollection(context)
        }.start()
    }
}