package com.jacobsfam.whatsappai.service.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class AlexaAnnounceTool implements Tool {

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String homeAssistantUrl;
    private final String homeAssistantToken;

    public AlexaAnnounceTool(
            @Value("${homeassistant.url:https://ha.jacobsfamjam.dpdns.org}") String url,
            @Value("${homeassistant.token}") String token,
            ObjectMapper objectMapper) {
        this.homeAssistantUrl = url;
        this.homeAssistantToken = token;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder().build();
    }

    @Override
    public String getName() {
        return "send_alexa_notification";
    }

    @Override
    public String getDescription() {
        return "Send a voice announcement through Alexa devices (Moshe's Echo Pop and Seth's Echo). " +
               "Use for critical alerts, reminders, or important family notifications. " +
               "Examples: 'Seth, the Home Assistant container is down', 'Time for Seder!', 'Yael's exam is today'";
    }

    @Override
    public JsonNode getParametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        ObjectNode message = objectMapper.createObjectNode();
        message.put("type", "string");
        message.put("description", "The message to announce via Alexa. Be clear and concise. Alexa will speak this exactly as written.");
        properties.set("message", message);

        schema.set("properties", properties);
        schema.putArray("required").add("message");

        return schema;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context) {
        try {
            String message = (String) arguments.get("message");

            if (message == null || message.trim().isEmpty()) {
                return ToolExecutionResult.error("Message cannot be empty");
            }

            // Build Home Assistant notify/alexa_media request (correct API)
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.putArray("target")
                .add("media_player.seth_s_echo");
            requestBody.put("message", message);

            ObjectNode data = objectMapper.createObjectNode();
            data.put("type", "tts");
            requestBody.set("data", data);

            String json = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                .url(homeAssistantUrl + "/api/services/notify/alexa_media")
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .addHeader("Authorization", "Bearer " + homeAssistantToken)
                .addHeader("Content-Type", "application/json")
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String error = response.body() != null ? response.body().string() : "Unknown error";
                    log.error("Failed to send Alexa announcement: {}", error);
                    return ToolExecutionResult.error("Failed to send announcement: HTTP " + response.code());
                }

                log.info("Sent Alexa announcement: {}", message);
                return ToolExecutionResult.success(
                    "✓ Announcement sent to Alexa: \"" + message + "\""
                );
            }

        } catch (Exception e) {
            log.error("Error sending Alexa announcement", e);
            return ToolExecutionResult.error("Failed to send announcement: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of(); // Allow all family members to send Alexa announcements
    }
}
