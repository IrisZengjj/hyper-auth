package com.keycloak.custom;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.ServicesLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import jakarta.ws.rs.core.MultivaluedMap;

import java.util.Map;

public class MyCustomAuthenticator implements Authenticator {

    private static final ServicesLogger logger = ServicesLogger.LOGGER;
    // private static final String CREDENTIAL_SERVER_VERIFY_URL = "http://localhost:8081/api/verify";
    private static final String CREDENTIAL_SERVER_DIRECT_AUTH_URL = "http://localhost:8081/api/direct-auth";
    private final ObjectMapper objectMapper = new ObjectMapper();
    // 仅保留 URL 参数名常量，移除线程池
    private static final String DEVICE_CREDENTIAL_PARAM = "device_credential_hash";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        System.out.println("[DEBUG] [authenticate] MyCustomAuthenticator 启动");
        KeycloakSession session = context.getSession();

        // 提取 URL 参数（核心逻辑不变）
        MultivaluedMap<String, String> queryParams = context.getHttpRequest().getUri().getQueryParameters();
        System.out.println("[DEBUG] [authenticate] 所有 URL 参数：" + queryParams);
        String deviceCredentialHash = queryParams.getFirst(DEVICE_CREDENTIAL_PARAM);
        System.out.println("[DEBUG] [authenticate] 提取到的 device_credential_hash：" + deviceCredentialHash);

        // 有哈希值：同步调用验证（无线程池，无生命周期问题）
        if (deviceCredentialHash != null && !deviceCredentialHash.trim().isEmpty()) {
            System.out.println("[DEBUG] [authenticate] 开始同步验证设备凭证哈希");
            try {
                // 同步调用服务器验证（耗时短，用户无感知）
                Map<String, Object> verifyResult = verifyAndLoginWithCredentialServer(deviceCredentialHash);
                System.out.println("[DEBUG] [authenticate] 同步验证结果：" + verifyResult);
                
                // 直接处理验证结果
                if (verifyResult != null && Boolean.TRUE.equals(verifyResult.get("success"))) {
                    String userId = (String) verifyResult.get("userId");
                    String dbId = (String) verifyResult.get("dbId"); // 从服务器获取数据库记录的id
                    
                    UserModel user = null;
                    if (dbId != null) {
                        // 使用数据库的id生成用户名：user1, user2...
                        String username = "user" + dbId;
                        user = findOrCreateUserByUsername(context, username, deviceCredentialHash);
                    } else {
                        // 如果没有返回dbId，则回退到原来的逻辑
                        String username = (String) verifyResult.get("username");
                        user = findUser(context, userId, username);
                    }
                    
                    if (user != null) {
                        context.setUser(user);
                        context.success(); // 验证成功，直接登录跳转
                        return;
                    } else {
                        System.out.println("[DEBUG] [authenticate] 验证成功但未找到用户");
                        showErrorPage(context, "设备验证成功，但未找到对应用户");
                        return;
                    }
                } else {
                    System.out.println("[DEBUG] [authenticate] 设备凭证验证失败");
                    showErrorPage(context, "设备验证失败，请用账号密码登录");
                    return;
                }
            } catch (Exception e) {
                logger.error("设备凭证同步验证异常: " + e.getMessage(), e);
                showErrorPage(context, "设备验证异常，请用账号密码登录");
                return;
            }
        }

        // 无哈希值：显示手动输入页（保留原有逻辑）
        System.out.println("[DEBUG] [authenticate] 未提取到有效 device_credential_hash，显示手动输入页");
        context.challenge(context.form().createForm("device.ftl"));
    }

    @Override
