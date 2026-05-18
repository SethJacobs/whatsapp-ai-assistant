package com.jacobsfam.whatsappai.model.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class MessageResponse {

    private String text;
    private boolean success = true;
    private String error;

    public MessageResponse(String text) {
        this.text = text;
    }

    public static MessageResponse text(String text) {
        return new MessageResponse(text);
    }

    public static MessageResponse error(String error) {
        MessageResponse response = new MessageResponse();
        response.setSuccess(false);
        response.setError(error);
        return response;
    }
}
