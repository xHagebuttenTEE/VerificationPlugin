package de.hage.verification.ui;

import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.VerificationStatistics;
import de.hage.verification.service.VerificationService;
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

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerProfileUI implements InventoryHolder {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(Locale.GERMANY));

    private final VerificationPlugin plugin;
    private final Player viewer;
    private final UUID targetUuid;
    private final VerificationService.VerificationInfo info;
    private final VerificationStatistics stats;
    private final Map<String, Long> advancedStats;
    private final String rank;
    private final Runnable onBack;
    private final Inventory inventory;

    public PlayerProfileUI(VerificationPlugin plugin, Player viewer, UUID targetUuid,
                           VerificationService.VerificationInfo info, VerificationStatistics stats,
                           Map<String, Long> advancedStats, String rank, Runnable onBack) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.targetUuid = targetUuid;
        this.info = info;
        this.stats = stats;
        this.advancedStats = advancedStats;
        this.rank = rank;
        this.onBack = onBack;
        String name = PlayerUtil.getPlayerName(targetUuid);
        if (name == null) name = targetUuid.toString();
        this.inventory = Bukkit.createInventory(this, 45, "§8Spieler: " + name);
    }

    public static void open(VerificationPlugin plugin, Player viewer, UUID targetUuid, Runnable onBack) {
        CompletableFuture<VerificationService.VerificationInfo> infoFuture =
                plugin.getVerificationService().getVerificationInfo(targetUuid);
        CompletableFuture<Map<UUID, VerificationStatistics>> statsFuture = plugin.getStatisticsService() != null
                ? plugin.getStatisticsService().loadStatistics(List.of(targetUuid))
                : CompletableFuture.completedFuture(Map.of());
        infoFuture.thenCombineAsync(statsFuture, (info, statsMap) -> {
            VerificationStatistics own = statsMap.get(targetUuid);
            String rank = plugin.getVerificationService().getRankOf(targetUuid);
            List<String> identifiers = plugin.getConfig().getStringList("advanced-stats.identifiers");
            if (plugin.getAdvancedStatsRepository() != null
                    && plugin.getAdvancedStatsRepository().isAvailable()
                    && !identifiers.isEmpty()) {
                return plugin.getAdvancedStatsRepository().findLatest(targetUuid, identifiers)
                        .thenApply(adv -> new ProfileData(info, own, adv, rank));
            }
            return CompletableFuture.completedFuture(new ProfileData(info, own, Map.of(), rank));
        }).thenCompose(f -> f).thenAcceptAsync(data ->
                Bukkit.getScheduler().runTask(plugin, () ->
                        new PlayerProfileUI(plugin, viewer, targetUuid, data.info, data.stats, data.advanced, data.rank, onBack).open())
        ).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () -> viewer.sendMessage("§cFehler beim Laden des Spieler-Profils."));
            return null;
        });
    }

    private record ProfileData(VerificationService.VerificationInfo info, VerificationStatistics stats,
                               Map<String, Long> advanced, String rank) {}

    private boolean needsVerificatorFix() {
        return info.isVerified() && (info.getVerificatorName() == null || info.getVerificatorName().isEmpty());
    }

    private boolean canFix() {
        return needsVerificatorFix() && viewer.hasPermission("verification.admin");
    }

    @Override
    public Inventory getInventory() { return inventory; }

    public void open() {
        render();
        viewer.openInventory(inventory);
    }

    private void render() {
        inventory.clear();
        String name = PlayerUtil.getPlayerName(targetUuid);
        if (name == null) name = targetUuid.toString();

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(targetUuid);
            if (owner.hasPlayedBefore() || owner.isOnline()) {
                headMeta.setOwningPlayer(owner);
            }
            headMeta.setDisplayName("§f" + name);
            List<String> lore = new ArrayList<>();
            lore.add("§7UUID: §f" + targetUuid);
            if (rank != null) lore.add("§7Aktueller Rang: §f" + rank);
            headMeta.setLore(lore);
            head.setItemMeta(headMeta);
        }
        inventory.setItem(4, head);

        if (needsVerificatorFix()) {
            List<String> lore = new ArrayList<>();
            lore.add("§7Name: §fUnbekannt");
            if (canFix()) {
                lore.add("");
                lore.add("§eKlicke hier um Supporter zu bestimmen");
            }
            inventory.setItem(10, createItem(Material.WRITABLE_BOOK, "§aVerifiziert von", lore));
        } else {
            String verificatorName = info.getVerificatorName() != null ? info.getVerificatorName() : "Unbekannt";
            inventory.setItem(10, createItem(Material.WRITABLE_BOOK, "§aVerifiziert von",
                    List.of("§7Name: §f" + verificatorName)));
        }

        String time = info.getFormattedTime() != null ? info.getFormattedTime() : "Unbekannt";
        inventory.setItem(12, createItem(Material.CLOCK, "§aVerifiziert am",
                List.of("§7Zeitpunkt: §f" + time)));

        String playtimeValue = "Unbekannt";
        Long mcPlaytime = advancedStats.get("PLAY_ONE_MINUTE");
        if (mcPlaytime != null) {
            playtimeValue = TimeFormatter.formatPlaytime(mcPlaytime / 20);
        } else if (stats != null) {
            playtimeValue = TimeFormatter.formatPlaytime(stats.getTotalPlaytimeSeconds());
        }
        inventory.setItem(14, createItem(Material.CAKE, "§aSpielzeit",
                List.of("§7Wert: §f" + playtimeValue)));

        inventory.setItem(16, createItem(Material.NETHER_STAR, "§aMeilensteine",
                List.of("§7Zeigt erreichte Meilensteine", "", "§7(Coming soon...)")));

        if (stats != null && stats.getCumulativeEarned() != null) {
            inventory.setItem(20, createItem(Material.GOLD_NUGGET, "§aVerdientes Geld",
                    List.of("§7Wert: §f" + MONEY.format(stats.getCumulativeEarned()) + " $")));
        } else {
            inventory.setItem(20, createItem(Material.GOLD_NUGGET, "§aVerdientes Geld",
                    List.of("§7Wert: §fUnbekannt")));
        }

        if (stats != null && stats.getHighestBalance() != null) {
            inventory.setItem(22, createItem(Material.DIAMOND, "§aHöchster Kontostand",
                    List.of("§7Wert: §f" + MONEY.format(stats.getHighestBalance()) + " $")));
        } else {
            inventory.setItem(22, createItem(Material.DIAMOND, "§aHöchster Kontostand",
                    List.of("§7Wert: §fUnbekannt")));
        }

        if (stats != null && stats.getCurrentBalance() != null) {
            inventory.setItem(24, createItem(Material.GOLD_INGOT, "§aKontostand",
                    List.of("§7Wert: §f" + MONEY.format(stats.getCurrentBalance()) + " $")));
        } else {
            inventory.setItem(24, createItem(Material.GOLD_INGOT, "§aKontostand",
                    List.of("§7Wert: §fUnbekannt")));
        }

        int slot = 28;
        for (Map.Entry<String, Long> entry : advancedStats.entrySet()) {
            if (entry.getKey().equals("PLAY_ONE_MINUTE")) continue;
            if (slot > 34) break;
            inventory.setItem(slot, createItem(Material.EXPERIENCE_BOTTLE,
                    "§a" + formatAdvancedName(entry.getKey()),
                    List.of("§7Wert: §f" + formatAdvancedValue(entry.getKey(), entry.getValue()))));
            slot++;
        }

        inventory.setItem(40, createItem(Material.BARRIER, "§7Zurück", List.of()));
        UiItems.fillEmpty(inventory);
    }

    private String formatAdvancedName(String identifier) {
        return switch (identifier) {
            case "MOB_KILLS" -> "Kreaturen getötet";
            case "PLAY_ONE_MINUTE" -> "Spielzeit (Minecraft)";
            case "WALK_ONE_CM" -> "Strecke gegangen";
            case "SPRINT_ONE_CM" -> "Strecke gesprintet";
            case "FISH_CAUGHT" -> "Fische gefangen";
            case "ANIMALS_BRED" -> "Tiere gezüchtet";
            case "DEATHS" -> "Tode";
            case "JUMP" -> "Sprünge";
            default -> identifier;
        };
    }

    private String formatAdvancedValue(String identifier, long value) {
        return switch (identifier) {
            case "PLAY_ONE_MINUTE" -> TimeFormatter.formatPlaytime(value / 20);
            case "WALK_ONE_CM", "SPRINT_ONE_CM", "SWIM_ONE_CM", "CROUCH_ONE_CM",
                    "FLY_ONE_CM", "BOAT_ONE_CM", "HORSE_ONE_CM" -> formatDistance(value);
            default -> String.valueOf(value);
        };
    }

    private String formatDistance(long cm) {
        if (cm < 100) return cm + " cm";
        long m = cm / 100;
        if (m < 1000) return m + " m";
        long km = m / 1000;
        long restM = m % 1000;
        return km + "," + String.format("%03d", restM) + " km";
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
        if (slot == 40) {
            viewer.closeInventory();
            if (onBack != null) onBack.run();
            return;
        }
        if (slot == 10 && canFix()) {
            viewer.closeInventory();
            SelectSupporterUI.open(plugin, viewer, targetUuid,
                    () -> PlayerProfileUI.open(plugin, viewer, targetUuid, onBack));
        }
    }
}