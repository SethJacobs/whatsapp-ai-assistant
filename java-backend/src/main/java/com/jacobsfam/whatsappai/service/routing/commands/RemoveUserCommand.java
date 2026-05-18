package com.jacobsfam.whatsappai.service.routing.commands;

import com.jacobsfam.whatsappai.model.Command;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.service.security.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * /removeuser command - Removes a phone number from the allowlist.
 * Admin-only command.
 */
@Component
public class RemoveUserCommand implements Command {
    private final SecurityService securityService;

    @Autowired
    public RemoveUserCommand(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public String getName() {
        return "removeuser";
    }

    @Override
    public String getDescription() {
        return "Remove user from allowlist (Admin only)";
    }

    @Override
    public String getUsage() {
        return "/removeuser <phone>\n\nExample: /removeuser +1234567890";
    }

    @Override
    public String execute(String args, ExecutionContext context) {
        if (args.trim().isEmpty()) {
            return "❌ Usage: " + getUsage();
        }

        String phone = args.trim();

        // Validate E.164 format
        if (!phone.matches("^\\+[0-9]{10,15}$")) {
            return "❌ Invalid phone number format. Use E.164 format: +1234567890";
        }

        // Prevent removing yourself
        if (phone.equals(context.getUserPhone())) {
            return "❌ Cannot remove yourself from the allowlist";
        }

        try {
            securityService.removeContact(phone);
            return String.format("✅ Removed user: %s\n\n" +
                    "They can no longer use the assistant.", phone);
        } catch (IllegalArgumentException e) {
            return "❌ " + e.getMessage();
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("admin:*");
    }
}
