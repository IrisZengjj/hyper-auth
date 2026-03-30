package com.imsi.imei.utils

import android.content.Context

object ServerConfig {
    var IP_ADDRESS = ""

    fun init(context: Context) {
        if (IP_ADDRESS.isEmpty()) {
            val prefs = context.getSharedPreferences("AppConfig", Context.MODE_PRIVATE)
            IP_ADDRESS = prefs.getString("server_ip", "") ?: ""
        }
    }

    val REGISTER_URL: String
        get() = "http://$IP_ADDRESS:8080/admin/realms/myrealm/users"
    val API_URL: String
        get() = "http://$IP_ADDRESS:8081/api"
    val TOKEN_URL: String
        get() = "http://$IP_ADDRESS:8080/realms/myrealm/protocol/openid-connect/token"
    val AUTHENTICATION_URL: String
        get() = "http://$IP_ADDRESS:8080/realms/myrealm/protocol/openid-connect/token"
}
