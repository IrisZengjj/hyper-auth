package com.keycloak.credentialserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField; // 必须导入
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 身份凭证实体类（对应数据库identity_proof表）
 */

@Data
@TableName("identity_proof") // 绑定数据库表名
public class IdentityProof {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String phoneNumberHash;
    private String deviceFingerprintHash;
    private String finalCredentialHash;
    private LocalDateTime creationTimestamp;
    
    // 核心修复：显式指定数据库字段名=user_id
    @TableField("user_id") 
    private String userId; 
    
    private LocalDateTime lastUpdateTimestamp;
}

