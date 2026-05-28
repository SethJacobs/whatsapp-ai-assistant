package com.jacobsfam.whatsappai.service.heartbeat;

import com.jacobsfam.whatsappai.model.entity.HeartbeatExecution;
import com.jacobsfam.whatsappai.model.heartbeat.HeartbeatConfig;
import com.jacobsfam.whatsappai.model.heartbeat.HeartbeatTask;
import com.jacobsfam.whatsappai.repository.HeartbeatExecutionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main heartbeat scheduler that runs periodically and executes due tasks.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "heartbeat.enabled", havingValue = "true", matchIfMissing = true)
public class HeartbeatScheduler {

    @Autowired
    private HeartbeatConfigLoader configLoader;

    @Autowired
    private HeartbeatExecutor executor;

    @Autowired
    private HeartbeatExecutionRepository executionRepo;

    @Autowired
    private HeartbeatNotifier notifier;

    @Value("${heartbeat.failure-threshold:3}")
    private int failureThreshold;

    @Value("${admin.timezone:America/New_York}")
    private String timezone;

    // Track consecutive failures per task for escalation
    private final Map<String, Integer> failureCount = new ConcurrentHashMap<>();

    /**
     * Main scheduler tick - runs every 10 minutes by default.
     */
    @Scheduled(fixedRateString = "${heartbeat.scheduler-interval:600000}")
    public void tick() {
        try {
            log.debug("Heartbeat tick starting");

            HeartbeatConfig config = configLoader.getConfig();

            if (!config.isEnabled()) {
                log.debug("Heartbeat disabled in config, skipping");
                return;
            }

            // Check global active hours
            if (!isActiveHours(config.getActiveHours(), config.getTimezone())) {
                log.debug("Outside global active hours, skipping heartbeat");
                return;
            }

            LocalDateTime now = LocalDateTime.now(ZoneId.of(config.getTimezone()));

            int executedCount = 0;
            int skippedCount = 0;

            for (HeartbeatTask task : config.getTasks()) {
                if (isTaskDue(task, now, config)) {
                    executeTask(task, config);
                    executedCount++;
                } else {
                    skippedCount++;
                }
            }

            log.debug("Heartbeat tick complete: {} executed, {} skipped",
                executedCount, skippedCount);

        } catch (Exception e) {
            log.error("Heartbeat tick failed", e);
        }
    }

    /**
     * Execute a single task, with error handling and escalation.
     */
    private void executeTask(HeartbeatTask task, HeartbeatConfig config) {
        log.info("Executing heartbeat task: {}", task.getName());

        try {
            executor.execute(task, config);

            // Reset failure count on success
            failureCount.put(task.getName(), 0);

        } catch (Exception e) {
            log.error("Heartbeat task {} failed", task.getName(), e);

            // Increment failure count
            int failures = failureCount.getOrDefault(task.getName(), 0) + 1;
            failureCount.put(task.getName(), failures);

            // Record failure in database
            HeartbeatExecution execution = new HeartbeatExecution();
            execution.setTaskName(task.getName());
            execution.setExecutedAt(LocalDateTime.now());
            execution.setResult("ERROR");
            execution.setErrorMessage(e.getMessage());
            execution.setNotificationSent(false);
            executionRepo.save(execution);

            // Escalate if threshold reached
            if (failures >= failureThreshold) {
                escalateFailure(task, failures, e);
            }
        }
    }

    /**
     * Determine if a task is due to run based on its interval and last execution.
     */
    private boolean isTaskDue(HeartbeatTask task, LocalDateTime now, HeartbeatConfig config) {
        // Check task-specific active hours (overrides global)
        String activeHours = task.getActiveHours() != null
            ? task.getActiveHours()
            : config.getActiveHours();

        String taskTimezone = config.getTimezone();

        if (!isActiveHours(activeHours, taskTimezone)) {
            log.debug("Task {} outside active hours {}", task.getName(), activeHours);
            return false;
        }

        // Find last execution
        Optional<HeartbeatExecution> lastExec = executionRepo
            .findTopByTaskNameOrderByExecutedAtDesc(task.getName());

        if (lastExec.isEmpty()) {
            log.debug("Task {} has never run, scheduling now", task.getName());
            return true;  // Never executed, run now
        }

        // Calculate time since last run
        Duration timeSinceLastRun = Duration.between(
            lastExec.get().getExecutedAt(),
            now
        );

        boolean isDue = timeSinceLastRun.compareTo(task.getInterval()) >= 0;

        if (isDue) {
            log.debug("Task {} is due (last run: {}, interval: {})",
                task.getName(),
                lastExec.get().getExecutedAt(),
                task.getInterval());
        }

        return isDue;
    }

    /**
     * Check if current time is within active hours.
     * Format: "HH:mm-HH:mm" (e.g., "08:00-22:00")
     */
    private boolean isActiveHours(String activeHours, String timezoneStr) {
        if (activeHours == null || activeHours.isBlank()) {
            return true;  // No restriction
        }

        try {
            String[] parts = activeHours.split("-");
            if (parts.length != 2) {
                log.warn("Invalid active hours format: {}", activeHours);
                return true;  // Invalid format, allow execution
            }

            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());

            ZoneId zone = ZoneId.of(timezoneStr);
            LocalTime now = LocalTime.now(zone);

            // Handle overnight ranges (e.g., "22:00-06:00")
            if (end.isBefore(start)) {
                return now.isAfter(start) || now.isBefore(end);
            }

            return !now.isBefore(start) && !now.isAfter(end);

        } catch (Exception e) {
            log.warn("Error parsing active hours '{}': {}", activeHours, e.getMessage());
            return true;  // On error, allow execution
        }
    }

    /**
     * Send escalation notification after repeated failures.
     */
    private void escalateFailure(HeartbeatTask task, int consecutiveFailures, Exception lastError) {
        String escalationMessage = String.format(
            "⚠️ ESCALATION: Heartbeat task '%s' has failed %d times consecutively.\n\n" +
            "Last error: %s\n\n" +
            "The heartbeat system may need attention.",
            task.getName(),
            consecutiveFailures,
            lastError.getMessage()
        );

        log.error(escalationMessage);

        // Send escalation via both channels
        notifier.sendEscalation(escalationMessage);

        // Reset failure count after escalation to avoid spam
        failureCount.put(task.getName(), 0);
    }

    /**
     * Manual trigger for testing or immediate execution.
     */
    public void triggerTask(String taskName) {
        HeartbeatConfig config = configLoader.getConfig();

        Optional<HeartbeatTask> taskOpt = config.getTasks().stream()
            .filter(t -> t.getName().equals(taskName))
            .findFirst();

        if (taskOpt.isEmpty()) {
            throw new IllegalArgumentException("Task not found: " + taskName);
        }

        log.info("Manually triggering heartbeat task: {}", taskName);
        executeTask(taskOpt.get(), config);
    }

    /**
     * Get current status of all tasks.
     */
    public Map<String, Object> getStatus() {
        HeartbeatConfig config = configLoader.getConfig();

        return Map.of(
            "enabled", config.isEnabled(),
            "taskCount", config.getTasks().size(),
            "timezone", config.getTimezone(),
            "activeHours", config.getActiveHours(),
            "failureThreshold", failureThreshold
        );
    }
}
