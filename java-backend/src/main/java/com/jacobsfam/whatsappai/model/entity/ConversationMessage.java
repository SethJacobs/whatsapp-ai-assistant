package com.jacobsfam.whatsappai.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversation_messages")
@Data
@NoArgsConstructor
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(nullable = false)
    private String role; // user, assistant, system, tool

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String toolCalls; // JSON serialized

    private String toolCallId;

    private String name; // For tool messages

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    public ConversationMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }
}
