package de.hage.verification.database.repository;

import de.hage.verification.database.DatabaseType;
import de.hage.verification.util.AsyncExecutor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class AdvancedStatsRepository {

    private static final String SCHEMA = "AdvancedStats";
    private static final String TABLE = "LongStats";
    private static final String PLAYTIME_IDENTIFIER = "PLAY_ONE_MINUTE";

    private final DataSource dataSource;
    private final AsyncExecutor asyncExecutor;
    private final DatabaseType databaseType;
    private volatile Boolean available;

    public AdvancedStatsRepository(DataSource dataSource, AsyncExecutor asyncExecutor, DatabaseType databaseType) {
        this.dataSource = dataSource;
        this.asyncExecutor = asyncExecutor;
        this.databaseType = databaseType;
    }

    public boolean isAvailable() {
        if (databaseType != DatabaseType.MARIADB) return false;
        if (available != null) return available;
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, SCHEMA, TABLE, new String[]{"TABLE"})) {
                available = rs.next();
            }
        } catch (SQLException e) {
            available = false;
        }
        return available;
    }

    public CompletableFuture<Map<String, Long>> findLatest(UUID playerUuid, List<String> identifiers) {
        return asyncExecutor.supplyAsync(() -> {
            Map<String, Long> result = new HashMap<>();
            if (identifiers == null || identifiers.isEmpty() || !isAvailable()) return result;
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < identifiers.size(); i++) {
                if (i > 0) placeholders.append(",");
                placeholders.append("?");
            }
            String sql = "SELECT stat_identifier, long_value FROM ("
                    + "SELECT stat_identifier, long_value, ROW_NUMBER() OVER (PARTITION BY stat_identifier ORDER BY raw_timestamp DESC) AS rn "
                    + "FROM " + SCHEMA + "." + TABLE + " WHERE player_uid = ? AND stat_identifier IN (" + placeholders + ")"
                    + ") t WHERE rn = 1";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                for (int i = 0; i < identifiers.size(); i++) {
                    stmt.setString(i + 2, identifiers.get(i));
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        result.put(rs.getString("stat_identifier"), rs.getLong("long_value"));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Laden der AdvancedStats für " + playerUuid, e);
            }
            return result;
        });
    }

    public CompletableFuture<Long> findPlaytime(UUID playerUuid) {
        return asyncExecutor.supplyAsync(() -> {
            if (!isAvailable()) return null;
            String sql = "SELECT long_value FROM " + SCHEMA + "." + TABLE
                    + " WHERE player_uid = ? AND stat_identifier = '" + PLAYTIME_IDENTIFIER + "'"
                    + " ORDER BY raw_timestamp DESC LIMIT 1";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return rs.getLong("long_value");
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Laden der Spielzeit für " + playerUuid, e);
            }
            return null;
        });
    }

    public CompletableFuture<Map<UUID, Long>> findPlaytimeBatch(List<UUID> uuids) {
        return asyncExecutor.supplyAsync(() -> {
            Map<UUID, Long> result = new HashMap<>();
            if (uuids == null || uuids.isEmpty() || !isAvailable()) return result;
            StringBuilder ph = new StringBuilder();
            for (int i = 0; i < uuids.size(); i++) {
                if (i > 0) ph.append(",");
                ph.append("?");
            }
            String sql = "SELECT player_uid, long_value FROM ("
                    + "SELECT player_uid, long_value, ROW_NUMBER() OVER (PARTITION BY player_uid ORDER BY raw_timestamp DESC) AS rn "
                    + "FROM " + SCHEMA + "." + TABLE
                    + " WHERE player_uid IN (" + ph + ") AND stat_identifier = '" + PLAYTIME_IDENTIFIER + "'"
                    + ") t WHERE rn = 1";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (int i = 0; i < uuids.size(); i++) {
                    stmt.setString(i + 1, uuids.get(i).toString());
                }
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String u = rs.getString("player_uid");
                        if (u != null) result.put(UUID.fromString(u), rs.getLong("long_value"));
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Laden der Spielzeiten", e);
            }
            return result;
        });
    }
}