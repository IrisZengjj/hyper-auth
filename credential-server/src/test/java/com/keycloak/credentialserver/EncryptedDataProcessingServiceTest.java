package com.keycloak.credentialserver;

import com.keycloak.credentialserver.service.EncryptedDataProcessingService;
import com.keycloak.credentialserver.service.KeyManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class EncryptedDataProcessingServiceTest {

    @Mock
    private KeyManagementService keyManagementService;

    @Mock
    private com.keycloak.credentialserver.service.DataCollectionSyncService dataCollectionSyncService;

    @InjectMocks
    private EncryptedDataProcessingService encryptedDataProcessingService;

    @BeforeEach
    void setUp() {
        // 设置模拟行为
    }

    @Test
    void testProcessEncryptedDataMissingFields() {
        Map<String, String> request = new HashMap<>();
        request.put("encrypted_data", "test_data");

        // 测试缺少encrypted_key的情况
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            encryptedDataProcessingService.processEncryptedData(request);
        });
        assertTrue(exception.getMessage().contains("Missing encrypted_data or encrypted_key"));

        // 测试缺少encrypted_data的情况
        request.clear();
        request.put("encrypted_key", "test_key");
        exception = assertThrows(IllegalArgumentException.class, () -> {
            encryptedDataProcessingService.processEncryptedData(request);
        });
        assertTrue(exception.getMessage().contains("Missing encrypted_data or encrypted_key"));
    }

    @Test
    void testMapChineseToEnglish() throws Exception {
        // 使用反射访问私有方法进行测试
        java.lang.reflect.Method mapMethod = null;
        try {
            mapMethod = EncryptedDataProcessingService.class
                .getDeclaredMethod("mapChineseToEnglish", Map.class);
            mapMethod.setAccessible(true);

            // 创建包含中文字段的测试数据
            Map<String, Object> raw = new HashMap<>();
            
            Map<String, Object> hardwareInfo = new HashMap<>();
            hardwareInfo.put("设备名称", "TestDevice");
            hardwareInfo.put("设备型号", "TestModel");
            hardwareInfo.put("硬件序列号", "123456");
            hardwareInfo.put("IMEI主卡", "123456789012345");
            hardwareInfo.put("IMEI副卡", "123456789012346");
            hardwareInfo.put("CPU架构", "ARM");
            hardwareInfo.put("内存大小", "4GB");
            hardwareInfo.put("基带版本", "1.0.0");
            
            Map<String, Object> softwareInfo = new HashMap<>();
            softwareInfo.put("操作系统名称", "Android");
            softwareInfo.put("操作系统版本", "11");
            softwareInfo.put("Android_ID", "android123");
            softwareInfo.put("内核版本", "4.4.0");
            
            Map<String, Object> simInfo = new HashMap<>();
            simInfo.put("卡1手机号", "13800138000");
            simInfo.put("卡2手机号", "13900139000");
            simInfo.put("卡1IMSI", "460001234567890");
            simInfo.put("卡2IMSI", "460011234567890");
            simInfo.put("卡1ICCID", "8986001234567890123");
            simInfo.put("卡2ICCID", "8986011234567890123");

            raw.put("硬件信息", hardwareInfo);
            raw.put("软件信息", softwareInfo);
            raw.put("SIM卡信息", simInfo);
            raw.put("采集时间", "2024-01-01 12:00:00");
            raw.put("采集类型", "周期性采集");

            // 调用私有方法
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) mapMethod.invoke(encryptedDataProcessingService, raw);

            // 验证结果
            assertNotNull(result);
            assertTrue(result.containsKey("hardware_info"));
            assertTrue(result.containsKey("software_info"));
            assertTrue(result.containsKey("sim_info"));

            Map<String, Object> hwResult = (Map<String, Object>) result.get("hardware_info");
            assertEquals("TestDevice", hwResult.get("device_name"));
            assertEquals("TestModel", hwResult.get("model"));

            Map<String, Object> swResult = (Map<String, Object>) result.get("software_info");
            assertEquals("Android", swResult.get("os_name"));
            assertEquals("11", swResult.get("os_version"));

            Map<String, Object> simResult = (Map<String, Object>) result.get("sim_info");
            assertEquals("13800138000", simResult.get("phone_number"));
            assertEquals("460001234567890", simResult.get("imsi"));

        } catch (Exception e) {
            fail("测试失败: " + e.getMessage());
        }
    }
}