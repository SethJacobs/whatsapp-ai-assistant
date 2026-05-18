package com.jacobsfam.whatsappai.service.routing;

import com.jacobsfam.whatsappai.model.Command;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.service.security.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Routes incoming messages to either deterministic command handlers or LLM processing.
 * Commands start with "/" and are parsed deterministically.
 * All other messages are sent to the LLM via gateway.
 */
@Service
public class MessageRouter {
    private static final Logger logger = LoggerFactory.getLogger(MessageRouter.class);
    private static final Pattern COMMAND_PATTERN = Pattern.compile("^/(\\w+)(?:\\s+(.*))?$");

    private final Map<String, Command> commands = new HashMap<>();
    private final SecurityService securityService;

    @Autowired
    public MessageRouter(SecurityService securityService,
                        @Autowired(required = false) List<Command> commandBeans) {
        this.securityService = securityService;
        if (commandBeans != null) {
            commandBeans.forEach(cmd -> commands.put(cmd.getName().toLowerCase(), cmd));
        }
    }

    @PostConstruct
    public void init() {
        logger.info("Registered {} commands: {}",
            commands.size(),
            commands.keySet().stream().sorted().collect(Collectors.joining(", ")));
    }

    /**
     * Determine if a message is a command or should go to LLM.
     *
     * @param messageText The incoming message text
     * @return true if this is a command, false if it should go to LLM
     */
    public boolean isCommand(String messageText) {
        if (messageText == null || messageText.trim().isEmpty()) {
            return false;
        }
        return messageText.trim().startsWith("/");
    }

    /**
     * Route a message to the appropriate handler.
     *
     * @param messageText The incoming message text
     * @param context Execution context with user info
     * @return Response text to send back to user
     */
    public RouteResult route(String messageText, ExecutionContext context) {
        if (messageText == null || messageText.trim().isEmpty()) {
            return RouteResult.error("Empty message");
        }

        String trimmed = messageText.trim();

        // Check if it's a command
        if (trimmed.startsWith("/")) {
            return handleCommand(trimmed, context);
        }

        // Not a command - should go to LLM
        return RouteResult.llm();
    }

    /**
     * Parse and execute a command.
     */
    private RouteResult handleCommand(String messageText, ExecutionContext context) {
        Matcher matcher = COMMAND_PATTERN.matcher(messageText);

        if (!matcher.matches()) {
            return RouteResult.error("Invalid command format. Commands should start with / followed by a command name.");
        }

        String commandName = matcher.group(1).toLowerCase();
        String args = matcher.group(2) != null ? matcher.group(2).trim() : "";

        // Find command
        Command command = commands.get(commandName);
        if (command == null) {
            return RouteResult.error(String.format(
                "Unknown command: /%s\n\nTry /help to see available commands.",
                commandName
            ));
        }

        // Check permissions
        Set<String> required = command.getRequiredPermissions();
        if (!required.isEmpty() && !securityService.hasPermissions(context.getUserPhone(), required)) {
            return RouteResult.error(String.format(
                "Permission denied for command /%s\n\nRequired permissions: %s",
                commandName,
                String.join(", ", required)
            ));
        }

        // Execute command
        try {
            String response = command.execute(args, context);
            return RouteResult.command(response);
        } catch (Exception e) {
            logger.error("Error executing command /{}: {}", commandName, e.getMessage(), e);
            return RouteResult.error(String.format(
                "Error executing command /%s: %s",
                commandName,
                e.getMessage()
            ));
        }
    }

    /**
     * Get all registered commands (for /help).
     */
    public List<Command> getAllCommands() {
        return new ArrayList<>(commands.values());
    }

    /**
     * Result of routing a message.
     */
    public static class RouteResult {
        public enum Type { COMMAND, LLM, ERROR }

        private final Type type;
        private final String response;

        private RouteResult(Type type, String response) {
            this.type = type;
            this.response = response;
        }

        public static RouteResult command(String response) {
            return new RouteResult(Type.COMMAND, response);
        }

        public static RouteResult llm() {
            return new RouteResult(Type.LLM, null);
        }

        public static RouteResult error(String errorMessage) {
            return new RouteResult(Type.ERROR, errorMessage);
        }

        public Type getType() { return type; }
        public String getResponse() { return response; }
        public boolean isCommand() { return type == Type.COMMAND; }
        public boolean isLlm() { return type == Type.LLM; }
        public boolean isError() { return type == Type.ERROR; }
    }
}
