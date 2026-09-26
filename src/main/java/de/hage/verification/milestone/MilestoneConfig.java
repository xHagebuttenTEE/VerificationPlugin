package de.hage.verification.milestone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import de.hage.verification.VerificationPlugin;

public final class MilestoneConfig {

    private final VerificationPlugin plugin;
    private final List<Milestone> milestones = new ArrayList<>();
    private boolean enabled;
    private int checkIntervalSeconds;
    private boolean broadcastOnReach;

    public MilestoneConfig(VerificationPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        milestones.clear();
        FileConfiguration config = plugin.getConfig();
        enabled = config.getBoolean("milestones.enabled", true);
        checkIntervalSeconds = config.getInt("milestones.check-interval-seconds", 60);
        if (checkIntervalSeconds < 30) checkIntervalSeconds = 30;
        broadcastOnReach = config.getBoolean("milestones.broadcast-on-reach", true);

        List<?> rawList = config.getList("milestones.definitions");
        if (rawList == null) {
            return;
        }
        for (Object raw : rawList) {
            if (!(raw instanceof java.util.Map<?, ?> map)) {
                continue;
            }
            try {
                String id = String.valueOf(map.get("id"));
                String displayName = String.valueOf(map.get("display-name"));
                String typeStr = String.valueOf(map.get("type")).toUpperCase();
                MilestoneType type = MilestoneType.valueOf(typeStr);
                long threshold = 0;
                Object thresholdObj = map.get("threshold");
                if (thresholdObj instanceof Number n) {
                    threshold = n.longValue();
                }
                String value = map.containsKey("value") ? String.valueOf(map.get("value")) : null;

                if (id == null || id.isEmpty() || id.equals("null")) {
                    plugin.getLogger().warning("Meilenstein ohne ID übersprungen.");
                    continue;
                }
                if (displayName == null || displayName.isEmpty() || displayName.equals("null")) {
                    displayName = id;
                }

                milestones.add(new Milestone(id, displayName, type, threshold, value));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Fehler beim Laden einer Meilenstein-Definition.", e);
            }
        }
    }

    public boolean isEnabled() { return enabled; }
    public int getCheckIntervalSeconds() { return checkIntervalSeconds; }
    public boolean isBroadcastOnReach() { return broadcastOnReach; }
    public List<Milestone> getMilestones() { return Collections.unmodifiableList(milestones); }
}