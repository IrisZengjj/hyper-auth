package com.keycloak.credentialserver.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.keycloak.credentialserver.collection.mapper.HardwareInfoMapper;
import com.keycloak.credentialserver.collection.mapper.SimInfoMapper;
import com.keycloak.credentialserver.collection.mapper.SoftwareInfoMapper;
import com.keycloak.credentialserver.entity.HardwareInfo;
import com.keycloak.credentialserver.entity.SimInfo;
import com.keycloak.credentialserver.entity.SoftwareInfo;
import com.keycloak.credentialserver.entity.IdentityProof;
import com.keycloak.credentialserver.mapper.IdentityProofMapper;
import com.keycloak.credentialserver.util.KeycloakUserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.time.LocalDateTime;

@Service
public class DataCollectionSyncService {

    @Autowired
    private HardwareInfoMapper hardwareInfoMapper;
    @Autowired
    private SoftwareInfoMapper softwareInfoMapper;
    @Autowired
    private SimInfoMapper simInfoMapper;
    @Autowired
    private IdentityProofMapper identityProofMapper;
    @Autowired
    private KeycloakUserUtil keycloakUserUtil;
    
    @Autowired
    private IdentityProofService identityProofService;

    private static final String DEVICE_FINGERPRINT_SALT = "1234567890123456";
    private static final String PHONE_SALT = "PHONE_SALT_VAL";

