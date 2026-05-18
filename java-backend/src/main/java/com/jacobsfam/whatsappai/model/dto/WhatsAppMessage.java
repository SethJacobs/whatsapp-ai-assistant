package com.jacobsfam.whatsappai.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class WhatsAppMessage {

    @JsonProperty("from")
    private String from;

    @JsonProperty("name")
    private String name;

    @JsonProperty("text")
    private String text;

    @JsonProperty("timestamp")
    private Long timestamp;

    @JsonProperty("isGroup")
    private Boolean isGroup;
}
