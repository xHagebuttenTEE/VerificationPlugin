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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.VerificationEntry;
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
    private final Map<UUID, Long> playtime;
    private final Map<UUID, String> rankCache;
    private final Runnable onBack;
    private final Inventory inventory;
    private final Map<Integer, VerificationEntry> slotToEntry = new HashMap<>();
    private final int pageSize;
    private int page = 0;

    public VerificationUI(VerificationPlugin plugin, Player viewer,
                          List<VerificationEntry> entries,
                          Map<UUID, VerificationStatistics> statistics,
                          Map<UUID, Long> playtime,
                          Map<UUID, String> rankCache,
                          Runnable onBack) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.entries = entries;
        this.statistics = statistics;
        this.playtime = playtime;
        this.rankCache = rankCache;
        this.onBack = onBack;
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
            inventory.setItem(49, createItem(Material.BARRIER, "§7Zurück", List.of()));
            UiItems.fillEmpty(inventory);
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
            inventory.setItem(45, createItem(Material.ARROW, "§7Vorherige Seite", List.of()));
        }
        if (end < entries.size()) {
            inventory.setItem(53, createItem(Material.ARROW, "§7Nächste Seite", List.of()));
        }
        inventory.setItem(49, createItem(Material.BARRIER, "§7Zurück", List.of()));
        UiItems.fillEmpty(inventory);
    }

    private ItemStack createPlayerHead(VerificationEntry entry) {
        UUID uuid = entry.getVerifiedPlayer();
        String name = PlayerUtil.getPlayerName(uuid);
        if (name == null) name = uuid.toString();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(uuid);
            if (owner.hasPlayedBefore() || owner.isOnline()) {
                meta.setOwningPlayer(owner);
            }
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

            Long mcPlaytime = playtime.get(uuid);
            VerificationStatistics stats = statistics.get(uuid);
            String playtimeValue;
            if (mcPlaytime != null) {
                playtimeValue = TimeFormatter.formatPlaytime(mcPlaytime / 20);
            } else if (stats != null) {
                playtimeValue = TimeFormatter.formatPlaytime(stats.getTotalPlaytimeSeconds());
            } else {
                playtimeValue = "Unbekannt";
            }
            lore.add("§7Spielzeit: §f" + playtimeValue);

            if (stats != null && stats.getCurrentBalance() != null) {
                lore.add("§7Kontostand: §f" + MONEY_FORMAT.format(stats.getCurrentBalance()) + " $");
            } else {
                lore.add("§7Kontostand: §fUnbekannt");
            }
            if (stats != null && stats.getCumulativeEarned() != null) {
                lore.add("§7Insgesamt verdient: §f" + MONEY_FORMAT.format(stats.getCumulativeEarned()) + " $");
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
        if (slot == 49) {
            viewer.closeInventory();
            if (onBack != null) onBack.run();
            return;
        }
        VerificationEntry entry = slotToEntry.get(slot);
        if (entry != null) {
            viewer.closeInventory();
            PlayerProfileUI.open(plugin, viewer, entry.getVerifiedPlayer(),
                    () -> new VerificationUI(plugin, viewer, entries, statistics, playtime, rankCache, onBack).open());
        }
    }
}