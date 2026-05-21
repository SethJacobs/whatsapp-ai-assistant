package com.jacobsfam.whatsappai.service;

import com.jacobsfam.whatsappai.model.dto.WhatsAppMessage;
import com.jacobsfam.whatsappai.service.bridge.WhatsAppBridgeClient;
import com.jacobsfam.whatsappai.service.security.SecurityService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for async message processing with proper transaction management.
 * Solves LazyInitializationException by ensuring Hibernate session is available.
 */
@Service
@Slf4j
public class MessageProcessingService {

    @Autowired
    private WhatsAppBridgeClient bridgeClient;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private com.jacobsfam.whatsappai.controller.WebhookController webhookController;

    /**
     * Process message asynchronously with proper transaction management.
     * The @Transactional annotation ensures Hibernate session is available
     * throughout the async execution, preventing LazyInitializationException.
     */
    @Async
    @Transactional
    public void processMessageAsync(WhatsAppMessage message) {
        boolean isGroup = Boolean.TRUE.equals(message.getIsGroup());
        String source = isGroup ? "group " + message.getGroupId() : message.getFrom();

        try {
            String responseText = webhookController.processMessage(message);

            // Only send response if not null (null = unauthorized or read-only)
            if (responseText != null) {
                String recipient = isGroup ? message.getGroupId() : message.getFrom();
                bridgeClient.sendMessage(recipient, responseText);
            }

        } catch (Exception e) {
            log.error("Error processing message from {}", source, e);

            // Only send sanitized error messages to authorized users
            String sender = isGroup ? message.getParticipant() : message.getFrom();
            if (securityService.isPhoneAllowed(sender)) {
                String recipient = isGroup ? message.getGroupId() : message.getFrom();
                // Sanitize error - don't expose internal details
                bridgeClient.sendMessage(recipient,
                    "❌ Sorry, something went wrong processing your request.");
            }
        }
    }
}
