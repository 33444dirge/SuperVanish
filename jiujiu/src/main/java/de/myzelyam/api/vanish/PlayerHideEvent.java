package de.myzelyam.api.vanish;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Compatibility boundary for listeners expecting the conventional SuperVanish event. */
public class PlayerHideEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private boolean silent;
    public PlayerHideEvent(Player player) { this(player, false); }
    public PlayerHideEvent(Player player, boolean silent) { super(player); this.silent = silent; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean value) { cancelled = value; }
    public boolean isSilent() { return silent; }
    public void setSilent(boolean value) { silent = value; }
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
