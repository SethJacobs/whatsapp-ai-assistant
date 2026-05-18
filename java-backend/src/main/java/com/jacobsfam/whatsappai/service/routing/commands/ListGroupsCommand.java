package com.jacobsfam.whatsappai.service.routing.commands;

import com.jacobsfam.whatsappai.model.Command;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.model.entity.AllowedGroup;
import com.jacobsfam.whatsappai.repository.AllowedGroupRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * /listgroups command - Lists all allowed groups.
 * Admin-only command.
 */
@Component
public class ListGroupsCommand implements Command {
    private final AllowedGroupRepository allowedGroupRepository;

    @Autowired
    public ListGroupsCommand(AllowedGroupRepository allowedGroupRepository) {
        this.allowedGroupRepository = allowedGroupRepository;
    }

    @Override
    public String getName() {
        return "listgroups";
    }

    @Override
    public String getDescription() {
        return "List all allowed groups (Admin only)";
    }

    @Override
    public String getUsage() {
        return "/listgroups";
    }

    @Override
    public String execute(String args, ExecutionContext context) {
        List<AllowedGroup> groups = allowedGroupRepository.findAll();

        if (groups.isEmpty()) {
            return "📋 No groups in allowlist";
        }

        StringBuilder response = new StringBuilder();
        response.append("📋 *Allowed Groups* (").append(groups.size()).append("):\n\n");

        for (AllowedGroup group : groups) {
            response.append("👥 *").append(group.getGroupName()).append("*\n");
            response.append("   Mode: ").append(group.isReadOnly() ? "📖 Read-only" : "💬 Read-write").append("\n");
            response.append("   Permissions: ").append(group.getPermissions() != null ? group.getPermissions() : "none").append("\n");
            response.append("   ID: ").append(group.getGroupId()).append("\n\n");
        }

        return response.toString();
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Set.of("admin:*");
    }
}
