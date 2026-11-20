/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 */
package dev.philippcmd.supervanish.commands;

import dev.philippcmd.supervanish.utils.VanishManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ToggleVanishCommand
implements CommandExecutor {
    private final VanishManager vanishManager;
    private final boolean superVanish;

    public ToggleVanishCommand(VanishManager vanishManager, boolean superVanish) {
        this.vanishManager = vanishManager;
        this.superVanish = superVanish;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            this.vanishManager.plugin().patrol().message(sender, "players-only");
            return true;
        }
        Player player = (Player)sender;
        if (this.superVanish) {
            this.vanishManager.plugin().patrol().message(player, this.vanishManager.toggleSuperVanish(player) ? "super-on" : "super-off");
        } else {
            this.vanishManager.plugin().patrol().message(player, this.vanishManager.toggleVanish(player) ? "vanish-on" : "vanish-off");
        }
        return true;
    }
}
