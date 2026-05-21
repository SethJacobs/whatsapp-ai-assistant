package com.jacobsfam.whatsappai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import com.jacobsfam.whatsappai.model.dto.*;
import com.jacobsfam.whatsappai.service.bridge.WhatsAppBridgeClient;
import com.jacobsfam.whatsappai.service.conversation.ConversationService;
import com.jacobsfam.whatsappai.service.gateway.GatewayClient;
import com.jacobsfam.whatsappai.service.routing.MessageRouter;
import com.jacobsfam.whatsappai.service.security.SecurityService;
import com.jacobsfam.whatsappai.service.tools.ToolExecutor;
import com.jacobsfam.whatsappai.service.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/webhook")
@Slf4j
public class WebhookController {

    @Autowired
    private WhatsAppBridgeClient bridgeClient;

    @Autowired
    private MessageRouter messageRouter;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private GatewayClient gatewayClient;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.jacobsfam.whatsappai.service.MessageProcessingService messageProcessingService;

    @PostMapping("/message")
    public ResponseEntity<Void> handleIncomingMessage(@RequestBody WhatsAppMessage message) {
        boolean isGroup = Boolean.TRUE.equals(message.getIsGroup());
        String source = isGroup ? "group " + message.getGroupId() : message.getFrom();
        log.info("Received message from {}: {}", source, message.getText());

        // Process async with proper transaction management
        // This prevents LazyInitializationException by keeping Hibernate session open
        messageProcessingService.processMessageAsync(message);

        return ResponseEntity.ok().build();
    }

    // Public so MessageProcessingService can call it within transaction
    public String processMessage(WhatsAppMessage message) {
        String messageText = message.getText();
        boolean isGroup = Boolean.TRUE.equals(message.getIsGroup());

        // Determine chat ID and sender
        String chatId = isGroup ? message.getGroupId() : message.getFrom();
        String senderPhone = isGroup ? message.getParticipant() : message.getFrom();

        // Get chat context (authorization + permissions)
        com.jacobsfam.whatsappai.model.ChatContext chatContext =
            securityService.getChatContext(chatId, senderPhone);

        if (chatContext == null) {
            // Not authorized - silently ignore
            log.warn("Unauthorized access attempt from {} (group: {}) - ignoring message",
                    senderPhone, isGroup);
            return null;
        }

        // Check if read-only mode
        if (chatContext.isReadOnly()) {
            log.info("Read-only group {} - processing for context but not responding", chatId);
            // Process message for context/learning but don't send response
            processForContext(message, chatContext);
            return null; // Don't send response
        }

        // Create execution context
        ExecutionContext context = ExecutionContext.builder()
            .userPhone(senderPhone)
            .sessionId(UUID.randomUUID().toString())
            .groupId(isGroup ? message.getGroupId() : null)
            .isGroup(isGroup)
            .build();

        // Route message (command vs LLM)
        MessageRouter.RouteResult routeResult = messageRouter.route(messageText, context);

        // Handle command
        if (routeResult.isCommand()) {
            return routeResult.getResponse();
        }

        // Handle routing error
        if (routeResult.isError()) {
            return routeResult.getResponse();
        }

        // Handle LLM (natural language query)
        return processLlmMessage(message, context);
    }

    private String processLlmMessage(WhatsAppMessage message, ExecutionContext context) {
        String userPhone = message.getFrom();
        String messageText = message.getText();

        // Get or create conversation
        List<ChatMessage> history = conversationService.getConversationHistory(userPhone);

        // Add user message to history
        ChatMessage userMessage = ChatMessage.user(messageText);
        history.add(userMessage);

        // Create chat request with tools
        ChatCompletionRequest request = ChatCompletionRequest.builder()
            .messages(history)
            .tools(toolRegistry.getToolDefinitionsForGateway())
            .build();

        // Send to gateway
        ChatCompletionResponse gatewayResponse = gatewayClient.chat(request);

        // Check if LLM wants to use tools
        ChatMessage assistantMessage = gatewayResponse.getFirstMessage();
        if (assistantMessage.getToolCalls() != null && !assistantMessage.getToolCalls().isEmpty()) {
            return handleToolCalls(message, history, gatewayResponse, context);
        }

        // No tools - save conversation and return response
        conversationService.saveConversation(userPhone, history);
        conversationService.addMessage(userPhone, assistantMessage);

        return assistantMessage.getContent();
    }

