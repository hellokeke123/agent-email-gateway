package com.agentgateway.interceptor;

import com.agentgateway.entity.Worker;
import com.agentgateway.service.WorkerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class WorkerTokenInterceptor implements HandlerInterceptor {
    private final WorkerService workerService;
    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = request.getHeader("X-Worker-Token");
        if (token == null || token.isBlank()) token = request.getHeader("Authorization") != null && request.getHeader("Authorization").startsWith("Bearer ") ? request.getHeader("Authorization").substring(7) : null;
        Worker worker = workerService.authenticate(token);
        request.setAttribute("boundWorker", worker);
        return true;
    }
}
