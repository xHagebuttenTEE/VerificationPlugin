package de.hage.verification.command.subcommand;

import org.bukkit.command.CommandSender;

public interface Subcommand {
    void execute(CommandSender sender, String targetName);
}