package de.hage.verification.ui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class VerificationUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof VerificationUI ui) {
            event.setCancelled(true);
            ui.handleClick(event);
        }
    }
}