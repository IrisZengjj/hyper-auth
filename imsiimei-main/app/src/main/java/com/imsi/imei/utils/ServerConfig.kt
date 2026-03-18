package com.imsi.imei.utils

object ServerConfig {
    private const val IP_ADDRESS = "10.201.212.129"

    const val REGISTER_URL = "http://$IP_ADDRESS:8080/admin/realms/myrealm/users"
    const val API_URL = "http://$IP_ADDRESS:8081/api"
    const val TOKEN_URL = "http://$IP_ADDRESS:8080/realms/myrealm/protocol/openid-connect/token"
    const val AUTHENTICATION_URL = "http://$IP_ADDRESS:8080/realms/myrealm/protocol/openid-connect/token"
}
