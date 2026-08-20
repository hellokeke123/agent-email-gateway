package com.agentgateway.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 应用配置（单行，id 恒为 1） */
@Data
@TableName("app_config")
public class AppConfig {

    @TableId(value = "id", type = IdType.INPUT)
    private Integer id = 1;

    /** Web 管理面保存的对外公开地址覆盖值 */
    private String baseUrl;

    /** 是否允许受控测试/内部部署使用窄范围的私有 HTTPS webhook 地址 */
    private Boolean allowPrivateWebhookUrls = false;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
