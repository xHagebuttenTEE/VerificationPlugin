package de.hage.verification.command;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.command.subcommand.HistorySubcommand;
import de.hage.verification.command.subcommand.InfoSubcommand;
import de.hage.verification.command.subcommand.NewSubcommand;
import de.hage.verification.command.subcommand.Subcommand;
import de.hage.verification.command.subcommand.UICommand;

public final class VerificationCommand implements CommandExecutor, TabCompleter {

    private final VerificationPlugin plugin;
    private final Map<String, Subcommand> subcommands = new HashMap<>();
    private final UICommand uiCommand;

    public VerificationCommand(VerificationPlugin plugin) {
        this.plugin = plugin;
        subcommands.put("new", new NewSubcommand(plugin));
        subcommands.put("info", new InfoSubcommand(plugin));
        subcommands.put("history", new HistorySubcommand(plugin));
        this.uiCommand = new UICommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.usage"));
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("ui")) {
            uiCommand.execute(sender);
            return true;
        }

        if (sub.equals("resetmessages")) {
            handleResetMessages(sender);
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.usage"));
            return true;
        }

        Subcommand subcommand = subcommands.get(sub);
        if (subcommand == null) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.usage"));
            return true;
        }
        subcommand.execute(sender, args[1]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> available = new ArrayList<>();
            if (sender.hasPermission("verification.new")) available.add("new");
            if (sender.hasPermission("verification.info")) available.add("info");
            if (sender.hasPermission("verification.history")) available.add("history");
            if (sender.hasPermission("verification.ui")) available.add("ui");
            if (sender.hasPermission("verification.resetmessages")) available.add("resetmessages");
            return filterByPrefix(available, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("new") || sub.equals("info") || sub.equals("history")) {
                List<String> names = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .collect(Collectors.toList());
                return filterByPrefix(names, args[1]);
            }
            return Collections.emptyList();
        }

        return Collections.emptyList();
    }

    private List<String> filterByPrefix(List<String> list, String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return list;
        }
        String lower = prefix.toLowerCase();
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }

    private void handleResetMessages(CommandSender sender) {
        if (!sender.hasPermission("verification.resetmessages")) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
            return;
        }

        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        File backupFile = new File(plugin.getDataFolder(), "messages.yml.bak");

        try {
            if (messagesFile.exists()) {
                Files.copy(messagesFile.toPath(), backupFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            if (messagesFile.exists() && !messagesFile.delete()) {
                throw new IOException("Konnte messages.yml nicht löschen.");
            }
            plugin.saveResource("messages.yml", true);

            if (!messagesFile.exists()) {
                throw new IOException("messages.yml wurde nicht neu erstellt.");
            }

            plugin.getMessageService().reload();

            sender.sendMessage(plugin.getMessageService().getMessage("reset.success"));
            sender.sendMessage(plugin.getMessageService().getMessage("reset.backupCreated"));
        } catch (IOException e) {
            plugin.getLogger().warning("Fehler beim Zurücksetzen der messages.yml: " + e.getMessage());
            sender.sendMessage(plugin.getMessageService().getMessage("reset.failure"));
        }
    }
}