package de.hage.verification.command.subcommand;

import java.util.List;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.VerificationHistoryEntry;
import de.hage.verification.service.VerificationService.VerificationInfo;
import de.hage.verification.util.PlayerUtil;

public final class InfoSubcommand implements Subcommand {

    private final VerificationPlugin plugin;

    public InfoSubcommand(VerificationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String targetName) {
        if (!sender.hasPermission("verification.info")) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
            return;
        }

        UUID targetUUID = PlayerUtil.getPlayerUUID(targetName);
        if (targetUUID == null) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.playerNotFound"));
            return;
        }

        plugin.getVerificationService().getVerificationInfo(targetUUID).thenAcceptAsync(info -> {
            if (!info.isVerified()) {
                sender.sendMessage(plugin.getMessageService().getMessage("info.notVerified"));
                return;
            }
            String playerName = info.getPlayerName();
            if (playerName == null) playerName = targetName;
            sender.sendMessage(plugin.getMessageService().getMessage("info.header", "player", playerName));

            String verificatorName = info.getVerificatorName();
            if (verificatorName == null) {
                verificatorName = plugin.getMessageService().getRawMessage("info.unknownVerificator");
            }
            sender.sendMessage(plugin.getMessageService().getMessage("info.verificator",
                    "verificator", verificatorName));

            String time = info.getFormattedTime();
            if (time == null) time = "Unbekannt";
            sender.sendMessage(plugin.getMessageService().getMessage("info.time", "time", time));

            List<VerificationHistoryEntry> history = info.getHistory();
            if (history != null && !history.isEmpty()) {
                sender.sendMessage(plugin.getMessageService().getMessage("info.historyHeader"));
                for (VerificationHistoryEntry entry : history) {
                    String entryTime = plugin.getVerificationService().formatTime(entry.getTimestamp());
                    if (entryTime == null) entryTime = "?";
                    String actor = entry.getActorName() != null ? entry.getActorName() : "Unbekannt";
                    String details = entry.getDetails() != null ? entry.getDetails() : "";
                    sender.sendMessage(plugin.getMessageService().getMessage("info.historyEntry",
                            "time", entryTime,
                            "actor", actor,
                            "action", entry.getAction(),
                            "details", details));
                }
            }
        }).exceptionally(ex -> {
            sender.sendMessage(plugin.getMessageService().getMessage("command.dbConnectionError"));
            plugin.getLogger().warning("Fehler beim Abrufen der Info für " + targetName + ": " + ex.getMessage());
            return null;
        });
    }
}