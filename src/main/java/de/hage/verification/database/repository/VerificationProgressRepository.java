package de.hage.verification.database.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import de.hage.verification.database.model.VerificationProgressEntry;
import de.hage.verification.util.AsyncExecutor;

public final class VerificationProgressRepository {

    public static final String TABLE_NAME = "Verification_Progress";

    private final DataSource dataSource;
    private final AsyncExecutor asyncExecutor;

    public VerificationProgressRepository(DataSource dataSource, AsyncExecutor asyncExecutor) {
        this.dataSource = dataSource;
        this.asyncExecutor = asyncExecutor;
    }

    public CompletableFuture<Void> insert(UUID playerUUID, String playerName,
                                          String eventType, String eventKey, String eventValue) {
        return asyncExecutor.runAsync(() -> {
            String sql = "INSERT INTO " + TABLE_NAME
                    + " (player_uuid, player_name, event_type, event_key, event_value, timestamp)"
                    + " VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, playerName);
                stmt.setString(3, eventType);
                stmt.setString(4, eventKey);
                stmt.setString(5, eventValue);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Schreiben des Fortschritts", e);
            }
        });
    }

    public CompletableFuture<List<VerificationProgressEntry>> findRecent(UUID playerUUID, int limit) {
        return asyncExecutor.supplyAsync(() -> {
            List<VerificationProgressEntry> result = new ArrayList<>();
            String sql = "SELECT id, player_uuid, player_name, event_type, event_key, event_value, timestamp"
                    + " FROM " + TABLE_NAME
                    + " WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setInt(2, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        result.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Laden der Fortschritte", e);
            }
            return result;
        });
    }

    public CompletableFuture<Integer> countByType(UUID playerUUID, String eventType) {
        return asyncExecutor.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM " + TABLE_NAME
                    + " WHERE player_uuid = ? AND event_type = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, eventType);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                    return 0;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Zählen der Fortschritte", e);
            }
        });
    }

    private VerificationProgressEntry mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String uuidStr = rs.getString("player_uuid");
        UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : null;
        String name = rs.getString("player_name");
        String type = rs.getString("event_type");
        String key = rs.getString("event_key");
        String value = rs.getString("event_value");
        Timestamp ts = rs.getTimestamp("timestamp");
        return new VerificationProgressEntry(id, uuid, name, type, key, value, ts);
    }
}