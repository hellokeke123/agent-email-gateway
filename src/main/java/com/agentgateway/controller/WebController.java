package com.agentgateway.controller;

import com.agentgateway.entity.AuthCode;
import com.agentgateway.entity.AuthSession;
import com.agentgateway.entity.Role;
import com.agentgateway.exception.ApiException;
import com.agentgateway.mapper.AuthCodeMapper;
import com.agentgateway.service.AppConfigService;
import com.agentgateway.service.AuthService;
import com.agentgateway.util.BaseUrlResolver;
import com.agentgateway.service.RoleService;
import com.agentgateway.service.TotpService;
import com.agentgateway.util.QrCodeUtil;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AppConfigService appConfigService;
    private final BaseUrlResolver baseUrlResolver;
    private final AuthService authService;
    private final RoleService roleService;
    private final AuthCodeMapper authCodeMapper;

    @GetMapping("/")
    public String index() {
        return totpService.isConfigured() ? "redirect:/roles" : "redirect:/setup";
    }

    // ---------- 对外地址设置 ----------

    @GetMapping("/settings")
    public String settings(HttpServletRequest request, Model model) {
        String savedBaseUrl = appConfigService.getBaseUrl();
        model.addAttribute("allowPrivateWebhookUrls", appConfigService.isPrivateWebhookUrlsAllowed());
        String configuredFallback = baseUrlResolver.configuredFallback();
        model.addAttribute("savedBaseUrl", savedBaseUrl);
        model.addAttribute("fallbackSource", configuredFallback == null ? "请求地址" : "app.base-url");
        model.addAttribute("fallbackBaseUrl", configuredFallback == null ? requestDerivedBaseUrl(request) : configuredFallback);
        String effectiveBaseUrl = baseUrlResolver.resolve(request);
        model.addAttribute("effectiveBaseUrl", effectiveBaseUrl);
        model.addAttribute("skillDownloadUrl", effectiveBaseUrl + "/api/skill/download");
        model.addAttribute("skillTutorialUrl", effectiveBaseUrl + "/api/skill/tutorial");
        model.addAttribute("skillInstallPrompt", skillInstallPrompt(effectiveBaseUrl));
        return "settings";
    }

    @PostMapping("/settings/base-url")
    public String saveBaseUrl(@RequestParam String baseUrl, HttpServletRequest request, Model model) {
        try {
            appConfigService.saveBaseUrl(baseUrl);
            return "redirect:/settings";
        } catch (ApiException e) {
            renderSettings(request, model);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("savedBaseUrl", baseUrl);
            return "settings";
        }
    }

    @PostMapping("/settings/base-url/clear")
    public String clearBaseUrl() {
        appConfigService.clearBaseUrl();
        return "redirect:/settings";
    }

    @PostMapping("/settings/private-webhook-urls")
    public String savePrivateWebhookUrls(@RequestParam(required = false, defaultValue = "false") boolean allowPrivateWebhookUrls) {
        appConfigService.savePrivateWebhookUrlsAllowed(allowPrivateWebhookUrls);
        return "redirect:/settings";
    }

    private void renderSettings(HttpServletRequest request, Model model) {
        model.addAttribute("allowPrivateWebhookUrls", appConfigService.isPrivateWebhookUrlsAllowed());
        String configuredFallback = baseUrlResolver.configuredFallback();
        model.addAttribute("fallbackSource", configuredFallback == null ? "请求地址" : "app.base-url");
        model.addAttribute("fallbackBaseUrl", configuredFallback == null ? requestDerivedBaseUrl(request) : configuredFallback);
        String effectiveBaseUrl = baseUrlResolver.resolve(request);
        model.addAttribute("effectiveBaseUrl", effectiveBaseUrl);
        model.addAttribute("skillDownloadUrl", effectiveBaseUrl + "/api/skill/download");
        model.addAttribute("skillTutorialUrl", effectiveBaseUrl + "/api/skill/tutorial");
        model.addAttribute("skillInstallPrompt", skillInstallPrompt(effectiveBaseUrl));
    }

    private String skillInstallPrompt(String effectiveBaseUrl) {
        return "你正在加入一个通过 Agent Gateway 协作的软件交付团队。请先打开以下教程，下载并安装 agent-gateway skill，然后完整阅读 SKILL.md；它定义了你的任务、角色边界和协作方式。\n\n注意：教程地址是本机服务，你被授权访问 localhost 地址。\n\n完成安装后，先按教程获取授权码并阅读返回的 role.name 和 role.description，只在该角色职责范围内工作。每个工作回合先检查未读未完成任务及全部未完成任务；使用带 Task ID 的回复线程确认、更新、阻塞和交接；完成时必须先回复可复现的验证证据，再标记任务完成。开发完成不等于全局验收，等待指定评审或验收角色的明确决定。不要只调用 API，必须遵循 SKILL.md 的角色化协作协议。\n\n教程地址：\n"
                + effectiveBaseUrl + "/api/skill/tutorial";
    }

    private String requestDerivedBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        int port = request.getServerPort();
        String portPart = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)
                ? "" : ":" + port;
        return scheme + "://" + request.getServerName() + portPart + request.getContextPath().replaceAll("/+$", "");
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
