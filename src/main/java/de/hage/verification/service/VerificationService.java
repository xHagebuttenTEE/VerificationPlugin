package de.hage.verification.service;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.VerificationHistoryEntry;
import de.hage.verification.database.repository.VerificationHistoryRepository;
import de.hage.verification.database.repository.VerificationRepository;
import de.hage.verification.util.PlayerUtil;

public final class VerificationService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final VerificationPlugin plugin;
    private final VerificationRepository repository;
    private final VerificationHistoryRepository historyRepository;
    private final VaultService vaultService;
    private final String defaultGroup;
    private final String verifiedGroup;
    private final String verifiedDisplayName;
    private final boolean historyEnabled;

    public VerificationService(VerificationPlugin plugin,
                               VerificationRepository repository,
                               VerificationHistoryRepository historyRepository,
                               VaultService vaultService,
                               String defaultGroup,
                               String verifiedGroup,
                               String verifiedDisplayName,
                               boolean historyEnabled) {
        this.plugin = plugin;
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.vaultService = vaultService;
        this.defaultGroup = defaultGroup;
        this.verifiedGroup = verifiedGroup;
        this.verifiedDisplayName = verifiedDisplayName;
        this.historyEnabled = historyEnabled;
    }

    public CompletableFuture<Boolean> verifyPlayer(UUID playerUUID, UUID verificatorUUID) {
        return repository.exists(playerUUID).thenComposeAsync(exists -> {
            if (exists) {
                return CompletableFuture.completedFuture(false);
            }
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
            return repository.insertIfAbsent(playerUUID, verificatorUUID)
                .thenComposeAsync(inserted -> {
                    if (!inserted) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return applyLuckPermsChange(player, playerUUID, verificatorUUID)
                            .thenApplyAsync(v -> true);
                });
        });
    }

    private CompletableFuture<Void> applyLuckPermsChange(OfflinePlayer player, UUID playerUUID, UUID verificatorUUID) {
        try {
            vaultService.removePlayerGroup(player, defaultGroup);
            vaultService.addPlayerGroup(player, verifiedGroup);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "LuckPerms-Update fehlgeschlagen für " + playerUUID + ", rollback wird ausgeführt.", e);
            return repository.delete(playerUUID).thenComposeAsync(v -> {
                CompletableFuture<Void> failed = new CompletableFuture<>();
                failed.completeExceptionally(e);
                return failed;
            });
        }
        if (historyEnabled) {
            String actorName = (verificatorUUID != null)
                    ? PlayerUtil.getPlayerName(verificatorUUID)
                    : "CONSOLE";
            if (actorName == null) actorName = "CONSOLE";
            String playerName = player.getName();
            if (playerName == null) playerName = playerUUID.toString();
            String details = defaultGroup + " → " + verifiedGroup;
            return historyRepository.insert(playerUUID, playerName,
                    verificatorUUID, actorName, "VERIFIED", details)
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.WARNING,
                                "History-Eintrag konnte nicht geschrieben werden.", ex);
                        return null;
                    });
        }
        return CompletableFuture.completedFuture(null);
    }

    public CompletableFuture<VerificationInfo> getVerificationInfo(UUID playerUUID) {
        return repository.findByPlayer(playerUUID).thenComposeAsync(entry -> {
            if (entry == null) {
                return CompletableFuture.completedFuture(
                        new VerificationInfo(false, null, null, null, null, null));
            }
            final String playerName = PlayerUtil.getPlayerName(entry.getVerifiedPlayer());
            final String verificatorName;
            if (entry.hasVerificator()) {
                verificatorName = PlayerUtil.getPlayerName(entry.getVerificator());
            } else {
                verificatorName = null;
            }
            final String formattedTime = entry.getVerificationTime() != null
                    ? DATE_FORMAT.format(entry.getVerificationTime().toInstant())
                    : null;

            if (!historyEnabled) {
                return CompletableFuture.completedFuture(
                        new VerificationInfo(true, playerName, verificatorName,
                                formattedTime, null, null));
            }
            int limit = plugin.getConfig().getInt("history.maxEntriesInInfo", 3);
            if (limit <= 0) {
                return CompletableFuture.completedFuture(
                        new VerificationInfo(true, playerName, verificatorName,
                                formattedTime, null, null));
            }
            return historyRepository.findRecent(playerUUID, limit)
                    .thenApplyAsync(history -> new VerificationInfo(true, playerName,
                            verificatorName, formattedTime, history, limit));
        });
    }

    public CompletableFuture<List<VerificationHistoryEntry>> getHistory(UUID playerUUID) {
        int limit = plugin.getConfig().getInt("history.maxEntriesInHistoryCommand", 10);
        return historyRepository.findRecent(playerUUID, limit);
    }

    public String formatTime(Timestamp timestamp) {
        if (timestamp == null) return null;
        return DATE_FORMAT.format(timestamp.toInstant());
    }

    public String getVerifiedDisplayName() {
        return verifiedDisplayName;
    }

    public boolean isHistoryEnabled() {
        return historyEnabled;
    }

    public static final class VerificationInfo {
        private final boolean verified;
        private final String playerName;
        private final String verificatorName;
        private final String formattedTime;
        private final List<VerificationHistoryEntry> history;
        private final Integer historyLimit;

        public VerificationInfo(boolean verified, String playerName, String verificatorName,
                                String formattedTime, List<VerificationHistoryEntry> history,
                                Integer historyLimit) {
            this.verified = verified;
            this.playerName = playerName;
            this.verificatorName = verificatorName;
            this.formattedTime = formattedTime;
            this.history = history;
            this.historyLimit = historyLimit;
        }

        public boolean isVerified() {
            return verified;
        }

        public String getPlayerName() {
            return playerName;
        }

        public String getVerificatorName() {
            return verificatorName;
        }

        public String getFormattedTime() {
            return formattedTime;
        }

        public List<VerificationHistoryEntry> getHistory() {
            return history;
        }

        public Integer getHistoryLimit() {
            return historyLimit;
        }
    }
}