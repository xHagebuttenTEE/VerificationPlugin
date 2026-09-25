package de.hage.verification.task;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;
import javax.sql.DataSource;
import org.bukkit.scheduler.BukkitTask;
import de.hage.verification.VerificationPlugin;

public final class HealthCheckTask {

    private final VerificationPlugin plugin;
    private final DataSource dataSource;
    private BukkitTask task;
    private boolean lastState = true;

    public HealthCheckTask(VerificationPlugin plugin, DataSource dataSource) {
        this.plugin = plugin;
        this.dataSource = dataSource;
    }

    public void start() {
        int interval = plugin.getConfig().getInt("healthcheck.interval-seconds", 60);
        if (interval < 10) interval = 10;
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::check,
                interval * 20L, interval * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void check() {
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(3)) {
                throw new SQLException("Verbindung nicht gültig");
            }
            if (!lastState) {
                plugin.getLogger().info("Datenbankverbindung wiederhergestellt.");
                lastState = true;
            }
        } catch (SQLException e) {
            if (lastState) {
                plugin.getLogger().log(Level.SEVERE,
                        "Health-Check: Datenbankverbindung fehlgeschlagen!", e);
                lastState = false;
            }
        }
    }
}