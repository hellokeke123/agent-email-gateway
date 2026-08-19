package com.agentgateway.mapper;

import com.agentgateway.entity.AuthCode;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AuthCodeMapper extends BaseMapper<AuthCode> {

    @Select("SELECT * FROM auth_code WHERE code = #{code}")
    AuthCode selectByCode(@Param("code") String code);

    /** 将某角色所有有效码置为失效并撤销（签发新码前调用；配合 uk_role_active 唯一索引兜底） */
    @Update("UPDATE auth_code SET is_active = 0, revoked = 1, revoked_at = #{now}, revoked_reason = #{reason} "
            + "WHERE role_id = #{roleId} AND is_active = 1")
    int revokeActiveCodes(@Param("roleId") Long roleId, @Param("now") LocalDateTime now, @Param("reason") String reason);

    /** 清扫：把已过期且未撤销的码置为撤销（EXPIRED） */
    @Update("UPDATE auth_code SET is_active = 0, revoked = 1, revoked_at = #{now}, revoked_reason = 'EXPIRED' "
            + "WHERE revoked = 0 AND expires_at < #{now}")
    int expireAllPast(@Param("now") LocalDateTime now);
}
