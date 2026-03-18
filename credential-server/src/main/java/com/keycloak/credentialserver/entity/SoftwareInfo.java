package com.keycloak.credentialserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_collection.software_info")
public class SoftwareInfo {
    @TableId(value = "software_id", type = IdType.AUTO)
    private Long id;

    private String osName;
    private String osVersion;
    private String androidId;
    private String kernelVersion;

    @TableField("created_at")
    private LocalDateTime creationTimestamp;
}

