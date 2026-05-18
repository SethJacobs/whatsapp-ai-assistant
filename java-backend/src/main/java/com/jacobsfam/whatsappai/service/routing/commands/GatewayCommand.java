package com.jacobsfam.whatsappai.service.routing.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacobsfam.whatsappai.model.Command;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * /gateway command - Shows AI gateway status.
 */
@Component
public class GatewayCommand implements Command {
    private final String gatewayBaseUrl;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient;

    @Autowired
    public GatewayCommand(@Value("${gateway.base-url}") String gatewayBaseUrl,
                         ObjectMapper objectMapper) {
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.objectMapper = objectMapper;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public String getName() {
        return "gateway";
    }

    @Override
    public String getDescription() {
        return "Show AI gateway status";
    }

    @Override
    public String getUsage() {
        return "/gateway";
    }

    @Override
    public String execute(String args, ExecutionContext context) {
        StringBuilder response = new StringBuilder();
        response.append("🌐 *AI Gateway Status*\n\n");
        response.append("*URL:* ").append(gatewayBaseUrl).append("\n\n");

        // Check health endpoint
        try {
            String healthUrl = gatewayBaseUrl + "/health";
            Request request = new Request.Builder()
                .url(healthUrl)
                .get()
                .build();

            try (Response httpResponse = httpClient.newCall(request).execute()) {
                if (httpResponse.isSuccessful() && httpResponse.body() != null) {
                    String body = httpResponse.body().string();
                    JsonNode healthJson = objectMapper.readTree(body);

                    response.append("*Health:* ✅ ").append(healthJson.path("status").asText("UP")).append("\n");

                    if (healthJson.has("uptime_seconds")) {
                        long uptimeSeconds = healthJson.path("uptime_seconds").asLong();
                        response.append("*Uptime:* ").append(formatDuration(uptimeSeconds)).append("\n");
                    }

                    if (healthJson.has("ram_usage_mb")) {
                        response.append("*RAM Usage:* ").append(healthJson.path("ram_usage_mb").asInt()).append(" MB\n");
                    }
                } else {
                    response.append("*Health:* ❌ HTTP ").append(httpResponse.code()).append("\n");
                }
            }
        } catch (Exception e) {
            response.append("*Health:* ❌ ").append(e.getMessage()).append("\n");
        }

        // Check status endpoint for available models
        try {
            String statusUrl = gatewayBaseUrl + "/status";
            Request request = new Request.Builder()
                .url(statusUrl)
                .get()
                .build();

            try (Response httpResponse = httpClient.newCall(request).execute()) {
                if (httpResponse.isSuccessful() && httpResponse.body() != null) {
                    String body = httpResponse.body().string();
                    JsonNode statusJson = objectMapper.readTree(body);

                    if (statusJson.has("available_models")) {
                        response.append("\n*Available Models:*\n");
                        JsonNode models = statusJson.path("available_models");
                        models.fields().forEachRemaining(entry -> {
                            String modelName = entry.getKey();
                            JsonNode modelInfo = entry.getValue();
                            String provider = modelInfo.path("provider").asText("");
                            response.append(String.format("  • %s (%s)\n", modelName, provider));
                        });
                    }
                }
            }
        } catch (Exception e) {
            response.append("\n❌ Failed to fetch models: ").append(e.getMessage());
        }

        return response.toString();
    }

    private String formatDuration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else {
            return String.format("%dm", minutes);
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("gateway:read");
    }
}
