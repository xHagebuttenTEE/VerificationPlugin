package de.hage.verification.listener;

import io.papermc.paper.advancement.AdvancementDisplay;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.service.ProgressService;

public final class AdvancementListener implements Listener {

    private final VerificationPlugin plugin;
    private final boolean enabled;

    public AdvancementListener(VerificationPlugin plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!enabled) return;
        if (plugin.getProgressService() == null || !plugin.getProgressService().isEnabled()) return;

        Advancement advancement = event.getAdvancement();
        AdvancementDisplay display = advancement.getDisplay();
        if (display == null) return;
        if (display.isHidden()) return;

        Player player = event.getPlayer();
        String key = advancement.getKey().toString();
        String title = PlainTextComponentSerializer.plainText().serialize(display.title());

        plugin.getProgressService().record(player.getUniqueId(), player.getName(),
                ProgressService.TYPE_ADVANCEMENT, key, title);
    }
}