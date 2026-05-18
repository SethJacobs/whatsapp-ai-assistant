package com.jacobsfam.whatsappai.service.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.dto.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ToolRegistry {

    private final Map<String, Tool> tools = new ConcurrentHashMap<>();

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @PostConstruct
    public void registerTools() {
        // Scan classpath for @Component classes implementing Tool interface
        Map<String, Tool> toolBeans = applicationContext.getBeansOfType(Tool.class);

        toolBeans.values().forEach(tool -> {
            tools.put(tool.getName(), tool);
            log.info("Registered tool: {} - {}", tool.getName(), tool.getDescription());
        });

        log.info("Total tools registered: {}", tools.size());
    }

    /**
     * Get all registered tools.
     */
    public Collection<Tool> getAllTools() {
        return tools.values();
    }

    /**
     * Get tool by name.
     */
    public Tool getTool(String name) {
        return tools.get(name);
    }

    /**
     * Check if tool exists.
     */
    public boolean hasTool(String name) {
        return tools.containsKey(name);
    }

    /**
     * Convert all tools to OpenAI function definitions for gateway.
     */
    public List<ToolDefinition> getToolDefinitionsForGateway() {
        return tools.values().stream()
                .map(this::convertToOpenAiFunction)
                .collect(Collectors.toList());
    }

    /**
     * Convert a single tool to OpenAI function definition.
     */
    private ToolDefinition convertToOpenAiFunction(Tool tool) {
        return ToolDefinition.builder()
                .type("function")
                .function(ToolDefinition.FunctionDefinition.builder()
                        .name(tool.getName())
                        .description(tool.getDescription())
                        .parameters(tool.getParametersSchema())
                        .build())
                .build();
    }
}
