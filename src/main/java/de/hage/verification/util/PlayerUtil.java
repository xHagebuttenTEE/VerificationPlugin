package de.hage.verification.util;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

public final class PlayerUtil {

    private PlayerUtil() {}

    public static UUID getPlayerUUID(String name) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        if (offline == null) {
            return null;
        }
        return offline.getUniqueId();
    }

    public static String getPlayerName(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline == null) {
            return null;
        }
        return offline.getName();
    }
}