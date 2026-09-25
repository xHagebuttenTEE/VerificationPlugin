package de.hage.verification;

import java.io.File;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import de.hage.verification.command.VerificationCommand;
import de.hage.verification.config.DatabaseConfig;
import de.hage.verification.database.DatabaseType;
import de.hage.verification.database.repository.VerificationHistoryRepository;
import de.hage.verification.database.repository.VerificationProgressRepository;
import de.hage.verification.database.repository.VerificationRepository;
import de.hage.verification.database.repository.VerificationStatisticsRepository;
import de.hage.verification.listener.AdvancementListener;
import de.hage.verification.listener.LuckPermsRankListener;
import de.hage.verification.service.EconomyService;
import de.hage.verification.service.MessageService;
import de.hage.verification.service.ProgressService;
import de.hage.verification.service.StatisticsService;
import de.hage.verification.service.VaultService;
import de.hage.verification.service.VerificationService;
import de.hage.verification.task.HealthCheckTask;
import de.hage.verification.ui.VerificationUIListener;
import de.hage.verification.util.AsyncExecutor;
import net.milkbowl.vault.permission.Permission;

public final class VerificationPlugin extends JavaPlugin {

    private HikariDataSource dataSource;
    private Permission permission;
    private String tableName;
    private DatabaseType databaseType;

    private AsyncExecutor asyncExecutor;
    private VerificationRepository repository;
    private VerificationHistoryRepository historyRepository;
    private VerificationStatisticsRepository statisticsRepository;
    private VerificationProgressRepository progressRepository;
    private VaultService vaultService;
    private MessageService messageService;
    private EconomyService economyService;
    private VerificationService verificationService;
    private StatisticsService statisticsService;
    private ProgressService progressService;
    private HealthCheckTask healthCheckTask;
    private DatabaseConfig databaseConfig;

    private String defaultGroup;
    private String verifiedGroup;
    private String verifiedDisplayName;
    private boolean historyEnabled;
    private boolean statisticsEnabled;
    private boolean trackPlaytime;
    private boolean trackBalance;
    private boolean trackCumulativeEarned;
    private boolean progressEnabled;
    private boolean trackAdvancements;
    private boolean trackRankChanges;
    private long cacheTtl;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        this.databaseConfig = new DatabaseConfig(this);

