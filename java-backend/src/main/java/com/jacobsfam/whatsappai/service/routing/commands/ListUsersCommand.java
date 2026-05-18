package com.jacobsfam.whatsappai.service.routing.commands;

import com.jacobsfam.whatsappai.model.Command;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.entity.AllowedContact;
import com.jacobsfam.whatsappai.repository.AllowedContactRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * /listusers command - Lists all allowed users.
 * Admin-only command.
 */
@Component
public class ListUsersCommand implements Command {
    private final AllowedContactRepository allowedContactRepository;

    @Autowired
    public ListUsersCommand(AllowedContactRepository allowedContactRepository) {
        this.allowedContactRepository = allowedContactRepository;
    }

    @Override
    public String getName() {
        return "listusers";
    }

    @Override
    public String getDescription() {
        return "List all allowed users (Admin only)";
    }

    @Override
    public String getUsage() {
        return "/listusers";
    }

    @Override
    public String execute(String args, ExecutionContext context) {
        List<AllowedContact> contacts = allowedContactRepository.findAll();

        if (contacts.isEmpty()) {
            return "📋 No users in allowlist";
        }

        StringBuilder response = new StringBuilder();
        response.append("📋 *Allowed Users* (").append(contacts.size()).append("):\n\n");

        for (AllowedContact contact : contacts) {
            response.append("👤 *").append(contact.getLabel()).append("*\n");
            response.append("   Phone: ").append(contact.getPhoneNumber()).append("\n");
            response.append("   Permissions: ").append(contact.getPermissions() != null ? contact.getPermissions() : "none").append("\n");
            response.append("   Status: ").append(contact.isEnabled() ? "✅ Enabled" : "❌ Disabled").append("\n\n");
        }

        return response.toString();
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("admin:*");
    }
}
