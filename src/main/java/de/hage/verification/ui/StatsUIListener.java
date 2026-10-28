package de.hage.verification.ui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class StatsUIListener implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Object holder = event.getInventory().getHolder();
        if (holder instanceof StatsMainUI ui) {
            event.setCancelled(true);
            ui.handleClick(event);
        } else if (holder instanceof SupporterListUI ui) {
            event.setCancelled(true);
            ui.handleClick(event);
        } else if (holder instanceof SupporterProfileUI ui) {
            event.setCancelled(true);
            ui.handleClick(event);
        } else if (holder instanceof PlayerProfileUI ui) {
            event.setCancelled(true);
            ui.handleClick(event);
        } else if (holder instanceof LeaderboardUI ui) {
            event.setCancelled(true);
            ui.handleClick(event);
        } else if (holder instanceof SelectSupporterUI ui) {
            event.setCancelled(true);
            ui.handleClick(event);
        }
    }
}