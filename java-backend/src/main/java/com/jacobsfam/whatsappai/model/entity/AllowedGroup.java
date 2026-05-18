package com.jacobsfam.whatsappai.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents a WhatsApp group that is allowed to interact with the assistant.
 * Groups can be read-write (assistant responds) or read-only (assistant learns but doesn't respond).
 */
@Entity
@Table(name = "allowed_groups")
@Data
@NoArgsConstructor
public class AllowedGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * WhatsApp group ID (format: 123456789-1234567890@g.us)
     */
    @Column(nullable = false, unique = true)
    private String groupId;

    /**
     * Human-readable group name
     */
    @Column
    private String groupName;

    /**
     * If true, assistant reads messages for context but doesn't respond
     * If false, assistant responds normally
     */
    @Column(nullable = false)
    private boolean readOnly = false;

    /**
     * Comma-separated permissions (e.g., "system:read,docker:read")
     * Controls what tools/commands can be used in this group
     */
    @Column(length = 1000)
    private String permissions;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public AllowedGroup(String groupId, String groupName, boolean readOnly) {
        this.groupId = groupId;
        this.groupName = groupName;
        this.readOnly = readOnly;
    }
}
