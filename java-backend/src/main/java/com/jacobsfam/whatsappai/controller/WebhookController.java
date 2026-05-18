package com.jacobsfam.whatsappai.controller;

import com.jacobsfam.whatsappai.model.dto.MessageResponse;
import com.jacobsfam.whatsappai.model.dto.WhatsAppMessage;
import com.jacobsfam.whatsappai.service.bridge.WhatsAppBridgeClient;
import com.jacobsfam.whatsappai.service.routing.MessageRouter;
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

    @Autowired
    private MessageRouter messageRouter;

    @PostMapping("/message")
    public ResponseEntity<Void> handleIncomingMessage(@RequestBody WhatsAppMessage message) {
        log.info("Received message from {}: {}", message.getFrom(), message.getText());

        // Process async to avoid blocking webhook
        CompletableFuture.runAsync(() -> {
            try {
                MessageResponse response = messageRouter.route(message);

                String responseText = response.isSuccess()
                    ? response.getText()
                    : (response.getError() != null ? response.getError() : "Unknown error");

                bridgeClient.sendMessage(message.getFrom(), responseText);
            } catch (Exception e) {
                log.error("Error processing message", e);
                bridgeClient.sendMessage(message.getFrom(),
                    "❌ Error: " + e.getMessage());
            }
        });

        return ResponseEntity.ok().build();
    }

}
