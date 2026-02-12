<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>

    <div class="container">
        <h2 class="title-text">请输入用户名</h2>

        <#if message?has_content && message.type != 'success'>
            <div class="error-message">
                <span>!</span>
                <span>${kcSanitize(message.summary)?no_esc}</span>
            </div>
        </#if>

        <form id="kc-username-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
            <div class="form-group">
                <label for="username">用户名</label>
                <input type="text" id="username" name="username" value="${(login.username!'')}" placeholder="请输入您的用户名" required autofocus autocomplete="username" />
            </div>

            <button type="submit" class="btn">下一步</button>
        </form>

        <div class="link-text">
            <#if realm.registrationAllowed>
                <a href="${url.registrationUrl}">注册新用户</a>
            </#if>
        </div>
    </div>

</@layout.registrationLayout>