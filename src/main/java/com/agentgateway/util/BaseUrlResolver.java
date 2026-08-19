package com.agentgateway.util;

import com.agentgateway.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 推导对外公开地址：优先 app.base-url 配置，否则按请求推导 */
@Component
@RequiredArgsConstructor
public class BaseUrlResolver {

    private final AppProperties props;

    public String resolve(HttpServletRequest request) {
        String configured = props.getBaseUrl();
        if (configured != null && !configured.isBlank()) {
            return configured.replaceAll("/+$", "");
        }
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();
        String portPart = ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443))
                ? "" : ":" + port;
        return scheme + "://" + host + portPart;
    }
}
