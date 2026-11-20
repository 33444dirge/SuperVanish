package dev.philippcmd.supervanish.utils;

import dev.philippcmd.supervanish.Main;
import dev.philippcmd.supervanish.api.VanishStateChangeEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.Plugin;

public class VanishManager {
    public enum Mode { VANISH, SUPER_VANISH }
    public record Online(UUID id, String name, Player handle) {}
    private final Main plugin;
    private final File dataFile;
    private final Object saveLock = new Object();
    private final Map<UUID, Mode> vanished = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> viewers = new ConcurrentHashMap<>();
    private final Map<UUID, Online> online = new ConcurrentHashMap<>();
    private final List<Consumer<Player>> unvanishListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean saving = new AtomicBoolean();
    private final AtomicLong revision = new AtomicLong();
    private volatile boolean closed;
    private volatile boolean readable = true;
    // Listener re-entry is rejected for the same player; removed in finally, no retained sessions.
    private final Set<UUID> changing = ConcurrentHashMap.newKeySet();

    public VanishManager(Plugin plugin) {
        this.plugin = (Main) plugin;
        dataFile = new File(plugin.getDataFolder(), "players.yml");
        if (dataFile.exists()) {
            YamlConfiguration config = new YamlConfiguration();
            try {
                config.load(dataFile);
                load(config.getStringList("vanished"), Mode.VANISH);
                load(config.getStringList("super-vanished"), Mode.SUPER_VANISH);
            } catch (Exception e) {
                readable = false;
                throw new IllegalStateException("players.yml 无法读取，为保护原数据已停止启用", e);
            }
        }
    }

    private void load(List<String> ids, Mode mode) {
        for (String id : ids) {
            try { vanished.put(UUID.fromString(id), mode); }
            catch (IllegalArgumentException e) { plugin.getLogger().warning("players.yml 中无效的 UUID：" + id); }
        }
    }

