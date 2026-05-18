package com.jacobsfam.whatsappai.service.conversation;

import com.jacobsfam.whatsappai.model.dto.ChatMessage;
import com.jacobsfam.whatsappai.model.entity.Conversation;
import com.jacobsfam.whatsappai.model.entity.ConversationMessage;
import com.jacobsfam.whatsappai.repository.ConversationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConversationService {

    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final Duration SESSION_TIMEOUT = Duration.ofHours(2);

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<ChatMessage> getHistory(String phoneNumber) {
        Conversation conversation = conversationRepository
                .findFirstByPhoneNumberOrderByLastMessageAtDesc(phoneNumber)
                .filter(c -> Duration.between(c.getLastMessageAt(), LocalDateTime.now())
                        .compareTo(SESSION_TIMEOUT) < 0)
                .orElse(null);

        if (conversation == null) {
            log.debug("No active conversation for {}", phoneNumber);
            return new ArrayList<>();
        }

        // Get recent messages
        List<ConversationMessage> messages = conversation.getMessages().stream()
                .sorted((a, b) -> a.getTimestamp().compareTo(b.getTimestamp()))
                .skip(Math.max(0, conversation.getMessages().size() - MAX_HISTORY_MESSAGES))
                .collect(Collectors.toList());

        return messages.stream()
                .map(this::toChatMessage)
                .collect(Collectors.toList());
    }

    @Transactional
    public void appendMessage(String phoneNumber, ChatMessage message) {
        Conversation conversation = conversationRepository
                .findFirstByPhoneNumberOrderByLastMessageAtDesc(phoneNumber)
                .filter(c -> Duration.between(c.getLastMessageAt(), LocalDateTime.now())
                        .compareTo(SESSION_TIMEOUT) < 0)
                .orElseGet(() -> createNewConversation(phoneNumber));

        ConversationMessage msg = fromChatMessage(message);
        conversation.addMessage(msg);
        conversationRepository.save(conversation);

        log.debug("Appended {} message to conversation {}", message.getRole(), conversation.getId());
    }

    @Transactional
    public void saveExchange(String phoneNumber, List<ChatMessage> messages) {
        Conversation conversation = conversationRepository
                .findFirstByPhoneNumberOrderByLastMessageAtDesc(phoneNumber)
                .orElseGet(() -> createNewConversation(phoneNumber));

        for (ChatMessage chatMsg : messages) {
            ConversationMessage msg = fromChatMessage(chatMsg);
            conversation.addMessage(msg);
        }

        conversationRepository.save(conversation);
        log.debug("Saved {} messages to conversation {}", messages.size(), conversation.getId());
    }

    private Conversation createNewConversation(String phoneNumber) {
        Conversation conversation = new Conversation(phoneNumber);
        conversationRepository.save(conversation);
        log.info("Created new conversation for {}", phoneNumber);
        return conversation;
    }

    private ChatMessage toChatMessage(ConversationMessage entity) {
        ChatMessage msg = new ChatMessage();
        msg.setRole(entity.getRole());
        msg.setContent(entity.getContent());
        msg.setToolCallId(entity.getToolCallId());
        msg.setName(entity.getName());

        // Deserialize tool_calls if present
        if (entity.getToolCalls() != null) {
            try {
                List<com.jacobsfam.whatsappai.model.dto.ToolCall> toolCalls =
                        objectMapper.readValue(
                                entity.getToolCalls(),
                                objectMapper.getTypeFactory().constructCollectionType(
                                        List.class,
                                        com.jacobsfam.whatsappai.model.dto.ToolCall.class
                                )
                        );
                msg.setToolCalls(toolCalls);
            } catch (Exception e) {
                log.error("Failed to deserialize tool_calls", e);
            }
        }

        return msg;
    }

    private ConversationMessage fromChatMessage(ChatMessage chatMsg) {
        ConversationMessage msg = new ConversationMessage();
        msg.setRole(chatMsg.getRole());
        msg.setContent(chatMsg.getContent());
        msg.setToolCallId(chatMsg.getToolCallId());
        msg.setName(chatMsg.getName());

        // Serialize tool_calls if present
        if (chatMsg.getToolCalls() != null) {
            try {
                msg.setToolCalls(objectMapper.writeValueAsString(chatMsg.getToolCalls()));
            } catch (Exception e) {
                log.error("Failed to serialize tool_calls", e);
            }
        }

        return msg;
    }
}
