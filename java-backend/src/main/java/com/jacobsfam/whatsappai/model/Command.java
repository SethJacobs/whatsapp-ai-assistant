package com.jacobsfam.whatsappai.model;

import java.util.Set;

/**
 * Interface for deterministic command handlers.
 * Commands are parsed from messages starting with "/" (e.g., "/help", "/status").
 */
public interface Command {
    /**
     * @return The command name without the "/" prefix (e.g., "help", "status")
     */
    String getName();

    /**
     * @return Human-readable description of what this command does
     */
    String getDescription();

    /**
     * @return Usage example (e.g., "/docker ps", "/status")
     */
    String getUsage();

    /**
     * Execute the command with the given arguments.
     *
     * @param args Arguments after the command (e.g., "ps" for "/docker ps")
     * @param context Execution context with user info
     * @return Command response text to send back to user
     */
    String execute(String args, ExecutionContext context);

    /**
     * @return Set of permissions required to execute this command
     */
    Set<String> getRequiredPermissions();
}
