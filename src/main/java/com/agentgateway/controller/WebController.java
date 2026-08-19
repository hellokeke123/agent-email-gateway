package com.agentgateway.controller;

import com.agentgateway.entity.AuthCode;
import com.agentgateway.entity.AuthSession;
import com.agentgateway.entity.Role;
import com.agentgateway.exception.ApiException;
import com.agentgateway.mapper.AuthCodeMapper;
import com.agentgateway.service.AuthService;
import com.agentgateway.service.RoleService;
import com.agentgateway.service.TotpService;
import com.agentgateway.util.QrCodeUtil;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.Instant;

/** Thymeleaf 页面路由 */
@Controller
@RequiredArgsConstructor
public class WebController {

    private final TotpService totpService;
    private final AuthService authService;
    private final RoleService roleService;
    private final AuthCodeMapper authCodeMapper;

    @GetMapping("/")
    public String index() {
        return totpService.isConfigured() ? "redirect:/roles" : "redirect:/setup";
    }

    // ---------- TOTP 首次部署 ----------

    @GetMapping("/setup")
    public String setup(Model model) {
        if (totpService.isConfigured()) {
            model.addAttribute("alreadyConfigured", true);
        } else {
            totpService.bootstrap();
            model.addAttribute("qr", QrCodeUtil.qrDataUri(totpService.provisioningUri(), 220));
            model.addAttribute("alreadyConfigured", false);
        }
        return "setup";
    }

    @PostMapping("/setup")
    public String setupSubmit(@RequestParam String code, Model model) {
        if (totpService.isConfigured()) {
            return "redirect:/";
        }
        try {
            totpService.enable(code.trim());
            return "redirect:/";
        } catch (ApiException e) {
            model.addAttribute("qr", QrCodeUtil.qrDataUri(totpService.provisioningUri(), 220));
            model.addAttribute("alreadyConfigured", false);
            model.addAttribute("error", e.getMessage());
            return "setup";
        }
    }

    // ---------- TOTP 验证 ----------

    @GetMapping("/verify")
    public String verify(@RequestParam(required = false) String sessionId,
                         @RequestParam(required = false) String returnTo,
                         Model model) {
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("returnTo", returnTo);
        model.addAttribute("locked", totpService.isLocked());
        return "verify";
    }

    @PostMapping("/verify")
    public String verifySubmit(@RequestParam String code,
                               @RequestParam(required = false) String sessionId,
                               @RequestParam(required = false) String returnTo,
                               HttpSession httpSession,
                               Model model) {
        try {
            totpService.verify(code.trim());
        } catch (ApiException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("returnTo", returnTo);
            model.addAttribute("locked", totpService.isLocked());
            return "verify";
        }
        httpSession.setAttribute("totp_verified", Boolean.TRUE);
        httpSession.setAttribute("totp_verified_at", Instant.now());
        if (sessionId != null && !sessionId.isBlank()) {
            try {
                authService.markVerified(sessionId);
            } catch (ApiException e) {
                model.addAttribute("error", e.getMessage());
                model.addAttribute("sessionId", sessionId);
                model.addAttribute("returnTo", returnTo);
                return "verify";
            }
        }
        if (returnTo != null && !returnTo.isBlank()) {
            return "redirect:" + returnTo;
        }
        if (sessionId != null && !sessionId.isBlank()) {
            return "redirect:/select?sessionId=" + sessionId;
        }
        return "redirect:/roles";
    }

    // ---------- Agent 授权选角色 ----------

    @GetMapping("/select")
    public String select(@RequestParam String sessionId, Model model) {
        authService.getSessionOrThrow(sessionId);
        model.addAttribute("sessionId", sessionId);
        model.addAttribute("roles", roleService.listEnabled());
        return "select-role";
    }

    @PostMapping("/select")
    public String selectSubmit(@RequestParam String sessionId,
                               @RequestParam Long roleId,
                               Model model) {
        try {
            authService.bindRole(sessionId, roleId);
        } catch (ApiException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("sessionId", sessionId);
            model.addAttribute("roles", roleService.listEnabled());
            return "select-role";
        }
        return "redirect:/auth-complete?sessionId=" + sessionId;
    }

    @GetMapping("/auth-complete")
    public String authComplete(@RequestParam String sessionId, Model model) {
        AuthSession s = authService.getSessionOrThrow(sessionId);
        model.addAttribute("sessionId", sessionId);
        if (AuthSession.STATE_COMPLETED.equals(s.getState()) && s.getAuthCodeId() != null) {
            AuthCode code = authCodeMapper.selectById(s.getAuthCodeId());
            Role role = roleService.getEnabledOrThrow(s.getRoleId());
            model.addAttribute("code", code.getCode());
            model.addAttribute("expiresAt", code.getExpiresAt());
            model.addAttribute("roleName", role.getName());
        }
        return "auth-complete";
    }

    // ---------- 角色管理 ----------

    @GetMapping("/roles")
    public String roles(Model model) {
        model.addAttribute("roles", roleService.listAll());
        return "roles";
    }

    @GetMapping("/roles/add")
    public String roleAddForm() {
        return "role-form";
    }

    @PostMapping("/roles/add")
    public String roleAdd(@RequestParam String name,
                          @RequestParam(required = false) String description,
                          Model model) {
        try {
            roleService.create(name, description);
            return "redirect:/roles";
        } catch (ApiException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("roleName", name);
            model.addAttribute("roleDescription", description);
            model.addAttribute("editing", false);
            return "role-form";
        }
    }

    @GetMapping("/roles/{id}/edit")
    public String roleEditForm(@PathVariable Long id, Model model) {
        Role role = roleService.getOrThrow(id);
        model.addAttribute("role", role);
        model.addAttribute("editing", true);
        return "role-form";
    }

    @PostMapping("/roles/{id}/edit")
    public String roleEdit(@PathVariable Long id,
                           @RequestParam(required = false) String name,
                           @RequestParam(required = false) String description,
                           @RequestParam(required = false) Boolean enabled,
                           Model model) {
        try {
            roleService.update(id, name, description, enabled);
            return "redirect:/roles";
        } catch (ApiException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("role", roleService.getOrThrow(id));
            model.addAttribute("editing", true);
            return "role-form";
        }
    }

    @PostMapping("/roles/{id}/delete")
    public String roleDelete(@PathVariable Long id) {
        roleService.softDelete(id);
        return "redirect:/roles";
    }
}
