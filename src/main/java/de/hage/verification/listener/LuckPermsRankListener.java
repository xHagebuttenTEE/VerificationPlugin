package de.hage.verification.listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.event.EventBus;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.service.ProgressService;

public final class LuckPermsRankListener {

    private final VerificationPlugin plugin;
    private final Map<UUID, String> lastKnownGroup = new ConcurrentHashMap<>();
    private final boolean enabled;

    public LuckPermsRankListener(VerificationPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
    }

    public void register() {
        LuckPerms luckPerms = Bukkit.getServicesManager().load(LuckPerms.class);
        if (luckPerms == null) {
            plugin.getLogger().info("LuckPerms nicht gefunden – Rang-Tracking wird deaktiviert.");
            return;
        }
        EventBus eventBus = luckPerms.getEventBus();
        eventBus.subscribe(plugin, UserDataRecalculateEvent.class, this::onRecalculate);
    }

    private void onRecalculate(UserDataRecalculateEvent event) {
        if (!enabled) return;
        if (plugin.getProgressService() == null || !plugin.getProgressService().isEnabled()) return;

        User user = event.getUser();
        UUID uuid = user.getUniqueId();
        String currentGroup = user.getPrimaryGroup();
        if (currentGroup == null || currentGroup.isEmpty()) return;

        String previousGroup = lastKnownGroup.put(uuid, currentGroup);
        if (previousGroup == null || previousGroup.equals(currentGroup)) {
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        String playerName = (player != null) ? player.getName() : user.getUsername();
        if (playerName == null) playerName = uuid.toString();

        String eventType = ProgressService.TYPE_RANK_UP;
        String eventValue = previousGroup + " → " + currentGroup;

        plugin.getProgressService().record(uuid, playerName, eventType, currentGroup, eventValue);

        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().log(Level.INFO,
                    "Rangwechsel erfasst: " + playerName + " " + eventValue);
        }
    }
}