<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
    <div class="container">
        <div style="margin-bottom: 30px;">
            <h1 class="brand-title" style="margin:0; color:#007bff; font-size:32px; font-weight:bold;">HyperAuth</h1>
            <p style="color:#666; font-size:14px; margin-top:8px;">账号登录</p>
        </div>

        <#if message?has_content && message.type != 'success'>
            <div class="error-message">${kcSanitize(message.summary)?no_esc}</div>
        </#if>

        <form id="kc-form" action="${url.loginAction}" method="post" autocomplete="off">
            <div class="form-group">
                <label for="username">用户名</label>
                <input type="text" id="username" name="username" placeholder="请输入用户名" required />
            </div>

            <div class="form-group">
                <label for="password">密码</label>
                <input type="password" id="password" name="password" placeholder="请输入密码" required />
            </div>

            <button type="submit" class="btn">登录</button>
        </form>
        
        <div class="link-text">
            <#if realm.registrationAllowed>
                <a href="${url.registrationUrl}">注册新用户</a>
            </#if>
        </div>
    </div>
</@layout.registrationLayout>