package com.jacobsfam.whatsappai.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;

/**
 * Tool interface for extensible AI assistant capabilities.
 * Implementations are auto-discovered via @Component annotation.
 */
public interface Tool {

    /**
     * Tool name (must be unique).
     * Used by LLM to call the tool.
     */
    String getName();

    /**
     * Human-readable description of what the tool does.
     * Helps LLM decide when to use this tool.
     */
    String getDescription();

    /**
     * JSON Schema for tool parameters.
     * Defines the structure of arguments the tool accepts.
     */
    JsonNode getParametersSchema();

    /**
     * Execute the tool with given arguments.
     *
     * @param arguments Tool arguments as key-value pairs
     * @param context Execution context (user info, session, etc.)
     * @return Execution result
     */
    ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context);

    /**
     * Required permissions to execute this tool.
     * Format: "category:action" (e.g., "docker:read", "system:write")
     * Empty set = no special permissions required.
     */
    Set<String> getRequiredPermissions();
}
