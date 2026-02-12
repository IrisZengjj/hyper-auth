<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>

    <div class="container">
        <h2 class="title-text">账号登录</h2>

        <#if message?has_content && message.type != 'success'>
            <div class="error-message">
                <span>!</span>
                <span>${kcSanitize(message.summary)?no_esc}</span>
            </div>
        </#if>

        <form id="kc-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="form-group">
                <label for="username">用户名</label>
                <input type="text" id="username" name="username" value="${(login.username!'')}" placeholder="请输入您的用户名" required autofocus autocomplete="username" />
            </div>

            <div class="form-group">
                <label for="password">密码</label>
                <input type="password" id="password" name="password" placeholder="请输入密码" required autocomplete="current-password" />
            </div>

            <button type="submit" class="btn">登录</button>
        </form>

        <#if realm.registrationAllowed || realm.resetPasswordAllowed>
            <div class="link-text">
                <#if realm.registrationAllowed>
                    <a href="${url.registrationUrl}">注册新用户</a><#if realm.resetPasswordAllowed> | </#if>
                </#if>
                <#if realm.resetPasswordAllowed>
                    <a href="${url.loginResetCredentialsUrl}">忘记密码?</a>
                </#if>
            </div>
        </#if>

    </div>

</@layout.registrationLayout>