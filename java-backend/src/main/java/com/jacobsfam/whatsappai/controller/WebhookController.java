package com.jacobsfam.whatsappai.controller;

import com.jacobsfam.whatsappai.model.dto.WhatsAppMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Thin HTTP layer for receiving WhatsApp messages from the bridge.
 * All business logic is in MessageProcessingService.
 */
@RestController
@RequestMapping("/webhook")
@Slf4j
public class WebhookController {

    @Autowired
    private com.jacobsfam.whatsappai.service.MessageProcessingService messageProcessingService;

    @PostMapping("/message")
    public ResponseEntity<Void> handleIncomingMessage(@RequestBody WhatsAppMessage message) {
        boolean isGroup = Boolean.TRUE.equals(message.getIsGroup());
        String source = isGroup ? "group " + message.getGroupId() : message.getFrom();
        log.info("Received message from {}: {}", source, message.getText());

        // Process async with proper transaction management
        // This prevents LazyInitializationException by keeping Hibernate session open
        messageProcessingService.processMessageAsync(message);

        return ResponseEntity.ok().build();
    }
}
