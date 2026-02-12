package com.keycloak.credentialserver.controller;

import com.keycloak.credentialserver.service.DataCollectionSyncService;
import com.keycloak.credentialserver.service.EncryptedDataProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class CollectController {

    @Autowired
    private DataCollectionSyncService dataCollectionSyncService;

    @Autowired
    private EncryptedDataProcessingService encryptedDataProcessingService;

    /**
     * 原有功能：接收明文数据，调用同步服务进行哈希融合并存库
     * 假设原有端点为 /collect (或根据上下文推断为接收明文JSON的入口)
     */
    @PostMapping("/collect")
    public ResponseEntity<Map<String, Object>> collect(@RequestBody Map<String, Object> requestData) {
        try {
            // 调用原有核心业务逻辑：插入data_collection -> 生成Hash -> 插入multimodal_auth_db
            String finalCredentialHash = dataCollectionSyncService.collectAndGenerateCredential(requestData);
            
            return ResponseEntity.ok(Map.of(
                "success", true, 
                "message", "数据采集与凭证生成成功",
                "credential_hash", finalCredentialHash
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false, 
                "message", "处理失败: " + e.getMessage()
            ));
        }
    }

    /**
     * 新增功能：接收加密数据，解密并映射后调用同步服务
     */
    @PostMapping("/collect-encrypted")
    public ResponseEntity<Map<String, Object>> collectEncrypted(@RequestBody Map<String, String> request) {
        try {
            Map<String, Object> result = encryptedDataProcessingService.processEncryptedData(request);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
    /**
     * 判断是否为认证数据采集类型
     * @param collectionType 采集类型
     * @return 是否为认证数据采集
     */
    private boolean isAuthenticationCollection(String collectionType) {
        // 当前支持的认证类型：实时认证
        return "实时认证".equals(collectionType);
    }
}
