<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=true; section>
<div class="container">
    <h2 class="title-text">设备验证失败</h2>
    <p>${errorMsg}</p>
    <form method="post" action="${url.loginAction}">
        <button type="submit" name="form_action" value="use_password" class="btn">
            进入账号密码登录
        </button>
    </form>
</div>
</@layout.registrationLayout>