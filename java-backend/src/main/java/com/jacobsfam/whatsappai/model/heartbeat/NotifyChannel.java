package com.jacobsfam.whatsappai.model.heartbeat;

/**
 * Notification channel for heartbeat alerts.
 */
public enum NotifyChannel {
    ALEXA,      // Send via Alexa announcement (urgent)
    WHATSAPP,   // Send via WhatsApp message (info)
    BOTH,       // Send via both channels
    NONE        // No notification (internal check only)
}
