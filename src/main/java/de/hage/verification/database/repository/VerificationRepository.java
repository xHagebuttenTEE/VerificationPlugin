package de.hage.verification.database.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import de.hage.verification.database.model.VerificationEntry;
import de.hage.verification.util.AsyncExecutor;

public final class VerificationRepository {

    private final DataSource dataSource;
    private final AsyncExecutor asyncExecutor;
    private final String tableName;

    public VerificationRepository(DataSource dataSource, AsyncExecutor asyncExecutor, String tableName) {
        this.dataSource = dataSource;
        this.asyncExecutor = asyncExecutor;
        this.tableName = tableName;
    }

    public CompletableFuture<Boolean> exists(UUID playerUUID) {
        return asyncExecutor.supplyAsync(() -> {
            String sql = "SELECT 1 FROM " + tableName + " WHERE verified_player = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Prüfen der Verifizierung für " + playerUUID, e);
            }
        });
    }

    public CompletableFuture<Boolean> insertIfAbsent(UUID playerUUID, UUID verificatorUUID) {
        return asyncExecutor.supplyAsync(() -> {
            String sql = "INSERT IGNORE INTO " + tableName
                    + " (verified_player, verificator, verification_time) VALUES (?, ?, CURRENT_TIMESTAMP)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                if (verificatorUUID != null) {
                    stmt.setString(2, verificatorUUID.toString());
                } else {
                    stmt.setNull(2, java.sql.Types.VARCHAR);
                }
                int rows = stmt.executeUpdate();
                return rows > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Einfügen der Verifizierung für " + playerUUID, e);
            }
        });
    }

    public CompletableFuture<Void> delete(UUID playerUUID) {
        return asyncExecutor.runAsync(() -> {
            String sql = "DELETE FROM " + tableName + " WHERE verified_player = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Löschen der Verifizierung für " + playerUUID, e);
            }
        });
    }

    public CompletableFuture<VerificationEntry> findByPlayer(UUID playerUUID) {
        return asyncExecutor.supplyAsync(() -> {
            String sql = "SELECT verified_player, verificator, verification_time FROM " + tableName
                    + " WHERE verified_player = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String verifiedPlayerStr = rs.getString("verified_player");
                        String verificatorStr = rs.getString("verificator");
                        Timestamp time = rs.getTimestamp("verification_time");
                        UUID verified = UUID.fromString(verifiedPlayerStr);
                        UUID verificator = (verificatorStr != null) ? UUID.fromString(verificatorStr) : null;
                        return new VerificationEntry(verified, verificator, time);
                    }
                    return null;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Abrufen der Verifizierung für " + playerUUID, e);
            }
        });
    }
}