package de.hage.verification.service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import de.hage.verification.VerificationPlugin;

public final class MessageService {

    private final VerificationPlugin plugin;
    private final Map<String, String> messages = new HashMap<>();
    private final String prefix;

    public MessageService(VerificationPlugin plugin) {
        this.plugin = plugin;
        this.prefix = loadMessages();
    }

    private String loadMessages() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(messagesFile);
        for (String key : config.getKeys(true)) {
            if (config.isString(key)) {
                messages.put(key, config.getString(key));
            }
        }
        return messages.getOrDefault("prefix", "§8[§aVerification§8] §r");
    }

    public String getMessage(String key) {
        String msg = messages.get(key);
        return (msg != null) ? prefix + msg : prefix + "Nachricht nicht gefunden: " + key;
    }

    public String getMessage(String key, String... placeholders) {
        String msg = getMessage(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            msg = msg.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return msg;
    }

    public String getRawMessage(String key) {
        return messages.getOrDefault(key, "Unbekannt");
    }
}