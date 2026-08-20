package com.agentgateway.controller;

import com.agentgateway.dto.CreateWorkerRequest;
import com.agentgateway.dto.WorkerCredentialResponse;
import com.agentgateway.dto.WorkerDto;
import com.agentgateway.service.WorkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class ApiWorkerController {
    private final WorkerService workerService;
    @GetMapping public List<WorkerDto> list() { return workerService.list(); }
    @GetMapping("/{id}") public WorkerDto get(@PathVariable Long id) { return workerService.get(id); }
    @PostMapping public WorkerCredentialResponse create(@RequestBody @Valid CreateWorkerRequest request) { return workerService.create(request); }
    @PostMapping("/{id}/token") public WorkerCredentialResponse rotateToken(@PathVariable Long id) { return workerService.rotateToken(id); }
    @PostMapping("/{id}/disable") public WorkerDto disable(@PathVariable Long id) { return workerService.disable(id); }
    @PostMapping("/{id}/enable") public WorkerDto enable(@PathVariable Long id) { return workerService.enable(id); }
}
