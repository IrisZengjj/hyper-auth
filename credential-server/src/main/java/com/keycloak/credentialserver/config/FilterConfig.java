package com.keycloak.credentialserver.config;

import com.keycloak.credentialserver.filter.EncryptedUploadFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Autowired
    private EncryptedUploadFilter encryptedUploadFilter;

    @Bean
    public FilterRegistrationBean<EncryptedUploadFilter> encryptedUploadFilterRegistration() {
        FilterRegistrationBean<EncryptedUploadFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(encryptedUploadFilter);
        registration.addUrlPatterns("/api/upload"); // 只拦截 /api/upload 路径
        registration.setName("encryptedUploadFilter");
        registration.setOrder(1); // 设置优先级
        return registration;
    }
}