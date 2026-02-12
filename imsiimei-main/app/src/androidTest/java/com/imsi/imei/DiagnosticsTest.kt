package com.imsi.imei

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 诊断测试类
 * 用于检查本地存储权限和服务器连接性，帮助定位“本地存储失败”和“上传失败”的原因。
 * 运行此测试不会修改原有代码逻辑。
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticsTest {

    /**
     * 测试1：验证外部存储是否可写
     * 对应问题：本地存储失败
     */
    @Test
    fun testExternalStorageWritable() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val externalDir = appContext.getExternalFilesDir(null)
        
        // 检查目录是否存在
        assertNotNull("错误：外部存储目录不可用 (getExternalFilesDir returned null)。可能是存储被卸载或设备策略限制。", externalDir)
        
        val testFile = File(externalDir, "diagnostics_test.txt")
        try {
            // 尝试写入
            testFile.writeText("Storage Write Test Success")
            
            // 验证是否存在
            assertTrue("错误：文件写入后无法找到，可能是文件系统权限问题。", testFile.exists())
            
            // 验证读取
            assertEquals("错误：文件内容读取不匹配。", "Storage Write Test Success", testFile.readText())
            
            // 清理
            testFile.delete()
        } catch (e: Exception) {
            fail("严重错误：无法写入外部存储。原因: ${e.message}")
        }
    }

    /**
     * 测试2：验证服务器连接性
     * 对应问题：上传失败
     * 注意：此URL必须与 DataTransmitter.kt 中的 SERVER_URL 一致
     */
    @Test
    fun testServerConnectivity() {
        // 这里的IP必须与 DataTransmitter.kt 中的保持一致 (http://192.168.31.216:8081/api)
        // 我们测试 /configured-public-key 端点，因为它是 GET 请求，易于测试
        val serverUrl = "http://192.168.31.216:8081/api/configured-public-key"
        
        try {
            val url = URL(serverUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000 // 5秒超时
            connection.readTimeout = 5000
            connection.requestMethod = "GET"
            connection.connect()
            
            val responseCode = connection.responseCode
            
            // 200 OK 表示服务器可达且接口正常
            if (responseCode == 200) {
                // 连接成功
                println("服务器连接成功: $serverUrl")
            } else {
                fail("服务器连接失败。响应码: $responseCode。请检查服务器日志或防火墙设置。")
            }
            
            connection.disconnect()
        } catch (e: java.net.ConnectException) {
            fail("无法连接到服务器 ($serverUrl)。\n原因: 连接被拒绝。请检查：\n1. 手机和服务器是否在同一局域网？\n2. 服务器IP是否为 192.168.31.216？\n3. 服务器端口 8081 是否开放？\n4. 电脑防火墙是否允许入站连接？")
        } catch (e: java.net.SocketTimeoutException) {
            fail("连接服务器超时 ($serverUrl)。\n原因: 网络不通或服务器响应过慢。")
        } catch (e: Exception) {
            fail("连接测试发生未预期的错误: ${e.message}")
        }
    }

    /**
     * 测试3：端到端数据上传测试
     * 对应问题：验证数据能否成功加密、上传并被服务器接收
     * 注意：此测试会向服务器发送一条测试数据
     */
    @Test
    fun testEndToEndUpload() {
        val serverBaseUrl = "http://192.168.31.216:8081/api"
        
        // 1. 获取公钥
        var publicKeyString = ""
        try {
            val url = URL("$serverBaseUrl/configured-public-key")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.requestMethod = "GET"
            
            if (connection.responseCode == 200) {
                connection.inputStream.use { input ->
                    publicKeyString = input.bufferedReader().readText()
                }
            } else {
                fail("获取公钥失败，响应码: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            fail("获取公钥失败，无法进行上传测试: ${e.message}")
        }

        assertFalse("公钥为空", publicKeyString.isEmpty())

        // 2. 构造完整的测试数据 (模拟真实采集格式)
        val testData = org.json.JSONObject()
        
        val hw = org.json.JSONObject()
        hw.put("设备名称", "TestDevice_Diagnostics")
        hw.put("设备型号", "TestModel_001")
        hw.put("硬件序列号", "SN12345678")
        hw.put("IMEI主卡", "861234567890123")
        hw.put("IMEI副卡", "861234567890124")
        hw.put("CPU架构", "arm64-v8a")
        hw.put("内存大小", "8GB")
        hw.put("基带版本", "1.0.0")
        testData.put("硬件信息", hw)

        val sw = org.json.JSONObject()
        sw.put("操作系统名称", "Android")
        sw.put("操作系统版本", "12")
        sw.put("Android_ID", "android_id_test_123")
        sw.put("内核版本", "5.10")
        testData.put("软件信息", sw)

        val sim = org.json.JSONObject()
        sim.put("卡1手机号", "13800000001")
        sim.put("卡2手机号", "13800000002")
        sim.put("卡1IMSI", "460001234567890")
        sim.put("卡2IMSI", "460011234567890")
        sim.put("卡1ICCID", "89860012345678901234")
        sim.put("卡2ICCID", "89860112345678901234")
        testData.put("SIM卡信息", sim)

        testData.put("采集时间", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date()))
        testData.put("采集类型", "诊断测试上传")

        // 3. 执行上传
        val latch = java.util.concurrent.CountDownLatch(1)
        var uploadSuccess = false
        
        com.imsi.imei.utils.SecureDataUploader.uploadData(
            "$serverBaseUrl/upload", // 测试拦截器路径
            testData,
            publicKeyString
        ) { success ->
            uploadSuccess = success
            latch.countDown()
        }

        try {
            latch.await(10, java.util.concurrent.TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            fail("上传测试超时")
        }
        
        assertTrue("数据上传失败。虽然连接通畅，但服务器处理返回错误（可能是解密失败或数据库插入失败）。请检查服务器日志。", uploadSuccess)
    }
}