    @Transactional(rollbackFor = Exception.class)
    public String collectAndGenerateCredential(Map<String, Object> requestData) throws Exception {
        // 1. 解析数据
        Map<String, Object> hardwareMap = (Map<String, Object>) requestData.get("hardware_info");
        Map<String, Object> softwareMap = (Map<String, Object>) requestData.get("software_info");
        Map<String, Object> simMap = (Map<String, Object>) requestData.get("sim_info");

        if (hardwareMap == null || softwareMap == null || simMap == null) {
            throw new IllegalArgumentException("缺少必要的采集数据模块");
        }

        // 2. 获取采集类型
        String collectionType = (String) requestData.get("collection_type");

        // 3. 如果是认证数据采集，直接生成临时凭证进行比对，不存储到数据库
        if ("实时认证".equals(collectionType)) {
            // Layer 0: 生成设备指纹（使用与常规采集相同的逻辑，确保算法一致性）
            HardwareInfo tempHardwareInfo = mapToHardwareInfo(hardwareMap);
            SoftwareInfo tempSoftwareInfo = mapToSoftwareInfo(softwareMap);
            String deviceFingerprint;
            try {
                deviceFingerprint = generateDeviceFingerprint(tempHardwareInfo, tempSoftwareInfo);
            } catch (Exception e) {
                throw new Exception("生成设备指纹失败: " + e.getMessage());
            }

            // Layer 1: 生成手机号Hash
            String phoneNumber = (String) simMap.get("phone_number");
            if (!StringUtils.hasText(phoneNumber)) {
                phoneNumber = "UNKNOWN_" + deviceFingerprint.substring(0, Math.min(16, deviceFingerprint.length()));
            }
            String phoneNumberHash;
            try {
                phoneNumberHash = generatePhoneNumberHash(phoneNumber, deviceFingerprint);
            } catch (Exception e) {
                throw new Exception("生成手机号哈希失败: " + e.getMessage());
            }

            // Layer 2: Device Hash
            String deviceHash;
            try {
                deviceHash = hmacSha256(phoneNumberHash, deviceFingerprint);
            } catch (Exception e) {
                throw new Exception("生成设备哈希失败: " + e.getMessage());
            }

            // Layer 3: Final Credential Hash
            String simAttributes = Stream.of(
                    (String) simMap.get("imsi"), (String) simMap.get("imsi2"),
                    (String) simMap.get("iccid"), (String) simMap.get("iccid2"),
                    (String) hardwareMap.get("imei_primary"), (String) hardwareMap.get("imei_secondary")
            ).filter(Objects::nonNull).collect(Collectors.joining("|"));

            String finalCredentialHash;
            try {
                finalCredentialHash = hmacSha256(deviceHash, simAttributes);
            } catch (Exception e) {
                throw new Exception("生成最终凭证哈希失败: " + e.getMessage());
            }

            // 直接在 identity_proof 表中进行比对验证
            QueryWrapper<IdentityProof> proofQuery = new QueryWrapper<>();
            proofQuery.eq("final_credential_hash", finalCredentialHash);
            IdentityProof existingProof = identityProofMapper.selectOne(proofQuery);

            if (existingProof != null) {
                return finalCredentialHash; // 认证成功，返回哈希
            } else {
                throw new Exception("实时认证失败：设备凭证未注册或信息不匹配");
            }
        }

        // 4. 如果是常规数据采集（手动采集或周期性采集），执行新逻辑
        HardwareInfo newHardwareInfo = mapToHardwareInfo(hardwareMap);
        SoftwareInfo newSoftwareInfo = mapToSoftwareInfo(softwareMap);
        SimInfo newSimInfo = mapToSimInfo(simMap);

        // 检查用户是否已存在
        Long existingRecordId = findExistingUser(newHardwareInfo, newSimInfo);

        if (existingRecordId != null) {
            // 用户已存在，更新现有记录
            updateExistingUser(existingRecordId, newHardwareInfo, newSoftwareInfo, newSimInfo);
            
            // 生成凭证 (三层脱敏融合逻辑)
            String deviceFingerprint;
            try {
                deviceFingerprint = generateDeviceFingerprint(newHardwareInfo, newSoftwareInfo);
            } catch (Exception e) {
                throw new Exception("生成设备指纹失败: " + e.getMessage());
            }

            String phoneNumber = newSimInfo.getPhoneNumber();
            if (!StringUtils.hasText(phoneNumber)) {
                phoneNumber = "UNKNOWN_" + deviceFingerprint.substring(0, Math.min(16, deviceFingerprint.length()));
            }
            String phoneNumberHash;
            try {
                phoneNumberHash = generatePhoneNumberHash(phoneNumber, deviceFingerprint);
            } catch (Exception e) {
                throw new Exception("生成手机号哈希失败: " + e.getMessage());
            }

            // Layer 2: Device Hash
            String deviceHash;
            try {
                deviceHash = hmacSha256(phoneNumberHash, deviceFingerprint);
            } catch (Exception e) {
                throw new Exception("生成设备哈希失败: " + e.getMessage());
            }

            // Layer 3: Final Credential Hash
            String simAttributes = Stream.of(
                    newSimInfo.getImsi(), newSimInfo.getImsi2(),
                    newSimInfo.getIccid(), newSimInfo.getIccid2(),
                    newHardwareInfo.getImeiPrimary(), newHardwareInfo.getImeiSecondary()
            ).filter(Objects::nonNull).collect(Collectors.joining("|"));

            return hmacSha256(deviceHash, simAttributes);
        } else {
            // 用户不存在，添加新记录
            hardwareInfoMapper.insert(newHardwareInfo);
            softwareInfoMapper.insert(newSoftwareInfo);
            simInfoMapper.insert(newSimInfo);

            // 生成凭证 (三层脱敏融合逻辑)
            String deviceFingerprint;
            try {
                deviceFingerprint = generateDeviceFingerprint(newHardwareInfo, newSoftwareInfo);
            } catch (Exception e) {
                throw new Exception("生成设备指纹失败: " + e.getMessage());
            }

            String phoneNumber = newSimInfo.getPhoneNumber();
            if (!StringUtils.hasText(phoneNumber)) {
                phoneNumber = "UNKNOWN_" + deviceFingerprint.substring(0, Math.min(16, deviceFingerprint.length()));
            }
            String phoneNumberHash;
            try {
                phoneNumberHash = generatePhoneNumberHash(phoneNumber, deviceFingerprint);
            } catch (Exception e) {
                throw new Exception("生成手机号哈希失败: " + e.getMessage());
            }

            // Layer 2: Device Hash
            String deviceHash;
            try {
                deviceHash = hmacSha256(phoneNumberHash, deviceFingerprint);
            } catch (Exception e) {
                throw new Exception("生成设备哈希失败: " + e.getMessage());
            }

            // Layer 3: Final Credential Hash
            String simAttributes = Stream.of(
                    newSimInfo.getImsi(), newSimInfo.getImsi2(),
                    newSimInfo.getIccid(), newSimInfo.getIccid2(),
                    newHardwareInfo.getImeiPrimary(), newHardwareInfo.getImeiSecondary()
            ).filter(Objects::nonNull).collect(Collectors.joining("|"));

            String finalCredentialHash;
            try {
                finalCredentialHash = hmacSha256(deviceHash, simAttributes);
            } catch (Exception e) {
                throw new Exception("生成最终凭证哈希失败: " + e.getMessage());
            }

            // 调用 Keycloak 工具类：如果用户不存在则创建，返回 Keycloak UUID
            String userId;
            try {
                userId = keycloakUserUtil.createKeycloakUser(finalCredentialHash);
            } catch (Exception e) {
                throw new Exception("创建Keycloak用户失败: " + e.getMessage());
            }

            // 持久化到 identity_proof 数据库
            IdentityProof proof = new IdentityProof();
            proof.setPhoneNumberHash(phoneNumberHash);
            proof.setDeviceFingerprintHash(deviceHash);
            proof.setFinalCredentialHash(finalCredentialHash);
            proof.setUserId(userId); // 存储 Keycloak 返回的 UUID
            proof.setCreationTimestamp(LocalDateTime.now());
            proof.setLastUpdateTimestamp(LocalDateTime.now());

            // 使用IdentityProofService来处理插入，避免重复插入导致的约束冲突
            identityProofService.saveCredentialWithAutoUser(proof);

            return finalCredentialHash;
        }
    }

