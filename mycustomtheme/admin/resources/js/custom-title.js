// 这是一个暴力但有效的脚本，每隔0.05秒检查并替换标题
setInterval(() => {
    if (document.title.includes("Keycloak")) {
        document.title = document.title.replace("Keycloak Administration Console", "HyperAuth 管理后台");
        document.title = document.title.replace("Keycloak", "HyperAuth");
    }
}, 50);
