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
import de.hage.verification.database.model.VerificationHistoryEntry;
import de.hage.verification.util.AsyncExecutor;

public final class VerificationHistoryRepository {

    public static final String TABLE_NAME = "Verification_History";

    private final DataSource dataSource;
    private final AsyncExecutor asyncExecutor;

    public VerificationHistoryRepository(DataSource dataSource, AsyncExecutor asyncExecutor) {
        this.dataSource = dataSource;
        this.asyncExecutor = asyncExecutor;
    }

    public CompletableFuture<Void> insert(UUID playerUUID, String playerName,
                                          UUID actorUUID, String actorName,
                                          String action, String details) {
        return asyncExecutor.runAsync(() -> {
            String sql = "INSERT INTO " + TABLE_NAME
                    + " (player_uuid, player_name, actor_uuid, actor_name, action, details, timestamp)"
                    + " VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID != null ? playerUUID.toString() : null);
                stmt.setString(2, playerName);
                if (actorUUID != null) {
                    stmt.setString(3, actorUUID.toString());
                } else {
                    stmt.setNull(3, java.sql.Types.VARCHAR);
                }
                stmt.setString(4, actorName);
                stmt.setString(5, action);
                stmt.setString(6, details);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Schreiben des History-Eintrags", e);
            }
        });
    }

    public CompletableFuture<List<VerificationHistoryEntry>> findRecent(UUID playerUUID, int limit) {
        return asyncExecutor.supplyAsync(() -> {
            List<VerificationHistoryEntry> entries = new ArrayList<>();
            String sql = "SELECT id, player_uuid, player_name, actor_uuid, actor_name, action, details, timestamp"
                    + " FROM " + TABLE_NAME
                    + " WHERE player_uuid = ? ORDER BY timestamp DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setInt(2, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        entries.add(mapRow(rs));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Lesen der History", e);
            }
            return entries;
        });
    }

    private VerificationHistoryEntry mapRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("id");
        String playerUuidStr = rs.getString("player_uuid");
        UUID playerUUID = playerUuidStr != null ? UUID.fromString(playerUuidStr) : null;
        String playerName = rs.getString("player_name");
        String actorUuidStr = rs.getString("actor_uuid");
        UUID actorUUID = actorUuidStr != null ? UUID.fromString(actorUuidStr) : null;
        String actorName = rs.getString("actor_name");
        String action = rs.getString("action");
        String details = rs.getString("details");
        Timestamp timestamp = rs.getTimestamp("timestamp");
        return new VerificationHistoryEntry(id, playerUUID, playerName, actorUUID, actorName,
                action, details, timestamp);
    }
}