    /**
     * 检查用户是否已存在
     * 如果两个电话号都存在（非null）同时比对成功，则说明该用户已存在
     * 如果两个电话号都是null，则比对硬件属性
     */
    private Long findExistingUser(HardwareInfo hardwareInfo, SimInfo simInfo) {
        // 检查电话号码是否都存在
        boolean phone1Exists = simInfo.getPhoneNumber() != null && !simInfo.getPhoneNumber().trim().isEmpty();
        boolean phone2Exists = simInfo.getPhoneNumber2() != null && !simInfo.getPhoneNumber2().trim().isEmpty();

        if (phone1Exists || phone2Exists) {
            // 至少有一个电话号码存在，按电话号码查找
            QueryWrapper<SimInfo> simQuery = new QueryWrapper<>();
            if (phone1Exists && phone2Exists) {
                // 两个电话号码都存在，查找同时匹配两条记录的用户
                simQuery.and(wrapper -> wrapper.eq("phone_number", simInfo.getPhoneNumber())
                                               .eq("phone_number2", simInfo.getPhoneNumber2())
                                               .or()
                                               .eq("phone_number", simInfo.getPhoneNumber2())
                                               .eq("phone_number2", simInfo.getPhoneNumber()));
            } else if (phone1Exists) {
                // 只有第一个电话号码存在
                simQuery.eq("phone_number", simInfo.getPhoneNumber());
            } else if (phone2Exists) {
                // 只有第二个电话号码存在
                simQuery.eq("phone_number", simInfo.getPhoneNumber2());
            }
            
            SimInfo existingSim = simInfoMapper.selectOne(simQuery);
            if (existingSim != null) {
                return existingSim.getId(); // 返回找到的记录ID
            }
        } else {
            // 两个电话号码都为null，按硬件属性查找
            QueryWrapper<HardwareInfo> hwQuery = new QueryWrapper<>();
            hwQuery.eq("device_name", hardwareInfo.getDeviceName())
                   .eq("model", hardwareInfo.getModel())
                   .eq("serial_number", hardwareInfo.getSerialNumber())
                   .eq("imei_primary", hardwareInfo.getImeiPrimary())
                   .eq("imei_secondary", hardwareInfo.getImeiSecondary())
                   .eq("cpu_architecture", hardwareInfo.getCpuArchitecture())
                   .eq("memory_size", hardwareInfo.getMemorySize())
                   .eq("baseband_version", hardwareInfo.getBasebandVersion());

            HardwareInfo existingHardware = hardwareInfoMapper.selectOne(hwQuery);
            if (existingHardware != null) {
                return existingHardware.getId(); // 返回找到的记录ID
            }
        }

        return null;
    }

