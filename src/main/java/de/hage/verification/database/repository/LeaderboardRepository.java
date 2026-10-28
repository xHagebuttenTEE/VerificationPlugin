package de.hage.verification.database.repository;

import de.hage.verification.database.model.LeaderboardEntry;
import de.hage.verification.util.AsyncExecutor;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class LeaderboardRepository {

    private final DataSource dataSource;
    private final AsyncExecutor asyncExecutor;

    public LeaderboardRepository(DataSource dataSource, AsyncExecutor asyncExecutor) {
        this.dataSource = dataSource;
        this.asyncExecutor = asyncExecutor;
    }

    public CompletableFuture<List<LeaderboardEntry>> findByMonth(String yearMonth) {
        return asyncExecutor.supplyAsync(() -> {
            List<LeaderboardEntry> result = new ArrayList<>();
            String sql = "SELECT `year_month`, supporter_uuid, verifications FROM v_monthly_leaderboard WHERE `year_month` = ? ORDER BY verifications DESC";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, yearMonth);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) result.add(mapRow(rs));
                }
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Laden der Rangliste für " + yearMonth, e);
            }
            return result;
        });
    }

    public CompletableFuture<List<String>> findAvailableMonths() {
        return asyncExecutor.supplyAsync(() -> {
            List<String> result = new ArrayList<>();
            String sql = "SELECT DISTINCT `year_month` FROM v_monthly_leaderboard ORDER BY `year_month` DESC";
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) result.add(rs.getString("year_month"));
            } catch (SQLException e) {
                throw new RuntimeException("Fehler beim Laden der verfügbaren Monate", e);
            }
            return result;
        });
    }

    private LeaderboardEntry mapRow(ResultSet rs) throws SQLException {
        String ym = rs.getString("year_month");
        String uuidStr = rs.getString("supporter_uuid");
        UUID uuid = uuidStr != null ? UUID.fromString(uuidStr) : null;
        long v = rs.getLong("verifications");
        return new LeaderboardEntry(ym, uuid, v);
    }
}