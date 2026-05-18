package com.jacobsfam.whatsappai.service.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacobsfam.whatsappai.model.dto.ChatCompletionRequest;
import com.jacobsfam.whatsappai.model.dto.ChatCompletionResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class GatewayClient {

    private final OkHttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final CircuitBreaker circuitBreaker;

    public GatewayClient(
            @Value("${gateway.base-url}") String baseUrl,
            @Value("${gateway.timeout-seconds:120}") int timeoutSeconds,
            ObjectMapper objectMapper,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .build();
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("gateway");
    }

    public ChatCompletionResponse chat(ChatCompletionRequest request) {
        return circuitBreaker.executeSupplier(() -> {
            try {
                String json = objectMapper.writeValueAsString(request);

                log.debug("Gateway request: {} messages, {} tools",
                        request.getMessages() != null ? request.getMessages().size() : 0,
                        request.getTools() != null ? request.getTools().size() : 0);

                Request httpRequest = new Request.Builder()
                        .url(baseUrl + "/v1/chat/completions")
                        .post(RequestBody.create(json, MediaType.get("application/json")))
                        .build();

                try (Response response = httpClient.newCall(httpRequest).execute()) {
                    if (!response.isSuccessful()) {
                        throw new GatewayException("Gateway returned " + response.code());
                    }

                    String responseBody = response.body().string();
                    ChatCompletionResponse chatResponse = objectMapper.readValue(
                            responseBody,
                            ChatCompletionResponse.class
                    );

                    log.info("Gateway response: route={}, provider={}, intent={}, hasToolCalls={}",
                            chatResponse.getXRoute(),
                            chatResponse.getXProvider(),
                            chatResponse.getXIntent(),
                            chatResponse.hasToolCalls());

                    return chatResponse;
                }
            } catch (IOException e) {
                log.error("Gateway request failed", e);
                throw new GatewayException("Failed to communicate with gateway: " + e.getMessage(), e);
            }
        });
    }

    public boolean isHealthy() {
        try {
            Request request = new Request.Builder()
                    .url(baseUrl + "/health")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (IOException e) {
            log.debug("Gateway health check failed", e);
            return false;
        }
    }

    public CircuitBreaker.State getCircuitBreakerState() {
        return circuitBreaker.getState();
    }
}
