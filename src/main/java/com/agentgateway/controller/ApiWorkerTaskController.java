package com.agentgateway.controller;

import com.agentgateway.dto.TaskDto;
import com.agentgateway.dto.TaskMutationRequest;
import com.agentgateway.entity.Worker;
import com.agentgateway.service.TaskService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/worker/tasks")
@RequiredArgsConstructor
public class ApiWorkerTaskController {
    private final TaskService taskService;
    @GetMapping public List<TaskDto> list(HttpServletRequest request) { return taskService.listForWorker(worker(request)); }
    @GetMapping("/{publicId}") public TaskDto detail(@PathVariable String publicId, HttpServletRequest request) { return taskService.detailForWorker(publicId, worker(request)); }
    @PostMapping("/{publicId}/claim") public TaskDto claim(@PathVariable String publicId, @RequestBody @Valid TaskMutationRequest body, HttpServletRequest request) { return taskService.claim(publicId, worker(request), body); }
    @PostMapping("/{publicId}/heartbeat") public TaskDto heartbeat(@PathVariable String publicId, @RequestBody @Valid TaskMutationRequest body, HttpServletRequest request) { return taskService.heartbeat(publicId, worker(request), body); }
    @PostMapping("/{publicId}/release") public TaskDto release(@PathVariable String publicId, @RequestBody @Valid TaskMutationRequest body, HttpServletRequest request) { return taskService.release(publicId, worker(request), body); }
    @PostMapping("/{publicId}/progress") public TaskDto progress(@PathVariable String publicId, @RequestBody @Valid TaskMutationRequest body, HttpServletRequest request) { return taskService.progress(publicId, worker(request), body); }
    @PostMapping("/{publicId}/block") public TaskDto block(@PathVariable String publicId, @RequestBody @Valid TaskMutationRequest body, HttpServletRequest request) { return taskService.block(publicId, worker(request), body); }
    @PostMapping("/{publicId}/complete") public TaskDto complete(@PathVariable String publicId, @RequestBody @Valid TaskMutationRequest body, HttpServletRequest request) { return taskService.complete(publicId, worker(request), body); }
    private Worker worker(HttpServletRequest request) { return (Worker) request.getAttribute("boundWorker"); }
}