public void action(AuthenticationFlowContext context) {
    MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
    String action = formData.getFirst("form_action");
    System.out.println("[DEBUG] [action] form_action = " + action);

     if ("use_password".equals(action)) {
        context.attempted();
        return;
    }

    // 手动输入场景：保留原有逻辑
    String multimodalData = formData.getFirst("multimodalData");
    System.out.println("[DEBUG] [action] 手动输入的 multimodalData：" + multimodalData);
    if (multimodalData == null || multimodalData.trim().isEmpty()) {
        showErrorPage(context, "设备数据为空，请选择是否进入账号密码登录");
        return;
    }

        Map<String, Object> verifyResult = verifyAndLoginWithCredentialServer(multimodalData);
    if (verifyResult != null && Boolean.TRUE.equals(verifyResult.get("success"))) {
        String userId = (String) verifyResult.get("userId");
        String dbId = (String) verifyResult.get("dbId"); // 从服务器获取数据库记录的id
        
        UserModel user = null;
        if (dbId != null) {
            // 使用数据库的id生成用户名：user1, user2...
            String username = "user" + dbId;
            user = findOrCreateUserByUsername(context, username, multimodalData);
        } else {
            // 如果没有返回dbId，则回退到原来的逻辑
            String username = (String) verifyResult.get("username");
            user = findUser(context, userId, username);
        }
        
        if (user != null) {
            context.setUser(user);
            context.success();
            return;
        }
    }
    showErrorPage(context, "设备验证失败，请选择是否进入账号密码登录");
}


    /**
     * 查找或创建用户，使用用户名作为标识，email存储credential hash
     */
    private UserModel findOrCreateUserByUsername(AuthenticationFlowContext context, String username, String credentialHash) {
        RealmModel realm = context.getRealm();
        KeycloakSession session = context.getSession();
        
        UserModel user = session.users().getUserByUsername(realm, username);
        
        if (user == null) {
            // 创建新用户
            user = session.users().addUser(realm, username);
            user.setEnabled(true);
            user.setEmail(credentialHash); // 将credential hash作为email存储
            
            System.out.println("[DEBUG] [findOrCreateUserByUsername] 创建新用户: " + username + ", email: " + credentialHash);
        } else {
            // 更新用户email（如果不同的话）
            if (credentialHash != null && !credentialHash.equals(user.getEmail())) {
                user.setEmail(credentialHash);
                System.out.println("[DEBUG] [findOrCreateUserByUsername] 更新用户email: " + username + ", email: " + credentialHash);
            }
        }
        
        return user;
    }

    /**
     * 显示错误页（抽取公共方法）
     */
    private void showErrorPage(AuthenticationFlowContext context, String errorMsg) {
    context.failureChallenge(
        AuthenticationFlowError.INVALID_CREDENTIALS,
        context.form()
            .setAttribute("errorMsg", errorMsg)
            .createForm("device-error.ftl")
    );
}

    /**
     * 同步调用 credential-server 验证（无线程池）
     */
    private Map<String, Object> verifyAndLoginWithCredentialServer(String deviceCredentialHash) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(CREDENTIAL_SERVER_DIRECT_AUTH_URL);
            httpPost.setHeader("Content-Type", "application/json; charset=UTF-8");

            // 安全转义哈希值，确保 JSON 合法
            String safeHash = deviceCredentialHash.replace("\"", "\\\"").replace("\\", "\\\\");
            String requestBody = String.format("{\"finalCredentialHash\":\"%s\"}", safeHash);
            System.out.println("[DEBUG] [verifyWithCredentialServer] 发送请求体：" + requestBody);
            httpPost.setEntity(new StringEntity(requestBody, "UTF-8"));

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getStatusLine().getStatusCode();
                String responseBody = EntityUtils.toString(response.getEntity(), "UTF-8");
                System.out.println("[DEBUG] [verifyWithCredentialServer] 服务器响应：状态码=" + statusCode + "，响应体=" + responseBody);

                if (statusCode == 200) {
                    return objectMapper.readValue(responseBody, Map.class);
                } else {
                    logger.errorf("Credential Server 错误: 状态码=%d，响应体=%s", statusCode, responseBody);
                }
            }
        } catch (Exception e) {
            logger.error("Credential Server 调用异常: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * 查找 Keycloak 用户（保留原有逻辑）
     */
    private UserModel findUser(AuthenticationFlowContext context, String userId, String username) {
        UserModel user = null;
        if (userId != null) {
            user = context.getSession().users().getUserById(context.getRealm(), userId);
            System.out.println("[DEBUG] [findUser] 根据 userId " + userId + " 查找用户：" + (user != null ? user.getUsername() : "未找到"));
        }
        if (user == null && username != null) {
            user = context.getSession().users().getUserByUsername(context.getRealm(), username);
            System.out.println("[DEBUG] [findUser] 根据 username " + username + " 查找用户：" + (user != null ? user.getId() : "未找到"));
        }
        return user;
    }

    // ------------------------------ 接口实现 ------------------------------
    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

    @Override
    public void close() {
        // 无线程池，无需关闭操作
        System.out.println("[DEBUG] [close] 认证器关闭（无资源需要释放）");
    }
}