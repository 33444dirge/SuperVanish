/*
 * Decompiled with CFR 0.152.
 *
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Bukkit
 *  org.bukkit.GameMode
 *  org.bukkit.NamespacedKey
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.event.player.PlayerToggleSneakEvent
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 */
package dev.philippcmd.supervanish.listeners;

import dev.philippcmd.supervanish.utils.VanishManager;
import dev.philippcmd.supervanish.Main;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class SpectatorToggleListener
implements Listener {
    private static final long DOUBLE_TAP_MS = 300L;
    private static final String PERMISSION = "vanish.spectator";
    private final Plugin plugin;
    private final VanishManager vanishManager;
    private final NamespacedKey previousModeKey;
    private final Map<UUID, Long> lastSneak = new ConcurrentHashMap<UUID, Long>();

    public SpectatorToggleListener(Plugin plugin, VanishManager vanishManager) {
        this.plugin = plugin;
        this.vanishManager = vanishManager;
        this.previousModeKey = new NamespacedKey(plugin, "previous_gamemode");
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (!this.vanishManager.isVanished(player) || !player.hasPermission(PERMISSION)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = this.lastSneak.put(player.getUniqueId(), now);
        if (last == null || now - last > 300L) {
            return;
        }
        this.lastSneak.remove(player.getUniqueId());
        if (player.getGameMode() == GameMode.SPECTATOR) {
            this.restore(player);
        } else {
            player.getPersistentDataContainer().set(this.previousModeKey, PersistentDataType.STRING, player.getGameMode().name());
            player.setGameMode(GameMode.SPECTATOR);
            player.sendActionBar((Component)net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(((Main)this.plugin).patrol().text("spectator-on")));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.lastSneak.remove(event.getPlayer().getUniqueId());
    }

    public void restoreIfSpectating(Player player) {
        ((Main)this.plugin).tasks().later(player, () -> {
            if (player.getPersistentDataContainer().has(this.previousModeKey)) {
                this.restore(player);
            }
        });
    }

    public void close() { this.lastSneak.clear(); }

    private void restore(Player player) {
        String stored = (String)player.getPersistentDataContainer().get(this.previousModeKey, PersistentDataType.STRING);
        player.getPersistentDataContainer().remove(this.previousModeKey);
        GameMode mode = Bukkit.getDefaultGameMode();
        if (stored != null) {
            try {
                mode = GameMode.valueOf((String)stored);
            }
            catch (IllegalArgumentException illegalArgumentException) {
                // empty catch block
            }
        }
        if (mode == GameMode.SPECTATOR) {
            mode = GameMode.SURVIVAL;
        }
        player.setGameMode(mode);
        player.sendActionBar((Component)net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(((Main)this.plugin).patrol().text("spectator-off")));
    }
}
