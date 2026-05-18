package com.jacobsfam.whatsappai.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tool_executions")
@Data
@NoArgsConstructor
public class ToolExecution {

    @Id
    private String id = UUID.randomUUID().toString();

    private String sessionId;

    @Column(nullable = false)
    private String toolName;

    @Column(columnDefinition = "TEXT")
    private String arguments;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(nullable = false)
    private String status; // success, error, timeout

    private Integer exitCode;

    @Column(nullable = false)
    private LocalDateTime executedAt = LocalDateTime.now();

    private Long durationMs;

    public ToolExecution(String toolName, String arguments) {
        this.toolName = toolName;
        this.arguments = arguments;
    }
}
