package de.hage.verification.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.VerificationProgressEntry;
import de.hage.verification.database.repository.VerificationProgressRepository;

public final class ProgressService {

    public static final String TYPE_ADVANCEMENT = "ADVANCEMENT";
    public static final String TYPE_RANK_UP = "RANK_UP";
    public static final String TYPE_RANK_DOWN = "RANK_DOWN";

    private final VerificationPlugin plugin;
    private final VerificationProgressRepository repository;
    private final boolean enabled;

    public ProgressService(VerificationPlugin plugin,
                           VerificationProgressRepository repository,
                           boolean enabled) {
        this.plugin = plugin;
        this.repository = repository;
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void record(UUID playerUUID, String playerName, String eventType,
                       String eventKey, String eventValue) {
        if (!enabled) return;
        repository.insert(playerUUID, playerName, eventType, eventKey, eventValue)
                .exceptionally(ex -> {
                    plugin.getLogger().log(Level.WARNING,
                            "Fortschritt konnte nicht gespeichert werden.", ex);
                    return null;
                });
    }

    public CompletableFuture<List<VerificationProgressEntry>> getRecent(UUID playerUUID, int limit) {
        if (!enabled) {
            return CompletableFuture.completedFuture(List.of());
        }
        return repository.findRecent(playerUUID, limit);
    }

    public CompletableFuture<Integer> countByType(UUID playerUUID, String eventType) {
        if (!enabled) {
            return CompletableFuture.completedFuture(0);
        }
        return repository.countByType(playerUUID, eventType);
    }
}