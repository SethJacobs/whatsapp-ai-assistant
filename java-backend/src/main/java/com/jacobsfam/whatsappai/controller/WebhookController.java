package com.jacobsfam.whatsappai.controller;

import com.jacobsfam.whatsappai.model.dto.*;
import com.jacobsfam.whatsappai.service.bridge.WhatsAppBridgeClient;
import com.jacobsfam.whatsappai.service.conversation.ConversationService;
import com.jacobsfam.whatsappai.service.gateway.GatewayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/webhook")
@Slf4j
public class WebhookController {

    @Autowired
    private WhatsAppBridgeClient bridgeClient;

    @Autowired
    private GatewayClient gatewayClient;

    @Autowired
    private ConversationService conversationService;

    @PostMapping("/message")
    public ResponseEntity<Void> handleIncomingMessage(@RequestBody WhatsAppMessage message) {
        log.info("Received message from {}: {}", message.getFrom(), message.getText());

        // Process async to avoid blocking webhook
        CompletableFuture.runAsync(() -> {
            try {
                String response = processMessage(message);
                bridgeClient.sendMessage(message.getFrom(), response);
            } catch (Exception e) {
                log.error("Error processing message", e);
                bridgeClient.sendMessage(message.getFrom(),
                    "❌ Error: " + e.getMessage());
            }
        });

        return ResponseEntity.ok().build();
    }

    private String processMessage(WhatsAppMessage message) {
        // Get conversation history
        List<ChatMessage> history = conversationService.getHistory(message.getFrom());

        // Add user message
        ChatMessage userMessage = ChatMessage.user(message.getText());
        history.add(userMessage);

        // Call gateway (no tools yet, Phase 4 will add tool registry)
        ChatCompletionRequest request = ChatCompletionRequest.builder()
                .messages(history)
                .temperature(0.7)
                .build();

        log.debug("Calling gateway with {} messages", history.size());

        ChatCompletionResponse gatewayResponse = gatewayClient.chat(request);

        // Extract response
        ChatMessage assistantMessage = gatewayResponse.getFirstMessage();

        if (assistantMessage == null) {
            throw new RuntimeException("No response from gateway");
        }

        // Save conversation
        List<ChatMessage> toSave = new ArrayList<>();
        toSave.add(userMessage);
        toSave.add(assistantMessage);
        conversationService.saveExchange(message.getFrom(), toSave);

        log.info("Gateway response: route={}, intent={}, length={}",
                gatewayResponse.getXRoute(),
                gatewayResponse.getXIntent(),
                assistantMessage.getContent() != null ? assistantMessage.getContent().length() : 0);

        return assistantMessage.getContent();
    }
}
