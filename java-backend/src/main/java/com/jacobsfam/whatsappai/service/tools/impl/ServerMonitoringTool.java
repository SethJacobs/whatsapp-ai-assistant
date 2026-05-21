package com.jacobsfam.whatsappai.service.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;

@Component
@Slf4j
public class ServerMonitoringTool implements Tool {

    @Autowired
    private ObjectMapper objectMapper;

    private static final List<String> CRITICAL_CONTAINERS = Arrays.asList(
        "homeassistant",
        "ai-gateway",
        "immich-server",
        "paperless-webserver",
        "whatsapp-ai-backend",
        "whatsapp-bridge",
        "nginx-proxy"
    );

    @Override
    public String getName() {
        return "check_ezra_world_infrastructure";
    }

    @Override
    public String getDescription() {
        return "Check the status of all critical home server infrastructure. " +
               "Returns health status of Docker containers including Home Assistant, AI Gateway, Immich, Paperless, and more. " +
               "Use this to monitor your environment and detect failures proactively.";
    }

    @Override
    public JsonNode getParametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.createObjectNode());
        schema.putArray("required");
        return schema;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context) {
        try {
            // Execute docker ps to get container status
            ProcessBuilder pb = new ProcessBuilder(
                "docker", "ps", "-a",
                "--format", "{{.Names}}\t{{.Status}}\t{{.State}}"
            );

            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            Map<String, ContainerStatus> containerMap = new HashMap<>();
            String line;

            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t");
                if (parts.length >= 3) {
                    String name = parts[0];
                    String status = parts[1];
                    String state = parts[2];

                    containerMap.put(name, new ContainerStatus(name, status, state));
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return ToolExecutionResult.error("Failed to check container status");
            }

            // Build status report
            StringBuilder report = new StringBuilder();
            report.append("🏠 *Ezra's World Infrastructure Status*\n\n");

            List<String> failures = new ArrayList<>();
            int healthy = 0;

            for (String criticalContainer : CRITICAL_CONTAINERS) {
                ContainerStatus status = findContainer(containerMap, criticalContainer);

                if (status == null) {
                    report.append("❌ ").append(criticalContainer).append(": NOT FOUND\n");
                    failures.add(criticalContainer + " (not found)");
                } else if (!"running".equalsIgnoreCase(status.state)) {
                    report.append("❌ ").append(status.name)
                          .append(": ").append(status.state.toUpperCase())
                          .append(" (").append(status.status).append(")\n");
                    failures.add(status.name + " (" + status.state + ")");
                } else {
                    report.append("✅ ").append(status.name).append(": Running\n");
                    healthy++;
                }
            }

            report.append("\n*Summary:* ")
                  .append(healthy).append("/").append(CRITICAL_CONTAINERS.size())
                  .append(" services healthy");

            if (!failures.isEmpty()) {
                report.append("\n\n⚠️ *CRITICAL: Failures detected!*\n")
                      .append(String.join(", ", failures));
                log.warn("Infrastructure failures detected: {}", failures);
            }

            return ToolExecutionResult.success(report.toString());

        } catch (Exception e) {
            log.error("Error checking infrastructure", e);
            return ToolExecutionResult.error("Failed to check infrastructure: " + e.getMessage());
        }
    }

    private ContainerStatus findContainer(Map<String, ContainerStatus> containerMap, String searchName) {
        // Try exact match first
        if (containerMap.containsKey(searchName)) {
            return containerMap.get(searchName);
        }

        // Try partial match
        return containerMap.values().stream()
            .filter(c -> c.name.contains(searchName) || searchName.contains(c.name))
            .findFirst()
            .orElse(null);
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("docker:read");
    }

    private static class ContainerStatus {
        String name;
        String status;
        String state;

        ContainerStatus(String name, String status, String state) {
            this.name = name;
            this.status = status;
            this.state = state;
        }
    }
}
