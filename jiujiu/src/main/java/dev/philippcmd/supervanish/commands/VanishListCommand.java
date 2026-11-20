/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 */
package dev.philippcmd.supervanish.commands;

import dev.philippcmd.supervanish.utils.VanishManager;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class VanishListCommand
implements CommandExecutor {
    private final VanishManager vanishManager;

    public VanishListCommand(VanishManager vanishManager) {
        this.vanishManager = vanishManager;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<String> vanishedPlayers = this.vanishManager.getVanishedPlayerNames();
        if (vanishedPlayers.isEmpty()) {
            this.vanishManager.plugin().patrol().message(sender, "list-empty");
        } else {
            this.vanishManager.plugin().patrol().message(sender, "list", "{players}", String.join("、", vanishedPlayers));
        }
        return true;
    }
}
