/** 调用实例
 */
package com.imsi.imei.activities

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import com.imsi.imei.R
import ga.mdm.DeviceInfoManager

class HardwareInfoActivity : Activity() {

    private lateinit var tvHardwareInfo: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hardware_info)

        // 初始化 TextView
        tvHardwareInfo = findViewById(R.id.tv_hardware_info)

        // 显示设备信息
        displayDeviceInfo()
    }

    private fun displayDeviceInfo() {
        try {
            val deviceInfoManager: DeviceInfoManager = DeviceInfoManager.getInstance()
            val deviceInfo = deviceInfoManager.getDeviceInfo()

            val hardwareInfo = StringBuilder().apply {
                append("设备名称: ${getSafeValue(deviceInfo?.get(4))}\n")
                append("设备型号: ${getSafeValue(deviceInfo?.get(5))}\n")
                append("硬件序列号 (SN): ${getSafeValue(deviceInfo?.get(9))}\n")
                append("IMEI (主卡): ${getSafeValue(deviceInfo?.get(0))}\n")
                append("IMEI (副卡): ${getSafeValue(deviceInfo?.get(1))}\n")
                append("CPU架构: ${getSafeValue(deviceInfo?.get(15))}\n")
                append("内存大小: ${getSafeValue(deviceInfo?.get(2))}\n")
                append("基带版本: ${getSafeValue(deviceInfo?.get(7))}\n")
            }.toString()

            tvHardwareInfo.text = hardwareInfo

        } catch (e: Exception) {
            Toast.makeText(this, "获取硬件属性失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getSafeValue(value: Any?): String {
        return value?.toString() ?: "未收集到信息"
    }
}
