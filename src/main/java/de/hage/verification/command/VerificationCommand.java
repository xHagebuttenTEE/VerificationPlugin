package de.hage.verification.command;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.command.subcommand.HistorySubcommand;
import de.hage.verification.command.subcommand.InfoSubcommand;
import de.hage.verification.command.subcommand.NewSubcommand;
import de.hage.verification.command.subcommand.Subcommand;

public final class VerificationCommand implements CommandExecutor {

    private final VerificationPlugin plugin;
    private final Map<String, Subcommand> subcommands = new HashMap<>();

    public VerificationCommand(VerificationPlugin plugin) {
        this.plugin = plugin;
        subcommands.put("new", new NewSubcommand(plugin));
        subcommands.put("info", new InfoSubcommand(plugin));
        subcommands.put("history", new HistorySubcommand(plugin));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.usage"));
            return true;
        }
        Subcommand sub = subcommands.get(args[0].toLowerCase());
        if (sub == null) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.usage"));
            return true;
        }
        sub.execute(sender, args[1]);
        return true;
    }
}