package com.agentgateway.controller;

import com.agentgateway.dto.RoleDto;
import com.agentgateway.entity.Role;
import com.agentgateway.service.RoleService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class ApiRoleController {

    private final RoleService roleService;

    /** 列出其它已启用角色（只读名称，Agent 不可操作） */
    @GetMapping
    public Map<String, List<RoleDto>> list(HttpServletRequest request) {
        Role bound = (Role) request.getAttribute("boundRole");
        List<RoleDto> others = roleService.listEnabled().stream()
                .filter(r -> !r.getId().equals(bound.getId()))
                .map(RoleDto::from)
                .toList();
        return Map.of("roles", others);
    }
}
