package com.jacobsfam.whatsappai.model.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Data
@NoArgsConstructor
public class Conversation {

    @Id
    private String id = UUID.randomUUID().toString();

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime lastMessageAt = LocalDateTime.now();

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("timestamp ASC")
    private List<ConversationMessage> messages = new ArrayList<>();

    public Conversation(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public void addMessage(ConversationMessage message) {
        messages.add(message);
        message.setConversation(this);
        this.lastMessageAt = LocalDateTime.now();
    }
}
