package com.jacobsfam.whatsappai.service.heartbeat;

import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.heartbeat.HeartbeatTask;
import com.jacobsfam.whatsappai.model.heartbeat.NotifyChannel;
import com.jacobsfam.whatsappai.service.bridge.WhatsAppBridgeClient;
import com.jacobsfam.whatsappai.service.tools.ToolExecutor;
import com.jacobsfam.whatsappai.service.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Handles notifications for heartbeat alerts via WhatsApp and Alexa.
 */
@Service
@Slf4j
public class HeartbeatNotifier {

    @Autowired
    private WhatsAppBridgeClient bridgeClient;

    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private ToolRegistry toolRegistry;

    @Value("${admin.phone:}")
    private String adminPhone;

    /**
     * Send notification based on task's configured channel.
     */
    public void sendNotification(HeartbeatTask task, String message) {
        if (adminPhone == null || adminPhone.isBlank()) {
            log.warn("Admin phone not configured, cannot send notifications");
            return;
        }

        log.info("Sending heartbeat alert for task '{}' via {}",
            task.getName(), task.getNotifyChannel());

        // Clean up message - remove HEARTBEAT_OK if present (shouldn't be, but just in case)
        String cleanMessage = message.replace("HEARTBEAT_OK", "").trim();

        switch (task.getNotifyChannel()) {
            case WHATSAPP:
                sendWhatsApp(task.getName(), cleanMessage);
                break;

            case ALEXA:
                sendAlexa(cleanMessage);
                break;

            case BOTH:
                sendWhatsApp(task.getName(), cleanMessage);
                sendAlexa(cleanMessage);
                break;

            case NONE:
                log.info("Notification suppressed (notify: none)");
                break;
        }
    }

    /**
     * Send escalation notification via both channels.
     */
    public void sendEscalation(String message) {
        log.warn("ESCALATION: {}", message);

        try {
            sendWhatsApp("ESCALATION", message);
        } catch (Exception e) {
            log.error("Failed to send WhatsApp escalation", e);
        }

        try {
            sendAlexa(message);
        } catch (Exception e) {
            log.error("Failed to send Alexa escalation", e);
        }
    }

    /**
     * Send notification via WhatsApp.
     */
    private void sendWhatsApp(String taskName, String message) {
        try {
            String formattedMessage = String.format(
                "🤖 *Heartbeat Alert* (%s)\n\n%s",
                taskName,
                message
            );

            bridgeClient.sendMessage(adminPhone, formattedMessage);
            log.info("Sent WhatsApp notification for task: {}", taskName);

        } catch (Exception e) {
            log.error("Failed to send WhatsApp notification", e);
        }
    }

    /**
     * Send notification via Alexa announcement.
     */
    private void sendAlexa(String message) {
        try {
            Tool alexaTool = toolRegistry.getTool("send_alexa_notification");

            if (alexaTool == null) {
                log.warn("Alexa tool not available, skipping Alexa notification");
                return;
            }

            ExecutionContext context = ExecutionContext.builder()
                .userPhone(adminPhone)
                .sessionId("heartbeat-notification")
                .build();

            toolExecutor.execute(alexaTool, Map.of("message", message), context);
            log.info("Sent Alexa notification");

        } catch (Exception e) {
            log.error("Failed to send Alexa notification", e);
        }
    }
}
