package com.agentgateway.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/** Web 管理页门禁：session 内 TOTP 已验证且在有效期内，否则重定向到 /verify */
@Component
public class TotpVerifiedInterceptor implements HandlerInterceptor {

    @Value("${app.web-session-ttl-minutes:30}")
    private long ttlMinutes;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        HttpSession session = request.getSession(false);
        boolean verified = session != null && Boolean.TRUE.equals(session.getAttribute("totp_verified"));
        if (!verified) {
            redirectToVerify(request, response);
            return false;
        }
        Object verifiedAtObj = session.getAttribute("totp_verified_at");
        Instant verifiedAt = verifiedAtObj instanceof Instant ? (Instant) verifiedAtObj : null;
        if (verifiedAt == null || Instant.now().isAfter(verifiedAt.plus(Duration.ofMinutes(ttlMinutes)))) {
            session.invalidate();
            redirectToVerify(request, response);
            return false;
        }
        return true;
    }

    private void redirectToVerify(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uri = request.getRequestURI();
        if (request.getQueryString() != null) {
            uri += "?" + request.getQueryString();
        }
        response.sendRedirect("/verify?returnTo=" + URLEncoder.encode(uri, StandardCharsets.UTF_8));
    }
}
