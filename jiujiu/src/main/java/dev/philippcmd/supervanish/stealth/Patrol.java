package dev.philippcmd.supervanish.stealth;

import dev.philippcmd.supervanish.Main;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;

public final class Patrol implements Listener, org.bukkit.command.CommandExecutor {
    private final Main plugin;
    private final Map<String, UUID> names = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> opening = ConcurrentHashMap.newKeySet();
    private volatile YamlConfiguration config;
    private Listener coreListener;
    private volatile boolean coreHooked;
    public Patrol(Main plugin) { this.plugin = plugin; }

    public void enable() {
        File file = new File(plugin.getDataFolder(), "stealth.yml");
        if (!file.exists()) plugin.saveResource("stealth.yml", false);
        try { load(); } catch (Exception e) { throw new IllegalStateException("stealth.yml 无法读取", e); }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(new SilentChestListener(plugin), plugin);
        plugin.getCommand("vanish-stealth").setExecutor(this);
        plugin.manager().onUnvanish(player -> plugin.tasks().later(player, () -> {
            if (player.getOpenInventory().getTopInventory() instanceof org.bukkit.craftbukkit.inventory.CraftInventory inventory
                    && inventory.getInventory() instanceof SilentChest.SilentContainer) player.closeInventory();
        }));
        var core = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (core != null && core.isEnabled() && core.getPluginMeta().getVersion().equals("24.0")) {
            coreListener = new CoreLogListener(this);
            Bukkit.getPluginManager().registerEvents(coreListener, plugin);
            coreHooked = true;
            plugin.getLogger().info("已挂接 CoreProtect 24.0 现有预记录事件；聊天、会话及改名不在接口范围内。");
        } else plugin.getLogger().warning("未找到本次核对的 CoreProtect 24.0，记录过滤未启用；静默开箱与禁止拾取仍可用。");
    }
    private void load() throws Exception {
        YamlConfiguration next = new YamlConfiguration();
        next.load(new File(plugin.getDataFolder(), "stealth.yml"));
        for (String key : java.util.List.of("prevent-item-pickup", "prevent-experience-pickup", "silent-chests", "filter-coreprotect", "publish-vanish-metadata")) {
            String path = "features." + key;
            if (next.contains(path) && !next.isBoolean(path)) throw new IllegalArgumentException(path + " 必须是 true 或 false");
        }
        if (next.contains("admin-permission") && !next.isString("admin-permission"))
            throw new IllegalArgumentException("admin-permission 必须是权限节点字符串");
        try (var stream = plugin.getResource("stealth.yml")) {
            next.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
        config = next;
        plugin.manager().refreshVanishMetadata();
    }
    public boolean enabled(String key) { return config.getBoolean("features." + key, true); }
    public boolean hidden(Player player) { return plugin.manager().isVanished(player); }
    public boolean hiddenName(String name) {
        if (!enabled("filter-coreprotect") || name == null) return false;
        UUID id = names.get(name.toLowerCase(Locale.ROOT));
        return id != null && plugin.manager().isVanished(id);
    }
    public void remember(Player player) {
        UUID id = player.getUniqueId(); String name = player.getName().toLowerCase(Locale.ROOT);
        names.entrySet().removeIf(entry -> entry.getValue().equals(id) && !entry.getKey().equals(name));
        names.put(name, id);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void join(PlayerJoinEvent event) { remember(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR) public void quit(PlayerQuitEvent event) {
        if (!hidden(event.getPlayer())) names.remove(event.getPlayer().getName().toLowerCase(Locale.ROOT), event.getPlayer().getUniqueId());
        opening.remove(event.getPlayer().getUniqueId());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void pickup(EntityPickupItemEvent event) {
        if (enabled("prevent-item-pickup") && event.getEntity() instanceof Player player && hidden(player)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void experience(com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent event) {
        if (enabled("prevent-experience-pickup") && hidden(event.getPlayer())) event.setCancelled(true);
    }
    // Consume only after protection decisions. No synthetic right-click is emitted on the following tick.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (!enabled("silent-chests") || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer(); Block block = event.getClickedBlock();
        if (block == null || !hidden(player) || !SilentChest.supports(block.getType())) return;
        if (event.useInteractedBlock() == Event.Result.DENY) return;
        // Sneak+item normally places the item; preserve that vanilla behavior.
        if (player.isSneaking() && (!player.getInventory().getItemInMainHand().getType().isAir()
                || !player.getInventory().getItemInOffHand().getType().isAir())) return;
        event.setUseInteractedBlock(Event.Result.DENY); event.setUseItemInHand(Event.Result.DENY);
        UUID id = player.getUniqueId();
        if (!opening.add(id)) return;
        Location location = block.getLocation(); Material expected = block.getType();
        plugin.tasks().later(player, () -> {
            opening.remove(id);
            if (!hidden(player) || !enabled("silent-chests")) return;
            if (!plugin.tasks().owns(location) || player.getWorld() != location.getWorld()
                    || player.getLocation().distanceSquared(location.clone().add(0.5, 0.5, 0.5)) > 64) return;
            if (location.getBlock().getType() != expected) return;
            try { SilentChest.open(player, location); }
            catch (RuntimeException | LinkageError e) {
                plugin.getLogger().warning("静默开箱失败：" + e);
                message(player, "open-failed");
            }
        });
    }
    public String text(String key, String... replacements) {
        String text = config.getString("messages." + key, "");
        for (int i = 0; i + 1 < replacements.length; i += 2) text = text.replace(replacements[i], replacements[i+1]);
        return text;
    }
    public void message(CommandSender sender, String key, String... replacements) {
        sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(text(key, replacements)));
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String permission = config.getString("admin-permission", "vanish.stealth.admin");
        if (permission.isBlank() || !sender.hasPermission(permission)) { message(sender, "no-permission"); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.tasks().async(() -> {
                String result;
                try { load(); result = "reloaded"; } catch (Exception e) { result = "reload-failed"; plugin.getLogger().warning(e.toString()); }
                String key = result;
                if (sender instanceof Player player) plugin.tasks().atPlayer(player, () -> message(player, key)); else message(sender, key);
            });
        } else {
            message(sender, "status", "{chests}", text(enabled("silent-chests") ? "enabled" : "disabled"),
                    "{pickup}", text(enabled("prevent-item-pickup") ? "enabled" : "disabled"),
                    "{coreprotect}", text(coreHooked && enabled("filter-coreprotect") ? "enabled" : "disabled"));
            message(sender, "scope");
        }
        return true;
    }
    public void close() {
        HandlerList.unregisterAll(this);
        if (coreListener != null) HandlerList.unregisterAll(coreListener);
        coreHooked = false; names.clear(); opening.clear();
    }
}
