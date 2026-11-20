package dev.philippcmd.supervanish.commands;

import dev.philippcmd.supervanish.utils.VanishManager;
import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/** Command lookup uses cached identities, without reading players in other regions. */
public class VanishShowCommand implements TabExecutor {
    private final VanishManager vanishManager;
    public VanishShowCommand(VanishManager vanishManager) { this.vanishManager = vanishManager; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var messages = vanishManager.plugin().patrol();
        if (!(sender instanceof Player player)) { messages.message(sender, "players-only"); return true; }
        if (args.length != 1) { messages.message(player, "show-usage"); return true; }
        if (args[0].equalsIgnoreCase("--all")) {
            int count = 0;
            for (var target : vanishManager.normalPlayers()) {
                if (!target.id().equals(player.getUniqueId()) && vanishManager.addVanishViewer(target.id(), player.getUniqueId())) count++;
            }
            messages.message(player, "show-all", "{count}", String.valueOf(count));
            return true;
        }
        var target = vanishManager.byName(args[0]);
        if (target == null) messages.message(player, "not-found", "{player}", args[0]);
        else if (!vanishManager.isVanished(target.id())) messages.message(player, "not-hidden", "{player}", target.name());
        else if (!vanishManager.addVanishViewer(target.id(), player.getUniqueId())) messages.message(player, "cannot-reveal", "{player}", target.name());
        else messages.message(player, "revealed", "{player}", target.name());
        return true;
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("--all") : List.of();
    }
}
