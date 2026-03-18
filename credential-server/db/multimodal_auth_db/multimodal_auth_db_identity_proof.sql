-- MySQL dump 10.13  Distrib 8.0.36, for Win64 (x86_64)
--
-- Host: localhost    Database: multimodal_auth_db
-- ------------------------------------------------------
-- Server version	8.0.37

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `identity_proof`
--

DROP TABLE IF EXISTS `identity_proof`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `identity_proof` (
  `id` int NOT NULL AUTO_INCREMENT,
  `phone_number_hash` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_fingerprint_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设备指纹哈希（第一层融合）',
  `final_credential_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '最终凭证哈希（最终结果）',
  `creation_timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '凭证创建时间',
  `last_update_timestamp` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '凭证最后更新时间',
  `user_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone_number_hash` (`phone_number_hash`) COMMENT '确保每个电话号码哈希的唯一性'
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='移动码号为中心的身份凭证证据链';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `identity_proof`
--

LOCK TABLES `identity_proof` WRITE;
/*!40000 ALTER TABLE `identity_proof` DISABLE KEYS */;
INSERT INTO `identity_proof` VALUES (1,'e1e93436-9a2b-44f3-81ce-42c852f08702','e4d339e2bffbb787e693a6ef2cb4a649c27fd684a74d20f171b9c674d9c93a1a','966c4778b4ccddf14bd7e8ab30b77e51073dd2f22aa60c8ee07db6890827ac97','2025-10-13 09:00:42','2025-12-30 03:06:57','753eab57-f201-4d20-b202-571d5349135e'),(2,'bbc3ee1a9e7cc555504a469887a8f94b5e5f4f8ce130cb3aa4085f22878cb227','e4d339e2bffbb787e693a6ef2cb4a649c27fd684a74d20f171b9c674d9c93a1a','52b16d17c103ef291c64f997190fd7d3b62c4107a6f930b6c25cba18a784a5df','2025-10-13 09:10:27','2025-12-30 03:06:57','32603551-3eea-4d88-ae7f-a578cc79ecc0'),(3,'fff724dd3417f6aae435d614e97b52ddd6ad23b0fbc0c143a229ab9c260d175b','e4d339e2bffbb787e693a6ef2cb4a649c27fd684a74d20f171b9c674d9c93a1a','1034733ac0df5684e5128bff0da95c7b8cf648f186f9eb2cc041ae689f4173bf','2025-10-13 09:24:00','2025-12-30 03:06:57','83c4740c-d352-4c42-9232-b0d0d8d344ce'),(4,'29850e5ae718cd074775ae60c62f7744ad0f2d03e41130111f2a8d125eaa6036','e4d339e2bffbb787e693a6ef2cb4a649c27fd684a74d20f171b9c674d9c93a1a','fd8a904b5098999790d795cbc8b0c320bcf0ce0941ccc1827f7657f214c28b20','2025-10-13 09:33:35','2025-12-30 03:06:57','46c0c905-52d4-44e9-adc1-1fd2fbea21bb'),(5,'fb9cc5d04285f53d10f1928fd414b67498ecd4252d337278d9d78987d3bd9962','e4d339e2bffbb787e693a6ef2cb4a649c27fd684a74d20f171b9c674d9c93a1a','a081caebcdb8c1629fbf598ee62a940af654e998f9fe2ba4dc27beb478323cfc','2025-10-13 09:37:18','2025-12-30 03:06:57','1f299b45-f997-4945-bb42-748b79698e77'),(6,'test_phone_hash_123','test_device_hash_456','test_new_hash_123456','2025-12-30 03:10:55','2025-12-30 03:10:55','852a46a3-d7e1-433c-843a-72ca11936a4b');
/*!40000 ALTER TABLE `identity_proof` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-12-30 17:52:32
