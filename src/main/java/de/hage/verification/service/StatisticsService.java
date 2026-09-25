package de.hage.verification.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.database.model.VerificationStatistics;
import de.hage.verification.database.repository.VerificationStatisticsRepository;

public final class StatisticsService implements Listener {

    private final VerificationPlugin plugin;
    private final VerificationStatisticsRepository repository;
    private final EconomyService economyService;
    private final boolean trackPlaytime;
    private final boolean trackBalance;
    private final boolean trackCumulativeEarned;
    private final Map<UUID, SessionInfo> sessions = new ConcurrentHashMap<>();
    private BukkitTask flushTask;

    public StatisticsService(VerificationPlugin plugin,
                             VerificationStatisticsRepository repository,
                             EconomyService economyService,
                             boolean trackPlaytime,
                             boolean trackBalance,
                             boolean trackCumulativeEarned) {
        this.plugin = plugin;
        this.repository = repository;
        this.economyService = economyService;
        this.trackPlaytime = trackPlaytime;
        this.trackBalance = trackBalance;
        this.trackCumulativeEarned = trackCumulativeEarned;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player p : Bukkit.getOnlinePlayers()) {
            sessions.put(p.getUniqueId(), new SessionInfo(p.getName(), System.currentTimeMillis()));
        }
        int interval = plugin.getConfig().getInt("statistics.flush-interval-seconds", 300);
        if (interval < 30) interval = 30;
        flushTask = Bukkit.getScheduler().runTaskTimer(plugin, this::flushAll,
                interval * 20L, interval * 20L);
    }

    public void stop() {
        if (flushTask != null) {
            flushTask.cancel();
            flushTask = null;
        }
        flushAll();
    }

    public CompletableFuture<Map<UUID, VerificationStatistics>> loadStatistics(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return CompletableFuture.completedFuture(new HashMap<>());
        }
        return repository.findAll(uuids);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        sessions.put(event.getPlayer().getUniqueId(),
                new SessionInfo(event.getPlayer().getName(), System.currentTimeMillis()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        SessionInfo info = sessions.remove(uuid);
        if (info == null) {
            return;
        }
        if (trackPlaytime) {
            long seconds = (System.currentTimeMillis() - info.startTime) / 1000;
            if (seconds > 0) {
                repository.addPlaytime(uuid, info.playerName, seconds);
            }
        }
        if (trackBalance && economyService.isAvailable()) {
            BigDecimal balance = economyService.getBalance(uuid);
            if (balance != null) {
                repository.updateBalance(uuid, info.playerName, balance, trackCumulativeEarned);
            }
        }
    }

    private void flushAll() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, SessionInfo> e : sessions.entrySet()) {
            UUID uuid = e.getKey();
            SessionInfo info = e.getValue();
            if (trackPlaytime) {
                long seconds = (now - info.startTime) / 1000;
                if (seconds > 0) {
                    sessions.put(uuid, new SessionInfo(info.playerName, now));
                    repository.addPlaytime(uuid, info.playerName, seconds);
                }
            }
            if (trackBalance && economyService.isAvailable()) {
                BigDecimal balance = economyService.getBalance(uuid);
                if (balance != null) {
                    repository.updateBalance(uuid, info.playerName, balance, trackCumulativeEarned);
                }
            }
        }
    }

    private record SessionInfo(String playerName, long startTime) {}
}