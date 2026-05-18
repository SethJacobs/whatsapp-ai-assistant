package com.jacobsfam.whatsappai.service.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import com.jacobsfam.whatsappai.service.gateway.GatewayClient;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class GatewayStatusTool implements Tool {

    @Autowired
    private GatewayClient gatewayClient;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${gateway.base-url}")
    private String gatewayBaseUrl;

    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    public String getName() {
        return "gateway_status";
    }

    @Override
    public String getDescription() {
        return "Check the status and health of the Pi AI Gateway including available models and routing info";
    }

    @Override
    public JsonNode getParametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        return schema;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context) {
        try {
            StringBuilder output = new StringBuilder();
            output.append("🌉 Pi AI Gateway Status\n\n");

            // Check health
            boolean healthy = gatewayClient.isHealthy();
            output.append("Health: ").append(healthy ? "✅ OK" : "❌ Down").append("\n");

            if (!healthy) {
                return ToolExecutionResult.success(output.toString());
            }

            // Get detailed status
            Request statusRequest = new Request.Builder()
                    .url(gatewayBaseUrl + "/status")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(statusRequest).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String body = response.body().string();
                    JsonNode status = objectMapper.readTree(body);

                    // Parse status info
                    if (status.has("local_model")) {
                        String localModel = status.get("local_model").asText();
                        output.append("Local Model: ").append(localModel).append("\n");
                    }

                    if (status.has("cloud_models")) {
                        int cloudModels = status.get("cloud_models").asInt();
                        output.append("Cloud Models: ").append(cloudModels).append("\n");
                    }

                    if (status.has("free_ram_mb")) {
                        int freeRam = status.get("free_ram_mb").asInt();
                        output.append("Free RAM: ").append(freeRam).append(" MB\n");
                    }

                    if (status.has("routing_default")) {
                        String routing = status.get("routing_default").asText();
                        output.append("Default Route: ").append(routing).append("\n");
                    }
                }
            }

            // Circuit breaker state
            CircuitBreaker.State cbState = gatewayClient.getCircuitBreakerState();
            output.append("Circuit Breaker: ").append(cbState).append("\n");

            output.append("\nURL: ").append(gatewayBaseUrl);

            return ToolExecutionResult.success(output.toString());
        } catch (Exception e) {
            log.error("Error executing gateway_status", e);
            return ToolExecutionResult.error("Failed to get gateway status: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("gateway:read");
    }
}
