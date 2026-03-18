package com.imsi.imei.activities

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.imsi.imei.R
import com.imsi.imei.utils.PeriodicDataCollector
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import com.imsi.imei.utils.ServerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import org.json.JSONObject
import android.util.Log

class RegisterActivity : AppCompatActivity() {

    private var btnRegister: Button? = null
    private var etUsername: EditText? = null
    private var etPassword: EditText? = null
    private var etEmail: EditText? = null
    private var etFirstName: EditText? = null
    private var etLastName: EditText? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        btnRegister = findViewById(R.id.btn_register)
        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        etEmail = findViewById(R.id.et_email)
        etFirstName = findViewById(R.id.et_first_name)
        etLastName = findViewById(R.id.et_last_name)

        btnRegister?.setOnClickListener {
            performManualDataCollection()
        }
    }

    private fun performManualDataCollection() {
        btnRegister?.isEnabled = false
        Toast.makeText(this, "开始手动数据采集...", Toast.LENGTH_SHORT).show()

        // 在后台线程执行手动采集
        Thread {
            val success = PeriodicDataCollector.executePeriodicCollection(this@RegisterActivity)
            runOnUiThread {
                if (success) {
                    Toast.makeText(this@RegisterActivity, "手动数据采集成功", Toast.LENGTH_SHORT).show()
                    registerUserInKeycloak()
                } else {
                    btnRegister?.isEnabled = true
                    Toast.makeText(this@RegisterActivity, "手动数据采集失败", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun registerUserInKeycloak() {
        val username = etUsername?.text.toString()
        val password = etPassword?.text.toString()
        val email = etEmail?.text.toString()
        val firstName = etFirstName?.text.toString()
        val lastName = etLastName?.text.toString()

        if (username.isEmpty() || password.isEmpty() || email.isEmpty() || firstName.isEmpty() || lastName.isEmpty()) {
            Toast.makeText(this, "请填写所有字段", Toast.LENGTH_SHORT).show()
            btnRegister?.isEnabled = true
            return
        }

        val client = OkHttpClient()
        val url = ServerConfig.REGISTER_URL

        val credential = mapOf("type" to "password", "value" to password, "temporary" to false)
        val user = mapOf(
            "username" to username,
            "email" to email,
            "firstName" to firstName,
            "lastName" to lastName,
            "enabled" to true,
            "credentials" to listOf(credential)
        )

        val json = Gson().toJson(user)
        val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())

        lifecycleScope.launch {
            val token = getAuthToken()
            if (token == null) {
                Toast.makeText(this@RegisterActivity, "获取令牌失败", Toast.LENGTH_LONG).show()
                btnRegister?.isEnabled = true
                return@launch
            }

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", "Bearer $token")
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(this@RegisterActivity, "注册失败: ${e.message}", Toast.LENGTH_LONG).show()
                        btnRegister?.isEnabled = true
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(this@RegisterActivity, "注册成功", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@RegisterActivity, "注册失败: ${response.message}", Toast.LENGTH_LONG).show()
                        }
                        btnRegister?.isEnabled = true
                    }
                }
            })
        }
    }

    private suspend fun getAuthToken(): String? {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val url = ServerConfig.AUTHENTICATION_URL
            val formBody = FormBody.Builder()
                .add("grant_type", "password")
                .add("client_id", "credential-server-admin")
                .add("client_secret", "NMvvGxpEKfHEJEpVJzSizYuP144ahcL2")
                .add("username", "testuser")
                .add("password", "123456.")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            try {
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    val jsonObject = JSONObject(responseData)
                    jsonObject.getString("access_token")
                } else {
                    Log.e("AuthError", "Failed to get token: ${response.code} ${response.message}")
                    val responseBody = response.body?.string()
                    if (responseBody != null) {
                        Log.e("AuthError", "Response body: $responseBody")
                    }
                    null
                }
            } catch (e: IOException) {
                Log.e("AuthError", "Network error while getting token", e)
                null
            }
        }
    }
}
