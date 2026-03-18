<#import "template.ftl" as layout>

<@layout.registrationLayout displayMessage=true; section>

<div class="device-error-wrapper">

  <div class="device-error-card">
    <div class="icon">⚠️</div>

    <h1>设备验证失败</h1>

    <p class="desc">
      当前设备未通过安全验证，<br>
      请使用账号密码方式继续登录。
    </p>

    <form method="post" action="${url.loginAction}">
      <button
        type="submit"
        name="form_action"
        value="use_password"
        class="primary-btn">
        使用账号密码登录
      </button>
    </form>

    <div class="hint">
      如果你认为这是误判，请联系系统管理员
    </div>
  </div>

</div>

<style>
.device-error-wrapper {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
}

.device-error-card {
  width: 420px;
  padding: 40px 32px;
  background: #ffffff;
  border-radius: 16px;
  text-align: center;
  box-shadow: 0 30px 60px rgba(0,0,0,0.2);
}

.device-error-card .icon {
  font-size: 42px;
  margin-bottom: 12px;
}

.device-error-card h1 {
  font-size: 26px;
  font-weight: 700;
  margin-bottom: 12px;
  color: #111827;
}

.device-error-card .desc {
  font-size: 15px;
  line-height: 1.6;
  color: #4b5563;
  margin-bottom: 28px;
}

.primary-btn {
  width: 100%;
  height: 48px;
  border-radius: 10px;
  border: none;
  font-size: 16px;
  font-weight: 600;
  background: linear-gradient(135deg, #6366f1, #7c3aed);
  color: #fff;
  cursor: pointer;
  transition: all .2s ease;
}

.primary-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 10px 20px rgba(99,102,241,.4);
}

.hint {
  margin-top: 18px;
  font-size: 12px;
  color: #9ca3af;
}
</style>

</@layout.registrationLayout>

