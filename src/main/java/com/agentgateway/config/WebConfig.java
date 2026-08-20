package com.agentgateway.config;

import com.agentgateway.interceptor.AuthCodeInterceptor;
import com.agentgateway.interceptor.TotpVerifiedInterceptor;
import com.agentgateway.interceptor.WorkerTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final TotpVerifiedInterceptor totpVerifiedInterceptor;
    private final AuthCodeInterceptor authCodeInterceptor;
    private final WorkerTokenInterceptor workerTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Web 管理页门禁
        registry.addInterceptor(totpVerifiedInterceptor)
                .addPathPatterns("/roles/**", "/settings/**", "/select", "/auth-complete");
        // API 授权码校验
        registry.addInterceptor(authCodeInterceptor)
                .addPathPatterns("/api/messages/**", "/api/roles");
        // Worker lifecycle credentials only protect worker task and event endpoints.
        registry.addInterceptor(workerTokenInterceptor)
                .addPathPatterns("/api/worker/tasks/**", "/api/worker/events/**");
        // Human management API shares the verified-TOTP boundary used by the web console.
        registry.addInterceptor(totpVerifiedInterceptor)
                .addPathPatterns("/api/workers/**");
    }
}
