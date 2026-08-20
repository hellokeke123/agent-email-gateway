package com.agentgateway.service;

import com.agentgateway.dto.CreateTaskRequest;
import com.agentgateway.dto.TaskDto;
import com.agentgateway.dto.TaskMutationRequest;
import com.agentgateway.entity.Task;
import com.agentgateway.entity.TaskEvent;
import com.agentgateway.entity.Worker;
import com.agentgateway.exception.ApiException;
import com.agentgateway.mapper.TaskEventMapper;
import com.agentgateway.mapper.TaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {
    private final TaskMapper taskMapper;
    private final TaskEventMapper eventMapper;
    private final ObjectMapper objectMapper;

    @Transactional
    public TaskDto create(Long sourceMessageId, Long initiatorRoleId, Long targetRoleId, CreateTaskRequest request) {
        validateForCreate(request);
        Task task = new Task();
        task.setPublicId(UUID.randomUUID().toString());
        task.setSourceMessageId(sourceMessageId);
        task.setInitiatorRoleId(initiatorRoleId);
        task.setTargetRoleId(targetRoleId);
        task.setStatus("PENDING");
        task.setTitle(request.getTitle().trim());
        task.setPayload(request.getPayload());
        task.setVersion(1);
        task.setProgress(0);
        taskMapper.insert(task);
        recordEvent(task, "TASK_CREATED");
        return toDto(task, false);
    }

    public List<TaskDto> listForWorker(Worker worker) {
        expireLeases();
        return taskMapper.selectList(new LambdaQueryWrapper<Task>().eq(Task::getTargetRoleId, worker.getRoleId())
                .orderByDesc(Task::getId)).stream().map(task -> toDto(task, false)).toList();
    }

    public TaskDto detailForWorker(String publicId, Worker worker) { return toDto(getScoped(publicId, worker), false); }

    @Transactional
    public TaskDto claim(String publicId, Worker worker, TaskMutationRequest request) {
        expireLeases();
        Task task = getScoped(publicId, worker); requireVersion(task, request);
        if (!"PENDING".equals(task.getStatus())) conflict("Task is not available to claim");
        task.setStatus("CLAIMED"); task.setClaimedByWorkerId(worker.getId()); task.setLeaseToken(UUID.randomUUID().toString()); task.setLeaseExpiresAt(lease(request));
        update(task); recordEvent(task, "TASK_CLAIMED"); return toDto(task, true);
    }

    @Transactional public TaskDto heartbeat(String id, Worker worker, TaskMutationRequest request) { Task task = leased(id, worker, request); task.setLeaseExpiresAt(lease(request)); update(task); recordEvent(task, "TASK_HEARTBEAT"); return toDto(task, true); }
    @Transactional public TaskDto release(String id, Worker worker, TaskMutationRequest request) { Task task = leased(id, worker, request); task.setStatus("PENDING"); clearLease(task); update(task); recordEvent(task, "TASK_RELEASED"); return toDto(task, false); }
    @Transactional public TaskDto progress(String id, Worker worker, TaskMutationRequest request) { Task task = leased(id, worker, request); if (request.getProgress() == null || request.getProgress() < 0 || request.getProgress() > 100) throw ApiException.badRequest("progress must be between 0 and 100"); task.setStatus("IN_PROGRESS"); task.setProgress(request.getProgress()); update(task); recordEvent(task, "TASK_PROGRESS"); return toDto(task, true); }
    @Transactional public TaskDto block(String id, Worker worker, TaskMutationRequest request) { Task task = leased(id, worker, request); if (request.getReason() == null || request.getReason().isBlank()) throw ApiException.badRequest("reason is required"); task.setStatus("BLOCKED"); task.setBlockReason(request.getReason()); clearLease(task); update(task); recordEvent(task, "TASK_BLOCKED"); return toDto(task, false); }
    @Transactional public TaskDto complete(String id, Worker worker, TaskMutationRequest request) { Task task = leased(id, worker, request); task.setStatus("COMPLETED"); task.setProgress(100); task.setResult(request.getResult()); clearLease(task); update(task); recordEvent(task, "TASK_COMPLETED"); return toDto(task, false); }

    private void expireLeases() {
        taskMapper.update(null, new LambdaUpdateWrapper<Task>().in(Task::getStatus, List.of("CLAIMED", "IN_PROGRESS"))
                .lt(Task::getLeaseExpiresAt, LocalDateTime.now()).set(Task::getStatus, "PENDING")
                .set(Task::getClaimedByWorkerId, null).set(Task::getLeaseToken, null).set(Task::getLeaseExpiresAt, null).setSql("version = version + 1"));
    }
    private Task leased(String id, Worker worker, TaskMutationRequest request) { Task task = getScoped(id, worker); requireVersion(task, request); if (!worker.getId().equals(task.getClaimedByWorkerId()) || task.getLeaseExpiresAt() == null || !task.getLeaseExpiresAt().isAfter(LocalDateTime.now()) || request.getLeaseToken() == null || !request.getLeaseToken().equals(task.getLeaseToken())) conflict("Task lease is invalid or expired"); return task; }
    private Task getScoped(String publicId, Worker worker) { Task task = taskMapper.selectOne(new LambdaQueryWrapper<Task>().eq(Task::getPublicId, publicId).eq(Task::getTargetRoleId, worker.getRoleId())); if (task == null) throw ApiException.notFound("Task not found"); return task; }
    private void requireVersion(Task task, TaskMutationRequest request) { if (!task.getVersion().equals(request.getVersion())) conflict("Task version is stale"); }
    private void update(Task task) { int version = task.getVersion(); task.setVersion(version + 1); if (taskMapper.update(task, new LambdaUpdateWrapper<Task>().eq(Task::getId, task.getId()).eq(Task::getVersion, version)) != 1) conflict("Task was modified concurrently"); }
    private void clearLease(Task task) { task.setClaimedByWorkerId(null); task.setLeaseToken(null); task.setLeaseExpiresAt(null); }
    private LocalDateTime lease(TaskMutationRequest request) { int seconds = request.getLeaseSeconds() == null ? 300 : Math.max(30, Math.min(3600, request.getLeaseSeconds())); return LocalDateTime.now().plusSeconds(seconds); }
    /** Validated before the enclosing message transaction writes any row. */
    public void validateForCreate(CreateTaskRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()) throw ApiException.badRequest("task title is required");
        if (request.getTitle().trim().length() > 500) throw ApiException.badRequest("task title must be at most 500 characters");
        if (request.getPayload() != null && request.getPayload().length() > 65535) throw ApiException.badRequest("task payload is too large");
    }
    private void recordEvent(Task task, String type) {
        TaskEvent event = new TaskEvent(); event.setTaskId(task.getId()); event.setWorkerId(task.getClaimedByWorkerId()); event.setEventType(type);
        try { event.setPayload(objectMapper.writeValueAsString(java.util.Map.of("taskPublicId", task.getPublicId(), "status", task.getStatus(), "version", task.getVersion()))); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Unable to serialize task event", e); }
        event.setDeliveryStatus("PENDING"); event.setAttempts(0); event.setNextAttemptAt(LocalDateTime.now()); eventMapper.insert(event);
    }
    private TaskDto toDto(Task task, boolean includeLease) { return TaskDto.builder().publicId(task.getPublicId()).sourceMessageId(task.getSourceMessageId()).initiatorRoleId(task.getInitiatorRoleId()).targetRoleId(task.getTargetRoleId()).status(task.getStatus()).title(task.getTitle()).payload(task.getPayload()).progress(task.getProgress()).blockReason(task.getBlockReason()).result(task.getResult()).version(task.getVersion()).leaseToken(includeLease ? task.getLeaseToken() : null).leaseExpiresAt(task.getLeaseExpiresAt()).createdAt(task.getCreatedAt()).updatedAt(task.getUpdatedAt()).build(); }
    private void conflict(String message) { throw ApiException.of(HttpStatus.CONFLICT, com.agentgateway.exception.ErrorCode.VALIDATION_ERROR, message); }
}
