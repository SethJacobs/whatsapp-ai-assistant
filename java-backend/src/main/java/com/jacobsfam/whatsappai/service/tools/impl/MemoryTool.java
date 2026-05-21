package com.jacobsfam.whatsappai.service.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import com.jacobsfam.whatsappai.model.entity.Memory;
import com.jacobsfam.whatsappai.repository.MemoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class MemoryTool implements Tool {

    @Autowired
    private MemoryRepository memoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "memory";
    }

    @Override
    public String getDescription() {
        return "Store and retrieve memories about the Jacobs family. Use this to remember important facts, preferences, schedules, " +
               "Torah learning progress, family events, and anything else that will help you be more personal and helpful. " +
               "Operations: 'store' (save a fact), 'retrieve' (get a specific fact), 'search' (find related facts).";
    }

    @Override
    public JsonNode getParametersSchema() {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        // operation
        ObjectNode operation = objectMapper.createObjectNode();
        operation.put("type", "string");
        operation.put("description", "The operation: 'store', 'retrieve', or 'search'");
        operation.putArray("enum").add("store").add("retrieve").add("search");
        properties.set("operation", operation);

        // key
        ObjectNode key = objectMapper.createObjectNode();
        key.put("type", "string");
        key.put("description", "For store/retrieve: the memory key (e.g., 'current_masechet', 'yael_exam_date', 'moshe_favorite_song')");
        properties.set("key", key);

        // value
        ObjectNode value = objectMapper.createObjectNode();
        value.put("type", "string");
        value.put("description", "For store: the value to remember");
        properties.set("value", value);

        // query
        ObjectNode query = objectMapper.createObjectNode();
        query.put("type", "string");
        query.put("description", "For search: search term to find related memories");
        properties.set("query", query);

        schema.set("properties", properties);
        schema.putArray("required").add("operation");

        return schema;
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context) {
        try {
            String operation = (String) arguments.get("operation");

            if (operation == null) {
                return ToolExecutionResult.error("Operation is required (store, retrieve, or search)");
            }

            return switch (operation.toLowerCase()) {
                case "store" -> storeMemory(arguments);
                case "retrieve" -> retrieveMemory(arguments);
                case "search" -> searchMemories(arguments);
                default -> ToolExecutionResult.error("Unknown operation: " + operation);
            };

        } catch (Exception e) {
            log.error("Error in memory tool", e);
            return ToolExecutionResult.error("Memory operation failed: " + e.getMessage());
        }
    }

    private ToolExecutionResult storeMemory(Map<String, Object> arguments) {
        String key = (String) arguments.get("key");
        String value = (String) arguments.get("value");

        if (key == null || value == null) {
            return ToolExecutionResult.error("Both 'key' and 'value' are required for store operation");
        }

        Memory memory = memoryRepository.findByKey(key)
            .orElse(new Memory(key, value));
        memory.setValue(value);
        memoryRepository.save(memory);

        log.info("Stored memory: {} = {}", key, value);
        return ToolExecutionResult.success("✓ Remembered: " + key + " = " + value);
    }

    private ToolExecutionResult retrieveMemory(Map<String, Object> arguments) {
        String key = (String) arguments.get("key");

        if (key == null) {
            return ToolExecutionResult.error("'key' is required for retrieve operation");
        }

        return memoryRepository.findByKey(key)
            .map(memory -> ToolExecutionResult.success(memory.getValue()))
            .orElseGet(() -> ToolExecutionResult.error("No memory found for key: " + key));
    }

    private ToolExecutionResult searchMemories(Map<String, Object> arguments) {
        String query = (String) arguments.get("query");

        if (query == null) {
            return ToolExecutionResult.error("'query' is required for search operation");
        }

        List<Memory> results = memoryRepository.findByKeyContainingIgnoreCase(query);

        if (results.isEmpty()) {
            return ToolExecutionResult.success("No memories found matching: " + query);
        }

        String output = results.stream()
            .map(m -> m.getKey() + " = " + m.getValue())
            .collect(Collectors.joining("\n"));

        return ToolExecutionResult.success("Found " + results.size() + " memories:\n" + output);
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Collections.emptySet(); // Everyone can use memory
    }
}
