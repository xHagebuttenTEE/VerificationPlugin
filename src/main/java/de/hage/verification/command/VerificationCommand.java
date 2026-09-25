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
        if (sub.equals("resetconfig")) {
            handleResetConfig(sender);
            return true;
        }
        if (sub.equals("resetdatabase")) {
            handleResetDatabase(sender);
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
            if (sender.hasPermission("verification.resetconfig")) available.add("resetconfig");
            if (sender.hasPermission("verification.resetdatabase")) available.add("resetdatabase");
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
        if (prefix == null || prefix.isEmpty()) return list;
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
        resetFile(sender, "messages.yml", "reset.success", "reset.failure", false);
    }

    private void handleResetConfig(CommandSender sender) {
        if (!sender.hasPermission("verification.resetconfig")) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
            return;
        }
        resetFile(sender, "config.yml", "reset.configSuccess", "reset.configFailure", true);
    }

    private void handleResetDatabase(CommandSender sender) {
        if (!sender.hasPermission("verification.resetdatabase")) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
            return;
        }
        resetFile(sender, "database.yml", "reset.databaseSuccess", "reset.databaseFailure", false);
    }

    private void resetFile(CommandSender sender, String filename, String successKey,
                           String failureKey, boolean reloadConfigAfter) {
        File file = new File(plugin.getDataFolder(), filename);
        File backup = new File(plugin.getDataFolder(), filename + ".bak");

        try {
            if (file.exists()) {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            if (file.exists() && !file.delete()) {
                throw new IOException("Konnte " + filename + " nicht löschen.");
            }

            if (reloadConfigAfter) {
                plugin.saveDefaultConfig();
            } else {
                plugin.saveResource(filename, true);
            }

            if (!file.exists()) {
                throw new IOException(filename + " wurde nicht neu erstellt.");
            }

            if (filename.equals("messages.yml")) {
                plugin.getMessageService().reload();
            }
            if (filename.equals("config.yml")) {
                plugin.reloadConfig();
            }

            sender.sendMessage(plugin.getMessageService().getMessage(successKey));
            sender.sendMessage(plugin.getMessageService().getMessage("reset.backupCreated",
                    "file", backup.getName()));

            if (!filename.equals("messages.yml")) {
                sender.sendMessage(plugin.getMessageService().getMessage("reset.restartRequired"));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Fehler beim Zurücksetzen der " + filename + ": " + e.getMessage());
            sender.sendMessage(plugin.getMessageService().getMessage(failureKey));
        }
    }
}