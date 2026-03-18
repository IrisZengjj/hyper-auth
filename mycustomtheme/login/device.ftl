<!DOCTYPE html>
<html lang="${locale.language}" dir="${locale.direction}">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>${msg("deviceAuthTitle", "设备凭证认证")}</title>
    <!-- 引用 Keycloak 内置的 Bootstrap（无需自己下载） -->
    <link href="${url.resourcesPath}/lib/bootstrap/css/bootstrap.min.css" rel="stylesheet">
    <style>
        /* 自定义样式：居中、阴影、圆角 */
        body {
            background-color: #f8f9fa;
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .card {
            width: 100%;
            max-width: 500px;
            border: none;
            border-radius: 12px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
            overflow: hidden;
        }
        .card-header {
            background-color: #2c3e50;
            color: white;
            padding: 20px;
            text-align: center;
            font-size: 1.25rem;
            font-weight: 600;
        }
        .card-body {
            padding: 30px;
        }
        .spinner {
            width: 50px;
            height: 50px;
            border: 5px solid #e0e0e0;
            border-top-color: #2c3e50;
            border-radius: 50%;
            animation: spin 1s linear infinite;
            margin: 0 auto 20px;
        }
        @keyframes spin { to { transform: rotate(360deg); } }
        .form-label {
            font-weight: 500;
            color: #333;
        }
        .btn-primary {
            background-color: #2c3e50;
            border-color: #2c3e50;
            padding: 12px;
            font-size: 1rem;
            border-radius: 8px;
            margin-top: 10px;
        }
        .btn-primary:hover {
            background-color: #34495e;
            border-color: #34495e;
        }
        .btn-secondary {
            background-color: #95a5a6;
            border-color: #95a5a6;
            padding: 12px;
            font-size: 1rem;
            border-radius: 8px;
            margin-top: 10px;
        }
        .btn-secondary:hover {
            background-color: #7f8c8d;
            border-color: #7f8c8d;
        }
        .text-center {
            text-align: center;
            margin-bottom: 20px;
            color: #666;
        }
    </style>
</head>
<body>
    <div class="card">
        <div class="card-header">
            设备凭证认证
        </div>
        <div class="card-body">
            <!-- 场景1：有 URL 哈希 → 显示验证中动画 -->
            <#if (request.params.device_credential_hash)?? && request.params.device_credential_hash != "">
                <div class="spinner"></div>
                <h5 class="text-center">正在验证设备凭证...</h5>
                <p class="text-center text-muted">请勿刷新页面，验证完成后自动跳转</p>
                <script>setInterval(() => window.location.reload(), 1500);</script>
            <!-- 场景2：无 URL 哈希 → 显示手动输入表单 -->
            <#else>
                <form method="post" action="${url.loginAction}">
                    <div class="mb-3">
                        <label for="multimodalData" class="form-label">设备凭证数据</label>
                        <input type="text" class="form-control form-control-lg" id="multimodalData" name="multimodalData" 
                               placeholder="请输入设备凭证数据" required>
                    </div>
                    <button type="submit" class="btn btn-primary w-100 mb-2">提交验证</button>
                    <button type="submit" class="btn btn-secondary w-100" name="form_action" value="use_password">
                        用账号密码登录
                    </button>
                </form>
            </#if>
        </div>
    </div>
</body>
</html>

