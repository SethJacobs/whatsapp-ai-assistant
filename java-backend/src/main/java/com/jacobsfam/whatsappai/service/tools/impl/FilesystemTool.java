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
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class FilesystemTool implements Tool {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "filesystem";
    }

    @Override
    public String getDescription() {
        return "Execute filesystem and shell operations on the Pi. Supports: " +
               "running commands, reading/writing files, managing cron jobs, creating scripts. " +
               "Operations: 'exec' (run command), 'read' (read file), 'write' (write file), " +
               "'append' (append to file), 'list' (list directory), 'cron' (manage cron jobs), " +
               "'create_script' (create executable script).";
    }

    @Override
    public JsonNode getParametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        // Operation parameter
        ObjectNode operationParam = objectMapper.createObjectNode();
        operationParam.put("type", "string");
        operationParam.put("description", "Operation to perform: exec, read, write, append, list, cron, create_script");
        operationParam.set("enum", objectMapper.createArrayNode()
            .add("exec")
            .add("read")
            .add("write")
            .add("append")
            .add("list")
            .add("cron")
            .add("create_script"));
        properties.set("operation", operationParam);

        // Command parameter (for exec)
        ObjectNode commandParam = objectMapper.createObjectNode();
        commandParam.put("type", "string");
        commandParam.put("description", "Shell command to execute (for operation=exec)");
        properties.set("command", commandParam);

        // Path parameter (for file operations)
        ObjectNode pathParam = objectMapper.createObjectNode();
        pathParam.put("type", "string");
        pathParam.put("description", "File or directory path (for file operations)");
        properties.set("path", pathParam);

        // Content parameter (for write/append/create_script)
        ObjectNode contentParam = objectMapper.createObjectNode();
        contentParam.put("type", "string");
        contentParam.put("description", "Content to write (for write/append/create_script operations)");
        properties.set("content", contentParam);

        // Cron action parameter
        ObjectNode cronActionParam = objectMapper.createObjectNode();
        cronActionParam.put("type", "string");
        cronActionParam.put("description", "Cron action: list, add, remove (for operation=cron)");
        cronActionParam.set("enum", objectMapper.createArrayNode()
            .add("list")
            .add("add")
            .add("remove"));
        properties.set("cron_action", cronActionParam);

        // Cron expression parameter
        ObjectNode cronExpressionParam = objectMapper.createObjectNode();
        cronExpressionParam.put("type", "string");
        cronExpressionParam.put("description", "Cron expression (for cron_action=add), e.g., '0 * * * * /path/to/script.sh'");
        properties.set("cron_expression", cronExpressionParam);

        // Timeout parameter (optional)
        ObjectNode timeoutParam = objectMapper.createObjectNode();
        timeoutParam.put("type", "integer");
        timeoutParam.put("description", "Command timeout in seconds (default: 60, max: 300)");
        properties.set("timeout_seconds", timeoutParam);

        schema.set("properties", properties);

        // Required parameters
        schema.set("required", objectMapper.createArrayNode().add("operation"));

        return schema;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context) {
        try {
            String operation = (String) arguments.get("operation");

            if (operation == null) {
                return ToolExecutionResult.error("Operation is required");
            }

            return switch (operation.toLowerCase()) {
                case "exec" -> executeCommand(arguments);
                case "read" -> readFile(arguments);
                case "write" -> writeFile(arguments, false);
                case "append" -> writeFile(arguments, true);
                case "list" -> listDirectory(arguments);
                case "cron" -> manageCron(arguments);
                case "create_script" -> createScript(arguments);
                default -> ToolExecutionResult.error("Unknown operation: " + operation);
            };

        } catch (Exception e) {
            log.error("Error executing filesystem operation", e);
            return ToolExecutionResult.error("Filesystem operation failed: " + e.getMessage());
        }
    }

    private ToolExecutionResult executeCommand(Map<String, Object> arguments) {
        try {
            String command = (String) arguments.get("command");
            if (command == null || command.trim().isEmpty()) {
                return ToolExecutionResult.error("Command is required for exec operation");
            }

            Integer timeoutSeconds = arguments.containsKey("timeout_seconds")
                ? ((Number) arguments.get("timeout_seconds")).intValue()
                : 60;

            // Cap timeout at 5 minutes
            timeoutSeconds = Math.min(timeoutSeconds, 300);

            log.info("Executing command: {}", command);

            ProcessBuilder pb = new ProcessBuilder("bash", "-c", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Read output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return ToolExecutionResult.error("Command timed out after " + timeoutSeconds + " seconds");
            }

            int exitCode = process.exitValue();
            String result = output.toString().trim();

            if (exitCode == 0) {
                return ToolExecutionResult.success(
                    result.isEmpty() ? "Command executed successfully (no output)" : result
                );
            } else {
                return ToolExecutionResult.error(
                    "Command exited with code " + exitCode + "\n" + result
                );
            }

        } catch (Exception e) {
            log.error("Error executing command", e);
            return ToolExecutionResult.error("Command execution failed: " + e.getMessage());
        }
    }

    private ToolExecutionResult readFile(Map<String, Object> arguments) {
        try {
            String pathStr = (String) arguments.get("path");
            if (pathStr == null) {
                return ToolExecutionResult.error("Path is required for read operation");
            }

            Path path = Paths.get(pathStr);
            if (!Files.exists(path)) {
                return ToolExecutionResult.error("File does not exist: " + pathStr);
            }

            if (!Files.isRegularFile(path)) {
                return ToolExecutionResult.error("Not a file: " + pathStr);
            }

            String content = Files.readString(path);
            return ToolExecutionResult.success(content);

        } catch (Exception e) {
            log.error("Error reading file", e);
            return ToolExecutionResult.error("Failed to read file: " + e.getMessage());
        }
    }

    private ToolExecutionResult writeFile(Map<String, Object> arguments, boolean append) {
        try {
            String pathStr = (String) arguments.get("path");
            String content = (String) arguments.get("content");

            if (pathStr == null) {
                return ToolExecutionResult.error("Path is required for write operation");
            }

            if (content == null) {
                return ToolExecutionResult.error("Content is required for write operation");
            }

            Path path = Paths.get(pathStr);

            // Create parent directories if needed
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            if (append) {
                Files.writeString(path, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                return ToolExecutionResult.success("Content appended to: " + pathStr);
            } else {
                Files.writeString(path, content);
                return ToolExecutionResult.success("File written: " + pathStr);
            }

        } catch (Exception e) {
            log.error("Error writing file", e);
            return ToolExecutionResult.error("Failed to write file: " + e.getMessage());
        }
    }

    private ToolExecutionResult listDirectory(Map<String, Object> arguments) {
        try {
            String pathStr = (String) arguments.get("path");
            if (pathStr == null) {
                pathStr = System.getProperty("user.home");
            }

            Path path = Paths.get(pathStr);
            if (!Files.exists(path)) {
                return ToolExecutionResult.error("Directory does not exist: " + pathStr);
            }

            if (!Files.isDirectory(path)) {
                return ToolExecutionResult.error("Not a directory: " + pathStr);
            }

            StringBuilder output = new StringBuilder();
            output.append("📁 ").append(path.toAbsolutePath()).append("\n\n");

            Files.list(path).sorted().forEach(p -> {
                try {
                    String icon = Files.isDirectory(p) ? "📂" : "📄";
                    String size = Files.isRegularFile(p) ? " (" + Files.size(p) + " bytes)" : "";
                    output.append(icon).append(" ").append(p.getFileName()).append(size).append("\n");
                } catch (Exception e) {
                    output.append("❓ ").append(p.getFileName()).append(" (error reading)\n");
                }
            });

            return ToolExecutionResult.success(output.toString());

        } catch (Exception e) {
            log.error("Error listing directory", e);
            return ToolExecutionResult.error("Failed to list directory: " + e.getMessage());
        }
    }

    private ToolExecutionResult manageCron(Map<String, Object> arguments) {
        try {
            String action = (String) arguments.get("cron_action");
            if (action == null) {
                return ToolExecutionResult.error("cron_action is required (list, add, remove)");
            }

            return switch (action.toLowerCase()) {
                case "list" -> {
                    ProcessBuilder pb = new ProcessBuilder("crontab", "-l");
                    pb.redirectErrorStream(true);
                    Process process = pb.start();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    StringBuilder output = new StringBuilder();
                    output.append("📅 Current cron jobs:\n\n");

                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }

                    process.waitFor();
                    yield ToolExecutionResult.success(output.toString());
                }

                case "add" -> {
                    String expression = (String) arguments.get("cron_expression");
                    if (expression == null) {
                        yield ToolExecutionResult.error("cron_expression is required for add action");
                    }

                    // Get current crontab
                    Process listProcess = new ProcessBuilder("crontab", "-l").start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(listProcess.getInputStream()));
                    StringBuilder currentCrontab = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        currentCrontab.append(line).append("\n");
                    }
                    listProcess.waitFor();

                    // Add new entry
                    currentCrontab.append(expression).append("\n");

                    // Write back
                    Process writeProcess = new ProcessBuilder("crontab", "-").start();
                    writeProcess.getOutputStream().write(currentCrontab.toString().getBytes());
                    writeProcess.getOutputStream().close();
                    writeProcess.waitFor();

                    yield ToolExecutionResult.success("Cron job added: " + expression);
                }

                case "remove" -> {
                    String expression = (String) arguments.get("cron_expression");
                    if (expression == null) {
                        yield ToolExecutionResult.error("cron_expression is required for remove action");
                    }

                    // Get current crontab
                    Process listProcess = new ProcessBuilder("crontab", "-l").start();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(listProcess.getInputStream()));
                    StringBuilder newCrontab = new StringBuilder();
                    String line;
                    boolean found = false;
                    while ((line = reader.readLine()) != null) {
                        if (!line.equals(expression)) {
                            newCrontab.append(line).append("\n");
                        } else {
                            found = true;
                        }
                    }
                    listProcess.waitFor();

                    if (!found) {
                        yield ToolExecutionResult.error("Cron job not found: " + expression);
                    }

                    // Write back
                    Process writeProcess = new ProcessBuilder("crontab", "-").start();
                    writeProcess.getOutputStream().write(newCrontab.toString().getBytes());
                    writeProcess.getOutputStream().close();
                    writeProcess.waitFor();

                    yield ToolExecutionResult.success("Cron job removed: " + expression);
                }

                default -> ToolExecutionResult.error("Unknown cron action: " + action);
            };

        } catch (Exception e) {
            log.error("Error managing cron", e);
            return ToolExecutionResult.error("Cron management failed: " + e.getMessage());
        }
    }

    private ToolExecutionResult createScript(Map<String, Object> arguments) {
        try {
            String pathStr = (String) arguments.get("path");
            String content = (String) arguments.get("content");

            if (pathStr == null) {
                return ToolExecutionResult.error("Path is required for create_script operation");
            }

            if (content == null) {
                return ToolExecutionResult.error("Content is required for create_script operation");
            }

            Path path = Paths.get(pathStr);

            // Create parent directories if needed
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }

            // Ensure shebang
            if (!content.startsWith("#!")) {
                content = "#!/bin/bash\n" + content;
            }

            // Write file
            Files.writeString(path, content);

            // Make executable
            File file = path.toFile();
            file.setExecutable(true, false);

            return ToolExecutionResult.success("Executable script created: " + pathStr);

        } catch (Exception e) {
            log.error("Error creating script", e);
            return ToolExecutionResult.error("Failed to create script: " + e.getMessage());
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        // This is a powerful tool - requires admin permissions
        return Set.of("admin:*");
    }
}
