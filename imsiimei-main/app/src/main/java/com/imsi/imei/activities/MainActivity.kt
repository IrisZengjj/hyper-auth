package com.imsi.imei.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.imsi.imei.R
import com.imsi.imei.utils.*
import ga.mdm.DeviceInfoManager
import org.json.JSONObject
import java.io.File

class MainActivity : Activity(), View.OnClickListener {

    // UI 元素
    private var btnHardwareInfo: Button? = null
    private var btnSoftwareInfo: Button? = null
    private var btnSimInfo: Button? = null
    private var tvPhoneNumber: TextView? = null
    private var btnManualCollect: Button? = null
    private var btnAuthCollect: Button? = null
    private var tvCollectionStatus: TextView? = null

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化UI元素
        initViews()

        // 启动周期性数据采集
        startPeriodicCollection()

        // 在应用启动时执行首次数据采集（保留原有功能）
        performInitialDataCollection()

        // 展示手机号信息
        displayTelephoneInfo()

        // 更新采集状态显示
        updateCollectionStatus()
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.btn_hardware_info -> {
                // 跳转到设备属性页面
                val intent = Intent(this, HardwareInfoActivity::class.java)
                startActivity(intent)
            }
            R.id.btn_software_info -> {
                // 跳转到软件信息页面
                val intent = Intent(this, SoftwareInfoActivity::class.java)
                startActivity(intent)
            }
            R.id.btn_sim_info -> {
                // 跳转到SIM卡信息页面
                val intent = Intent(this, SimInfoActivity::class.java)
                startActivity(intent)
            }
            R.id.btn_manual_collect -> {
                // 跳转到注册页面
                val intent = Intent(this, RegisterActivity::class.java)
                startActivity(intent)
            }
            R.id.btn_auth_collect -> {
                // 跳转到Keycloak认证页面
                val intent = Intent(this, KeycloakAuthActivity::class.java)
                startActivity(intent)
            }
        }
    }

    /**
     * 初始化UI元素
     */
    private fun initViews() {
        btnHardwareInfo = findViewById(R.id.btn_hardware_info)
        btnSoftwareInfo = findViewById(R.id.btn_software_info)
        btnSimInfo = findViewById(R.id.btn_sim_info)
        tvPhoneNumber = findViewById(R.id.tvPhoneNumber)
        btnManualCollect = findViewById(R.id.btn_manual_collect)
        btnAuthCollect = findViewById(R.id.btn_auth_collect)
        tvCollectionStatus = findViewById(R.id.tv_collection_status)

        // 设置点击事件监听器
        btnHardwareInfo?.setOnClickListener(this)
        btnSoftwareInfo?.setOnClickListener(this)
        btnSimInfo?.setOnClickListener(this)
        btnManualCollect?.setOnClickListener(this)
        btnAuthCollect?.setOnClickListener(this)
    }

    /**
     * 启动周期性数据采集
     */
    private fun startPeriodicCollection() {
        PeriodicDataCollector.startPeriodicCollection(this)
        Log.i(TAG, "周期性数据采集已启动")
    }

    /**
     * 执行初始数据采集（保留原有功能）
     */
    private fun performInitialDataCollection() {
        // 在后台线程执行初始采集
        Thread {
            val success = PeriodicDataCollector.executePeriodicCollection(this@MainActivity)
            runOnUiThread {
                if (success) {
                    Log.i(TAG, "初始数据采集成功")
                } else {
                    Log.e(TAG, "初始数据采集失败")
                }
                updateCollectionStatus()
            }
        }.start()
    }

    /**
     * 认证时触发数据采集
     */
    private fun performAuthDataCollection() {
        btnAuthCollect?.isEnabled = false
        Toast.makeText(this, "开始认证数据采集...", Toast.LENGTH_SHORT).show()

        // 执行认证数据采集（不保存到本地，直接上传）
        AuthDataCollector.collectAndUploadForAuth(this) { success, message, finalCredentialHash ->
            runOnUiThread {
                btnAuthCollect?.isEnabled = true
                if (success) {
                    Toast.makeText(this@MainActivity, "认证数据采集成功", Toast.LENGTH_SHORT).show()
                    
                    // 启动认证结果页面，传递成功状态和凭证哈希
                    val intent = Intent(this@MainActivity, AuthResultActivity::class.java)
                    intent.putExtra(AuthResultActivity.EXTRA_AUTH_STATUS, true)
                    intent.putExtra(AuthResultActivity.EXTRA_AUTH_MESSAGE, message)
                    if (finalCredentialHash != null) {
                        intent.putExtra(AuthResultActivity.EXTRA_FINAL_CREDENTIAL_HASH, finalCredentialHash)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "认证数据采集失败", Toast.LENGTH_SHORT).show()
                    
                    // 启动认证结果页面，传递失败状态
                    val intent = Intent(this@MainActivity, AuthResultActivity::class.java)
                    intent.putExtra(AuthResultActivity.EXTRA_AUTH_STATUS, false)
                    intent.putExtra(AuthResultActivity.EXTRA_AUTH_MESSAGE, message)
                    startActivity(intent)
                }
            }
        }
    }

    /**
     * 展示手机号信息
     */
    private fun displayTelephoneInfo() {
        try {
            val deviceInfoManager = DeviceInfoManager.getInstance()
            val deviceInfo = deviceInfoManager.getDeviceInfo()

            val phoneNumber1 = if (deviceInfo != null && deviceInfo.size > 17) deviceInfo[17]?.toString() ?: "N/A" else "N/A"
            val phoneNumber2 = if (deviceInfo != null && deviceInfo.size > 18) deviceInfo[18]?.toString() ?: "N/A" else "N/A"
            val phoneNumberText = "手机号\n卡1：$phoneNumber1\n卡2：$phoneNumber2"

            tvPhoneNumber?.text = phoneNumberText
        } catch (e: Exception) {
            Log.e(TAG, "无法获取或展示设备信息", e)
            Toast.makeText(this, "无法获取设备信息", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 更新采集状态显示
     */
    private fun updateCollectionStatus() {
        val fileCount = FileManager.getDataFileCount(this)
        val isFirst = FileManager.isFirstCollection(this)

        val statusText = if (isFirst) {
            "采集状态: 首次采集\n文件数量: $fileCount"
        } else {
            "采集状态: 已采集\n文件数量: $fileCount"
        }

        tvCollectionStatus?.text = statusText
    }

    override fun onDestroy() {
        super.onDestroy()
        // 停止周期性数据采集
        PeriodicDataCollector.stopPeriodicCollection(this)
    }
}