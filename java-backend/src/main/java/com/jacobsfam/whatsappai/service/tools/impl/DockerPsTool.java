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
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class DockerPsTool implements Tool {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "docker_ps";
    }

    @Override
    public String getDescription() {
        return "List running Docker containers with their status, names, and ports";
    }

    @Override
    public JsonNode getParametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();

        // Optional: show_all parameter
        ObjectNode showAllProp = objectMapper.createObjectNode();
        showAllProp.put("type", "boolean");
        showAllProp.put("description", "Show all containers including stopped ones");
        properties.set("show_all", showAllProp);

        schema.set("properties", properties);
        return schema;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context) {
        try {
            boolean showAll = arguments.containsKey("show_all") &&
                             (Boolean) arguments.get("show_all");

            String command = showAll ? "docker ps -a" : "docker ps";

            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            StringBuilder output = new StringBuilder();
            output.append("🐳 Docker Containers\n\n");

            String line;
            int count = 0;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }

                // Parse docker ps output
                String[] parts = line.split("\\s{2,}"); // Split on 2+ spaces
                if (parts.length >= 6) {
                    String image = parts[1];
                    String status = parts[4];
                    String name = parts[parts.length - 1];

                    String statusIcon = status.toLowerCase().contains("up") ? "✅" : "❌";
                    output.append(String.format("%s %s\n", statusIcon, name));
                    output.append(String.format("   Image: %s\n", image));
                    output.append(String.format("   Status: %s\n\n", status));
                    count++;
                }
            }

            process.waitFor();

            if (count == 0) {
                output.append("No containers found.\n");
            } else {
                output.insert(0, String.format("🐳 Docker Containers (%d)\n\n", count));
            }

            return ToolExecutionResult.success(output.toString());
        } catch (Exception e) {
            log.error("Error executing docker_ps", e);
            return ToolExecutionResult.error("Failed to list containers: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("docker:read");
    }
}
