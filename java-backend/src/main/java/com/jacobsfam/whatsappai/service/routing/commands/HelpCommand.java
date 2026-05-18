package com.jacobsfam.whatsappai.service.routing.commands;

import com.jacobsfam.whatsappai.model.Command;
import com.jacobsfam.whatsappai.model.ExecutionContext;
import com.jacobsfam.whatsappai.service.routing.MessageRouter;
import com.jacobsfam.whatsappai.service.security.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /help command - Shows available commands and their usage.
 */
@Component
public class HelpCommand implements Command {
    private final MessageRouter messageRouter;
    private final SecurityService securityService;

    @Autowired
    public HelpCommand(MessageRouter messageRouter, SecurityService securityService) {
        this.messageRouter = messageRouter;
        this.securityService = securityService;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Show available commands";
    }

    @Override
    public String getUsage() {
        return "/help [command]";
    }

    @Override
    public String execute(String args, ExecutionContext context) {
        // If specific command requested, show detailed help
        if (!args.isEmpty()) {
            return getCommandHelp(args, context);
        }

        // Otherwise show list of all available commands
        List<Command> allCommands = messageRouter.getAllCommands();

        // Filter to only show commands the user has permission for
        String userPhone = context.getUserPhone();
        List<Command> availableCommands = allCommands.stream()
            .filter(cmd -> cmd.getRequiredPermissions().isEmpty() ||
                          securityService.hasPermissions(userPhone, cmd.getRequiredPermissions()))
            .sorted(Comparator.comparing(Command::getName))
            .collect(Collectors.toList());

        StringBuilder response = new StringBuilder();
        response.append("🤖 *WhatsApp AI Assistant*\n\n");
        response.append("*Available Commands:*\n\n");

        for (Command cmd : availableCommands) {
            response.append(String.format("/%s - %s\n", cmd.getName(), cmd.getDescription()));
        }

        response.append("\n💡 _Send any message (without /) to chat with AI_\n");
        response.append("💡 _Use /help [command] for detailed usage_");

        return response.toString();
    }

    private String getCommandHelp(String commandName, ExecutionContext context) {
        List<Command> allCommands = messageRouter.getAllCommands();
        Optional<Command> cmdOpt = allCommands.stream()
            .filter(c -> c.getName().equalsIgnoreCase(commandName))
            .findFirst();

        if (cmdOpt.isEmpty()) {
            return String.format("❌ Unknown command: /%s\n\nUse /help to see all commands.", commandName);
        }

        Command cmd = cmdOpt.get();

        // Check if user has permission
        if (!cmd.getRequiredPermissions().isEmpty() &&
            !securityService.hasPermissions(context.getUserPhone(), cmd.getRequiredPermissions())) {
            return String.format("🔒 You don't have permission to use /%s", commandName);
        }

        StringBuilder response = new StringBuilder();
        response.append(String.format("*/%s*\n\n", cmd.getName()));
        response.append(String.format("*Description:* %s\n\n", cmd.getDescription()));
        response.append(String.format("*Usage:* %s\n", cmd.getUsage()));

        if (!cmd.getRequiredPermissions().isEmpty()) {
            response.append(String.format("\n*Required permissions:* %s",
                String.join(", ", cmd.getRequiredPermissions())));
        }

        return response.toString();
    }

    @Override
    public Set<String> getRequiredPermissions() {
        return Collections.emptySet(); // Everyone can use /help
    }
}
