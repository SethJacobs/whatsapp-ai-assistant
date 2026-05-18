package com.jacobsfam.whatsappai.model;

import lombok.Builder;
import lombok.Data;

/**
 * Context for a chat (individual or group).
 * Distinguishes between different chat types and their permissions.
 */
@Data
@Builder
public class ChatContext {

    public enum ChatType {
        INDIVIDUAL,  // One-on-one chat
        GROUP        // Group chat
    }

    /**
     * Type of chat
     */
    private ChatType chatType;

    /**
     * For individual chats: phone number
     * For group chats: group ID (e.g., 123456789-1234567890@g.us)
     */
    private String chatId;

    /**
     * For group messages: the sender's phone number
     * For individual messages: same as chatId
     */
    private String senderPhone;

    /**
     * If true, assistant can read but should not respond
     */
    private boolean readOnly;

    /**
     * Permissions for this chat
     */
    private String permissions;

    /**
     * Human-readable name (group name or contact name)
     */
    private String displayName;
}
