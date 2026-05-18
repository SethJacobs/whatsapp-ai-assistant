package com.jacobsfam.whatsappai.controller;

import com.jacobsfam.whatsappai.model.dto.MessageResponse;
import com.jacobsfam.whatsappai.model.dto.WhatsAppMessage;
import com.jacobsfam.whatsappai.service.bridge.WhatsAppBridgeClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/webhook")
@Slf4j
public class WebhookController {

    @Autowired
    private WhatsAppBridgeClient bridgeClient;

    @PostMapping("/message")
    public ResponseEntity<Void> handleIncomingMessage(@RequestBody WhatsAppMessage message) {
        log.info("Received message from {}: {}", message.getFrom(), message.getText());

        // Process async to avoid blocking webhook
        CompletableFuture.runAsync(() -> {
            try {
                // TODO: Route message through MessageRouter
                // For now, just echo back
                MessageResponse response = MessageResponse.text(
                    "Echo: " + message.getText()
                );

                bridgeClient.sendMessage(message.getFrom(), response.getText());
            } catch (Exception e) {
                log.error("Error processing message", e);
            }
        });

        return ResponseEntity.ok().build();
    }
}
