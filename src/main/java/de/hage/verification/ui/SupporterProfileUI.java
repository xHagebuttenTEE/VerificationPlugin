package de.hage.verification.ui;

import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.SupporterStats;
import de.hage.verification.database.model.VerificationEntry;
import de.hage.verification.database.model.VerificationStatistics;
import de.hage.verification.util.PlayerUtil;
import de.hage.verification.util.TimeFormatter;
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

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SupporterProfileUI implements InventoryHolder {

    private final VerificationPlugin plugin;
    private final Player viewer;
    private final SupporterStats stats;
    private final VerificationStatistics ownStats;
    private final List<VerificationEntry> recentVerified;
    private final String rank;
    private final Long playtimeTicks;
    private final Runnable onBack;
    private final Inventory inventory;
    private final Map<Integer, VerificationEntry> slotToEntry = new HashMap<>();

    public SupporterProfileUI(VerificationPlugin plugin, Player viewer, SupporterStats stats,
                              VerificationStatistics ownStats, List<VerificationEntry> recentVerified,
                              String rank, Long playtimeTicks, Runnable onBack) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.stats = stats;
        this.ownStats = ownStats;
        this.recentVerified = recentVerified;
        this.rank = rank;
        this.playtimeTicks = playtimeTicks;
        this.onBack = onBack;
        String name = PlayerUtil.getPlayerName(stats.getSupporterUuid());
        if (name == null) name = stats.getSupporterUuid().toString();
        this.inventory = Bukkit.createInventory(this, 36, "§8Supporter: " + name);
    }

    public static void open(VerificationPlugin plugin, Player viewer, SupporterStats stats, Runnable onBack) {
        CompletableFuture<Map<UUID, VerificationStatistics>> statsFuture = plugin.getStatisticsService() != null
                ? plugin.getStatisticsService().loadStatistics(List.of(stats.getSupporterUuid()))
                : CompletableFuture.completedFuture(Map.of());
        CompletableFuture<List<VerificationEntry>> recentFuture =
                plugin.getVerificationService().getPlayersVerifiedBy(stats.getSupporterUuid());
        CompletableFuture<Long> playtimeFuture = (plugin.getAdvancedStatsRepository() != null
                && plugin.getConfig().getBoolean("advanced-stats.enabled", true))
                ? plugin.getAdvancedStatsRepository().findPlaytime(stats.getSupporterUuid())
                : CompletableFuture.completedFuture(null);

        statsFuture.thenCombineAsync(recentFuture, (statsMap, recent) -> {
            VerificationStatistics own = statsMap.get(stats.getSupporterUuid());
            String rank = plugin.getVerificationService().getRankOf(stats.getSupporterUuid());
            List<VerificationEntry> limited = recent.size() > 5 ? recent.subList(0, 5) : recent;
            return new Object[] { own, limited, rank };
        }).thenCombineAsync(playtimeFuture, (arr, playtime) -> {
            VerificationStatistics own = (VerificationStatistics) arr[0];
            @SuppressWarnings("unchecked")
            List<VerificationEntry> limited = (List<VerificationEntry>) arr[1];
            String rank = (String) arr[2];
            Bukkit.getScheduler().runTask(plugin, () ->
                    new SupporterProfileUI(plugin, viewer, stats, own, limited, rank, playtime, onBack).open());
            return null;
        }).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage("§cFehler beim Laden des Supporter-Profils."));
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
        String name = PlayerUtil.getPlayerName(stats.getSupporterUuid());
        if (name == null) name = stats.getSupporterUuid().toString();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(stats.getSupporterUuid());
            if (owner.hasPlayedBefore() || owner.isOnline()) {
                headMeta.setOwningPlayer(owner);
            }
            headMeta.setDisplayName("§f" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§7UUID: §f" + stats.getSupporterUuid());
            if (rank != null) lore.add("§7Rang: §f" + rank);
            headMeta.setLore(lore);
            head.setItemMeta(headMeta);
        }
        inventory.setItem(4, head);

        inventory.setItem(10, createItem(Material.BOOK, "§aGesamt-Verifizierungen",
                List.of("§7Wert: §f" + stats.getTotalVerifications())));

        List<String> lastLore = new ArrayList<>();
        lastLore.add("§7Zeitpunkt: §f" + (stats.getLastVerification() != null
                ? plugin.getVerificationService().formatTime(stats.getLastVerification()) : "Unbekannt"));
        if (stats.getLastVerification() != null) {
            String ago = formatDaysAgo(stats.getLastVerification());
            if (!ago.isEmpty()) lastLore.add("§7" + ago);
        }
        inventory.setItem(12, createItem(Material.CLOCK, "§aLetzte Verifizierung", lastLore));

        List<String> monthlyLore = new ArrayList<>();
        monthlyLore.add("§7Diesen Monat: §f" + stats.getMonthlyVerifications());
        if (stats.getFirstVerification() != null) {
            monthlyLore.add("§7⌀ pro Monat: §f" + formatAveragePerMonth(stats));
        }
        inventory.setItem(14, createItem(Material.EXPERIENCE_BOTTLE, "§aMonatliche Verifizierungen", monthlyLore));

        String playtimeValue = playtimeTicks != null
                ? TimeFormatter.formatPlaytime(playtimeTicks / 20)
                : (ownStats != null ? TimeFormatter.formatPlaytime(ownStats.getTotalPlaytimeSeconds()) : "Unbekannt");
        inventory.setItem(16, createItem(Material.CAKE, "§aSpielzeit",
                List.of("§7Wert: §f" + playtimeValue)));

        int slot = 28;
        for (VerificationEntry entry : recentVerified) {
            if (slot > 32) break;
            String pname = PlayerUtil.getPlayerName(entry.getVerifiedPlayer());
            if (pname == null) pname = entry.getVerifiedPlayer().toString();
            ItemStack headItem = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta hm = (SkullMeta) headItem.getItemMeta();
            if (hm != null) {
                OfflinePlayer owner = Bukkit.getOfflinePlayer(entry.getVerifiedPlayer());
                if (owner.hasPlayedBefore() || owner.isOnline()) {
                    hm.setOwningPlayer(owner);
                }
                hm.setDisplayName("§f" + pname);
                hm.setLore(List.of("§7Verifiziert am: §f" + (entry.getVerificationTime() != null
                        ? plugin.getVerificationService().formatTime(entry.getVerificationTime()) : "Unbekannt")));
                headItem.setItemMeta(hm);
            }
            inventory.setItem(slot, headItem);
            slotToEntry.put(slot, entry);
            slot++;
        }

        inventory.setItem(35, createItem(Material.BARRIER, "§7Zurück", List.of()));
        UiItems.fillEmpty(inventory);
    }

    private String formatDaysAgo(Timestamp ts) {
        if (ts == null) return "";
        long days = ChronoUnit.DAYS.between(ts.toInstant(), Instant.now());
        if (days <= 0) return "heute";
        if (days == 1) return "vor 1 Tag";
        return "vor " + days + " Tagen";
    }

    private String formatAveragePerMonth(SupporterStats s) {
        long daysActive = ChronoUnit.DAYS.between(s.getFirstVerification().toInstant(), Instant.now()) + 1;
        double months = Math.max(1.0, daysActive / 30.0);
        double avg = s.getTotalVerifications() / months;
        return String.format(Locale.GERMANY, "%.1f", avg);
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
        if (slot == 35) {
            viewer.closeInventory();
            if (onBack != null) onBack.run();
            return;
        }
        VerificationEntry entry = slotToEntry.get(slot);
        if (entry != null) {
            viewer.closeInventory();
            PlayerProfileUI.open(plugin, viewer, entry.getVerifiedPlayer(),
                    () -> SupporterProfileUI.open(plugin, viewer, stats, onBack));
        }
    }
}