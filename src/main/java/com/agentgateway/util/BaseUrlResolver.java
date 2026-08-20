package com.agentgateway.util;

import com.agentgateway.config.AppProperties;
import com.agentgateway.service.AppConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 推导对外公开地址：优先 Web 设置，再使用 app.base-url，最后按请求推导 */
@Component
@RequiredArgsConstructor
public class BaseUrlResolver {

    private final AppConfigService appConfigService;
    private final com.agentgateway.config.AppProperties props;

    public String resolve(HttpServletRequest request) {
        String webOverride = appConfigService.getBaseUrl();
        if (webOverride != null && !webOverride.isBlank()) {
            return webOverride;
        }
        String configured = props.getBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return AppConfigService.normalizeBaseUrl(configured);
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String portPart = ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))
                ? "" : ":" + port;
        String contextPath = request.getContextPath();
        return scheme + "://" + host + portPart + (contextPath == null ? "" : contextPath.replaceAll("/+$", ""));
    }

    public String configuredFallback() {
        String configured = props.getBaseUrl();
        return configured == null || configured.isBlank() ? null : AppConfigService.normalizeBaseUrl(configured);
    }
}
