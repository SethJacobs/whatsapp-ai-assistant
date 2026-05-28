package com.jacobsfam.whatsappai.model.heartbeat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration loaded from HEARTBEAT.md file.
 * Contains task definitions and global settings.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatConfig {

    /**
     * List of heartbeat tasks to execute
     */
    private List<HeartbeatTask> tasks = new ArrayList<>();

    /**
     * Global active hours (e.g., "06:00-23:00")
     * Tasks without their own activeHours use this default
     */
    @JsonProperty("activeHours")
    private String activeHours = "06:00-23:00";

    /**
     * Timezone for active hours (e.g., "America/New_York")
     */
    private String timezone = "America/New_York";

    /**
     * Instructions for the LLM (markdown content after YAML frontmatter)
     */
    private String instructions;

    /**
     * Whether heartbeat system is enabled
     * Can be used to temporarily disable without deleting file
     */
    private boolean enabled = true;
}
