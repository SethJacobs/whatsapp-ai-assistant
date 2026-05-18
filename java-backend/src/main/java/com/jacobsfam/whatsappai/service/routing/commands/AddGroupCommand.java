package com.jacobsfam.whatsappai.service.routing.commands;

import com.jacobsfam.whatsappai.model.Command;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.service.security.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * /addgroup command - Adds the current group to the allowlist.
 * Must be used within a group chat. Admin-only command.
 */
@Component
public class AddGroupCommand implements Command {
    private final SecurityService securityService;

    @Autowired
    public AddGroupCommand(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public String getName() {
        return "addgroup";
    }

    @Override
    public String getDescription() {
        return "Add current group to allowlist (Admin only)";
    }

    @Override
    public String getUsage() {
        return "/addgroup [name] [readonly] [permissions]\n\nExamples:\n" +
               "  /addgroup                          (read-write, default permissions)\n" +
               "  /addgroup FamilyChat               (read-write, default permissions)\n" +
               "  /addgroup FamilyChat true          (read-only mode)\n" +
               "  /addgroup TeamChat false system:*  (read-write, all system permissions)";
    }

    @Override
    public String execute(String args, ExecutionContext context) {
        // This command needs the group ID from context
        // The WebhookController will need to pass this in ExecutionContext
        String groupId = context.getGroupId();
        if (groupId == null || !groupId.contains("@g.us")) {
            return "❌ This command must be used within a group chat";
        }

        String[] parts = args.trim().split("\\s+", 3);
        String groupName = parts.length > 0 && !parts[0].isEmpty() ? parts[0] : "Unnamed Group";
        boolean readOnly = parts.length > 1 && parts[1].equalsIgnoreCase("true");
        String permissions = parts.length > 2 ? parts[2] : "system:read,gateway:read";

        try {
            securityService.addGroup(groupId, groupName, readOnly, permissions);
            return String.format("✅ Added group:\n" +
                    "  Name: %s\n" +
                    "  Mode: %s\n" +
                    "  Permissions: %s\n\n" +
                    "%s",
                    groupName,
                    readOnly ? "📖 Read-only (learns but doesn't respond)" : "💬 Read-write (responds normally)",
                    permissions,
                    readOnly ? "I'll now read messages for context but won't respond." : "I'll now respond to messages in this group!");
        } catch (IllegalArgumentException e) {
            return "❌ " + e.getMessage();
        }
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("admin:*");
    }
}
