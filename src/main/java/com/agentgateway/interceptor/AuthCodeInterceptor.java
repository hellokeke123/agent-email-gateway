package com.agentgateway.interceptor;

import com.agentgateway.entity.AuthCode;
import com.agentgateway.entity.Role;
import com.agentgateway.exception.ApiException;
import com.agentgateway.service.AuthService;
import com.agentgateway.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 校验 X-Auth-Code，并把绑定的角色与授权码放入 request attribute */
@Component
@RequiredArgsConstructor
public class AuthCodeInterceptor implements HandlerInterceptor {

    private final AuthService authService;
    private final RoleService roleService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String code = request.getHeader("X-Auth-Code");
        if (code == null || code.isBlank()) {
            throw ApiException.authCodeInvalid();
        }
        AuthCode authCode = authService.validateOperationCode(code.trim());
        Role role = roleService.getEnabledOrThrow(authCode.getRoleId());
        request.setAttribute("boundAuthCode", authCode);
        request.setAttribute("boundRole", role);
        return true;
    }
}