    /**
     * 更新现有用户的记录
     */
    private void updateExistingUser(Long recordId, HardwareInfo hardwareInfo, SoftwareInfo softwareInfo, SimInfo simInfo) {
        // 更新硬件信息
        hardwareInfo.setId(recordId);
        hardwareInfo.setCreationTimestamp(LocalDateTime.now());
        hardwareInfoMapper.updateById(hardwareInfo);

        // 更新软件信息
        softwareInfo.setId(recordId);
        softwareInfo.setCreationTimestamp(LocalDateTime.now());
        softwareInfoMapper.updateById(softwareInfo);

        // 更新SIM信息
        simInfo.setId(recordId);
        simInfo.setCreationTimestamp(LocalDateTime.now());
        simInfoMapper.updateById(simInfo);

        // 更新IdentityProof信息（根据recordId查找对应的记录）
        QueryWrapper<IdentityProof> proofQuery = new QueryWrapper<>();
        proofQuery.eq("id", recordId);
        IdentityProof existingProof = identityProofMapper.selectOne(proofQuery);
        
        if (existingProof != null) {
            // 生成新的凭证信息
            String deviceFingerprint;
            String phoneNumberHash;
            String deviceHash;
            String finalCredentialHash;
            try {
                deviceFingerprint = generateDeviceFingerprint(hardwareInfo, softwareInfo);
                String phoneNumber = simInfo.getPhoneNumber();
                if (!StringUtils.hasText(phoneNumber)) {
                    phoneNumber = "UNKNOWN_" + deviceFingerprint.substring(0, Math.min(16, deviceFingerprint.length()));
                }
                phoneNumberHash = generatePhoneNumberHash(phoneNumber, deviceFingerprint);
                deviceHash = hmacSha256(phoneNumberHash, deviceFingerprint);

                String simAttributes = Stream.of(
                        simInfo.getImsi(), simInfo.getImsi2(),
                        simInfo.getIccid(), simInfo.getIccid2(),
                        hardwareInfo.getImeiPrimary(), hardwareInfo.getImeiSecondary()
                ).filter(Objects::nonNull).collect(Collectors.joining("|"));

                finalCredentialHash = hmacSha256(deviceHash, simAttributes);
            } catch (Exception e) {
                // 如果生成凭证失败，记录错误但不中断更新流程
                e.printStackTrace();
                return;
            }

            existingProof.setPhoneNumberHash(phoneNumberHash);
            existingProof.setDeviceFingerprintHash(deviceHash);
            existingProof.setFinalCredentialHash(finalCredentialHash);
            existingProof.setLastUpdateTimestamp(LocalDateTime.now());

            // 直接更新数据库，而不是调用saveCredentialWithAutoUser（因为是更新而不是插入）
            identityProofMapper.updateById(existingProof);
        }
    }