        if (!setupVault()) {
            getLogger().severe("Vault konnte nicht eingerichtet werden. Plugin wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupDatabase()) {
            getLogger().severe("Datenbank konnte nicht eingerichtet werden. Plugin wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadConfigValues();

        if (databaseType == DatabaseType.SQLITE) {
            if (!createSqliteTables()) {
                getLogger().severe("SQLite-Tabellen konnten nicht angelegt werden. Plugin wird deaktiviert.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            getLogger().info("SQLite-Schema wurde sichergestellt.");
        } else {
            if (!checkTableExists(tableName)) {
                getLogger().severe("Die Tabelle '" + tableName + "' existiert nicht. Plugin wird deaktiviert.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            if (historyEnabled && !checkTableExists(VerificationHistoryRepository.TABLE_NAME)) {
                if (!createHistoryTable()) {
                    getLogger().severe("History-Tabelle konnte nicht angelegt werden. Plugin wird deaktiviert.");
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                getLogger().info("Tabelle '" + VerificationHistoryRepository.TABLE_NAME + "' wurde angelegt.");
            }
            if (statisticsEnabled && !checkTableExists(VerificationStatisticsRepository.TABLE_NAME)) {
                if (!createStatisticsTable()) {
                    getLogger().severe("Statistik-Tabelle konnte nicht angelegt werden. Plugin wird deaktiviert.");
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                getLogger().info("Tabelle '" + VerificationStatisticsRepository.TABLE_NAME + "' wurde angelegt.");
            }
            if (progressEnabled && !checkTableExists(VerificationProgressRepository.TABLE_NAME)) {
                if (!createProgressTable()) {
                    getLogger().severe("Progress-Tabelle konnte nicht angelegt werden. Plugin wird deaktiviert.");
                    getServer().getPluginManager().disablePlugin(this);
                    return;
                }
                getLogger().info("Tabelle '" + VerificationProgressRepository.TABLE_NAME + "' wurde angelegt.");
            }
            if (statisticsEnabled && !columnExists(VerificationStatisticsRepository.TABLE_NAME,
                    VerificationStatisticsRepository.COLUMN_CUMULATIVE_EARNED)) {
                if (!addCumulativeEarnedColumnMariaDb()) {
                    getLogger().warning("Konnte 'cumulative_earned' nicht zur Statistik-Tabelle hinzufügen.");
                } else {
                    getLogger().info("Spalte 'cumulative_earned' zur Statistik-Tabelle hinzugefügt.");
                }
            }
        }

        this.asyncExecutor = new AsyncExecutor(this);
        this.repository = new VerificationRepository(dataSource, asyncExecutor, tableName, databaseType);
        this.historyRepository = new VerificationHistoryRepository(dataSource, asyncExecutor);
        this.statisticsRepository = new VerificationStatisticsRepository(dataSource, asyncExecutor, databaseType);
        this.progressRepository = new VerificationProgressRepository(dataSource, asyncExecutor);
        this.vaultService = new VaultService();
        this.messageService = new MessageService(this);
        this.economyService = new EconomyService();

        this.verificationService = new VerificationService(this, repository, historyRepository,
                vaultService, defaultGroup, verifiedGroup, verifiedDisplayName,
                historyEnabled, cacheTtl);

        this.progressService = new ProgressService(this, progressRepository, progressEnabled);

        if (statisticsEnabled) {
            this.statisticsService = new StatisticsService(this, statisticsRepository,
                    economyService, trackPlaytime, trackBalance, trackCumulativeEarned);
            statisticsService.start();
        }

        if (progressEnabled && trackAdvancements) {
            getServer().getPluginManager().registerEvents(new AdvancementListener(this, true), this);
        }

        if (progressEnabled && trackRankChanges) {
            new LuckPermsRankListener(this, true).register();
        }

        validateGroups();

        if (getConfig().getBoolean("healthcheck.enabled", true)) {
            this.healthCheckTask = new HealthCheckTask(this, dataSource);
            healthCheckTask.start();
        }

        getServer().getPluginManager().registerEvents(new VerificationUIListener(), this);

        PluginCommand verificationCmd = getCommand("verification");
        if (verificationCmd != null) {
            VerificationCommand executor = new VerificationCommand(this);
            verificationCmd.setExecutor(executor);
            verificationCmd.setTabCompleter(executor);
        } else {
            getLogger().severe("Befehl 'verification' nicht in plugin.yml definiert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("VerificationPlugin erfolgreich aktiviert (Datenbank: "
                + databaseType.name() + ").");
    }

    @Override
    public void onDisable() {
        if (healthCheckTask != null) {
            healthCheckTask.stop();
        }
        if (statisticsService != null) {
            statisticsService.stop();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private boolean setupVault() {
        RegisteredServiceProvider<Permission> registration = getServer()
                .getServicesManager().getRegistration(Permission.class);
        if (registration == null) return false;
        permission = registration.getProvider();
        return permission != null;
    }

    private boolean setupDatabase() {
        tableName = databaseConfig.getTable();

        String typeStr = databaseConfig.getType();

        if (typeStr.equals("sqlite")) {
            databaseType = DatabaseType.SQLITE;
        } else if (typeStr.equals("mariadb") || typeStr.equals("mysql")) {
            databaseType = DatabaseType.MARIADB;
        } else {
            databaseType = databaseConfig.hasCredentials() ? DatabaseType.MARIADB : DatabaseType.SQLITE;
        }

        if (databaseType == DatabaseType.SQLITE) {
            return setupSqlite();
        }
        return setupMariaDb();
    }

    private boolean setupSqlite() {
        String filename = databaseConfig.getSqliteFile();
        if (filename == null || filename.isEmpty()) filename = "verification.db";
        File dbFile = new File(getDataFolder(), filename);
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Plugin-Datenordner konnte nicht erstellt werden.");
            return false;
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setDriverClassName("org.sqlite.JDBC");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setConnectionTestQuery("SELECT 1");
        config.setConnectionInitSql("PRAGMA foreign_keys = ON; PRAGMA journal_mode = WAL;");

        try {
            dataSource = new HikariDataSource(config);
            try (Connection conn = dataSource.getConnection()) {
                getLogger().info("SQLite-Datenbankverbindung hergestellt (" + filename + ").");
            }
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "SQLite-Verbindung fehlgeschlagen.", e);
            return false;
        }
    }

    private boolean setupMariaDb() {
        String host = databaseConfig.getHost();
        int port = databaseConfig.getPort();
        String database = databaseConfig.getName();
        String user = databaseConfig.getUser();
        String password = databaseConfig.getPassword();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(databaseConfig.getMaxPoolSize());
        config.setMinimumIdle(databaseConfig.getMinIdle());
        config.setConnectionTimeout(databaseConfig.getConnectionTimeout());
        config.setDriverClassName("org.mariadb.jdbc.Driver");

        try {
            dataSource = new HikariDataSource(config);
            try (Connection conn = dataSource.getConnection()) {
                getLogger().info("MariaDB-Datenbankverbindung hergestellt.");
            }
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "MariaDB-Verbindung fehlgeschlagen.", e);
            return false;
        }
    }

    private boolean checkTableExists(String table) {
        if (dataSource == null) return false;
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getTables(null, null, table, new String[]{"TABLE"})) {
                return rs.next();
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Fehler beim Prüfen der Tabelle '" + table + "'.", e);
            return false;
        }
    }

    private boolean columnExists(String table, String column) {
        if (dataSource == null) return false;
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            try (ResultSet rs = meta.getColumns(null, null, table, column)) {
                return rs.next();
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Fehler beim Prüfen der Spalte '" + table + "." + column + "'.", e);
            return false;
        }
    }

    private boolean createSqliteTables() {
        String[] statements = {
            "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "verified_player VARCHAR(36) PRIMARY KEY, "
                + "verificator VARCHAR(36), "
                + "verification_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")",
            "CREATE TABLE IF NOT EXISTS " + VerificationHistoryRepository.TABLE_NAME + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "player_name VARCHAR(32) NOT NULL, "
                + "actor_uuid VARCHAR(36), "
                + "actor_name VARCHAR(32), "
                + "action VARCHAR(32) NOT NULL, "
                + "details VARCHAR(255), "
                + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")",
            "CREATE INDEX IF NOT EXISTS idx_history_player_uuid ON "
                + VerificationHistoryRepository.TABLE_NAME + "(player_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_history_timestamp ON "
                + VerificationHistoryRepository.TABLE_NAME + "(timestamp)",
            "CREATE TABLE IF NOT EXISTS " + VerificationStatisticsRepository.TABLE_NAME + " ("
                + "player_uuid VARCHAR(36) PRIMARY KEY, "
                + "player_name VARCHAR(32) NOT NULL, "
                + "total_playtime_seconds INTEGER NOT NULL DEFAULT 0, "
                + "current_balance NUMERIC NOT NULL DEFAULT 0, "
                + "highest_balance NUMERIC NOT NULL DEFAULT 0, "
                + "cumulative_earned NUMERIC NOT NULL DEFAULT 0, "
                + "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")",
            "CREATE TABLE IF NOT EXISTS " + VerificationProgressRepository.TABLE_NAME + " ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "player_name VARCHAR(32) NOT NULL, "
                + "event_type VARCHAR(32) NOT NULL, "
                + "event_key VARCHAR(128), "
                + "event_value TEXT, "
                + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")",
            "CREATE INDEX IF NOT EXISTS idx_progress_player ON "
                + VerificationProgressRepository.TABLE_NAME + "(player_uuid)",
            "CREATE INDEX IF NOT EXISTS idx_progress_type ON "
                + VerificationProgressRepository.TABLE_NAME + "(event_type)",
            "CREATE INDEX IF NOT EXISTS idx_progress_time ON "
                + VerificationProgressRepository.TABLE_NAME + "(timestamp)"
        };

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                stmt.execute(sql);
            }
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Fehler beim Anlegen der SQLite-Tabellen.", e);
            return false;
        }

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE " + VerificationStatisticsRepository.TABLE_NAME
                    + " ADD COLUMN cumulative_earned NUMERIC NOT NULL DEFAULT 0");
        } catch (SQLException e) {
            String msg = e.getMessage();
            if (msg == null || !msg.toLowerCase().contains("duplicate column")) {
                getLogger().log(Level.SEVERE,
                        "Fehler beim Hinzufügen der Spalte 'cumulative_earned'.", e);
                return false;
            }
        }

        return true;
    }

    private boolean createHistoryTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + VerificationHistoryRepository.TABLE_NAME + " ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "player_name VARCHAR(32) NOT NULL, "
                + "actor_uuid VARCHAR(36), "
                + "actor_name VARCHAR(32), "
                + "action VARCHAR(32) NOT NULL, "
                + "details VARCHAR(255), "
                + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_player_uuid (player_uuid), "
                + "INDEX idx_timestamp (timestamp)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            return true;
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Fehler beim Anlegen der History-Tabelle.", e);
            return false;
        }
    }

