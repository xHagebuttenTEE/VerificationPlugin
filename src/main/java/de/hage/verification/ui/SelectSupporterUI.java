package de.hage.verification.ui;

import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.SupporterStats;
import de.hage.verification.util.PlayerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SelectSupporterUI implements InventoryHolder {

    private final VerificationPlugin plugin;
    private final Player viewer;
    private final UUID targetUuid;
    private final List<SupporterStats> supporters;
    private final Runnable onBack;
    private final Inventory inventory;
    private final Map<Integer, SupporterStats> slotToEntry = new HashMap<>();
    private final int pageSize;
    private int page = 0;

    public SelectSupporterUI(VerificationPlugin plugin, Player viewer, UUID targetUuid,
                             List<SupporterStats> supporters, Runnable onBack) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetUuid = targetUuid;
        this.supporters = supporters;
        this.onBack = onBack;
        this.pageSize = plugin.getConfig().getInt("ui.entries-per-page", 45);
        this.inventory = Bukkit.createInventory(this, 54, "§8Supporter auswählen");
    }

    public static void open(VerificationPlugin plugin, Player viewer, UUID targetUuid, Runnable onBack) {
        if (!viewer.hasPermission("verification.admin")) {
            viewer.sendMessage("§cDu hast keine Berechtigung dafür.");
            return;
        }
        plugin.getSupporterStatsRepository().findAll().thenAcceptAsync(list -> {
            List<SupporterStats> valid = new ArrayList<>();
            for (SupporterStats s : list) {
                String name = PlayerUtil.getPlayerName(s.getSupporterUuid());
                if (name != null && !name.isEmpty()) valid.add(s);
            }
            Bukkit.getScheduler().runTask(plugin, () ->
                    new SelectSupporterUI(plugin, viewer, targetUuid, valid, onBack).open());
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage("§cFehler beim Laden der Supporter-Liste."));
            return null;
        });
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    private void render() {
        inventory.clear();
        slotToEntry.clear();
        if (supporters.isEmpty()) {
            inventory.setItem(22, createItem(Material.BARRIER, "§cKeine Supporter gefunden", List.of()));
            inventory.setItem(49, createItem(Material.BARRIER, "§7Zurück", List.of()));
            UiItems.fillEmpty(inventory);
            return;
        }
        int start = page * pageSize;
        int end = Math.min(start + pageSize, supporters.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            SupporterStats stats = supporters.get(i);
            ItemStack head = createSupporterHead(stats);
            inventory.setItem(slot, head);
            slotToEntry.put(slot, stats);
            slot++;
        }
        if (page > 0) {
            inventory.setItem(45, createItem(Material.ARROW, "§7Vorherige Seite", List.of()));
        }
        if (end < supporters.size()) {
            inventory.setItem(53, createItem(Material.ARROW, "§7Nächste Seite", List.of()));
        }
        inventory.setItem(49, createItem(Material.BARRIER, "§7Zurück", List.of()));
        UiItems.fillEmpty(inventory);
    }

    private ItemStack createSupporterHead(SupporterStats stats) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(stats.getSupporterUuid());
            if (owner.hasPlayedBefore() || owner.isOnline()) {
                meta.setOwningPlayer(owner);
            }
            String name = PlayerUtil.getPlayerName(stats.getSupporterUuid());
            if (name == null) name = stats.getSupporterUuid().toString();
            meta.setDisplayName("§f" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§7Gesamt-Verifizierungen: §f" + stats.getTotalVerifications());
            lore.add("§7Diesen Monat: §f" + stats.getMonthlyVerifications());
            lore.add("");
            lore.add("§aKlick um als Supporter einzutragen");
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
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
        int slot = event.getRawSlot();
        if (slot == 45 && page > 0) { page--; render(); return; }
        if (slot == 53 && (page + 1) * pageSize < supporters.size()) { page++; render(); return; }
        if (slot == 49) {
            viewer.closeInventory();
            if (onBack != null) onBack.run();
            return;
        }
        SupporterStats stats = slotToEntry.get(slot);
        if (stats != null) {
            String name = PlayerUtil.getPlayerName(stats.getSupporterUuid());
            if (name == null) name = stats.getSupporterUuid().toString();
            final String finalName = name;
            viewer.closeInventory();
            plugin.getVerificationService().updateVerificator(targetUuid, stats.getSupporterUuid(), viewer.getUniqueId())
                    .thenAcceptAsync(success -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (Boolean.TRUE.equals(success)) {
                            viewer.sendMessage("§aSupporter §f" + finalName + " §awurde als Verifizierender eingetragen.");
                        } else {
                            viewer.sendMessage("§cFehler: Spieler ist nicht (mehr) verifiziert.");
                        }
                        if (onBack != null) onBack.run();
                    }))
                    .exceptionally(ex -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            viewer.sendMessage("§cFehler beim Eintragen des Supporters.");
                            if (onBack != null) onBack.run();
                        });
                        return null;
                    });
        }
    }
}