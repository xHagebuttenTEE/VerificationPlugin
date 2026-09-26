package de.hage.verification.milestone;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.VerificationEntry;
import de.hage.verification.database.model.VerificationStatistics;
import de.hage.verification.database.repository.VerificationStatisticsRepository;
import de.hage.verification.service.ProgressService;
import de.hage.verification.util.PlayerUtil;

public final class MilestoneService {

    private final VerificationPlugin plugin;
    private final MilestoneConfig config;
    private final VerificationStatisticsRepository statisticsRepository;

    public MilestoneService(VerificationPlugin plugin,
                            MilestoneConfig config,
                            VerificationStatisticsRepository statisticsRepository) {
        this.plugin = plugin;
        this.config = config;
        this.statisticsRepository = statisticsRepository;
    }

    public void checkAll() {
        if (!config.isEnabled()) return;
        if (plugin.getProgressService() == null || !plugin.getProgressService().isEnabled()) return;
        if (config.getMilestones().isEmpty()) return;

        plugin.getVerificationService().getAllVerifiedPlayers().thenAcceptAsync(entries -> {
            for (VerificationEntry entry : entries) {
                try {
                    checkPlayer(entry.getVerifiedPlayer());
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING,
                            "Fehler beim Prüfen der Meilensteine für " + entry.getVerifiedPlayer(), e);
                }
            }
        });
    }

    private void checkPlayer(UUID playerUUID) {
        statisticsRepository.find(playerUUID).thenAcceptAsync(stats -> {
            for (Milestone milestone : config.getMilestones()) {
                checkMilestone(playerUUID, stats, milestone);
            }
        });
    }

    private void checkMilestone(UUID playerUUID, VerificationStatistics stats, Milestone milestone) {
        ProgressService progressService = plugin.getProgressService();
        if (progressService == null) return;

        progressService.hasEntry(playerUUID, ProgressService.TYPE_MILESTONE, milestone.getId())
                .thenAcceptAsync(alreadyRecorded -> {
                    if (alreadyRecorded) return;
                    isMet(playerUUID, stats, milestone).thenAcceptAsync(met -> {
                        if (!met) return;
                        final String playerName = resolvePlayerName(playerUUID);
                        final String milestoneName = milestone.getDisplayName();
                        progressService.recordSync(playerUUID, playerName,
                                ProgressService.TYPE_MILESTONE,
                                milestone.getId(),
                                milestoneName)
                            .thenRunAsync(() -> {
                                if (config.isBroadcastOnReach()) {
                                    final String message = plugin.getMessageService()
                                            .getMessage("milestone.reached",
                                                "player", playerName,
                                                "name", milestoneName);
                                    Bukkit.getScheduler().runTask(plugin, () ->
                                        Bukkit.getServer().broadcast(Component.text(message)));
                                }
                            });
                    });
                });
    }

    private String resolvePlayerName(UUID playerUUID) {
        String name = PlayerUtil.getPlayerName(playerUUID);
        return (name != null) ? name : playerUUID.toString();
    }

    private CompletableFuture<Boolean> isMet(UUID playerUUID, VerificationStatistics stats, Milestone milestone) {
        switch (milestone.getType()) {
            case PLAYTIME:
                if (stats == null) return CompletableFuture.completedFuture(false);
                return CompletableFuture.completedFuture(
                        stats.getTotalPlaytimeSeconds() >= milestone.getThreshold());
            case CUMULATIVE_EARNED:
                if (stats == null || stats.getCumulativeEarned() == null) {
                    return CompletableFuture.completedFuture(false);
                }
                return CompletableFuture.completedFuture(
                        stats.getCumulativeEarned().compareTo(BigDecimal.valueOf(milestone.getThreshold())) >= 0);
            case BALANCE:
                if (stats == null || stats.getHighestBalance() == null) {
                    return CompletableFuture.completedFuture(false);
                }
                return CompletableFuture.completedFuture(
                        stats.getHighestBalance().compareTo(BigDecimal.valueOf(milestone.getThreshold())) >= 0);
            case RANK:
                try {
                    OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
                    String group = plugin.getVaultService().getPrimaryGroup(player);
                    boolean matches = group != null && group.equalsIgnoreCase(milestone.getValue());
                    return CompletableFuture.completedFuture(matches);
                } catch (Exception e) {
                    return CompletableFuture.completedFuture(false);
                }
            case ADVANCEMENT:
                if (plugin.getProgressService() == null) {
                    return CompletableFuture.completedFuture(false);
                }
                return plugin.getProgressService().hasEntry(playerUUID,
                        ProgressService.TYPE_ADVANCEMENT, milestone.getValue());
            default:
                return CompletableFuture.completedFuture(false);
        }
    }
}