    private String handleToolCalls(WhatsAppMessage message, List<ChatMessage> history,
                                   ChatCompletionResponse gatewayResponse, ExecutionContext context) {
        String userPhone = message.getFrom();

        // Add assistant's tool_calls message to history
        ChatMessage assistantMessage = gatewayResponse.getFirstMessage();
        history.add(assistantMessage);

        // Execute each tool and add results to history
        for (ToolCall toolCall : assistantMessage.getToolCalls()) {
            String toolName = toolCall.getFunction().getName();
            String argumentsJson = toolCall.getFunction().getArguments();

            log.info("Executing tool: {} with args: {}", toolName, argumentsJson);

            // Get tool from registry
            Tool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                log.error("Unknown tool requested by LLM: {}", toolName);
                ChatMessage errorResponse = ChatMessage.tool(
                    toolCall.getId(),
                    toolName,
                    "Error: Unknown tool"
                );
                history.add(errorResponse);
                continue;
            }

            // Parse arguments
            Map<String, Object> arguments;
            try {
                arguments = objectMapper.readValue(argumentsJson, Map.class);
            } catch (Exception e) {
                log.error("Failed to parse tool arguments: {}", argumentsJson, e);
                ChatMessage errorResponse = ChatMessage.tool(
                    toolCall.getId(),
                    toolName,
                    "Error: Invalid arguments format"
                );
                history.add(errorResponse);
                continue;
            }

            // Update context with tool call ID
            ExecutionContext toolContext = context.toBuilder()
                .toolCallId(toolCall.getId())
                .build();

            // Execute tool
            ToolExecutionResult result = toolExecutor.execute(tool, arguments, toolContext);

            // Convert result to tool response message
            String resultContent = result.isSuccess()
                ? result.getOutput()
                : "Error: " + result.getErrorMessage();

            ChatMessage toolResponse = ChatMessage.tool(toolCall.getId(), toolName, resultContent);
            history.add(toolResponse);

            log.info("Tool {} executed with result: {}", toolName, result.isSuccess() ? "success" : "error");
        }

        // Send conversation with tool results back to gateway for final response
        ChatCompletionRequest followUpRequest = ChatCompletionRequest.builder()
            .messages(history)
            .tools(toolRegistry.getToolDefinitionsForGateway())
            .build();

        ChatCompletionResponse finalResponse = gatewayClient.chat(followUpRequest);
        ChatMessage finalMessage = finalResponse.getFirstMessage();

        // Save full conversation including tool calls
        conversationService.saveConversation(userPhone, history);
        conversationService.addMessage(userPhone, finalMessage);

        return finalMessage.getContent();
    }

    /**
     * Process message from read-only groups for context building.
     * Saves the message to conversation history but doesn't generate a response.
     */
    private void processForContext(WhatsAppMessage message,
                                   com.jacobsfam.whatsappai.model.ChatContext chatContext) {
        String chatId = chatContext.getChatId();
        String senderName = message.getName() != null ? message.getName() : "Unknown";
        String messageText = message.getText();

        // Format message with sender name for group context
        String contextMessage = String.format("[%s]: %s", senderName, messageText);

        // Save to conversation history
        ChatMessage userMessage = ChatMessage.user(contextMessage);
        conversationService.addMessage(chatId, userMessage);

        log.debug("Saved read-only message from group {} for context", chatContext.getDisplayName());
    }
}
