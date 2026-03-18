package com.keycloak.credentialserver.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class KeycloakUserUtil {

    private static final Logger logger = LoggerFactory.getLogger(KeycloakUserUtil.class);

    @Value("${keycloak.server-url}")
    private String serverUrl;
    @Value("${keycloak.realm}")
    private String realm;
    @Value("${keycloak.client-id}")
    private String clientId;
    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // 构造函数初始化 RestTemplate 和 ObjectMapper
    public KeycloakUserUtil() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // Getter方法
    public String getServerUrl() {
        return serverUrl;
    }

    public String getRealm() {
        return realm;
    }

    /**
     * 获取 Keycloak Access Token（修复 MultiValueMap 类型问题）
     */
    public String getAccessToken() {
        try {
            // 1. 正确构建 MultiValueMap 类型的参数（核心修复）
            String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", serverUrl, realm);
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "client_credentials");
            params.add("client_id", clientId);
            params.add("client_secret", clientSecret);

            // 2. 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            // 3. 发送请求并解析 Token
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);
            Map<String, Object> tokenMap = objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
            return tokenMap.get("access_token").toString();
        } catch (Exception e) {
            logger.error("获取 Keycloak Token 失败：" + e.getMessage(), e);
            throw new RuntimeException("获取 Keycloak Token 失败：" + e.getMessage(), e);
        }
    }

    /**
     * 生成基于用户名的密码后缀，确保每次创建相同用户名时使用相同的密码
     */
    public String generatePasswordSuffix(String username) {
        // 使用用户名的哈希值的一部分作为密码后缀，确保相同用户名总是生成相同密码
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(username.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (int i = 0; i < Math.min(8, hashBytes.length); i++) {
                String hex = Integer.toHexString(0xff & hashBytes[i]);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString().substring(0, 8);
        } catch (Exception e) {
            // 如果哈希失败，返回用户名的前几位字符
            return username.substring(0, Math.min(8, username.length()));
        }
    }

    // 用于生成递增用户名的静态计数器（使用数据库计数来确保唯一性）
    private static final AtomicInteger userCounter = new AtomicInteger(0);
    
    /**
     * 从数据库获取已存在的用户数量以初始化计数器
     */
    @PostConstruct
    public void initUserCounter() {
        // 通过搜索所有以"user"开头的用户来初始化计数器
        try {
            String accessToken = getAccessToken();
            String searchUrl = String.format("%s/admin/realms/%s/users?first=0&max=999999", serverUrl, realm);
            HttpHeaders searchHeaders = new HttpHeaders();
            searchHeaders.set("Authorization", "Bearer " + accessToken);
            HttpEntity<Void> searchRequest = new HttpEntity<>(searchHeaders);
            
            ResponseEntity<String> searchResponse = restTemplate.exchange(
                    searchUrl,
                    HttpMethod.GET,
                    searchRequest,
                    String.class
            );
            
            List<Map<String, Object>> userList = objectMapper.readValue(searchResponse.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            
            int maxUserNum = 0;
            for (Map<String, Object> user : userList) {
                String username = (String) user.get("username");
                if (username != null && username.startsWith("user")) {
                    try {
                        int userNum = Integer.parseInt(username.substring(4));
                        if (userNum > maxUserNum) {
                            maxUserNum = userNum;
                        }
                    } catch (NumberFormatException e) {
                        // 忽略非数字后缀的用户名
                    }
                }
            }
            
            userCounter.set(maxUserNum);
        } catch (Exception e) {
            logger.warn("初始化用户计数器失败：" + e.getMessage() + "，使用默认计数器值0");
            userCounter.set(0); // 默认从0开始
        }
    }
    
    /**
     * 创建 Keycloak 用户并返回用户名和用户ID
     */
    public Map<String, Object> createKeycloakUserWithIncrementalUsername(String finalCredentialHash) {
        try {
            String accessToken = getAccessToken();
            String userApiUrl = String.format("%s/admin/realms/%s/users", serverUrl, realm);

            // 第一步：检查用户是否已存在（基于email，即finalCredentialHash）
            String searchUrl = String.format("%s/admin/realms/%s/users?email=%s@auto-generated.com", serverUrl, realm, finalCredentialHash);
            HttpHeaders searchHeaders = new HttpHeaders();
            searchHeaders.set("Authorization", "Bearer " + accessToken);
            HttpEntity<Void> searchRequest = new HttpEntity<>(searchHeaders);
            
            ResponseEntity<String> searchResponse = restTemplate.exchange(
                    searchUrl,
                    HttpMethod.GET,
                    searchRequest,
                    String.class
            );
            List<Map<String, Object>> userList = objectMapper.readValue(searchResponse.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            if (!userList.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("userId", userList.get(0).get("id").toString());
                result.put("username", userList.get(0).get("username").toString());
                return result;
            }

            // 第二步：生成递增的用户名
            String username = "user" + (userCounter.incrementAndGet());

            // 构建用户数据 - final_credential_hash作为email，username作为用户名
            String password = "AutoPass" + generatePasswordSuffix(username);
            String userJson = String.format("{\n" +
                    "  \"username\": \"%s\",\n" +
                    "  \"email\": \"%s@auto-generated.com\",\n" +
                    "  \"enabled\": true,\n" +
                    "  \"emailVerified\": true,\n" +
                    "  \"credentials\": [\n" +
                    "    {\n" +
                    "      \"type\": \"password\",\n" +
                    "      \"value\": \"%s\",\n" +
                    "      \"temporary\": false\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}", username, finalCredentialHash, password);

            // 第三步：发送创建用户请求
            HttpHeaders createHeaders = new HttpHeaders();
            createHeaders.set("Authorization", "Bearer " + accessToken);
            createHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> createRequest = new HttpEntity<>(userJson, createHeaders);

            ResponseEntity<Void> createResponse = restTemplate.exchange(
                    userApiUrl,
                    HttpMethod.POST,
                    createRequest,
                    Void.class
            );
            String location = createResponse.getHeaders().getLocation().toString();
            String userId = location.substring(location.lastIndexOf("/") + 1);

            // 返回用户名和用户ID
            Map<String, Object> result = new HashMap<>();
            result.put("userId", userId);
            result.put("username", username);
            return result;

        } catch (Exception e) {
            throw new RuntimeException("创建 Keycloak 用户失败：" + e.getMessage(), e);
        }
    }

    /**
     * 创建 Keycloak 用户（纯 RestTemplate，无任何类型错误）
     */
    public String createKeycloakUser(String finalCredentialHash) {
        try {
            String accessToken = getAccessToken();
            String userApiUrl = String.format("%s/admin/realms/%s/users", serverUrl, realm);

            // 第一步：检查用户是否已存在（基于email，即finalCredentialHash）
            String searchUrl = String.format("%s/admin/realms/%s/users?email=%s@auto-generated.com", serverUrl, realm, finalCredentialHash);
            HttpHeaders searchHeaders = new HttpHeaders();
            searchHeaders.set("Authorization", "Bearer " + accessToken);
            HttpEntity<Void> searchRequest = new HttpEntity<>(searchHeaders);
            
            ResponseEntity<String> searchResponse = restTemplate.exchange(
                    searchUrl,
                    HttpMethod.GET,
                    searchRequest,
                    String.class
            );
            List<Map<String, Object>> userList = objectMapper.readValue(searchResponse.getBody(), new TypeReference<List<Map<String, Object>>>() {});
            if (!userList.isEmpty()) {
                return userList.get(0).get("id").toString();
            }

            // 生成递增的用户名
            String username = "user" + (userCounter.incrementAndGet());
            
            // 第二步：构建用户数据（避免 Map 类型错误）
            // 使用基于用户名的可预测密码，便于后续登录
            String password = "AutoPass" + generatePasswordSuffix(username);
            String userJson = String.format("{\n" +
                    "  \"username\": \"%s\",\n" +
                    "  \"email\": \"%s@auto-generated.com\",\n" +
                    "  \"enabled\": true,\n" +
                    "  \"emailVerified\": true,\n" +
                    "  \"credentials\": [\n" +
                    "    {\n" +
                    "      \"type\": \"password\",\n" +
                    "      \"value\": \"%s\",\n" +
                    "      \"temporary\": false\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}", username, finalCredentialHash, password);

            // 第三步：发送创建用户请求
            HttpHeaders createHeaders = new HttpHeaders();
            createHeaders.set("Authorization", "Bearer " + accessToken);
            createHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> createRequest = new HttpEntity<>(userJson, createHeaders);

            ResponseEntity<Void> createResponse = restTemplate.exchange(
                    userApiUrl,
                    HttpMethod.POST,
                    createRequest,
                    Void.class
            );
            String location = createResponse.getHeaders().getLocation().toString();
            return location.substring(location.lastIndexOf("/") + 1);

        } catch (Exception e) {
            throw new RuntimeException("创建 Keycloak 用户失败：" + e.getMessage(), e);
        }
    }

    /**
     * 通过用户名密码登录获取访问令牌（模拟用户登录）
     */
    public Map<String, Object> loginUser(String username, String password) {
        try {
            String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", serverUrl, realm);
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "password");
            params.add("client_id", clientId);
            params.add("username", username);
            params.add("password", password);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);
            return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("用户登录失败：" + e.getMessage(), e);
        }
    }

    /**
     * 使用刷新令牌刷新访问令牌
     */
    public Map<String, Object> refreshToken(String refreshToken) {
        try {
            String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", serverUrl, realm);
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "refresh_token");
            params.add("client_id", clientId);
            params.add("refresh_token", refreshToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);
            return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new RuntimeException("刷新令牌失败：" + e.getMessage(), e);
        }
    }

    /**
     * 登出用户（撤销令牌）
     */
    public void logoutUser(String refreshToken) {
        try {
            String logoutUrl = String.format("%s/realms/%s/protocol/openid-connect/logout", serverUrl, realm);
            
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("client_id", clientId);
            params.add("refresh_token", refreshToken);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

            restTemplate.postForEntity(logoutUrl, request, String.class);
        } catch (Exception e) {
            throw new RuntimeException("用户登出失败：" + e.getMessage(), e);
        }
    }
}