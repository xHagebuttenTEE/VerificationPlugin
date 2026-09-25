package de.hage.verification.service;

import java.math.BigDecimal;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import net.milkbowl.vault.economy.Economy;

public final class EconomyService {

    private final Economy economy;

    public EconomyService() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager()
                .getRegistration(Economy.class);
        this.economy = (registration != null) ? registration.getProvider() : null;
    }

    public boolean isAvailable() {
        return economy != null;
    }

    public BigDecimal getBalance(UUID playerUUID) {
        if (economy == null) {
            return null;
        }
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
            double balance = economy.getBalance(player);
            return BigDecimal.valueOf(balance);
        } catch (Exception e) {
            return null;
        }
    }
}