package com.jacobsfam.whatsappai.service.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacobsfam.whatsappai.model.dto.ChatCompletionRequest;
import com.jacobsfam.whatsappai.model.dto.ChatCompletionResponse;
import com.jacobsfam.whatsappai.model.dto.ChatMessage;
import com.jacobsfam.whatsappai.model.entity.Memory;
import com.jacobsfam.whatsappai.repository.MemoryRepository;
import com.jacobsfam.whatsappai.service.gateway.GatewayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Automatically extracts and stores facts from conversations.
 * Runs asynchronously after each conversation to build memory without explicit tool calls.
 */
@Service
@Slf4j
public class MemoryExtractionService {

    @Autowired
    private GatewayClient gatewayClient;

    @Autowired
    private MemoryRepository memoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String EXTRACTION_PROMPT = """
        You are a memory extraction system for Ezra, the Jacobs family AI assistant.

        Analyze the conversation and extract key facts that should be remembered long-term.
        Focus on:
        - Names, relationships, family members
        - Important dates (birthdays, exams, events)
        - Preferences and habits
        - Torah learning progress (current masechet, daf, topic)
        - Schedules and routines
        - Medical information
        - Projects and goals

        Return ONLY a JSON object with facts in this format:
        {
          "facts": [
            {"key": "current_masechet", "value": "Bava Metzia daf 42"},
            {"key": "yael_exam_date", "value": "May 30, 2025"},
            {"key": "moshe_bedtime", "value": "7:00 PM"}
          ]
        }

        Use clear, descriptive keys with underscores. Only extract facts that are worth remembering long-term.
        If there are no important facts to remember, return: {"facts": []}

        Return ONLY valid JSON, no other text.
        """;

    /**
     * Asynchronously extract and store facts from conversation.
     * Runs in background without blocking the response.
     */
    @Async
    public void extractAndStore(List<ChatMessage> recentMessages) {
        try {
            // Only process if we have recent messages
            if (recentMessages == null || recentMessages.size() < 2) {
                return;
            }

            // Get last few messages for context (user + assistant)
            List<ChatMessage> contextMessages = recentMessages.stream()
                .filter(m -> "user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                .skip(Math.max(0, recentMessages.size() - 4))
                .toList();

            if (contextMessages.isEmpty()) {
                return;
            }

            // Build extraction request
            List<ChatMessage> extractionMessages = new ArrayList<>();

            // Add system prompt
            ChatMessage systemMessage = new ChatMessage();
            systemMessage.setRole("system");
            systemMessage.setContent(EXTRACTION_PROMPT);
            extractionMessages.add(systemMessage);

            // Add conversation context
            extractionMessages.addAll(contextMessages);

            // Add extraction instruction
            ChatMessage extractInstruction = new ChatMessage();
            extractInstruction.setRole("user");
            extractInstruction.setContent("Extract facts to remember from this conversation. Return JSON only.");
            extractionMessages.add(extractInstruction);

            // Call gateway for extraction
            ChatCompletionRequest request = ChatCompletionRequest.builder()
                .messages(extractionMessages)
                .temperature(0.3) // Low temperature for consistent extraction
                .build();

            ChatCompletionResponse response = gatewayClient.chat(request);
            String content = response.getFirstMessage().getContent();

            if (content == null || content.trim().isEmpty()) {
                log.debug("No facts extracted from conversation");
                return;
            }

            // Parse JSON response
            JsonNode root = objectMapper.readTree(content.trim());
            JsonNode facts = root.get("facts");

            if (facts == null || !facts.isArray() || facts.isEmpty()) {
                log.debug("No facts to store");
                return;
            }

            // Store each fact
            int stored = 0;
            Iterator<JsonNode> elements = facts.elements();
            while (elements.hasNext()) {
                JsonNode fact = elements.next();
                String key = fact.get("key").asText();
                String value = fact.get("value").asText();

                if (key != null && !key.isEmpty() && value != null && !value.isEmpty()) {
                    storeMemory(key, value);
                    stored++;
                }
            }

            if (stored > 0) {
                log.info("Auto-extracted and stored {} facts", stored);
            }

        } catch (Exception e) {
            // Don't fail conversation if extraction fails
            log.warn("Failed to extract memories from conversation: {}", e.getMessage());
        }
    }

    private void storeMemory(String key, String value) {
        try {
            Memory memory = memoryRepository.findByKey(key)
                .orElse(new Memory(key, value));

            // Update value if different
            if (!value.equals(memory.getValue())) {
                memory.setValue(value);
                memoryRepository.save(memory);
                log.debug("Updated memory: {} = {}", key, value);
            }
        } catch (Exception e) {
            log.warn("Failed to store memory {}: {}", key, e.getMessage());
        }
    }
}
