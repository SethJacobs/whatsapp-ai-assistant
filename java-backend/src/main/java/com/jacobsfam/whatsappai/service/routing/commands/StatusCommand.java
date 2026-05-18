package com.jacobsfam.whatsappai.service.routing.commands;

import com.jacobsfam.whatsappai.model.Command;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.Set;

/**
 * /status command - Shows system status (CPU, memory, disk, uptime).
 */
@Component
public class StatusCommand implements Command {

    @Override
    public String getName() {
        return "status";
    }

    @Override
    public String getDescription() {
        return "Show system status";
    }

    @Override
    public String getUsage() {
        return "/status";
    }

    @Override
    public String execute(String args, ExecutionContext context) {
        StringBuilder response = new StringBuilder();
        response.append("📊 *System Status*\n\n");

        // Uptime
        String uptime = executeCommand("uptime");
        if (uptime != null) {
            response.append("*Uptime:*\n```\n").append(uptime).append("\n```\n\n");
        }

        // Memory
        String memory = executeCommand("free -h");
        if (memory != null) {
            response.append("*Memory:*\n```\n").append(memory).append("\n```\n\n");
        }

        // Disk
        String disk = executeCommand("df -h /");
        if (disk != null) {
            response.append("*Disk (root):*\n```\n").append(disk).append("\n```\n\n");
        }

        // CPU temp (Pi-specific, will fail gracefully on other systems)
        String temp = executeCommand("vcgencmd measure_temp");
        if (temp != null && !temp.contains("not found")) {
            response.append("*CPU Temperature:*\n```\n").append(temp).append("\n```");
        }

        return response.toString();
    }

    private String executeCommand(String command) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                StringBuilder output = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                process.waitFor();
                return output.toString().trim();
            }
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("system:read");
    }
}
