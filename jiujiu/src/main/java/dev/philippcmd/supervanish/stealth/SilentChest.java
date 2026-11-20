package dev.philippcmd.supervanish.stealth;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftHumanEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;

/** Native, live chest storage; only opener counting and its animations/sounds are omitted. */
public final class SilentChest {
    private SilentChest() {}
    /** All native ChestBlock materials supported by the verified 26.2 target. */
    public static boolean supports(org.bukkit.Material material) {
        if (material == null) return false;
        return switch (material) {
            case CHEST, TRAPPED_CHEST,
                 COPPER_CHEST, EXPOSED_COPPER_CHEST, WEATHERED_COPPER_CHEST, OXIDIZED_COPPER_CHEST,
                 WAXED_COPPER_CHEST, WAXED_EXPOSED_COPPER_CHEST,
                 WAXED_WEATHERED_COPPER_CHEST, WAXED_OXIDIZED_COPPER_CHEST -> true;
            default -> false;
        };
    }
    public static void open(Player player, Location location) {
        var handle = ((CraftPlayer) player).getHandle();
        var level = handle.level();
        BlockPos pos = new BlockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        if (!handle.isWithinBlockInteractionRange(pos, 0)) return;
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return;
        Location partner = location.clone();
        if (state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
            partner.setX(other.getX()); partner.setY(other.getY()); partner.setZ(other.getZ());
        }
        if (!Bukkit.isOwnedByCurrentRegion(location) || !Bukkit.isOwnedByCurrentRegion(partner)) return;
        MenuProvider original = state.getMenuProvider(level, pos);
        if (original == null) return; // Blocked lid/cat and native chest checks remain in effect.
        Location otherHalf = partner.clone();
        handle.openMenu(new SimpleMenuProvider((id, inventory, owner) -> {
            var originalMenu = original.createMenu(id, inventory, owner);
            if (!(originalMenu instanceof ChestMenu chest)) return null; // Locks/loot checked by original provider.
            var live = new SilentContainer(chest.getContainer(), location, otherHalf);
            return new ChestMenu(chest.getType(), id, inventory, live, chest.getRowCount()) {
                @Override public void broadcastChanges() {
                    if (!live.owned()) return;
                    live.snapshot();
                    super.broadcastChanges();
                }
            };
        }, original.getDisplayName()));
    }

    public static final class SilentContainer implements Container {
        private final Container source;
        private final Location first;
        private final Location second;
        private final InventoryHolder holder;
        private final int size;
        private volatile int maxStack;
        private volatile List<net.minecraft.world.item.ItemStack> lastSnapshot;
        private final List<HumanEntity> viewers = new CopyOnWriteArrayList<>();
        public SilentContainer(Container source, Location first, Location second) {
            this.source = source; this.first = first.clone(); this.second = second.clone();
            holder = source.getOwner();
            size = source.getContainerSize(); maxStack = source.getMaxStackSize();
            snapshot();
        }
        // Detached read snapshots support asynchronous logging consumers; never written back to the chest.
        public void snapshot() {
            requireOwner();
            lastSnapshot = source.getContents().stream().map(net.minecraft.world.item.ItemStack::copy).toList();
        }
        public boolean owned() { return Bukkit.isOwnedByCurrentRegion(first) && Bukkit.isOwnedByCurrentRegion(second); }
        private void requireOwner() {
            if (!owned()) throw new IllegalStateException("静默箱子只能在其所属区域访问");
        }
        @Override public int getContainerSize() { return size; }
        @Override public boolean isEmpty() { requireOwner(); return source.isEmpty(); }
        @Override public net.minecraft.world.item.ItemStack getItem(int slot) { requireOwner(); return source.getItem(slot); }
        @Override public net.minecraft.world.item.ItemStack removeItem(int slot, int amount) { requireOwner(); return source.removeItem(slot, amount); }
        @Override public net.minecraft.world.item.ItemStack removeItemNoUpdate(int slot) { requireOwner(); return source.removeItemNoUpdate(slot); }
        @Override public void setItem(int slot, net.minecraft.world.item.ItemStack item) { requireOwner(); source.setItem(slot, item); }
        @Override public int getMaxStackSize() { return maxStack; }
        @Override public void setMaxStackSize(int size) { requireOwner(); source.setMaxStackSize(size); maxStack = source.getMaxStackSize(); }
        @Override public void setChanged() { requireOwner(); source.setChanged(); snapshot(); }
        @Override public void clearContent() { requireOwner(); source.clearContent(); }
        @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return owned() && source.stillValid(player); }
        @Override public boolean canPlaceItem(int slot, net.minecraft.world.item.ItemStack item) { requireOwner(); return source.canPlaceItem(slot, item); }
        @Override public boolean canTakeItem(Container destination, int slot, net.minecraft.world.item.ItemStack item) { requireOwner(); return source.canTakeItem(destination, slot, item); }
        @Override public List<net.minecraft.world.item.ItemStack> getContents() {
            if (owned()) { snapshot(); return source.getContents(); }
            return lastSnapshot.stream().map(net.minecraft.world.item.ItemStack::copy).toList();
        }
        @Override public void onOpen(CraftHumanEntity viewer) { viewers.add(viewer); }
        @Override public void onClose(CraftHumanEntity viewer) { if (owned()) snapshot(); viewers.remove(viewer); }
        @Override public List<HumanEntity> getViewers() { return viewers; }
        @Override public InventoryHolder getOwner() { return holder; }
        @Override public Location getLocation() { return first.clone(); }
        @Override public void startOpen(ContainerUser viewer) { /* Intentionally do not increment the real chest's openers. */ }
        @Override public void stopOpen(ContainerUser viewer) { /* No real opener was added, so no close sound/count decrement. */ }
    }
}
