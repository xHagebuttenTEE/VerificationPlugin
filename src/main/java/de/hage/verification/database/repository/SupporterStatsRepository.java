package de.hage.verification.database.repository;

import de.hage.verification.database.model.SupporterStats;
import de.hage.verification.util.AsyncExecutor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SupporterStatsRepository {

    private final DataSource dataSource;
    private final AsyncExecutor asyncExecutor;

    public SupporterStatsRepository(DataSource dataSource, AsyncExecutor asyncExecutor) {
        this.dataSource = dataSource;
        this.asyncExecutor = asyncExecutor;
    }

    public CompletableFuture<List<SupporterStats>> findAll() {
        return asyncExecutor.supplyAsync(() -> {
            List<SupporterStats> result = new ArrayList<>();
            String sql = "SELECT supporter_uuid, total_verifications, monthly_verifications, "
                    + "first_verification, last_verification FROM v_supporter_stats "
                    + "ORDER BY total_verifications DESC";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(mapRow(rs));
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Laden der Supporter-Statistiken", e);
            }
            return result;
        });
    }

    public CompletableFuture<List<SupporterStats>> findTop(int limit) {
        return asyncExecutor.supplyAsync(() -> {
            List<SupporterStats> result = new ArrayList<>();
            String sql = "SELECT supporter_uuid, total_verifications, monthly_verifications, "
                    + "first_verification, last_verification FROM v_supporter_stats "
                    + "ORDER BY total_verifications DESC LIMIT ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) result.add(mapRow(rs));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Laden der Top-Supporter", e);
            }
            return result;
        });
    }

    public CompletableFuture<SupporterStats> findOne(UUID supporterUuid) {
        return asyncExecutor.supplyAsync(() -> {
            String sql = "SELECT supporter_uuid, total_verifications, monthly_verifications, "
                    + "first_verification, last_verification FROM v_supporter_stats "
                    + "WHERE supporter_uuid = ?";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, supporterUuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) return mapRow(rs);
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Laden der Supporter-Statistik für " + supporterUuid, e);
            }
            return null;
        });
    }

    private SupporterStats mapRow(ResultSet rs) throws SQLException {
        String uuidStr = rs.getString("supporter_uuid");
        UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : null;
        long total = rs.getLong("total_verifications");
        long monthly = rs.getLong("monthly_verifications");
        Timestamp first = rs.getTimestamp("first_verification");
        Timestamp last = rs.getTimestamp("last_verification");
        return new SupporterStats(uuid, total, monthly, first, last);
    }
}