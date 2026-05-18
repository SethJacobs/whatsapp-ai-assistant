package com.jacobsfam.whatsappai.service.tools.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.Tool;
import com.jacobsfam.whatsappai.model.ToolExecutionResult;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Simple test tool to verify the tool system is working.
 * Users can say "ping" and the LLM will call this tool.
 */
@Component
public class PingTool implements Tool {

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "A test tool that responds with 'pong'. Use this to verify the tool system is working.";
    }

    @Override
    public JsonNode getParametersSchema() {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.createObjectNode().put("type", "object");
    }

    @Override
    public ToolExecutionResult execute(Map<String, Object> arguments, ExecutionContext context) {
        return ToolExecutionResult.success("🏓 Pong! Tool system is working perfectly.");
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Collections.emptySet(); // Everyone can use this test tool
    }
}
