package com.jacobsfam.whatsappai.service.routing.commands;

import com.jacobsfam.whatsappai.model.Command;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Set;

/**
 * /docker command - Shows Docker container status.
 */
@Component
public class DockerCommand implements Command {

    @Override
    public String getName() {
        return "docker";
    }

    @Override
    public String getDescription() {
        return "Show Docker containers";
    }

    @Override
    public String getUsage() {
        return "/docker [ps|all]";
    }

    @Override
    public String execute(String args, ExecutionContext context) {
        boolean showAll = args.trim().equalsIgnoreCase("all");

        String command = showAll ? "docker ps -a" : "docker ps";
        String output = executeCommand(command);

        if (output == null) {
            return "❌ Failed to execute docker command. Is Docker running?";
        }

        StringBuilder response = new StringBuilder();
        response.append("🐳 *Docker Containers*");
        if (showAll) {
            response.append(" (including stopped)");
        }
        response.append("\n\n");
        response.append("```\n").append(output).append("\n```");

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
        return Set.of("docker:read");
    }
}
