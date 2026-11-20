package dev.philippcmd.supervanish.stealth;

import dev.philippcmd.supervanish.Main;
import org.bukkit.craftbukkit.inventory.CraftInventory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class SilentChestListener implements Listener {
    private final Main plugin;
    public SilentChestListener(Main plugin) { this.plugin = plugin; }
    public static boolean invalid(Inventory inventory) {
        return inventory instanceof CraftInventory craft && craft.getInventory() instanceof SilentChest.SilentContainer silent && !silent.owned();
    }
    private static boolean invalid(Inventory inventory, Player player) {
        return inventory instanceof CraftInventory craft && craft.getInventory() instanceof SilentChest.SilentContainer silent
                && !silent.stillValid(((org.bukkit.craftbukkit.entity.CraftPlayer) player).getHandle());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player actor && invalid(event.getView().getTopInventory(), actor)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) plugin.tasks().later(player, player::closeInventory);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void drag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player actor && invalid(event.getView().getTopInventory(), actor)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) plugin.tasks().later(player, player::closeInventory);
        }
    }
}
