package com.jacobsfam.whatsappai.model.heartbeat;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;

/**
 * Represents a single heartbeat task with scheduling and notification settings.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatTask {

    /**
     * Unique name for the task (e.g., "docker-health")
     */
    private String name;

    /**
     * How often this task should run (e.g., 30m, 2h, 1d)
     * Parsed into Duration by the config loader
     */
    @JsonProperty("interval")
    private String intervalString;

    /**
     * Parsed interval as Duration
     * Ignored by JSON parser (computed from intervalString)
     */
    @JsonIgnore
    private Duration interval;

    /**
     * The prompt/instruction to send to the LLM for this task
     */
    private String prompt;

    /**
     * Notification channel (alexa, whatsapp, both, none)
     * Defaults to WHATSAPP if not specified
     */
    @JsonProperty("notify")
    private NotifyChannel notifyChannel = NotifyChannel.WHATSAPP;

    /**
     * Active hours for this task (e.g., "08:00-22:00")
     * If null, uses global active hours from config
     */
    @JsonProperty("activeHours")
    private String activeHours;

    /**
     * Optional severity level (urgent, info)
     * Used for logging/metrics
     */
    private String severity;

    /**
     * Optional model override (e.g., "haiku" for cheaper checks)
     * If null, uses gateway's default model
     */
    private String model;
}
