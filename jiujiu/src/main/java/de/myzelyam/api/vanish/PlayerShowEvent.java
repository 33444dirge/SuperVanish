package de.myzelyam.api.vanish;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** Compatibility boundary; cancellation is respected before the actual visible-state change. */
public class PlayerShowEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;
    private boolean silent;
    public PlayerShowEvent(Player player) { this(player, false); }
    public PlayerShowEvent(Player player, boolean silent) { super(player); this.silent = silent; }
    public boolean isCancelled() { return cancelled; }
    public void setCancelled(boolean value) { cancelled = value; }
    public boolean isSilent() { return silent; }
    public void setSilent(boolean value) { silent = value; }
    public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
