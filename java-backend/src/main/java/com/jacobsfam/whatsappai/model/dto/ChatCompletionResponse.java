package com.jacobsfam.whatsappai.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class ChatCompletionResponse {

    private String id;

    private String object;

    private Long created;

    private String model;

    private List<Choice> choices;

    private Usage usage;

    @JsonProperty("x_route")
    private String xRoute; // Gateway metadata

    @JsonProperty("x_provider")
    private String xProvider; // Gateway metadata

    @JsonProperty("x_intent")
    private String xIntent; // Gateway metadata

    @Data
    @NoArgsConstructor
    public static class Choice {
        private Integer index;
        private ChatMessage message;
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    @Data
    @NoArgsConstructor
    public static class Usage {
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;
        @JsonProperty("completion_tokens")
        private Integer completionTokens;
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }

    public ChatMessage getFirstMessage() {
        if (choices != null && !choices.isEmpty()) {
            return choices.get(0).getMessage();
        }
        return null;
    }

    public boolean hasToolCalls() {
        ChatMessage msg = getFirstMessage();
        return msg != null && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty();
    }
}
