package com.jacobsfam.whatsappai.service.routing.commands;

import com.jacobsfam.whatsappai.model.Command;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.service.conversation.ConversationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;

/**
 * /clear command - Clears conversation history.
 */
@Component
public class ClearCommand implements Command {
    private final ConversationService conversationService;

    @Autowired
    public ClearCommand(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @Override
    public String getName() {
        return "clear";
    }

    @Override
    public String getDescription() {
        return "Clear conversation history";
    }

    @Override
    public String getUsage() {
        return "/clear";
    }

    @Override
    public String execute(String args, ExecutionContext context) {
        conversationService.clearConversation(context.getUserPhone());
        return "✅ Conversation history cleared. Starting fresh!";
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Collections.emptySet(); // Everyone can clear their own conversation
    }
}
