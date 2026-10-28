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
import java.util.UUID;
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
import de.hage.verification.ui.LeaderboardUI;
import de.hage.verification.ui.StatsMainUI;
import de.hage.verification.ui.SupporterProfileUI;
import de.hage.verification.util.PlayerUtil;

public final class VerificationCommand implements CommandExecutor, TabCompleter {

    private final VerificationPlugin plugin;
    private final Map<String, Subcommand> subcommands = new HashMap<>();
    private final Map<String, String> staticDescriptions = new HashMap<>();
    private final Map<String, String> staticPermissions = new HashMap<>();

    public VerificationCommand(VerificationPlugin plugin) {
        this.plugin = plugin;
        subcommands.put("new", new NewSubcommand(plugin));
        subcommands.put("info", new InfoSubcommand(plugin));
        subcommands.put("history", new HistorySubcommand(plugin));

        staticDescriptions.put("ui", "Öffnet das Statistik-Menü");
        staticDescriptions.put("stats", "Öffnet das Statistik-Menü oder das Profil eines Supporters");
        staticDescriptions.put("leaderboard", "Zeigt die Monats-Rangliste");
        staticDescriptions.put("resetmessages", "Setzt messages.yml zurück");
        staticDescriptions.put("resetconfig", "Setzt config.yml zurück");
        staticDescriptions.put("resetdatabase", "Setzt database.yml zurück");

        staticPermissions.put("ui", "verification.info");
        staticPermissions.put("stats", "verification.info");
        staticPermissions.put("leaderboard", "verification.info");
        staticPermissions.put("resetmessages", "verification.admin");
        staticPermissions.put("resetconfig", "verification.admin");
        staticPermissions.put("resetdatabase", "verification.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) { sendHelp(sender); return true; }
        String sub = args[0].toLowerCase();

        if (sub.equals("help") || sub.equals("?")) { sendHelp(sender); return true; }

        if (sub.equals("ui")) {
            if (!sender.hasPermission("verification.info")) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
                return true;
            }
            new StatsMainUI(plugin, player).open();
            return true;
        }

        if (sub.equals("stats")) {
            if (!sender.hasPermission("verification.info")) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
                return true;
            }
            if (args.length < 2) {
                new StatsMainUI(plugin, player).open();
                return true;
            }
            String targetName = args[1];
            UUID targetUUID = PlayerUtil.getPlayerUUID(targetName);
            if (targetUUID == null) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.playerNotFound"));
                return true;
            }
            plugin.getSupporterStatsRepository().findOne(targetUUID).thenAcceptAsync(stats ->
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (stats != null) {
                            SupporterProfileUI.open(plugin, player, stats,
                                    () -> new StatsMainUI(plugin, player).open());
                        } else {
                            player.sendMessage(plugin.getMessageService().getMessage("command.playerNotFound"));
                        }
                    })
            ).exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () ->
                        sender.sendMessage(plugin.getMessageService().getMessage("command.dbConnectionError")));
                return null;
            });
            return true;
        }

        if (sub.equals("leaderboard")) {
            if (!sender.hasPermission("verification.info")) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
                return true;
            }
            LeaderboardUI.open(plugin, player, null, () -> new StatsMainUI(plugin, player).open());
            return true;
        }

        if (sub.equals("resetmessages")) {
            if (!sender.hasPermission("verification.admin")) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
                return true;
            }
            resetFile(sender, "messages.yml", "reset.success", "reset.failure", false);
            return true;
        }

        if (sub.equals("resetconfig")) {
            if (!sender.hasPermission("verification.admin")) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
                return true;
            }
            resetFile(sender, "config.yml", "reset.configSuccess", "reset.configFailure", true);
            return true;
        }

        if (sub.equals("resetdatabase")) {
            if (!sender.hasPermission("verification.admin")) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
                return true;
            }
            resetFile(sender, "database.yml", "reset.databaseSuccess", "reset.databaseFailure", false);
            return true;
        }

        if (args.length < 2) { sendHelp(sender); return true; }

        Subcommand subcommand = subcommands.get(sub);
        if (subcommand == null) { sendHelp(sender); return true; }
        subcommand.execute(sender, args[1]);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getMessageService().getMessage("command.helpHeader"));
        for (Map.Entry<String, Subcommand> entry : subcommands.entrySet()) {
            Subcommand sub = entry.getValue();
            if (sender.hasPermission(sub.getPermission())) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.helpEntry",
                        "sub", entry.getKey(), "description", sub.getDescription()));
            }
        }
        for (Map.Entry<String, String> entry : staticDescriptions.entrySet()) {
            String perm = staticPermissions.get(entry.getKey());
            if (sender.hasPermission(perm)) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.helpEntry",
                        "sub", entry.getKey(), "description", entry.getValue()));
            }
        }
        sender.sendMessage(plugin.getMessageService().getMessage("command.helpFooter"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> available = new ArrayList<>();
            for (Map.Entry<String, Subcommand> entry : subcommands.entrySet()) {
                if (sender.hasPermission(entry.getValue().getPermission())) available.add(entry.getKey());
            }
            for (Map.Entry<String, String> entry : staticPermissions.entrySet()) {
                if (sender.hasPermission(entry.getValue())) available.add(entry.getKey());
            }
            return filterByPrefix(available, args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("new") || sub.equals("info") || sub.equals("history")) {
                Subcommand subcommand = subcommands.get(sub);
                if (subcommand == null || !sender.hasPermission(subcommand.getPermission())) {
                    return Collections.emptyList();
                }
                List<String> names = Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).collect(Collectors.toList());
                return filterByPrefix(names, args[1]);
            }
            if (sub.equals("stats") && sender.hasPermission("verification.info")) {
                return filterByPrefix(plugin.getCachedSupporterNames(), args[1]);
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

    private void resetFile(CommandSender sender, String filename, String successKey,
                           String failureKey, boolean reloadConfigAfter) {
        File file = new File(plugin.getDataFolder(), filename);
        File backup = new File(plugin.getDataFolder(), filename + ".bak");
        try {
            if (file.exists()) Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            if (file.exists() && !file.delete()) throw new IOException("Konnte " + filename + " nicht löschen.");
            if (reloadConfigAfter) plugin.saveDefaultConfig();
            else plugin.saveResource(filename, true);
            if (!file.exists()) throw new IOException(filename + " wurde nicht neu erstellt.");
            if (filename.equals("messages.yml")) plugin.getMessageService().reload();
            if (filename.equals("config.yml")) plugin.reloadConfig();
            sender.sendMessage(plugin.getMessageService().getMessage(successKey));
            sender.sendMessage(plugin.getMessageService().getMessage("reset.backupCreated", "file", backup.getName()));
            if (!filename.equals("messages.yml")) {
                sender.sendMessage(plugin.getMessageService().getMessage("reset.restartRequired"));
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Fehler beim Zurücksetzen der " + filename + ": " + e.getMessage());
            sender.sendMessage(plugin.getMessageService().getMessage(failureKey));
        }
    }
}