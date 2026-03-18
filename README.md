### 项目结构：
·端侧采集（imsiimei-main）：基于Android平台开发。采集设备硬件、软件及SIM卡凭证信息，生成JSON数据存储至本地并上传至服务器，包括注册采集、周期采集和认证采集。  
·服务端（credential-server）：springboot应用，连接mysql，处理和存储端侧属性及身份凭证。并提供注册和认证时的日志显示。  
·认证器（custom-authenticator）：编译为jar包形式，存放于keycloak发行版的providers路径下作为自定义认证流，并且在后台进行相应认证流程配置。  
·自定义主题（mycustomtheme）：包括登录页login和后台页面的图标显示admin。放在keycloak发行版的themes路径下，并且在后台进行相应主题配置。  

### 一、端侧采集插件 (imsiimei-main)  
端侧开发环境：  
- Gradle 版本 : 7.0.2  
- JDK 版本 : 1.8  
- Kotlin 版本 : 1.7.10  
- 编译 SDK : 30  
- 最低 SDK : 26  
- 目标 SDK : 30  

核心依赖版本：  
- Android Gradle Plugin (AGP) : 7.0.2  
- core-ktx : 1.6.0  
- appcompat : 1.3.1  
- material : 1.4.0  
- constraintlayout : 2.1.4  
- OkHttp : 4.9.3  
- Gson : 2.8.9  
- WorkManager : 2.6.0  
- Biometric : 1.1.0  

依赖终端公司提供的定制 SDK (`.jar`)，并通过 **PAC 刷机** 将应用提升为系统级权限。通过 `ga.mdm.DeviceInfoManager` 类封装系统接口调用逻辑。  
运行前置指令：由于项目需通过反射或私有接口访问系统底层数据，每次调试或运行前，请连接设备并执行以下ADB命令，以允许调用隐藏API：  
```bash
# 解除隐藏 API 调用限制
adb shell settings put global hidden_api_policy 1
```

注：真实ip地址在utils/ServerConfig.kt里修改。  

### 二、服务端 (credential-server)  
服务侧 (Java) 开发环境  
- JDK 版本 : 17  
- Spring Boot : 2.6.4  
- 数据库 : MySQL (mysql-connector-java 8.0.28)  
- ORM : MyBatis-Plus 3.5.1  
- 加密库 : Bouncy Castle ( bcprov-jdk15on 1.70)  
- 核心框架 : Spring Web

### 三、认证器 (custom-authenticator)  
（1）keycloak打包：  
使用maven编译基于quarkus的keycloak源码（https://github.com/keycloak/keycloak），生成可直接运行的发行版keycloak-999.0.0-SNAPSHOT。可直接通过keycloak-999.0.0-SNAPSHOT\bin目录运行命令：  
```bash
kc.bat start-dev --hostname=localhost --http-host=0.0.0.0
```
以开启认证器，访问http://localhost:8080/admin登录后台管理页面（用户名：admin，密码：admin）。   
（2）custom-authenticator根目录打包：  
```bash
mvn clean compile
mvn clean package
```
jar包存放于keycloak发行版的providers路径下作为自定义认证流，并且登录myrealm后台进行认证流程配置。  

### 四、相关配置 
·mycustomtheme内含login和admin主题，放在keycloak发行版的themes路径下，并且登录myrealm后台进行主题配置。  
