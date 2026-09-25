package de.hage.verification.ui;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.VerificationEntry;
import de.hage.verification.database.model.VerificationProgressEntry;
import de.hage.verification.database.model.VerificationStatistics;
import de.hage.verification.util.PlayerUtil;
import de.hage.verification.util.TimeFormatter;

public final class VerificationUI implements InventoryHolder {

    private static final DecimalFormat MONEY_FORMAT =
            new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.GERMANY));

    private final VerificationPlugin plugin;
    private final Player viewer;
    private final List<VerificationEntry> entries;
    private final Map<UUID, VerificationStatistics> statistics;
    private final Map<UUID, String> rankCache;
    private final Inventory inventory;
    private final Map<Integer, VerificationEntry> slotToEntry = new HashMap<>();
    private final int pageSize;
    private int page = 0;

    public VerificationUI(VerificationPlugin plugin, Player viewer,
                          List<VerificationEntry> entries,
                          Map<UUID, VerificationStatistics> statistics,
                          Map<UUID, String> rankCache) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.entries = entries;
        this.statistics = statistics;
        this.rankCache = rankCache;
        this.pageSize = plugin.getConfig().getInt("ui.entries-per-page", 45);
        this.inventory = Bukkit.createInventory(this, 54, "§8Verifizierte Spieler");
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    private void render() {
        inventory.clear();
        slotToEntry.clear();

        if (entries.isEmpty()) {
            inventory.setItem(22, createEmptyItem());
            return;
        }

        int start = page * pageSize;
        int end = Math.min(start + pageSize, entries.size());

        int slot = 0;
        for (int i = start; i < end; i++) {
            VerificationEntry entry = entries.get(i);
            ItemStack head = createPlayerHead(entry);
            inventory.setItem(slot, head);
            slotToEntry.put(slot, entry);
            slot++;
        }

        if (page > 0) {
            inventory.setItem(45, createPrevButton());
        }
        if (end < entries.size()) {
            inventory.setItem(53, createNextButton());
        }
    }

    private ItemStack createPlayerHead(VerificationEntry entry) {
        UUID uuid = entry.getVerifiedPlayer();
        String name = PlayerUtil.getPlayerName(uuid);
        if (name == null) name = uuid.toString();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
            meta.setDisplayName("§f" + name);

            List<String> lore = new ArrayList<>();
            String verificator = entry.hasVerificator()
                    ? PlayerUtil.getPlayerName(entry.getVerificator())
                    : null;
            if (verificator == null) verificator = "Unbekannt";
            String timeStr = entry.getVerificationTime() != null
                    ? plugin.getVerificationService().formatTime(entry.getVerificationTime())
                    : "Unbekannt";

            lore.add("§7Verifiziert am: §f" + timeStr);
            lore.add("§7Verifiziert von: §f" + verificator);

            VerificationStatistics stats = statistics.get(uuid);
            if (stats != null) {
                lore.add("§7Spielzeit: §f" + TimeFormatter.formatPlaytime(stats.getTotalPlaytimeSeconds()));
                if (stats.getCurrentBalance() != null) {
                    lore.add("§7Kontostand: §f" + MONEY_FORMAT.format(stats.getCurrentBalance()) + " $");
                }
                if (stats.getCumulativeEarned() != null) {
                    lore.add("§7Insgesamt verdient: §f" + MONEY_FORMAT.format(stats.getCumulativeEarned()) + " $");
                }
            } else {
                lore.add("§7Spielzeit: §fUnbekannt");
                lore.add("§7Kontostand: §fUnbekannt");
            }

            String rank = rankCache.get(uuid);
            if (rank != null) {
                lore.add("§7Aktueller Rang: §f" + rank);
            }

            lore.add("");
            lore.add("§aKlick für Details");
            meta.setLore(lore);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createEmptyItem() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Keine verifizierten Spieler");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createPrevButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7Vorherige Seite");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNextButton() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7Nächste Seite");
            item.setItemMeta(meta);
        }
        return item;
    }

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        if (slot == 45 && page > 0) {
            page--;
            render();
            return;
        }
        if (slot == 53 && (page + 1) * pageSize < entries.size()) {
            page++;
            render();
            return;
        }
        VerificationEntry entry = slotToEntry.get(slot);
        if (entry != null) {
            showDetails(entry);
        }
    }

    private void showDetails(VerificationEntry entry) {
        UUID uuid = entry.getVerifiedPlayer();
        String name = PlayerUtil.getPlayerName(uuid);
        if (name == null) name = uuid.toString();

        viewer.sendMessage("§8§m----------------------");
        viewer.sendMessage("§aSpieler: §f" + name);

        String verificator = entry.hasVerificator()
                ? PlayerUtil.getPlayerName(entry.getVerificator())
                : null;
        if (verificator == null) verificator = "Unbekannt";
        viewer.sendMessage("§7Verifiziert von: §f" + verificator);

        String timeStr = entry.getVerificationTime() != null
                ? plugin.getVerificationService().formatTime(entry.getVerificationTime())
                : "Unbekannt";
        viewer.sendMessage("§7Verifiziert am: §f" + timeStr);

        VerificationStatistics stats = statistics.get(uuid);
        if (stats != null) {
            viewer.sendMessage("§7Spielzeit: §f" + TimeFormatter.formatPlaytime(stats.getTotalPlaytimeSeconds()));
            if (stats.getCurrentBalance() != null) {
                viewer.sendMessage("§7Kontostand: §f" + MONEY_FORMAT.format(stats.getCurrentBalance()) + " $");
            }
            if (stats.getCumulativeEarned() != null) {
                viewer.sendMessage("§7Insgesamt verdient: §f" + MONEY_FORMAT.format(stats.getCumulativeEarned()) + " $");
            }
        }
        String rank = rankCache.get(uuid);
        if (rank != null) {
            viewer.sendMessage("§7Aktueller Rang: §f" + rank);
        }

        if (plugin.getProgressService() != null && plugin.getProgressService().isEnabled()) {
            int limit = plugin.getConfig().getInt("progress.maxEntriesInDetails", 10);
            plugin.getProgressService().getRecent(uuid, limit).thenAcceptAsync(list ->
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        viewer.sendMessage("§7Letzte Fortschritte:");
                        if (list.isEmpty()) {
                            viewer.sendMessage("§7Noch keine Fortschritte aufgezeichnet.");
                        } else {
                            for (VerificationProgressEntry p : list) {
                                String pt = plugin.getVerificationService().formatTime(p.getTimestamp());
                                String key = p.getEventKey() != null ? p.getEventKey() : "";
                                String value = p.getEventValue() != null ? p.getEventValue() : "";
                                viewer.sendMessage("§7[" + pt + "] §f" + p.getEventType()
                                        + " §7→ §f" + key + " §7" + value);
                            }
                        }
                        viewer.sendMessage("§8§m----------------------");
                    })
            ).exceptionally(ex -> {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        viewer.sendMessage("§8§m----------------------"));
                return null;
            });
        } else {
            viewer.sendMessage("§8§m----------------------");
        }
    }
}