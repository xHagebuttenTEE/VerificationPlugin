package de.hage.verification.command.subcommand;

import java.util.List;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.VerificationHistoryEntry;
import de.hage.verification.util.PlayerUtil;

public final class HistorySubcommand implements Subcommand {

    private final VerificationPlugin plugin;

    public HistorySubcommand(VerificationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String targetName) {
        if (!sender.hasPermission("verification.history")) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
            return;
        }

        if (!plugin.getVerificationService().isHistoryEnabled()) {
            sender.sendMessage(plugin.getMessageService().getMessage("history.noEntries"));
            return;
        }

        UUID targetUUID = PlayerUtil.getPlayerUUID(targetName);
        if (targetUUID == null) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.playerNotFound"));
            return;
        }

        plugin.getVerificationService().getHistory(targetUUID).thenAcceptAsync(entries -> {
            String playerName = PlayerUtil.getPlayerName(targetUUID);
            if (playerName == null) playerName = targetName;
            sender.sendMessage(plugin.getMessageService().getMessage("history.header", "player", playerName));
            if (entries.isEmpty()) {
                sender.sendMessage(plugin.getMessageService().getMessage("history.noEntries"));
                return;
            }
            for (VerificationHistoryEntry entry : entries) {
                String entryTime = plugin.getVerificationService().formatTime(entry.getTimestamp());
                if (entryTime == null) entryTime = "?";
                String actor = entry.getActorName() != null ? entry.getActorName() : "Unbekannt";
                String details = entry.getDetails() != null ? entry.getDetails() : "";
                sender.sendMessage(plugin.getMessageService().getMessage("history.entry",
                        "time", entryTime,
                        "actor", actor,
                        "action", entry.getAction(),
                        "details", details));
            }
            int limit = plugin.getConfig().getInt("history.maxEntriesInHistoryCommand", 10);
            if (entries.size() >= limit) {
                sender.sendMessage(plugin.getMessageService().getMessage("history.tooMany",
                        "count", String.valueOf(limit)));
            }
        }).exceptionally(ex -> {
            sender.sendMessage(plugin.getMessageService().getMessage("command.dbConnectionError"));
            plugin.getLogger().warning("Fehler beim Abrufen der Historie für " + targetName + ": " + ex.getMessage());
            return null;
        });
    }
}