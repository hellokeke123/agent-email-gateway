package com.agentgateway.controller;

import com.agentgateway.dto.TutorialResponse;
import com.agentgateway.service.SkillService;
import com.agentgateway.util.BaseUrlResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class ApiSkillController {

    private final SkillService skillService;
    private final BaseUrlResolver baseUrlResolver;

    /** 教 agent 安装 skill 的提示词 */
    @GetMapping("/tutorial")
    public TutorialResponse tutorial(HttpServletRequest request) {
        return TutorialResponse.builder()
                .tutorial(skillService.tutorialMarkdown(baseUrlResolver.resolve(request)))
                .build();
    }

    /** 下载 skill zip 包 */
    @GetMapping(value = "/download")
    public ResponseEntity<byte[]> download() {
        byte[] zip = skillService.buildZip();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"agent-gateway.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(zip);
    }
}
