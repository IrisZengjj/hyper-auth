package com.imsi.imei.activities

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.imsi.imei.R
import com.imsi.imei.utils.AuthDataCollector
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import com.imsi.imei.utils.ServerConfig

class KeycloakAuthActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnAuthenticate: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_keycloak_auth)

        ServerConfig.init(this)

        // 初始化UI元素
        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        btnAuthenticate = findViewById(R.id.btn_authenticate)

        btnAuthenticate.setOnClickListener {
            performAuthentication()
        }
    }

    private fun performAuthentication() {
        btnAuthenticate.isEnabled = false
        Toast.makeText(this, "开始认证数据采集...", Toast.LENGTH_SHORT).show()

        AuthDataCollector.collectAndUploadForAuth(this) { success, message, _ ->
            runOnUiThread {
                if (success) {
                    Toast.makeText(this, "认证数据采集成功", Toast.LENGTH_SHORT).show()
                    authenticateWithKeycloak()
                } else {
                    btnAuthenticate.isEnabled = true
                    Toast.makeText(this, "认证数据采集失败: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun authenticateWithKeycloak() {
        val username = etUsername.text.toString()
        val password = etPassword.text.toString()

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show()
            btnAuthenticate.isEnabled = true
            return
        }

        val client = OkHttpClient()
        val url = ServerConfig.TOKEN_URL
        val mediaType = "application/x-www-form-urlencoded".toMediaType()
        val requestBody = "grant_type=password&client_id=credential-server-admin&client_secret=NMvvGxpEKfHEJEpVJzSizYuP144ahcL2&username=$username&password=$password"
            .toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    btnAuthenticate.isEnabled = true
                    Toast.makeText(this@KeycloakAuthActivity, "设备认证请求失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    btnAuthenticate.isEnabled = true
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            val json = JSONObject(responseBody)
                            if (json.has("access_token")) {
                                Toast.makeText(this@KeycloakAuthActivity, "设备认证成功", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(this@KeycloakAuthActivity, "设备认证失败: 未返回access_token", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@KeycloakAuthActivity, "设备认证失败: 响应体为空", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@KeycloakAuthActivity, "设备认证失败: ${response.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }
}
