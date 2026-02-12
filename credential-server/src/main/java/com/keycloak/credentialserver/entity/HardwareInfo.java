package com.keycloak.credentialserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_collection.hardware_info")
public class HardwareInfo {
    @TableId(value = "hardware_id", type = IdType.AUTO)
    private Long id;

    private String deviceName;
    private String model;
    private String serialNumber;
    private String imeiPrimary;
    private String imeiSecondary;
    private String cpuArchitecture;
    private String memorySize;
    private String basebandVersion;

    @TableField("created_at")
    private LocalDateTime creationTimestamp;
}

