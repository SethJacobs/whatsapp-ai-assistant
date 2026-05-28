package com.jacobsfam.whatsappai.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Records each heartbeat task execution for tracking and auditing.
 */
@Entity
@Table(name = "heartbeat_executions")
@Data
@NoArgsConstructor
public class HeartbeatExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Name of the task that was executed
     */
    @Column(nullable = false)
    private String taskName;

    /**
     * When this execution occurred
     */
    @Column(nullable = false)
    private LocalDateTime executedAt;

    /**
     * Result type: "HEARTBEAT_OK", "ALERT", "ERROR"
     */
    @Column(nullable = false)
    private String result;

    /**
     * Whether a notification was sent for this execution
     */
    @Column(nullable = false)
    private boolean notificationSent = false;

    /**
     * Full LLM response (for debugging/audit)
     */
    @Column(columnDefinition = "TEXT")
    private String llmResponse;

    /**
     * Error message if execution failed
     */
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Number of tool calls made during this execution
     */
    private int toolCallCount = 0;

    /**
     * Duration of execution in milliseconds
     */
    private Long durationMs;
}
