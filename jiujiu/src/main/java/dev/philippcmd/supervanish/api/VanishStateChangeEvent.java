package dev.philippcmd.supervanish.api;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;

/** 此版本 SuperVanish 的实际公开/隐藏状态变化，不表示两种隐藏模式互换。 */
public final class VanishStateChangeEvent extends PlayerEvent {
    private static final HandlerList HANDLERS = new HandlerList();
    private final boolean vanishing;

    public VanishStateChangeEvent(Player player, boolean vanishing) {
        super(player);
        this.vanishing = vanishing;
    }

    public boolean isVanishing() {
        return vanishing;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    /** 在修改原状态之前校验；只支持目标 Folia 核心。 */
    public static void requireOwner(Player player) {
        if (!Bukkit.isOwnedByCurrentRegion(player)) {
            throw new IllegalStateException("Vanish changes must run on the player's owning region");
        }
    }

    /** 原 toggle 在同一实体上下文完成，事件不跨区域、不延迟。 */
    public static void publish(Player player, boolean wasVanished, boolean nowVanished) {
        if (wasVanished != nowVanished) {
            Bukkit.getPluginManager().callEvent(new VanishStateChangeEvent(player, nowVanished));
        }
    }
}
