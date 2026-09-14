package de.hage.verification.database.model;

import java.sql.Timestamp;
import java.util.UUID;

public final class VerificationHistoryEntry {

    private final long id;
    private final UUID playerUUID;
    private final String playerName;
    private final UUID actorUUID;
    private final String actorName;
    private final String action;
    private final String details;
    private final Timestamp timestamp;

    public VerificationHistoryEntry(long id, UUID playerUUID, String playerName,
                                    UUID actorUUID, String actorName,
                                    String action, String details, Timestamp timestamp) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.actorUUID = actorUUID;
        this.actorName = actorName;
        this.action = action;
        this.details = details;
        this.timestamp = timestamp;
    }

    public long getId() {
        return id;
    }

    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public String getPlayerName() {
        return playerName;
    }

    public UUID getActorUUID() {
        return actorUUID;
    }

    public String getActorName() {
        return actorName;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public boolean hasActor() {
        return actorUUID != null || (actorName != null && !actorName.isEmpty());
    }
}