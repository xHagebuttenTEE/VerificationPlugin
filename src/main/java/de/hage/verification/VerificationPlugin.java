package de.hage.verification;

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
import de.hage.verification.database.repository.VerificationHistoryRepository;
import de.hage.verification.database.repository.VerificationRepository;
import de.hage.verification.service.MessageService;
import de.hage.verification.service.VaultService;
import de.hage.verification.service.VerificationService;
import de.hage.verification.util.AsyncExecutor;
import net.milkbowl.vault.permission.Permission;

public final class VerificationPlugin extends JavaPlugin {

    private HikariDataSource dataSource;
    private Permission permission;
    private String tableName;

    private AsyncExecutor asyncExecutor;
    private VerificationRepository repository;
    private VerificationHistoryRepository historyRepository;
    private VaultService vaultService;
    private MessageService messageService;
    private VerificationService verificationService;

    private String defaultGroup;
    private String verifiedGroup;
    private String verifiedDisplayName;
    private boolean historyEnabled;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

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

        if (!checkTableExists(tableName)) {
            getLogger().severe("Die Tabelle '" + tableName + "' existiert nicht in der Datenbank. Plugin wird deaktiviert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        loadConfigValues();

        if (historyEnabled && !checkTableExists(VerificationHistoryRepository.TABLE_NAME)) {
            if (!createHistoryTable()) {
                getLogger().severe("Die History-Tabelle konnte nicht angelegt werden. Plugin wird deaktiviert.");
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            getLogger().info("Tabelle '" + VerificationHistoryRepository.TABLE_NAME + "' wurde angelegt.");
        }

        this.asyncExecutor = new AsyncExecutor(this);
        this.repository = new VerificationRepository(dataSource, asyncExecutor, tableName);
        this.historyRepository = new VerificationHistoryRepository(dataSource, asyncExecutor);
        this.vaultService = new VaultService();
        this.messageService = new MessageService(this);
        this.verificationService = new VerificationService(this, repository, historyRepository,
                vaultService, defaultGroup, verifiedGroup,
                verifiedDisplayName, historyEnabled);

        validateGroups();

        PluginCommand verificationCmd = getCommand("verification");
        if (verificationCmd != null) {
            verificationCmd.setExecutor(new VerificationCommand(this));
        } else {
            getLogger().severe("Befehl 'verification' nicht in plugin.yml definiert.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("VerificationPlugin erfolgreich aktiviert.");
    }

    @Override
    public void onDisable() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    private boolean setupVault() {
        RegisteredServiceProvider<Permission> registration = getServer()
                .getServicesManager().getRegistration(Permission.class);
        if (registration == null) {
            return false;
        }
        permission = registration.getProvider();
        return permission != null;
    }

    private boolean setupDatabase() {
        String host = getConfig().getString("database.host");
        int port = getConfig().getInt("database.port");
        String database = getConfig().getString("database.name");
        String user = getConfig().getString("database.user");
        String password = getConfig().getString("database.password");
        tableName = getConfig().getString("database.table", "verification_system");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mariadb://" + host + ":" + port + "/" + database);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(getConfig().getInt("database.pool.maxPoolSize", 10));
        config.setMinimumIdle(getConfig().getInt("database.pool.minIdle", 2));
        config.setConnectionTimeout(getConfig().getLong("database.pool.connectionTimeout", 30000));
        config.setDriverClassName("org.mariadb.jdbc.Driver");

        try {
            dataSource = new HikariDataSource(config);
            try (Connection conn = dataSource.getConnection()) {
                getLogger().info("Datenbankverbindung erfolgreich hergestellt.");
            }
            return true;
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Datenbankverbindung fehlgeschlagen.", e);
            return false;
        }
    }

    private boolean checkTableExists(String table) {
        if (dataSource == null) {
            return false;
        }
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

    private void loadConfigValues() {
        defaultGroup = getConfig().getString("roles.default", "bettler");
        verifiedGroup = getConfig().getString("roles.verified", "i");
        verifiedDisplayName = getConfig().getString("display.verifiedRole", "I");
        historyEnabled = getConfig().getBoolean("history.enabled", true);
    }

    private void validateGroups() {
        if (!vaultService.groupExists(defaultGroup)) {
            getLogger().warning("Die LuckPerms-Gruppe '" + defaultGroup + "' (roles.default) existiert nicht.");
        }
        if (!vaultService.groupExists(verifiedGroup)) {
            getLogger().warning("Die LuckPerms-Gruppe '" + verifiedGroup + "' (roles.verified) existiert nicht.");
        }
    }

    public HikariDataSource getDataSource() {
        return dataSource;
    }

    public Permission getPermission() {
        return permission;
    }

    public String getTableName() {
        return tableName;
    }

    public VerificationService getVerificationService() {
        return verificationService;
    }

    public MessageService getMessageService() {
        return messageService;
    }
}