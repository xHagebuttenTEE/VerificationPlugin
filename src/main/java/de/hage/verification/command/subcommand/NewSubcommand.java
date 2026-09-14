package de.hage.verification.command.subcommand;

import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import de.hage.verification.VerificationPlugin;
import de.hage.verification.util.PlayerUtil;

public final class NewSubcommand implements Subcommand {

    private final VerificationPlugin plugin;

    public NewSubcommand(VerificationPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String targetName) {
        if (!sender.hasPermission("verification.new")) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.noPermission"));
            return;
        }

        UUID targetUUID = PlayerUtil.getPlayerUUID(targetName);
        if (targetUUID == null) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.playerNotFound"));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(targetUUID);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(plugin.getMessageService().getMessage("command.playerNotFound"));
            return;
        }

        UUID verificatorUUID = (sender instanceof Player) ? ((Player) sender).getUniqueId() : null;
        String displayName = plugin.getVerificationService().getVerifiedDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = plugin.getConfig().getString("roles.verified", "i");
        }
        final String roleDisplay = displayName;

        plugin.getVerificationService().verifyPlayer(targetUUID, verificatorUUID).thenAcceptAsync(success -> {
            if (success) {
                sender.sendMessage(plugin.getMessageService().getMessage("command.success",
                        "player", target.getName(),
                        "role", roleDisplay));
            } else {
                sender.sendMessage(plugin.getMessageService().getMessage("command.alreadyVerified"));
            }
        }).exceptionally(ex -> {
            sender.sendMessage(plugin.getMessageService().getMessage("command.luckPermsError"));
            plugin.getLogger().warning("Fehler bei der Verifizierung von " + targetName + ": " + ex.getMessage());
            return null;
        });
    }
}