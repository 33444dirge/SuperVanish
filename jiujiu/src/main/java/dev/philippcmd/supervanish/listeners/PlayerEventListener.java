/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 */
package dev.philippcmd.supervanish.listeners;

import dev.philippcmd.supervanish.utils.VanishManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerEventListener
implements Listener {
    private final VanishManager vanishManager;

    public PlayerEventListener(VanishManager vanishManager) {
        this.vanishManager = vanishManager;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.vanishManager.handlePlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        this.vanishManager.handlePlayerQuit(event.getPlayer());
    }
}
