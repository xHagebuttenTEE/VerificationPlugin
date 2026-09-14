package de.hage.verification.database.model;

import java.sql.Timestamp;
import java.util.UUID;

public final class VerificationEntry {

    private final UUID verifiedPlayer;
    private final UUID verificator;
    private final Timestamp verificationTime;

    public VerificationEntry(UUID verifiedPlayer, UUID verificator, Timestamp verificationTime) {
        this.verifiedPlayer = verifiedPlayer;
        this.verificator = verificator;
        this.verificationTime = verificationTime;
    }

    public UUID getVerifiedPlayer() {
        return verifiedPlayer;
    }

    public UUID getVerificator() {
        return verificator;
    }

    public Timestamp getVerificationTime() {
        return verificationTime;
    }

    public boolean hasVerificator() {
        return verificator != null;
    }
}