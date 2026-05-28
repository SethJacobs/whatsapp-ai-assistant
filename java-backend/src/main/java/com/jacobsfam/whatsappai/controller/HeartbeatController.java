package com.jacobsfam.whatsappai.controller;

import com.jacobsfam.whatsappai.service.heartbeat.HeartbeatConfigLoader;
import com.jacobsfam.whatsappai.service.heartbeat.HeartbeatScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for heartbeat system management and testing.
 */
@RestController
@RequestMapping("/api/heartbeat")
@Slf4j
public class HeartbeatController {

    @Autowired
    private HeartbeatScheduler scheduler;

    @Autowired
    private HeartbeatConfigLoader configLoader;

    /**
     * Get heartbeat system status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(scheduler.getStatus());
    }

    /**
     * Manually trigger a specific task (for testing).
     */
    @PostMapping("/trigger/{taskName}")
    public ResponseEntity<Map<String, String>> triggerTask(@PathVariable String taskName) {
        try {
            log.info("Manual trigger requested for task: {}", taskName);
            scheduler.triggerTask(taskName);

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Task '" + taskName + "' triggered successfully"
            ));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));

        } catch (Exception e) {
            log.error("Failed to trigger task: {}", taskName, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Task execution failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Reload heartbeat configuration from disk.
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reloadConfig() {
        try {
            log.info("Manual config reload requested");
            configLoader.reload();

            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Configuration reloaded successfully"
            ));

        } catch (Exception e) {
            log.error("Failed to reload config", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Reload failed: " + e.getMessage()
            ));
        }
    }
}
