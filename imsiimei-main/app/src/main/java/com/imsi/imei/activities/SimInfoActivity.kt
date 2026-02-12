package com.imsi.imei.activities

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import com.imsi.imei.R
import ga.mdm.DeviceInfoManager

class SimInfoActivity : Activity() {

    private lateinit var tvIMSI: TextView
    private lateinit var tvPhoneNumber: TextView
    private lateinit var tvICCID: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sim_info)

        // 初始化 TextView
        tvIMSI = findViewById(R.id.tv_imsi)
        tvPhoneNumber = findViewById(R.id.tv_phone_number)
        tvICCID = findViewById(R.id.tv_iccid)

        // 直接获取 SIM 卡信息
        fetchSimInfo()
    }

    private fun fetchSimInfo() {
        try {
            // 按照文档要求创建实例
            val deviceInfoManager: DeviceInfoManager = DeviceInfoManager.getInstance()
            val simInfo = deviceInfoManager.getDeviceInfo()

            // 获取 SIM 卡信息并显示
            val phoneNumber1 = simInfo?.get(17) ?: "N/A"
            val phoneNumber2 = simInfo?.get(18) ?: "N/A"
            val imsi1 = simInfo?.get(13) ?: "N/A"
            val imsi2 = simInfo?.get(14) ?: "N/A"
            val iccid1 = simInfo?.get(11) ?: "N/A"
            val iccid2 = simInfo?.get(12) ?: "N/A"

            // 更新 UI
            tvPhoneNumber.text = "卡1手机号: $phoneNumber1\n卡2手机号: $phoneNumber2"
            tvIMSI.text = "卡1 IMSI: $imsi1\n卡2 IMSI: $imsi2"
            tvICCID.text = "卡1 ICCID: $iccid1\n卡2 ICCID: $iccid2"
        } catch (e: Exception) {
            Toast.makeText(this, "获取SIM卡信息失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

