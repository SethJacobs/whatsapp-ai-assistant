package com.jacobsfam.whatsappai.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
@Slf4j
public class SystemPromptConfig {

    @Bean
    public String systemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("system-prompt.txt");
            String prompt = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            log.info("Loaded system prompt ({} characters)", prompt.length());
            return prompt;
        } catch (IOException e) {
            log.error("Failed to load system prompt, using default", e);
            return "You are Ezra, a helpful AI assistant for the Jacobs family.";
        }
    }
}
