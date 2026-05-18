package com.jacobsfam.whatsappai.model;

import lombok.Builder;
import lombok.Data;

/**
 * Context information for tool execution.
 * Provides security and session context to tools.
 */
@Data
@Builder
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
}
