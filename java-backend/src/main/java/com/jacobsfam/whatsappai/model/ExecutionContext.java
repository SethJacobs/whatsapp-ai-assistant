package com.jacobsfam.whatsappai.model;

import lombok.Builder;
import lombok.Data;

/**
 * Context information for tool execution.
 * Provides security and session context to tools.
 */
@Data
@Builder(toBuilder = true)
public class ExecutionContext {

    /**
     * Phone number of the user requesting tool execution.
     */
    private String userPhone;

    /**
     * Session/conversation ID.
     */
    private String sessionId;

    /**
     * Optional: Tool call ID from LLM (for multi-turn tracking).
     */
    private String toolCallId;

    /**
     * Optional: Group ID if this is a group chat (e.g., 123456789-1234567890@g.us).
     */
    private String groupId;

    /**
     * Whether this is a group chat.
     */
    private boolean isGroup;
}
