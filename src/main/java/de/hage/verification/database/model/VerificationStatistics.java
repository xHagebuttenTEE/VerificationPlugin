package de.hage.verification.database.model;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.UUID;

public final class VerificationStatistics {

    private final UUID playerUUID;
    private final String playerName;
    private final long totalPlaytimeSeconds;
    private final BigDecimal currentBalance;
    private final BigDecimal highestBalance;
    private final BigDecimal cumulativeEarned;
    private final Timestamp lastUpdated;

    public VerificationStatistics(UUID playerUUID, String playerName,
                                   long totalPlaytimeSeconds,
                                   BigDecimal currentBalance,
                                   BigDecimal highestBalance,
                                   BigDecimal cumulativeEarned,
                                   Timestamp lastUpdated) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.totalPlaytimeSeconds = totalPlaytimeSeconds;
        this.currentBalance = currentBalance;
        this.highestBalance = highestBalance;
        this.cumulativeEarned = cumulativeEarned;
        this.lastUpdated = lastUpdated;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public long getTotalPlaytimeSeconds() { return totalPlaytimeSeconds; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public BigDecimal getHighestBalance() { return highestBalance; }
    public BigDecimal getCumulativeEarned() { return cumulativeEarned; }
    public Timestamp getLastUpdated() { return lastUpdated; }
}