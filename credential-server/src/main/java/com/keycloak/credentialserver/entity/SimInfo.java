package com.keycloak.credentialserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("data_collection.sim_info")
public class SimInfo {
    @TableId(value = "sim_id", type = IdType.AUTO)
    private Long id;

    private String imsi;
    private String imsi2;
    private String iccid;
    private String iccid2;
    private String phoneNumber;
    private String phoneNumber2;
    private String collectionTime;

    @TableField("created_at")
    private LocalDateTime creationTimestamp;
}

