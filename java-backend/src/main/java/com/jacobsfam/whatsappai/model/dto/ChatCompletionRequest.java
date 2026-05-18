package com.jacobsfam.whatsappai.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatCompletionRequest {

    private List<ChatMessage> messages;

    private String model; // Optional, gateway uses default

    @Builder.Default
    private Double temperature = 0.7;

    @JsonProperty("max_tokens")
    private Integer maxTokens;

    private List<ToolDefinition> tools;

    @JsonProperty("tool_choice")
    @Builder.Default
    private String toolChoice = "auto"; // "auto", "none"

    private String route; // Gateway-specific: "auto", "cloud", "local"
}
