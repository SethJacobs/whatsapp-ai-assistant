package com.jacobsfam.whatsappai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import com.jacobsfam.whatsappai.model.dto.*;
import com.jacobsfam.whatsappai.service.bridge.WhatsAppBridgeClient;
import com.jacobsfam.whatsappai.service.conversation.ConversationService;
import com.jacobsfam.whatsappai.service.gateway.GatewayClient;
import com.jacobsfam.whatsappai.service.security.SecurityService;
import com.jacobsfam.whatsappai.service.tools.ToolExecutor;
import com.jacobsfam.whatsappai.service.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/webhook")
@Slf4j
public class WebhookController {

    @Autowired
    private WhatsAppBridgeClient bridgeClient;

    @Autowired
    private GatewayClient gatewayClient;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private ObjectMapper objectMapper;

    @PostMapping("/message")
    public ResponseEntity<Void> handleIncomingMessage(@RequestBody WhatsAppMessage message) {
        log.info("Received message from {}: {}", message.getFrom(), message.getText());

        // Process async to avoid blocking webhook
        CompletableFuture.runAsync(() -> {
            try {
                // Security check
                if (!securityService.isAuthorized(message.getFrom())) {
                    bridgeClient.sendMessage(message.getFrom(),
                        "🔒 Unauthorized. Contact admin to add your number.");
                    return;
                }

                String response = processMessage(message);
                bridgeClient.sendMessage(message.getFrom(), response);
            } catch (Exception e) {
                log.error("Error processing message", e);
                bridgeClient.sendMessage(message.getFrom(),
                    "❌ Error: " + e.getMessage());
            }
        });

        return ResponseEntity.ok().build();
    }

    private String processMessage(WhatsAppMessage message) {
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

        return assistantMessage.getContent();
    }

    private String handleToolCalls(WhatsAppMessage message, List<ChatMessage> history,
                                   ChatCompletionResponse gatewayResponse) {
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

        return finalMessage.getContent();
    }

    private Map<String, Object> parseArguments(String argumentsJson) {
        try {
            JsonNode node = objectMapper.readTree(argumentsJson);
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
