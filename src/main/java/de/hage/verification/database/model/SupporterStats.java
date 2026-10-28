package de.hage.verification.database.model;

import java.sql.Timestamp;
import java.util.UUID;

public final class SupporterStats {

    private final UUID supporterUuid;
    private final long totalVerifications;
    private final long monthlyVerifications;
    private final Timestamp firstVerification;
    private final Timestamp lastVerification;

    public SupporterStats(UUID supporterUuid, long totalVerifications, long monthlyVerifications,
                          Timestamp firstVerification, Timestamp lastVerification) {
        this.supporterUuid = supporterUuid;
        this.totalVerifications = totalVerifications;
        this.monthlyVerifications = monthlyVerifications;
        this.firstVerification = firstVerification;
        this.lastVerification = lastVerification;
    }

    public UUID getSupporterUuid() { return supporterUuid; }
    public long getTotalVerifications() { return totalVerifications; }
    public long getMonthlyVerifications() { return monthlyVerifications; }
    public Timestamp getFirstVerification() { return firstVerification; }
    public Timestamp getLastVerification() { return lastVerification; }
}