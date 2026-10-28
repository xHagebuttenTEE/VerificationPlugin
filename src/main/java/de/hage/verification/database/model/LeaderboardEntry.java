package de.hage.verification.database.model;

import java.util.UUID;

public final class LeaderboardEntry {

    private final String yearMonth;
    private final UUID supporterUuid;
    private final long verifications;

    public LeaderboardEntry(String yearMonth, UUID supporterUuid, long verifications) {
        this.yearMonth = yearMonth;
        this.supporterUuid = supporterUuid;
        this.verifications = verifications;
    }

    public String getYearMonth() { return yearMonth; }
    public UUID getSupporterUuid() { return supporterUuid; }
    public long getVerifications() { return verifications; }
}