package de.hage.verification.service;

import java.util.Arrays;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.permission.Permission;

public final class VaultService {

    private final Permission permission;

    public VaultService() {
        RegisteredServiceProvider<Permission> registration = Bukkit.getServicesManager()
                .getRegistration(Permission.class);
        if (registration == null) {
            throw new IllegalStateException("Vault Permission-Service nicht gefunden.");
        }
        this.permission = registration.getProvider();
    }

    public void addPlayerGroup(OfflinePlayer player, String group) {
        permission.playerAddGroup(null, player, group);
    }

    public void removePlayerGroup(OfflinePlayer player, String group) {
        permission.playerRemoveGroup(null, player, group);
    }

    public boolean hasGroup(OfflinePlayer player, String group) {
        return permission.playerInGroup(null, player, group);
    }

    public boolean groupExists(String group) {
        return Arrays.asList(permission.getGroups()).contains(group);
    }

    public String getPrimaryGroup(OfflinePlayer player) {
        try {
            return permission.getPrimaryGroup(null, player);
        } catch (Exception e) {
            return null;
        }
    }
}