    private HardwareInfo mapToHardwareInfo(Map<String, Object> map) {
        HardwareInfo info = new HardwareInfo();
        info.setDeviceName((String) map.get("device_name"));
        info.setModel((String) map.get("model"));
        info.setSerialNumber((String) map.get("serial_number"));
        info.setImeiPrimary((String) map.get("imei_primary"));
        info.setImeiSecondary((String) map.get("imei_secondary"));
        info.setCpuArchitecture((String) map.get("cpu_architecture"));
        info.setMemorySize((String) map.get("memory_size"));
        info.setBasebandVersion((String) map.get("baseband_version"));
        info.setCreationTimestamp(LocalDateTime.now());
        return info;
    }

    private SoftwareInfo mapToSoftwareInfo(Map<String, Object> map) {
        SoftwareInfo info = new SoftwareInfo();
        info.setOsName((String) map.get("os_name"));
        info.setOsVersion((String) map.get("os_version"));
        info.setAndroidId((String) map.get("android_id"));
        info.setKernelVersion((String) map.get("kernel_version"));
        info.setCreationTimestamp(LocalDateTime.now());
        return info;
    }

    private SimInfo mapToSimInfo(Map<String, Object> map) {
        SimInfo info = new SimInfo();
        info.setImsi((String) map.get("imsi"));
        info.setImsi2((String) map.get("imsi2"));
        info.setIccid((String) map.get("iccid"));
        info.setIccid2((String) map.get("iccid2"));
        info.setPhoneNumber((String) map.get("phone_number"));
        info.setPhoneNumber2((String) map.get("phone_number2"));
        info.setCollectionTime((String) map.get("collection_time")); // Assuming string or format
        info.setCreationTimestamp(LocalDateTime.now());
        return info;
    }

    private String generateDeviceFingerprint(HardwareInfo hw, SoftwareInfo sw) throws Exception {
        // Combine attributes consistently
        String rawData = Stream.of(
                hw.getDeviceName(), hw.getModel(), hw.getSerialNumber(), 
                hw.getCpuArchitecture(), hw.getMemorySize(),
                sw.getOsName(), sw.getOsVersion(), sw.getAndroidId()
        ).map(s -> s == null ? "" : s).collect(Collectors.joining("|"));

        return encryptAes(rawData, DEVICE_FINGERPRINT_SALT);
    }

    private String generatePhoneNumberHash(String phoneNumber, String deviceFingerprint) throws Exception {
        // Standardize: Remove non-digits? User said "Standardize and Salt"
        // Simple standardization: Trim
        String standardPhone = phoneNumber.trim();
        // Use a portion of device fingerprint as additional salt to ensure uniqueness across devices
        String uniqueSalt = PHONE_SALT + "_" + deviceFingerprint.substring(0, Math.min(16, deviceFingerprint.length()));
        String input = standardPhone + uniqueSalt;
        return sha256(input);
    }

    //从原始数据生成设备指纹
    private String generateDeviceFingerprintFromRawData(Map<String, Object> hardwareMap, Map<String, Object> softwareMap) throws Exception {
        // Combine attributes consistently
        String rawData = Stream.of(
                (String) hardwareMap.get("device_name"),
                (String) hardwareMap.get("model"),
                (String) hardwareMap.get("serial_number"),
                (String) hardwareMap.get("imei_primary"),
                (String) hardwareMap.get("imei_secondary"),
                (String) hardwareMap.get("cpu_architecture"),
                (String) hardwareMap.get("memory_size"),
                (String) hardwareMap.get("baseband_version"),
                (String) softwareMap.get("os_name"),
                (String) softwareMap.get("os_version"),
                (String) softwareMap.get("android_id"),
                (String) softwareMap.get("kernel_version")
        ).map(s -> s == null ? "" : s).collect(Collectors.joining("|"));

        return encryptAes(rawData, DEVICE_FINGERPRINT_SALT);
    }

    // --- Crypto Utils ---

    private String encryptAes(String content, String key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public String decryptAes(String encrypted, String key) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decoded = Base64.getDecoder().decode(encrypted);
        byte[] original = cipher.doFinal(decoded);
        return new String(original, StandardCharsets.UTF_8);
    }

    private String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String hmacSha256(String key, String data) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hash);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}