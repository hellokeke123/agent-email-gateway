package com.agentgateway.service;

import com.agentgateway.entity.Task;
import com.agentgateway.entity.TaskEvent;
import com.agentgateway.entity.Worker;
import com.agentgateway.entity.WorkerCredential;
import com.agentgateway.mapper.TaskEventMapper;
import com.agentgateway.mapper.TaskMapper;
import com.agentgateway.mapper.WorkerCredentialMapper;
import com.agentgateway.mapper.WorkerMapper;
import com.agentgateway.util.WebhookUrlValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskDispatchService {
    private static final int MAX_ATTEMPTS = 8;
    private static final int MAX_RESPONSE_BYTES = 8192;
    private final TaskEventMapper eventMapper;
    private final TaskMapper taskMapper;
    private final WorkerMapper workerMapper;
    private final WorkerCredentialMapper credentialMapper;
    private final WebhookUrlValidator webhookUrlValidator;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.task-dispatch-delay-ms:5000}")
    public void dispatchDueEvents() {
        List<TaskEvent> events = eventMapper.selectList(new LambdaQueryWrapper<TaskEvent>()
                .and(wrapper -> wrapper.eq(TaskEvent::getDeliveryStatus, "PENDING")
                        .or().eq(TaskEvent::getDeliveryStatus, "DISPATCHING").lt(TaskEvent::getDispatchLeaseExpiresAt, LocalDateTime.now()))
                .le(TaskEvent::getNextAttemptAt, LocalDateTime.now())
                .orderByAsc(TaskEvent::getId).last("LIMIT 50"));
        for (TaskEvent event : events) {
            String leaseToken = UUID.randomUUID().toString();
            if (!claim(event, leaseToken)) continue;
            event.setDeliveryStatus("DISPATCHING");
            event.setDispatchLeaseToken(leaseToken);
            try { dispatch(event); } catch (Exception e) { log.warn("Task event {} dispatch failed", event.getId(), e); fail(event, leaseToken, e.getMessage()); }
        }
    }

    private void dispatch(TaskEvent event) throws Exception {
        Task task = taskMapper.selectById(event.getTaskId());
        if (task == null) { fail(event, event.getDispatchLeaseToken(), "task no longer exists"); return; }
        Worker worker = workerMapper.selectOne(new LambdaQueryWrapper<Worker>().eq(Worker::getRoleId, task.getTargetRoleId()).eq(Worker::getStatus, "ACTIVE"));
        if (worker == null) { deferForConfiguration(event, event.getDispatchLeaseToken(), "no active worker for target role"); return; }
        if (worker.getWebhookUrl() == null || worker.getWebhookUrl().isBlank()) { deferForConfiguration(event, event.getDispatchLeaseToken(), "worker has no webhook URL"); return; }
        WorkerCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<WorkerCredential>().eq(WorkerCredential::getWorkerId, worker.getId()).eq(WorkerCredential::getActive, true).isNull(WorkerCredential::getRevokedAt).orderByDesc(WorkerCredential::getId).last("LIMIT 1"));
        if (credential == null) { deferForConfiguration(event, event.getDispatchLeaseToken(), "worker lacks an active credential"); return; }
        URI uri = webhookUrlValidator.validate(worker.getWebhookUrl());
        String body = serializeEvent(event);
        String timestamp = String.valueOf(System.currentTimeMillis());
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(3000); connection.setReadTimeout(5000);
        connection.setRequestMethod("POST"); connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.getBytes(StandardCharsets.UTF_8).length);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("X-Task-Event-Id", String.valueOf(event.getId()));
        connection.setRequestProperty("X-Task-Timestamp", timestamp);
        connection.setRequestProperty("X-Task-Signature", "sha256=" + hmac(credential.getWebhookSigningSecretHash(), timestamp + "." + body));
        connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
        int status = connection.getResponseCode();
        java.io.InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream != null) stream.readNBytes(MAX_RESPONSE_BYTES);
        connection.disconnect();
        if (status >= 200 && status < 300) markDelivered(event, event.getDispatchLeaseToken()); else fail(event, event.getDispatchLeaseToken(), "HTTP " + status);
    }

    private boolean claim(TaskEvent event, String leaseToken) {
        LocalDateTime now = LocalDateTime.now();
        return eventMapper.update(null, new LambdaUpdateWrapper<TaskEvent>().eq(TaskEvent::getId, event.getId())
                .and(wrapper -> wrapper.eq(TaskEvent::getDeliveryStatus, "PENDING")
                        .or().eq(TaskEvent::getDeliveryStatus, "DISPATCHING").lt(TaskEvent::getDispatchLeaseExpiresAt, now))
                .set(TaskEvent::getDeliveryStatus, "DISPATCHING").set(TaskEvent::getDispatchLeaseToken, leaseToken)
                .set(TaskEvent::getDispatchLeaseExpiresAt, now.plusMinutes(5))) == 1;
    }

    private String serializeEvent(TaskEvent event) throws JsonProcessingException {
        JsonNode data = objectMapper.readTree(event.getPayload());
        if (data == null) throw new JsonProcessingException("Task event payload must be valid JSON") { };
        return objectMapper.writeValueAsString(Map.of(
                "eventId", event.getId(), "eventType", event.getEventType(),
                "occurredAt", event.getCreatedAt().toString(), "data", data));
    }

    private void markDelivered(TaskEvent event, String leaseToken) {
        eventMapper.update(null, new LambdaUpdateWrapper<TaskEvent>().eq(TaskEvent::getId, event.getId())
                .eq(TaskEvent::getDeliveryStatus, "DISPATCHING").eq(TaskEvent::getDispatchLeaseToken, leaseToken)
                .set(TaskEvent::getDeliveryStatus, "DELIVERED").set(TaskEvent::getDeliveredAt, LocalDateTime.now())
                .set(TaskEvent::getLastError, null).set(TaskEvent::getDispatchLeaseToken, null).set(TaskEvent::getDispatchLeaseExpiresAt, null));
    }

    private void deferForConfiguration(TaskEvent event, String leaseToken, String reason) {
        eventMapper.update(null, new LambdaUpdateWrapper<TaskEvent>().eq(TaskEvent::getId, event.getId())
                .eq(TaskEvent::getDeliveryStatus, "DISPATCHING").eq(TaskEvent::getDispatchLeaseToken, leaseToken)
                .set(TaskEvent::getDeliveryStatus, "PENDING").set(TaskEvent::getLastError, reason)
                .set(TaskEvent::getNextAttemptAt, LocalDateTime.now().plusSeconds(30))
                .set(TaskEvent::getDispatchLeaseToken, null).set(TaskEvent::getDispatchLeaseExpiresAt, null));
    }

    private void fail(TaskEvent event, String leaseToken, String reason) {
        int attempts = event.getAttempts() + 1;
        LambdaUpdateWrapper<TaskEvent> update = new LambdaUpdateWrapper<TaskEvent>().eq(TaskEvent::getId, event.getId())
                .eq(TaskEvent::getDeliveryStatus, "DISPATCHING").eq(TaskEvent::getDispatchLeaseToken, leaseToken)
                .set(TaskEvent::getAttempts, attempts).set(TaskEvent::getLastError, reason == null ? "delivery failed" : reason.substring(0, Math.min(1000, reason.length())))
                .set(TaskEvent::getDispatchLeaseToken, null).set(TaskEvent::getDispatchLeaseExpiresAt, null);
        if (attempts >= MAX_ATTEMPTS) {
            update.set(TaskEvent::getDeliveryStatus, "FAILED");
            log.error("Task event {} exhausted {} delivery attempts: {}", event.getId(), MAX_ATTEMPTS, reason);
        } else update.set(TaskEvent::getDeliveryStatus, "PENDING").set(TaskEvent::getNextAttemptAt, LocalDateTime.now().plusSeconds(Math.min(3600, 1L << Math.min(12, attempts))));
        eventMapper.update(null, update);
    }

    private String hmac(String key, String value) throws Exception { Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))); }
}