    public Main plugin() { return plugin; }
    public Mode mode(UUID id) { return vanished.get(id); }
    public boolean isVanished(UUID id) { return vanished.containsKey(id); }
    public Online online(UUID id) { return online.get(id); }
    public Online byName(String name) {
        return online.values().stream().filter(p -> p.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public List<Online> normalPlayers() {
        return online.values().stream().filter(p -> mode(p.id()) == Mode.VANISH)
                .sorted(Comparator.comparing(Online::name, String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public List<String> getVanishedPlayerNames() { return normalPlayers().stream().map(Online::name).toList(); }
    public boolean isVanished(Player player) { return vanished.containsKey(player.getUniqueId()); }
    public boolean isSuperVanished(Player player) { return mode(player.getUniqueId()) == Mode.SUPER_VANISH; }
    public boolean toggleVanish(Player player) { return toggle(player, Mode.VANISH); }
    public boolean toggleSuperVanish(Player player) { return toggle(player, Mode.SUPER_VANISH); }

    private boolean toggle(Player player, Mode requested) {
        UUID id = player.getUniqueId();
        return setMode(player, mode(id) == requested ? null : requested);
    }

    public boolean setMode(Player player, Mode next) {
        if (!plugin.tasks().owns(player)) throw new IllegalStateException("隐身操作必须在玩家所属线程执行");
        UUID id = player.getUniqueId();
        boolean wasVanished = vanished.containsKey(id);
        if (!changing.add(id)) return wasVanished;
        try {
        boolean silent = true;
        if (wasVanished != (next != null)) {
            org.bukkit.event.Cancellable compatibility = next != null
                    ? new de.myzelyam.api.vanish.PlayerHideEvent(player, true)
                    : new de.myzelyam.api.vanish.PlayerShowEvent(player, true);
            org.bukkit.Bukkit.getPluginManager().callEvent((org.bukkit.event.Event) compatibility);
            if (compatibility.isCancelled()) return wasVanished;
            silent = compatibility instanceof de.myzelyam.api.vanish.PlayerHideEvent hide
                    ? hide.isSilent() : ((de.myzelyam.api.vanish.PlayerShowEvent) compatibility).isSilent();
            var stateChange = new de.myzelyam.api.vanish.PlayerVanishStateChangeEvent(
                    id, player.getName(), next != null, null);
            org.bukkit.Bukkit.getPluginManager().callEvent(stateChange);
            if (stateChange.isCancelled()) return wasVanished;
        }
        if (next == null) vanished.remove(id); else vanished.put(id, next);
        syncVanishMetadata(player);
        viewers.remove(id);
        Online target = online.get(id);
        if (target != null) for (Online viewer : online.values()) refresh(viewer, target);
        revision.incrementAndGet();
        saveAsync();
        if (next == null) unvanishListeners.forEach(listener -> listener.accept(player));
        VanishStateChangeEvent.publish(player, wasVanished, next != null);
        if (wasVanished != (next != null)) {
            org.bukkit.Bukkit.getPluginManager().callEvent(next != null
                    ? new de.myzelyam.api.vanish.PostPlayerHideEvent(player, silent)
                    : new de.myzelyam.api.vanish.PostPlayerShowEvent(player, silent));
        }
        return next != null;
        } finally { changing.remove(id); }
    }

    public void handlePlayerJoin(Player player) {
        syncVanishMetadata(player);
        Online joined = new Online(player.getUniqueId(), player.getName(), player);
        online.put(joined.id(), joined);
        for (Online other : online.values()) {
            refresh(other, joined);
            refresh(joined, other);
        }
    }

    public void handlePlayerQuit(Player player) {
        player.removeMetadata("vanished", plugin);
        UUID id = player.getUniqueId();
        online.remove(id);
        viewers.remove(id);
        viewers.values().forEach(set -> set.remove(id));
    }

    public void onUnvanish(Consumer<Player> listener) { unvanishListeners.add(listener); }

    /** Publish the conventional Bukkit marker; never alter another plugin's metadata value. */
    public void syncVanishMetadata(Player player) {
        if (!plugin.tasks().owns(player)) throw new IllegalStateException("隐身标记必须在玩家所属线程更新");
        boolean enabled = plugin.patrol() == null || plugin.patrol().enabled("publish-vanish-metadata");
        if (enabled && isVanished(player)) player.setMetadata("vanished", new FixedMetadataValue(plugin, true));
        else player.removeMetadata("vanished", plugin);
    }

    public void refreshVanishMetadata() {
        for (Online session : online.values()) plugin.tasks().atPlayer(session.handle(), () -> {
            if (!closed && online.get(session.id()) == session) syncVanishMetadata(session.handle());
        });
    }
    public boolean addVanishViewer(Player target, Player viewer) {
        return addVanishViewer(target.getUniqueId(), viewer.getUniqueId());
    }
    public boolean addVanishViewer(UUID target, UUID viewer) {
        Online entry = online.get(target);
        Online observer = online.get(viewer);
        if (entry == null || observer == null || mode(entry.id()) != Mode.VANISH) return false;
        Set<UUID> grants = viewers.computeIfAbsent(entry.id(), ignored -> ConcurrentHashMap.newKeySet());
        grants.add(observer.id());
        if (online.get(entry.id()) != entry || online.get(observer.id()) != observer || mode(entry.id()) != Mode.VANISH) {
            grants.remove(observer.id());
            if (grants.isEmpty()) viewers.remove(entry.id(), grants);
            return false;
        }
        refresh(observer, entry);
        return true;
    }
    public boolean canSee(UUID viewer, UUID target) {
        if (viewer.equals(target)) return true;
        Mode mode = mode(target);
        if (mode == null) return true;
        if (mode == Mode.SUPER_VANISH) return false;
        Set<UUID> allowed = viewers.get(target);
        return allowed != null && allowed.contains(viewer);
    }

    /** Snapshots avoid reading another region's live player state during completion callbacks. */
    public Set<String> hiddenNamesFor(UUID viewer) {
        return online.values().stream()
                .filter(target -> !canSee(viewer, target.id()))
                .map(target -> target.name().toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private void refresh(Online viewer, Online target) {
        if (viewer.id().equals(target.id())) return;
        plugin.tasks().atPlayer(viewer.handle(), () -> {
            if (online.get(viewer.id()) != viewer || online.get(target.id()) != target) return;
            // Official 26.2 visibility API runs on observer's owner; no target live-state reads here.
            if (canSee(viewer.id(), target.id())) viewer.handle().showPlayer(plugin, target.handle());
            else viewer.handle().hidePlayer(plugin, target.handle());
        });
    }

    private void saveAsync() {
        if (closed || !saving.compareAndSet(false, true)) return;
        plugin.tasks().async(() -> {
            long saved = -1;
            try {
                do {
                    saved = revision.get();
                    synchronized (saveLock) { if (!closed) write(); }
                } while (!closed && saved != revision.get());
            } finally {
                saving.set(false);
                if (!closed && saved != revision.get()) saveAsync();
            }
        });
    }

    /** Synchronous only for explicit lifecycle flush, never called by player actions. */
    public void saveNow() { synchronized (saveLock) { if (!closed) write(); } }
    private void write() {
        if (!readable) return;
        List<String> normal = new ArrayList<>(), special = new ArrayList<>();
        vanished.forEach((id, mode) -> (mode == Mode.SUPER_VANISH ? special : normal).add(id.toString()));
        normal.sort(String::compareTo); special.sort(String::compareTo);
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("vanished", normal); yaml.set("super-vanished", special);
        var target = dataFile.toPath();
        var temp = target.resolveSibling("players.yml.tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { plugin.getLogger().severe("保存隐身数据失败：" + e.getMessage()); }
    }
    public void close() {
        synchronized (saveLock) { closed = true; write(); }
        online.clear(); viewers.clear(); vanished.clear(); unvanishListeners.clear();
    }
}
