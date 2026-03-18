Credential Server 功能说明

本 credential-server 是一个基于 Spring Boot 的后端服务，其核心功能是安全地接收来自终端的加密凭证数据，并在服务端完成多层哈希融合与数据库持久化。



主要功能点

1\. 数据解密

服务器提供一个 POST API /api/upload，用于接收终端上传的加密数据。



采用非对称加密（RSA）和对称加密（AES）相结合的混合加密方案。终端使用服务器的公钥加密一个临时的AES密钥，然后用该AES密钥加密真实数据。



服务器使用预先配置的 RSA 私钥 对 AES 密钥进行解密。



解密后的 AES 密钥用于对终端上传的加密数据进行解密，恢复原始的 JSON 格式数据。



2\. 核心凭证处理

服务端会从解密后的 JSON 数据中提取原始的硬件信息、软件信息和 SIM 卡信息。



从中识别并提取核心凭证，如手机号 (phoneNumber) 和 IMSI (imsi)。



3\. 多层哈希融合

服务器在接收到数据后，在服务端重新执行哈希和凭证融合过程。



动态盐值注入： 在服务端生成一个动态的盐值，用于增强哈希过程的安全性。



多层哈希：



使用 SHA-256 哈希算法对手机号进行哈希，生成 phoneNumberHash。



使用 SHA-256 哈希算法对终端上传的设备指纹进行哈希，生成 deviceFingerprintHash。



将 phoneNumberHash、deviceFingerprintHash 和动态盐值融合在一起，再次进行 SHA-256 哈希，生成最终的 finalCredentialHash。



4\. 数据库存储

完成哈希融合后，服务器将 phoneNumberHash、deviceFingerprintHash 和 finalCredentialHash 这三项关键凭证存储到 MySQL 数据库中。



IdentityProofRepository 接口负责与数据库进行交互，实现了凭证数据的持久化。



与 Keycloak 的关系

本 credential-server 承担了凭证预处理和存储的职责，作为 keycloak-main 认证流程的 前置模块。



credential-server 不直接与 Keycloak 认证服务器进行通信。



它负责生成并存储身份凭证的哈希值，这些哈希值将作为未来 keycloak-main 模块中认证流程的依据，用于验证用户的身份。

