package com.keycloak.credentialserver.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keycloak.credentialserver.entity.IdentityProof;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class EncryptedDataProcessingService {

    @Autowired
    private KeyManagementService keyManagementService;

    @Autowired
    private DataCollectionSyncService dataCollectionSyncService;

    @Autowired
    private IdentityProofService identityProofService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> processEncryptedData(Map<String, String> request) throws Exception {
        // 1. 获取加密数据
        String encryptedData = request.get("encrypted_data");
        String encryptedKey = request.get("encrypted_key");

        if (encryptedData == null || encryptedKey == null) {
            throw new IllegalArgumentException("Missing encrypted_data or encrypted_key");
        }

        // 2. 解密 AES Key (使用 RSA 私钥)
        PrivateKey privateKey = keyManagementService.getPrivateKey();
        // Client uses Base64.URL_SAFE for encryptedKey
        byte[] aesKeyBytes = decryptRsa(Base64.getUrlDecoder().decode(encryptedKey), privateKey);

        // 3. 解密数据 (使用 AES Key)
        // Client uses Base64.URL_SAFE for encryptedData
        String jsonStr = decryptAes(Base64.getUrlDecoder().decode(encryptedData), aesKeyBytes);

        // 4. 解析 JSON 并映射键名 (中文 -> 英文)
        Map<String, Object> rawData = objectMapper.readValue(jsonStr, Map.class);
        Map<String, Object> mappedData = mapChineseToEnglish(rawData);

        // 5. 调用同步服务
        String finalCredentialHash = dataCollectionSyncService.collectAndGenerateCredential(mappedData);
        
        // 检查是否为实时认证请求
        String authType = (String) mappedData.get("collection_type");
        Map<String, Object> response = new HashMap<>();
        
        if ("实时认证".equals(authType)) {
            // 实时认证：立即验证生成的凭证
            IdentityProof verifiedProof = 
                identityProofService.verifyFinalCredentialHash(finalCredentialHash);
            
            if (verifiedProof != null) {
                response.put("success", true);
                response.put("finalCredentialHash", finalCredentialHash);
                response.put("userId", verifiedProof.getUserId());
                response.put("message", "实时认证成功");
            } else {
                response.put("success", false);
                response.put("message", "实时认证失败");
            }
        } else {
            // 常规数据上传
            response.put("success", true);
            response.put("finalCredentialHash", finalCredentialHash);
            response.put("message", "数据接收并处理成功");
        }
        
        return response;
    }

    private byte[] decryptRsa(byte[] data, PrivateKey privateKey) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, privateKey);
        return cipher.doFinal(data);
    }

    private String decryptAes(byte[] data, byte[] key) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey);
        return new String(cipher.doFinal(data));
    }

    private Map<String, Object> mapChineseToEnglish(Map<String, Object> raw) {
        Map<String, Object> result = new HashMap<>();

        // Hardware mapping
        Map<String, Object> hwRaw = (Map<String, Object>) raw.get("硬件信息");
        if (hwRaw != null) {
            Map<String, Object> hw = new HashMap<>();
            hw.put("device_name", toStringValue(hwRaw.get("设备名称")));
            hw.put("model", toStringValue(hwRaw.get("设备型号")));
            hw.put("serial_number", toStringValue(hwRaw.get("硬件序列号")));
            hw.put("imei_primary", toStringValue(hwRaw.get("IMEI主卡")));
            hw.put("imei_secondary", toStringValue(hwRaw.get("IMEI副卡")));
            hw.put("cpu_architecture", toStringValue(hwRaw.get("CPU架构")));
            hw.put("memory_size", toStringValue(hwRaw.get("内存大小")));
            hw.put("baseband_version", toStringValue(hwRaw.get("基带版本")));
            result.put("hardware_info", hw);
        }

        // Software mapping
        Map<String, Object> swRaw = (Map<String, Object>) raw.get("软件信息");
        if (swRaw != null) {
            Map<String, Object> sw = new HashMap<>();
            sw.put("os_name", toStringValue(swRaw.get("操作系统名称")));
            sw.put("os_version", toStringValue(swRaw.get("操作系统版本")));
            sw.put("android_id", toStringValue(swRaw.get("Android_ID")));
            sw.put("kernel_version", toStringValue(swRaw.get("内核版本")));
            result.put("software_info", sw);
        }

        // SIM mapping
        Map<String, Object> simRaw = (Map<String, Object>) raw.get("SIM卡信息");
        if (simRaw != null) {
            Map<String, Object> sim = new HashMap<>();
            sim.put("phone_number", toStringValue(simRaw.get("卡1手机号")));
            sim.put("phone_number2", toStringValue(simRaw.get("卡2手机号")));
            sim.put("imsi", toStringValue(simRaw.get("卡1IMSI")));
            sim.put("imsi2", toStringValue(simRaw.get("卡2IMSI")));
            sim.put("iccid", toStringValue(simRaw.get("卡1ICCID")));
            sim.put("iccid2", toStringValue(simRaw.get("卡2ICCID")));
            // Add collection_time to sim_info as required by DataCollectionSyncService
            sim.put("collection_time", toStringValue(raw.get("采集时间")));
            result.put("sim_info", sim);
        }

        // Top level fields (optional, but kept for completeness)
        result.put("collection_time", toStringValue(raw.get("采集时间")));
        result.put("collection_type", toStringValue(raw.get("采集类型")));

        return result;
    }
    
    // 辅助方法：将对象转换为字符串
    private String toStringValue(Object obj) {
        if (obj == null) {
            return null;
        }
        return obj.toString();
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