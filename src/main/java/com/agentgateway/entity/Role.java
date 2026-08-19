package com.agentgateway.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 虚拟角色，消息的收发实体（原设计中的"邮箱"被角色替代） */
@Data
@TableName("app_role")
public class Role {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 角色名，如"角色A" */
    private String name;

    private String description;

    private Boolean enabled = true;

    /** 软删除标记 */
    private Boolean deleted = false;

    private LocalDateTime deletedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
