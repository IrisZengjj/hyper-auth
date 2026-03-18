package com.keycloak.credentialserver; // 包名与路径完全一致

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;

/**
 * 项目启动类
 */
@SpringBootApplication
// 扫描Mapper接口（包名与mapper实际路径一致）
@MapperScan({
        "com.keycloak.credentialserver.mapper",
        "com.keycloak.credentialserver.collection.mapper"
})
public class CredentialServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(CredentialServerApplication.class, args);
    }
}

