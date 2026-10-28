package de.hage.verification.ui;

import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.LeaderboardEntry;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LeaderboardUI implements InventoryHolder {

    private static final DateTimeFormatter MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final VerificationPlugin plugin;
    private final Player viewer;
    private final String yearMonth;
    private final List<LeaderboardEntry> entries;
    private final long totalThisMonth;
    private final List<String> availableMonths;
    private final Runnable onBack;
    private final Inventory inventory;
    private final Map<Integer, LeaderboardEntry> slotToEntry = new HashMap<>();

    public LeaderboardUI(VerificationPlugin plugin, Player viewer, String yearMonth,
                         List<LeaderboardEntry> entries, long totalThisMonth,
                         List<String> availableMonths, Runnable onBack) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.yearMonth = yearMonth;
        this.entries = entries;
        this.totalThisMonth = totalThisMonth;
        this.availableMonths = availableMonths;
        this.onBack = onBack;
        this.inventory = Bukkit.createInventory(this, 54, "§8Rangliste " + yearMonth);
    }

    public static void open(VerificationPlugin plugin, Player viewer, String yearMonth, Runnable onBack) {
        String targetMonth = yearMonth != null ? yearMonth : LocalDate.now().format(MONTH_FORMAT);
        plugin.getLeaderboardRepository().findByMonth(targetMonth).thenCombineAsync(
                plugin.getLeaderboardRepository().findAvailableMonths(), (entries, months) -> {
                    long total = entries.stream().mapToLong(LeaderboardEntry::getVerifications).sum();
                    List<LeaderboardEntry> limited = entries.size() > 10 ? entries.subList(0, 10) : entries;
                    Bukkit.getScheduler().runTask(plugin, () ->
                            new LeaderboardUI(plugin, viewer, targetMonth, limited, total, months, onBack).open());
                    return null;
                }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage("§cFehler beim Laden der Rangliste."));
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
        if (entries.isEmpty()) {
            inventory.setItem(22, createItem(Material.BARRIER, "§cKeine Einträge für " + yearMonth, List.of()));
        } else {
            for (int i = 0; i < entries.size(); i++) {
                LeaderboardEntry entry = entries.get(i);
                ItemStack head = createEntryHead(entry, i + 1);
                inventory.setItem(i, head);
                slotToEntry.put(i, entry);
            }
        }
        int currentIndex = availableMonths.indexOf(yearMonth);
        if (currentIndex < availableMonths.size() - 1) {
            inventory.setItem(45, createItem(Material.ARROW, "§7Älterer Monat", List.of()));
        }
        if (currentIndex > 0) {
            inventory.setItem(53, createItem(Material.ARROW, "§7Neuerer Monat", List.of()));
        }
        inventory.setItem(47, createItem(Material.PAPER, "§6Gesamt diesen Monat",
                List.of("§7Verifizierungen: §f" + totalThisMonth)));
        inventory.setItem(49, createItem(Material.BARRIER, "§7Zurück", List.of()));
        UiItems.fillEmpty(inventory);
    }

    private ItemStack createEntryHead(LeaderboardEntry entry, int rank) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(entry.getSupporterUuid());
            if (owner.hasPlayedBefore() || owner.isOnline()) {
                meta.setOwningPlayer(owner);
            }
            String name = PlayerUtil.getPlayerName(entry.getSupporterUuid());
            if (name == null) name = entry.getSupporterUuid().toString();
            String color = switch (rank) { case 1 -> "§6"; case 2 -> "§a"; case 3 -> "§9"; default -> "§7"; };
            meta.setDisplayName(color + "#" + rank + " §f" + name);
            meta.setLore(new ArrayList<>(List.of("§7Verifizierungen: §f" + entry.getVerifications())));
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
        if (slot == 49) {
            viewer.closeInventory();
            if (onBack != null) onBack.run();
            return;
        }
        if (slot == 45) {
            int idx = availableMonths.indexOf(yearMonth);
            if (idx >= 0 && idx < availableMonths.size() - 1) {
                viewer.closeInventory();
                open(plugin, viewer, availableMonths.get(idx + 1), onBack);
            }
            return;
        }
        if (slot == 53) {
            int idx = availableMonths.indexOf(yearMonth);
            if (idx > 0) {
                viewer.closeInventory();
                open(plugin, viewer, availableMonths.get(idx - 1), onBack);
            }
            return;
        }
        LeaderboardEntry entry = slotToEntry.get(slot);
        if (entry != null) {
            viewer.closeInventory();
            plugin.getSupporterStatsRepository().findOne(entry.getSupporterUuid()).thenAcceptAsync(stats -> {
                if (stats != null) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                            SupporterProfileUI.open(plugin, viewer, stats,
                                    () -> new LeaderboardUI(plugin, viewer, yearMonth, entries, totalThisMonth, availableMonths, onBack).open()));
                }
            }).exceptionally(ex -> {
                Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage("§cFehler beim Laden des Profils."));
                return null;
            });
        }
    }
}