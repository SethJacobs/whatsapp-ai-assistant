package com.jacobsfam.whatsappai.service.bridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
@Slf4j
public class WhatsAppBridgeClient {

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public WhatsAppBridgeClient(
            @Value("${bridge.base-url}") String baseUrl,
            ObjectMapper objectMapper) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
            .build();
    }

    public void sendMessage(String to, String message) {
        try {
            Map<String, String> payload = Map.of(
                "to", to,
                "message", message
            );

            String json = objectMapper.writeValueAsString(payload);

            Request request = new Request.Builder()
                .url(baseUrl + "/bridge/send")
                .post(RequestBody.create(json, MediaType.get("application/json")))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.error("Failed to send message: {}", response.code());
                } else {
                    log.debug("Message sent successfully to {}", to);
                }
            }
        } catch (IOException e) {
            log.error("Error sending message to bridge", e);
        }
    }

    public boolean isHealthy() {
        try {
            Request request = new Request.Builder()
                .url(baseUrl + "/bridge/health")
                .get()
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            return false;
        }
    }
}
