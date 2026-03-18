/** 调用实例
 */
package com.imsi.imei.activities

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import com.imsi.imei.R
import ga.mdm.DeviceInfoManager

class SoftwareInfoActivity : Activity() {

    private lateinit var tvSoftwareInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_software_info)

        // 初始化 TextView
        tvSoftwareInfo = findViewById(R.id.tv_software_info)

        // 显示设备信息
        displayDeviceInfo()
    }

    private fun displayDeviceInfo() {
        try {
            val deviceInfoManager: DeviceInfoManager = DeviceInfoManager.getInstance()
            val deviceInfo = deviceInfoManager.getDeviceInfo()

            val softwareInfo = StringBuilder().apply {
                append("操作系统名称: ${getSafeValue(deviceInfo?.get(6))}\n") // 修复转义
                append("操作系统版本: ${getSafeValue(deviceInfo?.get(8))}\n")
                append("Android_ID: ${getSafeValue(deviceInfo?.get(16))}\n")
                append("内核版本: ${getSafeValue(deviceInfo?.get(7))}")
            }.toString()

            tvSoftwareInfo.text = softwareInfo

        } catch (e: Exception) {
            Toast.makeText(this, "获取软件属性失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getSafeValue(value: Any?): String {
        return value?.toString() ?: "未收集到信息"
    }
}

