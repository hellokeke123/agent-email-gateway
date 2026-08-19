package com.agentgateway.config;

import com.agentgateway.mapper.AuthCodeMapper;
import com.agentgateway.mapper.AuthSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 定时清扫：过期会话标记 expired、过期授权码标记撤销 EXPIRED */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchedulerConfig {

    private final AuthSessionMapper sessionMapper;
    private final AuthCodeMapper codeMapper;

    @Scheduled(fixedDelay = 60_000)
    public void sweepExpired() {
        try {
            LocalDateTime now = LocalDateTime.now();
            int sessions = sessionMapper.expirePast(now);
            int codes = codeMapper.expireAllPast(now);
            if (sessions > 0 || codes > 0) {
                log.info("sweep: {} expired sessions, {} expired codes", sessions, codes);
            }
        } catch (Exception e) {
            log.error("sweep failed", e);
        }
    }
}
