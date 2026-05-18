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
public class SystemInfoTool implements Tool {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "system_info";
    }

    @Override
    public String getDescription() {
        return "Get system information including CPU, memory, disk usage, and uptime";
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
            output.append("📊 System Information\n\n");

            // Memory
            String memInfo = executeCommand("free -h | grep Mem");
            if (memInfo != null) {
                String[] parts = memInfo.split("\\s+");
                if (parts.length >= 3) {
                    output.append("💾 Memory: ").append(parts[2]).append(" used / ")
                          .append(parts[1]).append(" total\n");
                }
            }

            // Disk
            String diskInfo = executeCommand("df -h / | tail -1");
            if (diskInfo != null) {
                String[] parts = diskInfo.split("\\s+");
                if (parts.length >= 5) {
                    output.append("💿 Disk: ").append(parts[2]).append(" used / ")
                          .append(parts[1]).append(" total (").append(parts[4]).append(")\n");
                }
            }

            // Uptime
            String uptime = executeCommand("uptime -p");
            if (uptime != null) {
                output.append("⏱️  Uptime: ").append(uptime.trim()).append("\n");
            }

            // CPU Temperature (Raspberry Pi specific)
            String temp = executeCommand("vcgencmd measure_temp 2>/dev/null");
            if (temp != null && temp.contains("temp=")) {
                String temperature = temp.substring(temp.indexOf("temp=") + 5);
                output.append("🌡️  CPU Temp: ").append(temperature.trim()).append("\n");
            }

            // Load average
            String loadavg = executeCommand("cat /proc/loadavg");
            if (loadavg != null) {
                String[] parts = loadavg.split("\\s+");
                if (parts.length >= 3) {
                    output.append("📈 Load: ").append(parts[0]).append(" (1m), ")
                          .append(parts[1]).append(" (5m), ")
                          .append(parts[2]).append(" (15m)\n");
                }
            }

            return ToolExecutionResult.success(output.toString());
        } catch (Exception e) {
            log.error("Error executing system_info", e);
            return ToolExecutionResult.error("Failed to get system info: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("system:read");
    }

    private String executeCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            process.waitFor();
            return output.toString().trim();
        } catch (Exception e) {
            log.debug("Command failed: {}", command, e);
            return null;
        }
    }
}
