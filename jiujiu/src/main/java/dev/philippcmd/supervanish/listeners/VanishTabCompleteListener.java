/*
 * Adapted from SuperVanish TabCompleteListener (Leon Mangler and contributors).
 * Mozilla Public License 2.0: https://mozilla.org/MPL/2.0/
 */
package dev.philippcmd.supervanish.listeners;

import dev.philippcmd.supervanish.utils.VanishManager;
import java.util.ArrayList;
import java.util.Locale;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.TabCompleteEvent;

/** Filters upstream-style completion results using the maintained visibility rules. */
public final class VanishTabCompleteListener implements Listener {
    private final VanishManager manager;

    public VanishTabCompleteListener(VanishManager manager) { this.manager = manager; }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player viewer)) return;
        var hiddenNames = manager.hiddenNamesFor(viewer.getUniqueId());
        if (hiddenNames.isEmpty()) return;
        var completions = new ArrayList<>(event.getCompletions());
        if (completions.removeIf(name -> hiddenNames.contains(name.toLowerCase(Locale.ROOT)))) {
            event.setCompletions(completions);
        }
    }
}
