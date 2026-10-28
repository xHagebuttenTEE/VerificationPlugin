package de.hage.verification.command.subcommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.VerificationEntry;
import de.hage.verification.database.model.VerificationStatistics;
import de.hage.verification.ui.VerificationUI;

public final class UICommand {

    private final VerificationPlugin plugin;

    public UICommand(VerificationPlugin plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, Runnable onBack) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
            return;
        }
        if (!sender.hasPermission("verification.info")) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
            return;
        }

        UUID viewerUUID = player.getUniqueId();

        plugin.getVerificationService().getPlayersForUI(viewerUUID, true)
                .thenComposeAsync(this::buildUIResult)
                .thenAcceptAsync(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                    new VerificationUI(plugin, player,
                            result.entries, result.stats, result.playtime, result.rankCache, onBack).open();
                }))
                .exceptionally(ex -> {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            sender.sendMessage(plugin.getMessageService().getMessage("command.dbConnectionError")));
                    plugin.getLogger().log(Level.WARNING, "Fehler beim Laden des UIs", ex);
                    return null;
                });
    }

    private CompletableFuture<UIResult> buildUIResult(List<VerificationEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return CompletableFuture.completedFuture(new UIResult(entries, new HashMap<>(), new HashMap<>(), new HashMap<>()));
        }

        List<UUID> uuids = new ArrayList<>();
        for (VerificationEntry entry : entries) uuids.add(entry.getVerifiedPlayer());

        CompletableFuture<Map<UUID, VerificationStatistics>> statsF = plugin.getStatisticsService() != null
                ? plugin.getStatisticsService().loadStatistics(uuids)
                : CompletableFuture.completedFuture(new HashMap<>());

        CompletableFuture<Map<UUID, Long>> playtimeF = (plugin.getAdvancedStatsRepository() != null
                && plugin.getConfig().getBoolean("advanced-stats.enabled", true))
                ? plugin.getAdvancedStatsRepository().findPlaytimeBatch(uuids)
                : CompletableFuture.completedFuture(new HashMap<>());

        return statsF.thenCombineAsync(playtimeF, (stats, playtime) -> {
            Map<UUID, String> rankCache = new HashMap<>();
            for (VerificationEntry entry : entries) {
                UUID uuid = entry.getVerifiedPlayer();
                String rank = plugin.getVerificationService().getRankOf(uuid);
                if (rank != null) rankCache.put(uuid, rank);
            }
            return new UIResult(entries, stats, playtime, rankCache);
        });
    }

    private static final class UIResult {
        final List<VerificationEntry> entries;
        final Map<UUID, VerificationStatistics> stats;
        final Map<UUID, Long> playtime;
        final Map<UUID, String> rankCache;

        UIResult(List<VerificationEntry> entries,
                 Map<UUID, VerificationStatistics> stats,
                 Map<UUID, Long> playtime,
                 Map<UUID, String> rankCache) {
            this.entries = entries;
            this.stats = stats;
            this.playtime = playtime;
            this.rankCache = rankCache;
        }
    }
}