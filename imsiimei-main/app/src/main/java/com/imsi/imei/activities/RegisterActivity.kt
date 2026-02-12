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
        val url = "http://10.255.29.129:8080/admin/realms/myrealm/users"

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

        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJnMHJzV25NOHYxdFNybGViRXNhMDBFRF9NbGx3MlMwMVk4MElneXk2enBvIn0.eyJleHAiOjE3Njk4MzIxNTAsImlhdCI6MTc2OTgzMTg1MCwianRpIjoib25ydHJvOjI0MzgxZWRjLWEyMjUtNGVlMy1jYjRiLWE0OTY0YWIxYjhkYiIsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MC9yZWFsbXMvbXlyZWFsbSIsImF1ZCI6WyJyZWFsbS1tYW5hZ2VtZW50IiwiYWNjb3VudCJdLCJzdWIiOiI0ZjVmZTgwNy00NjA5LTRkY2QtYjUxZi04ODZlMDcyNGI2ZjQiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJjcmVkZW50aWFsLXNlcnZlci1hZG1pbiIsInNpZCI6ImRhNzEyYTM4LWIwYTAtYzg4Yy00MjZkLTEyMjNhM2M3ZGY4ZCIsImFjciI6IjEiLCJhbGxvd2VkLW9yaWdpbnMiOlsiLyoiLCJodHRwOi8vbG9jYWxob3N0OjMwMDAiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImRlZmF1bHQtcm9sZXMtbXlyZWFsbSIsIm9mZmxpbmVfYWNjZXNzIiwiYWRtaW4iLCJ1bWFfYXV0aG9yaXphdGlvbiJdfSwicmVzb3VyY2VfYWNjZXNzIjp7InJlYWxtLW1hbmFnZW1lbnQiOnsicm9sZXMiOlsidmlldy1pZGVudGl0eS1wcm92aWRlcnMiLCJ2aWV3LXJlYWxtIiwibWFuYWdlLWlkZW50aXR5LXByb3ZpZGVycyIsImltcGVyc29uYXRpb24iLCJyZWFsbS1hZG1pbiIsImNyZWF0ZS1jbGllbnQiLCJtYW5hZ2UtdXNlcnMiLCJxdWVyeS1yZWFsbXMiLCJ2aWV3LWF1dGhvcml6YXRpb24iLCJxdWVyeS1jbGllbnRzIiwicXVlcnktdXNlcnMiLCJtYW5hZ2UtZXZlbnRzIiwibWFuYWdlLXJlYWxtIiwidmlldy1ldmVudHMiLCJ2aWV3LXVzZXJzIiwidmlldy1jbGllbnRzIiwibWFuYWdlLWF1dGhvcml6YXRpb24iLCJtYW5hZ2UtY2xpZW50cyIsInF1ZXJ5LWdyb3VwcyJdfSwiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwic2NvcGUiOiJlbWFpbCBwcm9maWxlIiwiZW1haWxfdmVyaWZpZWQiOmZhbHNlLCJuYW1lIjoiYSBiIiwicHJlZmVycmVkX3VzZXJuYW1lIjoidGVzdHVzZXIiLCJnaXZlbl9uYW1lIjoiYSIsImZhbWlseV9uYW1lIjoiYiIsImVtYWlsIjoiYWRtaW5AdGVzdC5jb20ifQ.YFrG68mTIo-WmdHR-ePUuW6ZH-GveS-LiIwUmGhYlK2Ht-JHd_RzsQPXlWBHaOnPNiTEBZAnr5TddXUu8EOehNII_559Km941lW8b4ElOKsbUS0Jb7IxX5mck2jn-cL3Ck2Ma69LEKtoP9j6XD6_zQdkZf3FIoo0GQructi5wUpG_jJsoHTKf39RlhVp4e6aFiOAHnjOByPVi4I_ETIKWBZGPV7SFpY8-ldwVVoHNnTwPL0P-PP6i0QHi0YdyAmdVimZKMWBieDfYTp3KCAYeHTeX43KSoWqc8uyaDxS8y3dSnOH4LfgATI4Yw4H1JssAJzT08vPEqoN9RKltKf8AQ")
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
