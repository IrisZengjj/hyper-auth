package com.keycloak.credentialserver.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.keycloak.credentialserver.entity.IdentityProof;
import com.keycloak.credentialserver.service.IdentityProofService;
import com.keycloak.credentialserver.service.DataCollectionSyncService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/api")
public class CredentialController {

    private static final Logger logger = LoggerFactory.getLogger(CredentialController.class);

    @Value("${keycloak.server-url}")
    private String keycloakBaseUrl;

    @Value("${app.redirect-url}")
    private String appRedirectUrl;

    @Autowired
    private IdentityProofService identityProofService;
    
    @Autowired
    private DataCollectionSyncService dataCollectionSyncService;

    // ========== 验证接口 ==========
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyCredential(@RequestBody Map<String, String> request) {
        logger.info("收到凭证比对请求：{}", request);
        Map<String, Object> response = new HashMap<>();

        try {
            String finalCredentialHash = request.get("finalCredentialHash");
            IdentityProof validProof = identityProofService.verifyFinalCredentialHash(finalCredentialHash);

            if (validProof != null) {
                response.put("success", true);
                response.put("userId", validProof.getUserId());
                response.put("message", "凭证比对成功");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "凭证无效，请使用账号密码登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
                // return ResponseEntity.status(401).body(response);
            }

        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "服务异常：" + e.getMessage());
            logger.error("比对过程异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            // return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadCredential(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        // 检查是否为加密数据格式 (由过滤器处理)
        if (request.containsKey("encrypted_data") && request.containsKey("encrypted_key")) {
            // 这种情况不应该发生，因为过滤器应该已经处理了加密数据
            response.put("success", false);
            response.put("message", "加密数据应由过滤器处理，不应到达此方法");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        
        // 检查是否为实时认证请求
        String collectionType = asString(request.get("collection_type"));
        // 处理明文数据
        try {
            String phoneNumberHash = asString(request.get("phoneNumberHash"));
            String deviceFingerprintHash = asString(request.get("deviceFingerprintHash"));

            Object payloadObj = request.get("payload");
            if ((phoneNumberHash == null || deviceFingerprintHash == null) && payloadObj instanceof String) {
                Map<String, Object> decoded = decodeBase64Json((String) payloadObj);
                if (decoded != null) {
                    if (phoneNumberHash == null) phoneNumberHash = asString(decoded.get("phoneNumberHash"));
                    if (deviceFingerprintHash == null) deviceFingerprintHash = asString(decoded.get("deviceFingerprintHash"));
                }
            }

            if (phoneNumberHash == null || deviceFingerprintHash == null) {
                response.put("success", false);
                response.put("message", "缺少必要字段：phoneNumberHash 或 deviceFingerprintHash");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }

            String finalCredentialHash = sha256(phoneNumberHash + ":" + deviceFingerprintHash);

            IdentityProof proof = new IdentityProof();
            proof.setPhoneNumberHash(phoneNumberHash);
            proof.setDeviceFingerprintHash(deviceFingerprintHash);
            proof.setFinalCredentialHash(finalCredentialHash);

            // 检查是否为实时认证请求
            if ("实时认证".equals(collectionType)) {
                // 实时认证：不存储到数据库，直接验证现有凭证
                IdentityProof verifiedProof = identityProofService.verifyFinalCredentialHash(finalCredentialHash);
                if (verifiedProof != null) {
                    // 构建Keycloak认证URL
                    String authUrl = String.format(
                        "%s/realms/myrealm/protocol/openid-connect/auth?" +
                        "response_type=code&" +
                        "client_id=credential-server-admin&" +
                        "redirect_uri=%s&" +
                        "state=%s&" +
                        "login_hint=%s",
                        keycloakBaseUrl,
                        appRedirectUrl,
                        finalCredentialHash.substring(0, 8), // 使用哈希的前8位作为状态参数
                        "user_" + finalCredentialHash.substring(0, 6) // 使用哈希前6位作为用户名提示
                    );
                    
                    response.put("success", true);
                    response.put("finalCredentialHash", finalCredentialHash);
                    response.put("userId", verifiedProof.getUserId());
                    response.put("authUrl", authUrl);
                    response.put("message", "实时认证成功");
                } else {
                    response.put("success", false);
                    response.put("message", "实时认证失败");
                }
                return ResponseEntity.ok(response);
            } else {
                // 常规数据上传：存储到数据库
                boolean saved = identityProofService.saveCredentialWithAutoUser(proof);
                if (saved) {
                    response.put("success", true);
                    response.put("finalCredentialHash", finalCredentialHash);
                    response.put("message", "上传处理成功，已融合并入库");
                } else {
                    response.put("success", false);
                    response.put("message", "入库失败");
                }
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            logger.error("上传处理异常", e);
            response.put("success", false);
            response.put("message", "服务异常：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ========== 修改：使用不同端点名称以避免冲突 ==========
    @PostMapping("/collect-data")
    public ResponseEntity<Map<String, Object>> collectData(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 检查是否包含必要的采集数据
            if (!request.containsKey("hardware_info") || !request.containsKey("software_info") || !request.containsKey("sim_info")) {
                response.put("success", false);
                response.put("message", "缺少必要的采集数据：hardware_info, software_info, sim_info");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
            }
            
            // 调用数据采集同步服务
            String finalCredentialHash = dataCollectionSyncService.collectAndGenerateCredential(request);
            
            // 如果是实时认证类型，返回认证成功和Keycloak登录URL
            String collectionType = asString(request.get("collection_type"));
            if ("实时认证".equals(collectionType)) {
                // 构建Keycloak认证URL
                String authUrl = String.format(
                    "%s/realms/myrealm/protocol/openid-connect/auth?" +
                    "response_type=code&" +
                    "client_id=credential-server-admin&" +
                    "redirect_uri=%s&" +
                    "state=%s&" +
                    "login_hint=%s",
                    keycloakBaseUrl,
                    appRedirectUrl,
                    finalCredentialHash.substring(0, 8), // 使用哈希的前8位作为状态参数
                    "user_" + finalCredentialHash.substring(0, 6) // 使用哈希前6位作为用户名提示
                );
                
                response.put("success", true);
                response.put("message", "认证数据采集成功");
                response.put("finalCredentialHash", finalCredentialHash);
                response.put("authUrl", authUrl);
            } else {
                response.put("success", true);
                response.put("message", "常规数据采集成功");
                response.put("finalCredentialHash", finalCredentialHash);
            }
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("数据采集处理异常", e);
            response.put("success", false);
            response.put("message", "数据采集处理异常：" + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private static String asString(Object obj) {
        return obj == null ? null : String.valueOf(obj);
    }

    private static Map<String, Object> decodeBase64Json(String base64) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            String json = new String(bytes, StandardCharsets.UTF_8);
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private static String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ========== 新数据入库接口 ==========
    @PostMapping("/save-auto-user")
    public ResponseEntity<Map<String, Object>> saveAutoUser(@RequestBody IdentityProof identityProof) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean success = identityProofService.saveCredentialWithAutoUser(identityProof);
            if (success) {
                response.put("success", true);
                response.put("message", "凭证保存成功，已自动创建Keycloak用户");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "凭证保存失败");
                return ResponseEntity.status(500).body(response);
            }
        } catch (Exception e) {
            logger.error("保存凭证异常", e);
            response.put("success", false);
            response.put("message", "服务异常：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    // ========== 补全所有空user_id的接口 ==========
    @PostMapping("/fill-empty-userid")
    public ResponseEntity<Map<String, Object>> fillEmptyUserId() {
        Map<String, Object> response = new HashMap<>();
        try {
            identityProofService.fillEmptyUserId();

            response.put("success", true);
            response.put("message", "空user_id补全完成");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("补全空user_id异常", e);
            response.put("success", false);
            response.put("message", "补全失败：" + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
    
    // ========== 实时认证并返回认证URL接口 ==========
    @PostMapping("/auth-url")
    public ResponseEntity<Map<String, Object>> getAuthUrl(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String finalCredentialHash = request.get("finalCredentialHash");
            IdentityProof validProof = identityProofService.verifyFinalCredentialHash(finalCredentialHash);
            
            if (validProof != null) {
                // 构建Keycloak认证URL
                String authUrl = String.format(
                    "%s/realms/myrealm/protocol/openid-connect/auth?" +
                    "response_type=code&" +
                    "client_id=credential-server-admin&" +
                    "redirect_uri=%s&" +
                    "state=%s&" +
                    "login_hint=%s",
                    keycloakBaseUrl,
                    appRedirectUrl,
                    finalCredentialHash.substring(0, 8), // 使用哈希的前8位作为状态参数
                    "user_" + finalCredentialHash.substring(0, 6) // 使用哈希前6位作为用户名提示
                );
                
                response.put("success", true);
                response.put("userId", validProof.getUserId());
                response.put("authUrl", authUrl);
                response.put("message", "认证URL生成成功");
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "凭证无效，请使用账号密码登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "服务异常：" + e.getMessage());
            logger.error("认证URL生成过程异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ========== 实时认证并直接登录Keycloak用户接口 ==========
    @PostMapping("/direct-auth")
    public ResponseEntity<Map<String, Object>> directAuth(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String finalCredentialHash = request.get("finalCredentialHash");
            
            // 验证凭证并直接登录Keycloak用户
            Map<String, Object> loginResult = identityProofService.verifyAndLoginUser(finalCredentialHash);
            
            if (loginResult != null) {
                response.put("success", true);
                response.put("message", "认证成功并已登录Keycloak");
                response.putAll(loginResult); // 包含访问令牌等信息
                
                logger.info("终端认证成功并已登录Keycloak，凭证哈希：{}", finalCredentialHash);
                return ResponseEntity.ok(response);
            } else {
                response.put("success", false);
                response.put("message", "认证失败，无法登录");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "服务异常：" + e.getMessage());
            logger.error("直接认证登录过程异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
}