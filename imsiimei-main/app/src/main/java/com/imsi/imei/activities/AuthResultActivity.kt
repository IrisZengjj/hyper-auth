package com.imsi.imei.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.imsi.imei.R
import com.imsi.imei.utils.DataTransmitter

class AuthResultActivity : Activity() {
    
    companion object {
        private const val TAG = "AuthResultActivity"
        const val EXTRA_AUTH_STATUS = "auth_status"
        const val EXTRA_AUTH_MESSAGE = "auth_message"
        const val EXTRA_FINAL_CREDENTIAL_HASH = "final_credential_hash"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val authStatus = intent.extras?.getBoolean(EXTRA_AUTH_STATUS) ?: false
        val authMessage = intent.extras?.getString(EXTRA_AUTH_MESSAGE) ?: "认证结果未知"
        val finalCredentialHash = intent.extras?.getString(EXTRA_FINAL_CREDENTIAL_HASH) ?: ""
        
        if (authStatus) {
            // 认证成功，获取认证URL并显示成功页面
            setContentView(R.layout.activity_auth_success)
            setupSuccessPage(authMessage, finalCredentialHash)
        } else {
            // 认证失败，显示失败页面并模拟锁屏
            setContentView(R.layout.activity_auth_failure)
            setupFailurePage(authMessage)
        }
    }
    
    private fun setupSuccessPage(message: String, finalCredentialHash: String) {
        Log.d(TAG, "设置认证成功页面")
        
        val resultMessage = findViewById<TextView>(R.id.tv_auth_result_message)
        val continueButton = findViewById<Button>(R.id.btn_continue)
        
        resultMessage?.text = message
        
        // 如果有finalCredentialHash，则获取认证URL
        if (finalCredentialHash.isNotEmpty()) {
            getAuthUrlAndHandleResult(finalCredentialHash)
        }
        
        continueButton?.setOnClickListener {
            // 在安全系统中，简单地返回主页面
            finish() // 返回主页面
        }
    }
    
    private fun getAuthUrlAndHandleResult(finalCredentialHash: String) {
        Log.d(TAG, "开始获取认证URL，凭证哈希: $finalCredentialHash")
        
        DataTransmitter.getAuthUrl(this, finalCredentialHash) { success, message, authUrl ->
            runOnUiThread {
                if (success && !authUrl.isNullOrEmpty()) {
                    Log.d(TAG, "成功获取认证URL: $authUrl")
                    // 在安全系统中，如果无法打开浏览器，则显示提示信息
                    showAuthUrlResult(authUrl)
                } else {
                    Log.e(TAG, "获取认证URL失败: $message")
                    Toast.makeText(this, "获取认证URL失败: $message", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    
    private fun showAuthUrlResult(authUrl: String) {
        // 在安全系统中，显示认证URL供用户参考或复制
        Toast.makeText(this, "认证成功，认证URL已生成，请手动访问: $authUrl", Toast.LENGTH_LONG).show()
        Log.d(TAG, "认证URL: $authUrl")
    }
    
    private fun setupFailurePage(message: String) {
        Log.d(TAG, "设置认证失败页面")
        
        val resultMessage = findViewById<TextView>(R.id.tv_auth_result_message)
        val lockButton = findViewById<Button>(R.id.btn_lock_screen)
        
        resultMessage?.text = message
        
        lockButton?.setOnClickListener {
            simulateLockScreen()
        }
        
        // 模拟锁屏效果
        simulateLockScreen()
    }
    
    private fun simulateLockScreen() {
        try {
            // 设置窗口标志以模拟锁屏效果
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
            
            // 显示认证失败提醒
            Toast.makeText(this, "认证失败，设备已锁定，请联系管理员", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "模拟锁屏失败: ${e.message}")
            Toast.makeText(this, "模拟锁屏失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}