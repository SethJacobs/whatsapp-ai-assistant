package com.jacobsfam.whatsappai.service.routing.commands;

import com.jacobsfam.whatsappai.model.Command;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.service.security.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * /adduser command - Adds a phone number to the allowlist.
 * Admin-only command.
 */
@Component
public class AddUserCommand implements Command {
    private final SecurityService securityService;

    @Autowired
    public AddUserCommand(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public String getName() {
        return "adduser";
    }

    @Override
    public String getDescription() {
        return "Add user to allowlist (Admin only)";
    }

    @Override
    public String getUsage() {
        return "/adduser <phone> [label] [permissions]\n\nExamples:\n" +
               "  /adduser +1234567890\n" +
               "  /adduser +1234567890 John\n" +
               "  /adduser +1234567890 John system:read,docker:read";
    }

    @Override
    public String execute(String args, ExecutionContext context) {
        if (args.trim().isEmpty()) {
            return "❌ Usage: " + getUsage();
        }

        String[] parts = args.trim().split("\\s+", 3);
        String phone = parts[0];

        // Validate E.164 format
        if (!phone.matches("^\\+[0-9]{10,15}$")) {
            return "❌ Invalid phone number format. Use E.164 format: +1234567890";
        }

        String label = parts.length > 1 ? parts[1] : "User";
        String permissions = parts.length > 2 ? parts[2] : "system:read,gateway:read";

        try {
            securityService.addContact(phone, label, permissions);
            return String.format("✅ Added user:\n" +
                    "  Phone: %s\n" +
                    "  Label: %s\n" +
                    "  Permissions: %s\n\n" +
                    "They can now use the assistant!", phone, label, permissions);
        } catch (IllegalArgumentException e) {
            return "❌ " + e.getMessage();
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("admin:*");
    }
}
