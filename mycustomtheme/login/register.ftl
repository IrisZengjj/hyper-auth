<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>

    <div class="container register-container">
        <h2 class="title-text">注册新用户</h2>

        <#if message?has_content && message.type != 'success'>
            <div class="error-message">
                <span>!</span>
                <span>${kcSanitize(message.summary)?no_esc}</span>
            </div>
        </#if>

        <form id="kc-registration-form" class="${properties.kcFormClass!}" action="${url.registrationAction}" method="post">
            <div class="form-group">
                <label for="firstName">名</label>
                <input type="text" id="firstName" name="firstName" value="${(register.firstName!'')}" required autofocus autocomplete="given-name" />
            </div>

            <div class="form-group">
                <label for="lastName">姓</label>
                <input type="text" id="lastName" name="lastName" value="${(register.lastName!'')}" required autocomplete="family-name" />
            </div>

            <div class="form-group">
                <label for="email">邮箱</label>
                <input type="email" id="email" name="email" value="${(register.email!'')}" required autocomplete="email" />
            </div>

            <div class="form-group">
                <label for="username">用户名</label>
                <input type="text" id="username" name="username" value="${(register.username!'')}" <#if !realm.registrationEmailAsUsername>required</#if> autocomplete="username" />
            </div>

            <div class="form-group">
                <label for="password">密码</label>
                <input type="password" id="password" name="password" required autocomplete="new-password" />
            </div>

            <div class="form-group">
                <label for="password-confirm">确认密码</label>
                <input type="password" id="password-confirm" name="password-confirm" required autocomplete="new-password" />
            </div>

            <button type="submit" class="btn">注册</button>
        </form>

        <div class="link-text">
            <a href="${url.loginUrl}">返回登录</a>
        </div>
    </div>

</@layout.registrationLayout>