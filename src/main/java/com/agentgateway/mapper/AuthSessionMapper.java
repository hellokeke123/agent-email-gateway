package com.agentgateway.mapper;

import com.agentgateway.entity.AuthSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSession> {

    @Select("SELECT * FROM auth_session WHERE session_id = #{sessionId}")
    AuthSession selectBySessionId(@Param("sessionId") String sessionId);

    @Update("UPDATE auth_session SET state = #{state}, updated_at = #{now} WHERE id = #{id}")
    int updateState(@Param("id") Long id, @Param("state") String state, @Param("now") LocalDateTime now);

    @Update("UPDATE auth_session SET state = #{state}, role_id = #{roleId}, auth_code_id = #{authCodeId}, updated_at = #{now} WHERE id = #{id}")
    int bind(@Param("id") Long id, @Param("state") String state,
             @Param("roleId") Long roleId, @Param("authCodeId") Long authCodeId, @Param("now") LocalDateTime now);

    /** 把已过期且仍处于待完成状态的会话标记为 expired */
    @Update("UPDATE auth_session SET state = 'expired', updated_at = #{now} "
            + "WHERE expires_at < #{now} AND state IN ('waiting_totp', 'verified')")
    int expirePast(@Param("now") LocalDateTime now);
}
