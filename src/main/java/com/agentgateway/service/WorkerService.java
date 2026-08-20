package com.agentgateway.service;

import com.agentgateway.dto.CreateWorkerRequest;
import com.agentgateway.dto.WorkerCredentialResponse;
import com.agentgateway.dto.WorkerDto;
import com.agentgateway.entity.Worker;
import com.agentgateway.entity.WorkerCredential;
import com.agentgateway.exception.ApiException;
import com.agentgateway.mapper.WorkerCredentialMapper;
import com.agentgateway.mapper.WorkerMapper;
import com.agentgateway.service.RoleService;
import com.agentgateway.util.WebhookUrlValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkerService {
    private final WorkerMapper workerMapper;
    private final WorkerCredentialMapper credentialMapper;
    private final RoleService roleService;
    private final WebhookUrlValidator webhookUrlValidator;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public WorkerCredentialResponse create(CreateWorkerRequest request) {
        roleService.getEnabledOrThrow(request.getRoleId());
        if (workerMapper.selectCount(new LambdaQueryWrapper<Worker>().eq(Worker::getRoleId, request.getRoleId())) > 0) {
            throw conflict();
        }
        Worker worker = new Worker();
        worker.setRoleId(request.getRoleId());
        worker.setName(request.getName().trim());
        worker.setStatus("ACTIVE");
        worker.setWebhookUrl(normalizeWebhookUrl(request.getWebhookUrl()));
        try {
            workerMapper.insert(worker);
        } catch (DuplicateKeyException e) {
            throw conflict();
        }
        IssuedCredential issued = issueToken(worker);
        return WorkerCredentialResponse.builder().worker(toDto(worker)).token(issued.token()).webhookSigningSecret(issued.webhookSigningSecret()).build();
    }

    public List<WorkerDto> list() {
        return workerMapper.selectList(new LambdaQueryWrapper<Worker>().orderByDesc(Worker::getId)).stream().map(this::toDto).toList();
    }
    public WorkerDto get(Long id) { return toDto(getAny(id)); }

    @Transactional
    public WorkerCredentialResponse rotateToken(Long id) {
        Worker worker = getAny(id);
        credentialMapper.update(null, new LambdaUpdateWrapper<WorkerCredential>().eq(WorkerCredential::getWorkerId, id)
                .eq(WorkerCredential::getActive, true).set(WorkerCredential::getActive, false)
                .set(WorkerCredential::getRevokedAt, LocalDateTime.now()));
        IssuedCredential issued = issueToken(worker);
        return WorkerCredentialResponse.builder().worker(toDto(worker)).token(issued.token()).webhookSigningSecret(issued.webhookSigningSecret()).build();
    }

    public WorkerDto disable(Long id) { Worker w = getAny(id); w.setStatus("DISABLED"); w.setDisabledAt(LocalDateTime.now()); workerMapper.updateById(w); return toDto(w); }
    public WorkerDto enable(Long id) { Worker w = getAny(id); roleService.getEnabledOrThrow(w.getRoleId()); w.setStatus("ACTIVE"); w.setDisabledAt(null); workerMapper.updateById(w); return toDto(w); }

    public Worker authenticate(String token) {
        if (token == null || token.isBlank()) throw unauthorized("Worker token is required");
        WorkerCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<WorkerCredential>()
                .eq(WorkerCredential::getTokenHash, hash(token.trim())).eq(WorkerCredential::getActive, true));
        if (credential == null || credential.getRevokedAt() != null || (credential.getExpiresAt() != null && credential.getExpiresAt().isBefore(LocalDateTime.now()))) throw unauthorized("Worker token is invalid");
        Worker worker = getAny(credential.getWorkerId());
        if (!"ACTIVE".equals(worker.getStatus())) throw ApiException.of(HttpStatus.FORBIDDEN, com.agentgateway.exception.ErrorCode.AUTH_CODE_INVALID, "Worker is disabled");
        return worker;
    }

    private IssuedCredential issueToken(Worker worker) {
        byte[] tokenBytes = new byte[32]; secureRandom.nextBytes(tokenBytes);
        byte[] secretBytes = new byte[32]; secureRandom.nextBytes(secretBytes);
        String token = "wkr_" + Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String webhookSigningSecret = "whsec_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secretBytes);
        WorkerCredential credential = new WorkerCredential(); credential.setWorkerId(worker.getId()); credential.setTokenHash(hash(token)); credential.setWebhookSigningSecretHash(hash(webhookSigningSecret)); credential.setTokenPrefix(token.substring(0, 12)); credential.setActive(true); credentialMapper.insert(credential);
        return new IssuedCredential(token, webhookSigningSecret);
    }
    private record IssuedCredential(String token, String webhookSigningSecret) { }
    private String normalizeWebhookUrl(String value) { return value == null || value.isBlank() ? null : webhookUrlValidator.validate(value.trim()).toString(); }
    private Worker getAny(Long id) { Worker worker = workerMapper.selectById(id); if (worker == null) throw ApiException.notFound("Worker not found"); return worker; }
    private WorkerDto toDto(Worker w) { return WorkerDto.builder().id(w.getId()).roleId(w.getRoleId()).name(w.getName()).status(w.getStatus()).webhookUrl(w.getWebhookUrl()).createdAt(w.getCreatedAt()).build(); }
    private String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    private String hash(byte[] value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); } catch (Exception e) { throw new IllegalStateException(e); } }
    private ApiException conflict() { return ApiException.of(HttpStatus.CONFLICT, com.agentgateway.exception.ErrorCode.WORKER_CONFLICT, "A worker is already bound to this role"); }
    private ApiException unauthorized(String message) { return ApiException.of(HttpStatus.UNAUTHORIZED, com.agentgateway.exception.ErrorCode.AUTH_CODE_INVALID, message); }
}