    private boolean createStatisticsTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + VerificationStatisticsRepository.TABLE_NAME + " ("
                + "player_uuid VARCHAR(36) PRIMARY KEY, "
                + "player_name VARCHAR(32) NOT NULL, "
                + "total_playtime_seconds BIGINT NOT NULL DEFAULT 0, "
                + "current_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00, "
                + "highest_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00, "
                + "cumulative_earned DECIMAL(15,2) NOT NULL DEFAULT 0.00, "
                + "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                + "INDEX idx_last_updated (last_updated)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            return true;
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Fehler beim Anlegen der Statistik-Tabelle.", e);
            return false;
        }
    }

    private boolean createProgressTable() {
        String sql = "CREATE TABLE IF NOT EXISTS " + VerificationProgressRepository.TABLE_NAME + " ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "player_uuid VARCHAR(36) NOT NULL, "
                + "player_name VARCHAR(32) NOT NULL, "
                + "event_type VARCHAR(32) NOT NULL, "
                + "event_key VARCHAR(128), "
                + "event_value TEXT, "
                + "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
                + "INDEX idx_progress_player (player_uuid), "
                + "INDEX idx_progress_type (event_type), "
                + "INDEX idx_progress_time (timestamp)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            return true;
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Fehler beim Anlegen der Progress-Tabelle.", e);
            return false;
        }
    }

    private boolean addCumulativeEarnedColumnMariaDb() {
        String sql = "ALTER TABLE " + VerificationStatisticsRepository.TABLE_NAME
                + " ADD COLUMN cumulative_earned DECIMAL(15,2) NOT NULL DEFAULT 0.00";
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            return true;
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Fehler beim Hinzufügen der Spalte 'cumulative_earned'.", e);
            return false;
        }
    }

    private void loadConfigValues() {
        defaultGroup = getConfig().getString("roles.default", "bettler");
        verifiedGroup = getConfig().getString("roles.verified", "i");
        verifiedDisplayName = getConfig().getString("display.verifiedRole", "I");
        historyEnabled = getConfig().getBoolean("history.enabled", true);
        statisticsEnabled = getConfig().getBoolean("statistics.enabled", true);
        trackPlaytime = getConfig().getBoolean("statistics.track-playtime", true);
        trackBalance = getConfig().getBoolean("statistics.track-balance", true);
        trackCumulativeEarned = getConfig().getBoolean("statistics.track-cumulative-earned", true);
        progressEnabled = getConfig().getBoolean("progress.enabled", true);
        trackAdvancements = getConfig().getBoolean("progress.track-advancements", true);
        trackRankChanges = getConfig().getBoolean("progress.track-rank-changes", true);
        cacheTtl = getConfig().getLong("cache.info-ttl-seconds", 30);
    }

    private void validateGroups() {
        if (!vaultService.groupExists(defaultGroup)) {
            getLogger().warning("Die LuckPerms-Gruppe '" + defaultGroup + "' existiert nicht.");
        }
        if (!vaultService.groupExists(verifiedGroup)) {
            getLogger().warning("Die LuckPerms-Gruppe '" + verifiedGroup + "' existiert nicht.");
        }
    }

    public HikariDataSource getDataSource() { return dataSource; }
    public Permission getPermission() { return permission; }
    public String getTableName() { return tableName; }
    public DatabaseType getDatabaseType() { return databaseType; }
    public DatabaseConfig getDatabaseConfig() { return databaseConfig; }
    public VerificationService getVerificationService() { return verificationService; }
    public MessageService getMessageService() { return messageService; }
    public StatisticsService getStatisticsService() { return statisticsService; }
    public ProgressService getProgressService() { return progressService; }
    public EconomyService getEconomyService() { return economyService; }
}