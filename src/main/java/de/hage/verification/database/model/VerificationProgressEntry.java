package de.hage.verification.database.model;

import java.sql.Timestamp;
import java.util.UUID;

public final class VerificationProgressEntry {

    private final long id;
    private final UUID playerUUID;
    private final String playerName;
    private final String eventType;
    private final String eventKey;
    private final String eventValue;
    private final Timestamp timestamp;

    public VerificationProgressEntry(long id, UUID playerUUID, String playerName,
                                     String eventType, String eventKey,
                                     String eventValue, Timestamp timestamp) {
        this.id = id;
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.eventType = eventType;
        this.eventKey = eventKey;
        this.eventValue = eventValue;
        this.timestamp = timestamp;
    }

    public long getId() { return id; }
    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public String getEventType() { return eventType; }
    public String getEventKey() { return eventKey; }
    public String getEventValue() { return eventValue; }
    public Timestamp getTimestamp() { return timestamp; }
}