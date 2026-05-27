package com.jacobsfam.whatsappai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacobsfam.whatsappai.model.ChatContext;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for async message processing with proper transaction management.
 * Contains all business logic for processing WhatsApp messages.
 */
@Service
@Slf4j
public class MessageProcessingService {

    @Autowired
    private WhatsAppBridgeClient bridgeClient;

    @Autowired
    private SecurityService securityService;

    @Autowired
    private ConversationService conversationService;

    @Autowired
    private MessageRouter messageRouter;

    @Autowired
    private GatewayClient gatewayClient;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.jacobsfam.whatsappai.service.memory.MemoryExtractionService memoryExtractionService;

    /**
     * Process message asynchronously with proper transaction management.
     * The @Transactional annotation ensures Hibernate session is available
     * throughout the async execution, preventing LazyInitializationException.
     */
    @Async
    @Transactional
    public void processMessageAsync(WhatsAppMessage message) {
        boolean isGroup = Boolean.TRUE.equals(message.getIsGroup());
        String source = isGroup ? "group " + message.getGroupId() : message.getFrom();

        try {
            String responseText = processMessage(message);

            // Only send response if not null (null = unauthorized or read-only)
            if (responseText != null) {
                String recipient = isGroup ? message.getGroupId() : message.getFrom();
                bridgeClient.sendMessage(recipient, responseText);
            }

        } catch (Exception e) {
            log.error("Error processing message from {}", source, e);

            // Only send sanitized error messages to authorized users
            String sender = isGroup ? message.getParticipant() : message.getFrom();
            if (securityService.isPhoneAllowed(sender)) {
                String recipient = isGroup ? message.getGroupId() : message.getFrom();
                // Sanitize error - don't expose internal details
                bridgeClient.sendMessage(recipient,
                    "❌ Sorry, something went wrong processing your request.");
            }
        }
    }

    /**
     * Main message processing logic.
     */
    private String processMessage(WhatsAppMessage message) {
        String messageText = message.getText();
        boolean isGroup = Boolean.TRUE.equals(message.getIsGroup());

        // Determine chat ID and sender
        String chatId = isGroup ? message.getGroupId() : message.getFrom();
        String senderPhone = isGroup ? message.getParticipant() : message.getFrom();

        // Get chat context (authorization + permissions)
        ChatContext chatContext = securityService.getChatContext(chatId, senderPhone);

        if (chatContext == null) {
            // Not authorized - silently ignore
            log.warn("Unauthorized access attempt from {} (group: {}) - ignoring message",
                    senderPhone, isGroup);
            return null;
        }

        // Check if read-only mode
        if (chatContext.isReadOnly()) {
            log.info("Read-only group {} - processing for context but not responding", chatId);
            processForContext(message, chatContext);
            return null;
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

        // Auto-extract facts from conversation (async, non-blocking)
        List<ChatMessage> recentMessages = new ArrayList<>(history);
        recentMessages.add(assistantMessage);
        memoryExtractionService.extractAndStore(recentMessages);

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

        // Send conversation with tool results back to gateway for next response
        ChatCompletionRequest followUpRequest = ChatCompletionRequest.builder()
            .messages(history)
            .tools(toolRegistry.getToolDefinitionsForGateway())
            .build();

        ChatCompletionResponse followUpResponse = gatewayClient.chat(followUpRequest);
        ChatMessage followUpMessage = followUpResponse.getFirstMessage();

        // Check if LLM wants to call MORE tools (multi-turn tool calling)
        if (followUpMessage.getToolCalls() != null && !followUpMessage.getToolCalls().isEmpty()) {
            log.info("LLM requesting additional tools - continuing multi-turn execution");
            // Recursively handle more tool calls
            return handleToolCalls(message, history, followUpResponse, context);
        }

        // No more tool calls - save final conversation and return response
        conversationService.saveConversation(userPhone, history);
        conversationService.addMessage(userPhone, followUpMessage);

        // Auto-extract facts from conversation (async, non-blocking)
        List<ChatMessage> recentMessages = new ArrayList<>(history);
        recentMessages.add(followUpMessage);
        memoryExtractionService.extractAndStore(recentMessages);

        return followUpMessage.getContent();
    }

    /**
     * Process message from read-only groups for context building.
     */
    private void processForContext(WhatsAppMessage message, ChatContext chatContext) {
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
