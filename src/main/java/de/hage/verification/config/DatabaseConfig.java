package de.hage.verification.config;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import de.hage.verification.VerificationPlugin;

public final class DatabaseConfig {

    private final VerificationPlugin plugin;
    private final File file;
    private FileConfiguration config;

    public DatabaseConfig(VerificationPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "database.yml");
        reload();
    }

    public void reload() {
        boolean newlyCreated = false;
        if (!file.exists()) {
            plugin.saveResource("database.yml", false);
            newlyCreated = true;
        }
        this.config = YamlConfiguration.loadConfiguration(file);
        if (newlyCreated) {
            migrateFromMainConfig();
        }
    }

    private void migrateFromMainConfig() {
        File mainFile = new File(plugin.getDataFolder(), "config.yml");
        if (!mainFile.exists()) return;
        FileConfiguration main = YamlConfiguration.loadConfiguration(mainFile);
        if (!main.isConfigurationSection("database")) return;

        boolean migrated = false;

        String host = main.getString("database.host");
        if (host != null && !host.isEmpty() && !host.equals("127.0.0.1")) {
            config.set("host", host);
            migrated = true;
        }
        int port = main.getInt("database.port", 0);
        if (port > 0 && port != 3306) {
            config.set("port", port);
            migrated = true;
        }
        String name = main.getString("database.name");
        if (name != null && !name.isEmpty() && !name.equals("ÄNDERN")) {
            config.set("name", name);
            migrated = true;
        }
        String user = main.getString("database.user");
        if (user != null && !user.isEmpty() && !user.equals("ÄNDERN")) {
            config.set("user", user);
            migrated = true;
        }
        String password = main.getString("database.password");
        if (password != null && !password.isEmpty() && !password.equals("ÄNDERN")) {
            config.set("password", password);
            migrated = true;
        }
        String table = main.getString("database.table");
        if (table != null && !table.isEmpty() && !table.equals("verification_system")) {
            config.set("table", table);
            migrated = true;
        }
        String type = main.getString("database.type");
        if (type != null && !type.isEmpty() && !type.equals("auto")) {
            config.set("type", type);
            migrated = true;
        }

        if (migrated) {
            save();
            plugin.getLogger().info("Datenbank-Konfiguration wurde aus config.yml nach database.yml migriert. "
                    + "Der 'database'-Block in config.yml kann nun entfernt werden.");
        }
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Konnte database.yml nicht speichern.", e);
        }
    }

    public String getType() {
        String type = config.getString("type", "auto");
        return type != null ? type.toLowerCase().trim() : "auto";
    }

    public String getHost() { return config.getString("host", "127.0.0.1"); }
    public int getPort() { return config.getInt("port", 3306); }
    public String getName() { return config.getString("name", ""); }
    public String getUser() { return config.getString("user", ""); }
    public String getPassword() { return config.getString("password", ""); }
    public String getSqliteFile() { return config.getString("sqlite.file", "verification.db"); }
    public String getTable() { return config.getString("table", "verification_system"); }
    public int getMaxPoolSize() { return config.getInt("pool.maxPoolSize", 10); }
    public int getMinIdle() { return config.getInt("pool.minIdle", 2); }
    public long getConnectionTimeout() { return config.getLong("pool.connectionTimeout", 30000); }

    public boolean hasCredentials() {
        String name = getName();
        String user = getUser();
        return name != null && !name.isEmpty() && user != null && !user.isEmpty();
    }
}