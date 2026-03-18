package com.keycloak.custom;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

public class MyCustomAuthenticatorFactory implements AuthenticatorFactory {

    private static final Logger logger = Logger.getLogger(MyCustomAuthenticatorFactory.class);
    public static final String AUTHENTICATOR_ID = "my-custom-authenticator";
    private static final MyCustomAuthenticator AUTHENTICATOR_INSTANCE = new MyCustomAuthenticator();

    // 只保留接口强制要求且兼容的方法，删除所有可能冲突的 @Override
    public String getId() {
        return AUTHENTICATOR_ID;
    }

    public String getReferenceCategory() {
        return "credential";
    }

    public boolean isConfigurable() {
        return true;
    }

    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
                AuthenticationExecutionModel.Requirement.ALTERNATIVE,
                AuthenticationExecutionModel.Requirement.REQUIRED
        };
    }

    public String getDisplayType() {
        return "自定义设备凭证认证器";
    }

    public String getHelpText() {
        return "支持URL参数传递设备凭证哈希，或手动输入数据，验证成功免密登录";
    }

    public String getCategory() {
        return "Authentication";
    }

    public boolean isUserSetupAllowed() {
        return false;
    }

    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }

    public Authenticator create(KeycloakSession session) {
        return AUTHENTICATOR_INSTANCE;
    }

    public void init(Config.Scope config) {
        logger.info("自定义设备凭证认证器初始化成功！ID：" + AUTHENTICATOR_ID);
    }

    public void postInit(KeycloakSessionFactory factory) {}

    public void close() {}
}

