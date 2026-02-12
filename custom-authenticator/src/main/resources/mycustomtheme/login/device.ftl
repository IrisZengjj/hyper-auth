<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=false; section>
    
    <div class="container">
        <div class="loader"></div>
        <h2 class="title-text">正在验证设备环境...</h2>
        <p>请保持连接，系统正在安全检查。</p>

        <#if message?has_content && message.type != 'success'>
            <div class="error-message">
                <span>!</span>
                <span>${kcSanitize(message.summary)?no_esc}</span>
            </div>
        </#if>

        <form id="device-form" method="post" action="${url.loginAction}">
            <input type="hidden" id="multimodalData" name="multimodalData" value="">
            
            <div class="link-text">
                <button type="submit" name="form_action" value="use_password" class="fallback-btn">
                    验证时间过长？点此使用账号密码登录
                </button>
            </div>
            
            <div class="debug-area">
                <p class="debug-hint">[开发调试工具]</p>
                <button type="button" onclick="fillMockData()" class="btn debug-btn">
                    [模拟] 注入设备凭证并提交
                </button>
            </div>
        </form>
    </div>

<script>
async function verifyDevice() {
  try {
    // 采集逻辑...
    const data = {
            "encryptedData": "valid_encrypted_data_mock",
            "encryptedKey": "valid_key_mock",
            "userId": "0bf735cb-e178-42c2-93d0-97fa9c5b2dcf",
            "username": "testuser"
        };
    document.getElementById('multimodalData').value = JSON.stringify(data);
    document.getElementById('device-form').submit();
  } catch(e) {
    // 如果采集失败，倒计时后自动触发使用密码登录
    setTimeout(() => {
      const btn = document.querySelector('button[value="use_password"]');
      if (btn) btn.click();
    }, 2000);
  }
}
window.onload = verifyDevice;
</script></@layout.registrationLayout>