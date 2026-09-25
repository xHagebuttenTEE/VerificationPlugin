package de.hage.verification.database.repository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.sql.DataSource;
import de.hage.verification.database.DatabaseType;
import de.hage.verification.database.model.VerificationStatistics;
import de.hage.verification.util.AsyncExecutor;

public final class VerificationStatisticsRepository {

    public static final String TABLE_NAME = "Verification_Statistics";
    public static final String COLUMN_CUMULATIVE_EARNED = "cumulative_earned";

    private final DataSource dataSource;
    private final AsyncExecutor asyncExecutor;
    private final DatabaseType databaseType;

    public VerificationStatisticsRepository(DataSource dataSource, AsyncExecutor asyncExecutor,
                                            DatabaseType databaseType) {
        this.dataSource = dataSource;
        this.asyncExecutor = asyncExecutor;
        this.databaseType = databaseType;
    }

    public CompletableFuture<Void> addPlaytime(UUID playerUUID, String playerName, long additionalSeconds) {
        if (additionalSeconds <= 0) {
            return CompletableFuture.completedFuture(null);
        }
        return asyncExecutor.runAsync(() -> {
            String sql;
            if (databaseType == DatabaseType.SQLITE) {
                sql = "INSERT INTO " + TABLE_NAME
                        + " (player_uuid, player_name, total_playtime_seconds, last_updated)"
                        + " VALUES (?, ?, ?, CURRENT_TIMESTAMP)"
                        + " ON CONFLICT(player_uuid) DO UPDATE SET"
                        + " player_name = excluded.player_name,"
                        + " total_playtime_seconds = total_playtime_seconds + excluded.total_playtime_seconds,"
                        + " last_updated = CURRENT_TIMESTAMP";
            } else {
                sql = "INSERT INTO " + TABLE_NAME
                        + " (player_uuid, player_name, total_playtime_seconds, last_updated)"
                        + " VALUES (?, ?, ?, CURRENT_TIMESTAMP)"
                        + " ON DUPLICATE KEY UPDATE"
                        + " player_name = VALUES(player_name),"
                        + " total_playtime_seconds = total_playtime_seconds + VALUES(total_playtime_seconds),"
                        + " last_updated = CURRENT_TIMESTAMP";
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, playerName);
                stmt.setLong(3, additionalSeconds);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Aktualisieren der Spielzeit für " + playerUUID, e);
            }
        });
    }

    public CompletableFuture<Void> updateBalance(UUID playerUUID, String playerName,
                                                 BigDecimal balance, boolean trackCumulative) {
        if (balance == null) {
            return CompletableFuture.completedFuture(null);
        }
        return asyncExecutor.runAsync(() -> {
            String sql;
            if (databaseType == DatabaseType.SQLITE) {
                sql = "INSERT INTO " + TABLE_NAME
                        + " (player_uuid, player_name, current_balance, highest_balance,"
                        + " cumulative_earned, last_updated)"
                        + " VALUES (?, ?, ?, ?, 0, CURRENT_TIMESTAMP)"
                        + " ON CONFLICT(player_uuid) DO UPDATE SET"
                        + " player_name = excluded.player_name,"
                        + (trackCumulative
                            ? " cumulative_earned = cumulative_earned + MAX(0, excluded.current_balance - current_balance),"
                            : "")
                        + " current_balance = excluded.current_balance,"
                        + " highest_balance = MAX(highest_balance, excluded.highest_balance),"
                        + " last_updated = CURRENT_TIMESTAMP";
            } else {
                sql = "INSERT INTO " + TABLE_NAME
                        + " (player_uuid, player_name, current_balance, highest_balance,"
                        + " cumulative_earned, last_updated)"
                        + " VALUES (?, ?, ?, ?, 0, CURRENT_TIMESTAMP)"
                        + " ON DUPLICATE KEY UPDATE"
                        + " player_name = VALUES(player_name),"
                        + (trackCumulative
                            ? " cumulative_earned = cumulative_earned + GREATEST(0, VALUES(current_balance) - current_balance),"
                            : "")
                        + " current_balance = VALUES(current_balance),"
                        + " highest_balance = GREATEST(highest_balance, VALUES(highest_balance)),"
                        + " last_updated = CURRENT_TIMESTAMP";
            }
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, playerName);
                stmt.setBigDecimal(3, balance);
                stmt.setBigDecimal(4, balance);
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Aktualisieren des Kontostands für " + playerUUID, e);
            }
        });
    }

    public CompletableFuture<VerificationStatistics> find(UUID playerUUID) {
        return asyncExecutor.supplyAsync(() -> {
            String sql = "SELECT player_uuid, player_name, total_playtime_seconds,"
                    + " current_balance, highest_balance, cumulative_earned, last_updated"
                    + " FROM " + TABLE_NAME + " WHERE player_uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                    return null;
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Laden der Statistik für " + playerUUID, e);
            }
        });
    }

    public CompletableFuture<Map<UUID, VerificationStatistics>> findAll(List<UUID> playerUUIDs) {
        return asyncExecutor.supplyAsync(() -> {
            Map<UUID, VerificationStatistics> result = new HashMap<>();
            if (playerUUIDs == null || playerUUIDs.isEmpty()) {
                return result;
            }
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < playerUUIDs.size(); i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
            }
            String sql = "SELECT player_uuid, player_name, total_playtime_seconds,"
                    + " current_balance, highest_balance, cumulative_earned, last_updated"
                    + " FROM " + TABLE_NAME + " WHERE player_uuid IN (" + placeholders + ")";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < playerUUIDs.size(); i++) {
                    stmt.setString(i + 1, playerUUIDs.get(i).toString());
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        VerificationStatistics stats = mapRow(rs);
                        result.put(stats.getPlayerUUID(), stats);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Laden der Statistiken", e);
            }
            return result;
        });
    }

    private VerificationStatistics mapRow(ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("player_uuid"));
        String name = rs.getString("player_name");
        long playtime = rs.getLong("total_playtime_seconds");
        BigDecimal current = rs.getBigDecimal("current_balance");
        BigDecimal highest = rs.getBigDecimal("highest_balance");
        BigDecimal cumulative = rs.getBigDecimal("cumulative_earned");
        Timestamp updated = rs.getTimestamp("last_updated");
        return new VerificationStatistics(uuid, name, playtime, current, highest, cumulative, updated);
    }
}