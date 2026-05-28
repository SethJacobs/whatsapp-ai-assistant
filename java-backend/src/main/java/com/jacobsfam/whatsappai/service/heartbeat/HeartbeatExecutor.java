package com.jacobsfam.whatsappai.service.heartbeat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import com.jacobsfam.whatsappai.model.dto.*;
import com.jacobsfam.whatsappai.model.entity.HeartbeatExecution;
import com.jacobsfam.whatsappai.model.heartbeat.HeartbeatConfig;
import com.jacobsfam.whatsappai.model.heartbeat.HeartbeatTask;
import com.jacobsfam.whatsappai.repository.HeartbeatExecutionRepository;
import com.jacobsfam.whatsappai.service.gateway.GatewayClient;
import com.jacobsfam.whatsappai.service.tools.ToolExecutor;
import com.jacobsfam.whatsappai.service.tools.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Executes individual heartbeat tasks using isolated LLM calls.
 */
@Service
@Slf4j
public class HeartbeatExecutor {

    @Autowired
    private GatewayClient gatewayClient;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ToolExecutor toolExecutor;

    @Autowired
    private HeartbeatNotifier notifier;

    @Autowired
    private HeartbeatExecutionRepository executionRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${admin.phone:}")
    private String adminPhone;

    /**
     * Execute a heartbeat task.
     */
    public void execute(HeartbeatTask task, HeartbeatConfig config) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Executing heartbeat task: {} (interval: {})",
                task.getName(), task.getInterval());

            // Build isolated LLM request (no conversation history)
            List<ChatMessage> messages = new ArrayList<>();

            // System message with heartbeat instructions
            ChatMessage systemMsg = new ChatMessage();
            systemMsg.setRole("system");
            systemMsg.setContent(config.getInstructions());
            messages.add(systemMsg);

            // User message with task prompt
            ChatMessage userMsg = new ChatMessage();
            userMsg.setRole("user");
            userMsg.setContent(task.getPrompt());
            messages.add(userMsg);

            // Create request with tools
            ChatCompletionRequest.ChatCompletionRequestBuilder requestBuilder =
                ChatCompletionRequest.builder()
                    .messages(messages)
                    .tools(toolRegistry.getToolDefinitionsForGateway())
                    .temperature(0.3);  // Lower temp for consistent checks

            // Use specific model if configured
            if (task.getModel() != null) {
                requestBuilder.model(task.getModel());
            }

            ChatCompletionRequest request = requestBuilder.build();

            // Call gateway
            ChatCompletionResponse response = gatewayClient.chat(request);

            // Handle tool calls if present
            String finalResponse = handleToolCalls(response, messages, task);

            // Check if HEARTBEAT_OK
            boolean isOk = finalResponse.toUpperCase().contains("HEARTBEAT_OK");

            long duration = System.currentTimeMillis() - startTime;

            // Record execution
            HeartbeatExecution execution = new HeartbeatExecution();
            execution.setTaskName(task.getName());
            execution.setExecutedAt(LocalDateTime.now());
            execution.setResult(isOk ? "HEARTBEAT_OK" : "ALERT");
            execution.setLlmResponse(finalResponse);
            execution.setNotificationSent(!isOk);
            execution.setDurationMs(duration);
            executionRepo.save(execution);

            // Send notification if not OK
            if (!isOk) {
                log.info("Heartbeat task {} generated alert", task.getName());
                notifier.sendNotification(task, finalResponse);
            } else {
                log.debug("Heartbeat task {} returned OK", task.getName());
            }

        } catch (Exception e) {
            log.error("Failed to execute heartbeat task: {}", task.getName(), e);
            throw new RuntimeException("Heartbeat task execution failed", e);
        }
    }

    /**
     * Handle tool calls in heartbeat context (similar to message processing but isolated).
     */
    private String handleToolCalls(ChatCompletionResponse response,
                                   List<ChatMessage> history,
                                   HeartbeatTask task) {

        ChatMessage assistantMessage = response.getFirstMessage();

        // No tool calls - return content directly
        if (assistantMessage.getToolCalls() == null || assistantMessage.getToolCalls().isEmpty()) {
            return assistantMessage.getContent() != null ? assistantMessage.getContent() : "";
        }

        // Add assistant's tool_calls message to history
        history.add(assistantMessage);

        int toolCallCount = 0;

        // Execute each tool call
        for (ToolCall toolCall : assistantMessage.getToolCalls()) {
            String toolName = toolCall.getFunction().getName();
            String argumentsJson = toolCall.getFunction().getArguments();

            log.info("Heartbeat task {} calling tool: {}", task.getName(), toolName);

            // Get tool from registry
            Tool tool = toolRegistry.getTool(toolName);
            if (tool == null) {
                log.error("Unknown tool requested: {}", toolName);
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
                    "Error: Invalid arguments format - " + e.getMessage()
                );
                history.add(errorResponse);
                continue;
            }

            // Create execution context for heartbeat
            ExecutionContext context = ExecutionContext.builder()
                .userPhone(adminPhone)
                .sessionId("heartbeat-" + task.getName())
                .toolCallId(toolCall.getId())
                .isGroup(false)
                .build();

            // Execute tool
            ToolExecutionResult result = toolExecutor.execute(tool, arguments, context);

            // Convert result to tool response message
            String resultContent = result.isSuccess()
                ? result.getOutput()
                : "Error: " + result.getErrorMessage();

            ChatMessage toolResponse = ChatMessage.tool(toolCall.getId(), toolName, resultContent);
            history.add(toolResponse);

            toolCallCount++;

            log.info("Tool {} executed: {}", toolName, result.isSuccess() ? "success" : "error");
        }

        // Send conversation with tool results back to gateway
        ChatCompletionRequest followUpRequest = ChatCompletionRequest.builder()
            .messages(history)
            .tools(toolRegistry.getToolDefinitionsForGateway())
            .temperature(0.3)
            .build();

        ChatCompletionResponse followUpResponse = gatewayClient.chat(followUpRequest);
        ChatMessage followUpMessage = followUpResponse.getFirstMessage();

        // Check if LLM wants to call MORE tools (multi-turn)
        if (followUpMessage.getToolCalls() != null && !followUpMessage.getToolCalls().isEmpty()) {
            log.info("Heartbeat task {} requesting additional tools - continuing", task.getName());
            // Recursively handle more tool calls
            return handleToolCalls(followUpResponse, history, task);
        }

        // No more tool calls - return final response
        String finalContent = followUpMessage.getContent();
        return finalContent != null ? finalContent : "";
    }
}
