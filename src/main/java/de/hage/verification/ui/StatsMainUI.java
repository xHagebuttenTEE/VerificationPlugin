package de.hage.verification.ui;

import de.hage.verification.VerificationPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class StatsMainUI implements InventoryHolder {

    private final VerificationPlugin plugin;
    private final Player viewer;
    private final Inventory inventory;

    public StatsMainUI(VerificationPlugin plugin, Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 27, "§8Verifikations-Statistiken");
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    private void render() {
        inventory.clear();
        if (!viewer.hasPermission("verification.info")) {
            inventory.setItem(13, createItem(Material.BARRIER, "§cKeine Berechtigung", List.of()));
            UiItems.fillEmpty(inventory);
            return;
        }
        inventory.setItem(11, createItem(Material.BOOKSHELF, "§aSupporter-Übersicht",
                List.of("§7Zeigt alle Supporter und ihre Verifizierungen", "", "§aKlick zum Öffnen")));
        inventory.setItem(13, createItem(Material.GOLD_BLOCK, "§6Monats-Rangliste",
                List.of("§7Top-Supporter des aktuellen Monats", "", "§aKlick zum Öffnen")));
        inventory.setItem(15, createItem(Material.COMPASS, "§eAlle verifizierten Spieler",
                List.of("§7Zeigt alle verifizierten Spieler", "", "§aKlick zum Öffnen")));
        inventory.setItem(22, createItem(Material.BARRIER, "§cSchließen", List.of("§7Schließt dieses Menü")));
        UiItems.fillEmpty(inventory);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void handleClick(InventoryClickEvent event) {
        if (!viewer.hasPermission("verification.info")) return;
        int slot = event.getRawSlot();
        switch (slot) {
            case 11 -> { viewer.closeInventory(); SupporterListUI.open(plugin, viewer, () -> new StatsMainUI(plugin, viewer).open()); }
            case 13 -> { viewer.closeInventory(); LeaderboardUI.open(plugin, viewer, null, () -> new StatsMainUI(plugin, viewer).open()); }
            case 15 -> { viewer.closeInventory(); plugin.getServer().getScheduler().runTask(plugin, () -> plugin.getUICommand().execute(viewer, () -> new StatsMainUI(plugin, viewer).open())); }
            case 22 -> viewer.closeInventory();
        }
    }
}