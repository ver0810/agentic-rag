package com.agenticrag.controller;

import com.agenticrag.ingestion.dto.IngestionTaskDTO;
import com.agenticrag.ingestion.service.IngestionTaskService;
import com.agenticrag.user.auth.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ingestion/tasks")
public class IngestionTaskController {

    private final IngestionTaskService ingestionTaskService;

    public IngestionTaskController(IngestionTaskService ingestionTaskService) {
        this.ingestionTaskService = ingestionTaskService;
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<IngestionTaskDTO> getTask(@PathVariable String taskId,
                                                    @CurrentUser String userId) {
        return ResponseEntity.ok(ingestionTaskService.getTask(taskId, userId));
    }

    @PostMapping("/{taskId}/retry")
    public ResponseEntity<Map<String, String>> retry(@PathVariable String taskId,
                                                     @CurrentUser String userId) {
        String newTaskId = ingestionTaskService.retryTask(taskId, userId);
        return ResponseEntity.accepted().body(Map.of(
                "message", "Task retry queued",
                "taskId", newTaskId));
    }
}
