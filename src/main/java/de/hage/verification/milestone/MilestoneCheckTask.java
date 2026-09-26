package de.hage.verification.milestone;

import org.bukkit.scheduler.BukkitTask;
import de.hage.verification.VerificationPlugin;

public final class MilestoneCheckTask {

    private final VerificationPlugin plugin;
    private final MilestoneService service;
    private BukkitTask task;

    public MilestoneCheckTask(VerificationPlugin plugin, MilestoneService service) {
        this.plugin = plugin;
        this.service = service;
    }

    public void start(int intervalSeconds) {
        if (intervalSeconds < 30) intervalSeconds = 30;
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                service::checkAll, intervalSeconds * 20L, intervalSeconds * 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}