package com.jacobsfam.whatsappai.service.routing;

import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import com.jacobsfam.whatsappai.model.dto.*;
import com.jacobsfam.whatsappai.service.conversation.ConversationService;
import com.jacobsfam.whatsappai.service.gateway.GatewayClient;
import com.jacobsfam.whatsappai.service.security.SecurityService;
import com.jacobsfam.whatsappai.service.tools.ToolExecutor;
import com.jacobsfam.whatsappai.service.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class MessageRouter {

    private static final Pattern COMMAND_PATTERN = Pattern.compile("^/(\\w+)(?:\\s+(.*))?$");

    @Autowired
    private SecurityService securityService;

    @Autowired
    private GatewayClient gatewayClient;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ToolExecutor toolExecutor;

    /**
     * Route incoming message - try command parsing first, then LLM fallback.
     */
    public MessageResponse route(WhatsAppMessage message) {
        log.info("Routing message from {}: {}", message.getFrom(), message.getText());

        // 1. Security allowlist check
        if (!securityService.isAuthorized(message.getFrom())) {
            log.warn("Unauthorized message from {}", message.getFrom());
            return MessageResponse.error("🔒 Unauthorized. Contact admin to add your number.");
        }

        // 2. Try deterministic command parsing first
        Optional<MessageResponse> commandResponse = tryCommandParsing(message);
        if (commandResponse.isPresent()) {
            log.info("Handled via command parsing");
            return commandResponse.get();
        }

        // 3. Fallback to LLM consultation via gateway
        log.info("Falling back to LLM consultation");
        return consultGatewayWithTools(message);
    }

    /**
     * Try to parse as a deterministic command (e.g., /help, /status).
     */
    private Optional<MessageResponse> tryCommandParsing(WhatsAppMessage msg) {
        Matcher m = COMMAND_PATTERN.matcher(msg.getText().trim());
        if (!m.matches()) {
            return Optional.empty();
        }

        String command = m.group(1);
        String args = m.group(2);

        log.info("Matched command: {} with args: {}", command, args);

        return switch (command.toLowerCase()) {
            case "help" -> Optional.of(generateHelpMessage());
            case "status" -> Optional.of(executeSystemStatus(msg));
            case "docker" -> Optional.of(handleDockerCommand(msg, args));
            case "gateway" -> Optional.of(handleGatewayCommand(msg, args));
            default -> Optional.empty();
        };
    }

    /**
     * Generate help message with available commands.
     */
    private MessageResponse generateHelpMessage() {
        String helpText = """
            🤖 *WhatsApp AI Assistant*
            
            *Commands:*
            /help - Show this message
            /status - System status
            /docker ps - List containers
            /gateway status - Gateway health
            
            *Natural Language:*
            Just type your question normally!
            
            Examples:
            • "Show running containers"
            • "Check pi gateway status"
            • "What's the CPU usage?"
            """;
        return MessageResponse.text(helpText);
    }

    /**
     * Execute system_info tool.
     */
    private MessageResponse executeSystemStatus(WhatsAppMessage message) {
        Tool tool = toolRegistry.getTool("system_info");
        if (tool == null) {
            return MessageResponse.error("System info tool not available");
        }

        ExecutionContext ctx = ExecutionContext.builder()
                .userPhone(message.getFrom())
                .sessionId(message.getFrom())
                .build();

        ToolExecutionResult result = toolExecutor.execute(
                tool,
                Map.of(),
                ctx,
                Duration.ofSeconds(30)
        );

        if (result.isSuccess()) {
            return MessageResponse.text(result.getOutput());
        } else {
            return MessageResponse.error("❌ " + result.getErrorMessage());
        }
    }

    /**
     * Handle /docker commands.
     */
    private MessageResponse handleDockerCommand(WhatsAppMessage message, String args) {
        if (args == null || !args.trim().equalsIgnoreCase("ps")) {
            return MessageResponse.text("Usage: /docker ps");
        }

        Tool tool = toolRegistry.getTool("docker_ps");
        if (tool == null) {
            return MessageResponse.error("Docker tool not available");
        }

        ExecutionContext ctx = ExecutionContext.builder()
                .userPhone(message.getFrom())
                .sessionId(message.getFrom())
                .build();

        ToolExecutionResult result = toolExecutor.execute(
                tool,
                Map.of("show_all", false),
                ctx,
                Duration.ofSeconds(30)
        );

        if (result.isSuccess()) {
            return MessageResponse.text(result.getOutput());
        } else {
            return MessageResponse.error("❌ " + result.getErrorMessage());
        }
    }

    /**
     * Handle /gateway commands.
     */
    private MessageResponse handleGatewayCommand(WhatsAppMessage message, String args) {
        if (args == null || !args.trim().equalsIgnoreCase("status")) {
            return MessageResponse.text("Usage: /gateway status");
        }

        Tool tool = toolRegistry.getTool("gateway_status");
        if (tool == null) {
            return MessageResponse.error("Gateway status tool not available");
        }

        ExecutionContext ctx = ExecutionContext.builder()
                .userPhone(message.getFrom())
                .sessionId(message.getFrom())
                .build();

        ToolExecutionResult result = toolExecutor.execute(
                tool,
                Map.of(),
                ctx,
                Duration.ofSeconds(30)
        );

        if (result.isSuccess()) {
            return MessageResponse.text(result.getOutput());
        } else {
            return MessageResponse.error("❌ " + result.getErrorMessage());
        }
    }

    /**
     * Consult gateway with tools for natural language queries.
     */
    private MessageResponse consultGatewayWithTools(WhatsAppMessage message) {
        try {
            // Get conversation history
            List<ChatMessage> history = conversationService.getHistory(message.getFrom());

            // Add user message
            ChatMessage userMessage = ChatMessage.user(message.getText());
            history.add(userMessage);

            // Get all available tools
            List<ToolDefinition> tools = toolRegistry.getToolDefinitionsForGateway();

            // Call gateway with tools
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                    .messages(history)
                    .tools(tools)
                    .toolChoice("auto")
                    .temperature(0.7)
                    .build();

            log.debug("Calling gateway with {} messages, {} tools", history.size(), tools.size());

            ChatCompletionResponse gatewayResponse = gatewayClient.chat(request);

            // Check if model wants to call tools
            if (gatewayResponse.hasToolCalls()) {
                log.info("Model requested {} tool calls",
                        gatewayResponse.getFirstMessage().getToolCalls().size());
                return handleToolCalls(message, history, gatewayResponse);
            }

            // Direct text response (no tools)
            ChatMessage assistantMessage = gatewayResponse.getFirstMessage();

            if (assistantMessage == null) {
                throw new RuntimeException("No response from gateway");
            }

            // Save conversation
            List<ChatMessage> toSave = new ArrayList<>();
            toSave.add(userMessage);
            toSave.add(assistantMessage);
            conversationService.saveExchange(message.getFrom(), toSave);

            log.info("Gateway response: route={}, intent={}",
                    gatewayResponse.getXRoute(), gatewayResponse.getXIntent());

            return MessageResponse.text(assistantMessage.getContent());
        } catch (Exception e) {
            log.error("Error consulting gateway", e);
            return MessageResponse.error("❌ Error: " + e.getMessage());
        }
    }

    /**
     * Handle multi-turn tool calling flow.
     */
    private MessageResponse handleToolCalls(WhatsAppMessage message, List<ChatMessage> history,
                                           ChatCompletionResponse gatewayResponse) {
        try {
            // Assistant message with tool_calls
            ChatMessage assistantMessage = gatewayResponse.getFirstMessage();
            history.add(assistantMessage);

            // Execute each tool
            for (ToolCall toolCall : assistantMessage.getToolCalls()) {
                String toolName = toolCall.getFunction().getName();
                String argumentsJson = toolCall.getFunction().getArguments();

                log.info("Executing tool: {} with args: {}", toolName, argumentsJson);

                // Get tool
                Tool tool = toolRegistry.getTool(toolName);
                if (tool == null) {
                    log.error("Tool not found: {}", toolName);
                    ChatMessage toolResponse = ChatMessage.tool(
                        toolCall.getId(),
                        toolName,
                        "{\"error\": \"Tool not found: " + toolName + "\"}"
                    );
                    history.add(toolResponse);
                    continue;
                }

                // Parse arguments
                Map<String, Object> arguments = parseArguments(argumentsJson);

                // Execute tool
                ExecutionContext context = ExecutionContext.builder()
                        .userPhone(message.getFrom())
                        .sessionId(message.getFrom())
                        .toolCallId(toolCall.getId())
                        .build();

                ToolExecutionResult result = toolExecutor.execute(tool, arguments, context);

                // Create tool response message
                String resultContent = result.isSuccess() ?
                        result.getOutput() :
                        "{\"error\": \"" + result.getErrorMessage() + "\"}";

                ChatMessage toolResponse = ChatMessage.tool(
                    toolCall.getId(),
                    toolName,
                    resultContent
                );
                history.add(toolResponse);

                log.info("Tool {} executed: success={}", toolName, result.isSuccess());
            }

            // Send tool results back to gateway for final response
            List<ToolDefinition> tools = toolRegistry.getToolDefinitionsForGateway();

            ChatCompletionRequest followUpRequest = ChatCompletionRequest.builder()
                    .messages(history)
                    .tools(tools)
                    .toolChoice("auto")
                    .temperature(0.7)
                    .build();

            log.debug("Sending tool results back to gateway");

            ChatCompletionResponse finalResponse = gatewayClient.chat(followUpRequest);
            ChatMessage finalMessage = finalResponse.getFirstMessage();

            if (finalMessage == null) {
                throw new RuntimeException("No final response from gateway");
            }

            // Save entire conversation including tool calls
            history.add(finalMessage);
            conversationService.saveExchange(message.getFrom(), history);

            log.info("Tool calling complete. Final response length: {}",
                    finalMessage.getContent() != null ? finalMessage.getContent().length() : 0);

            return MessageResponse.text(finalMessage.getContent());
        } catch (Exception e) {
            log.error("Error handling tool calls", e);
            return MessageResponse.error("❌ Error executing tools: " + e.getMessage());
        }
    }

    /**
     * Parse tool arguments from JSON string to Map.
     */
    private Map<String, Object> parseArguments(String argumentsJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(argumentsJson);
            Map<String, Object> map = new HashMap<>();
            node.fields().forEachRemaining(entry -> {
                map.put(entry.getKey(),
                       entry.getValue().isTextual() ? entry.getValue().asText() :
                       entry.getValue().isBoolean() ? entry.getValue().asBoolean() :
                       entry.getValue().isNumber() ? entry.getValue().asInt() :
                       entry.getValue());
            });
            return map;
        } catch (Exception e) {
            log.error("Failed to parse tool arguments: {}", argumentsJson, e);
            return new HashMap<>();
        }
    }
}
