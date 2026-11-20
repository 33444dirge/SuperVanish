package dev.philippcmd.supervanish.utils;

import dev.philippcmd.supervanish.Main;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.Location;

/** All region/entity hops incur at least one tick. */
public final class SchedulerUtil {
    private static Boolean detectedFolia;
    private final Main plugin;
    private final boolean folia;
    private final Set<Pending> pending = ConcurrentHashMap.newKeySet();
    private static final class Pending {
        private ScheduledTask task;
        private boolean ended;
        synchronized void attach(ScheduledTask value) {
            task = value;
            if (ended && task != null) task.cancel();
        }
        synchronized void cancel() {
            ended = true;
            if (task != null) task.cancel();
        }
    }

    public SchedulerUtil(Main plugin) {
        this.plugin = plugin;
        folia = detectFolia();
    }

    private static synchronized boolean detectFolia() {
        if (detectedFolia != null) return detectedFolia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            detectedFolia = true;
        } catch (ClassNotFoundException e) { detectedFolia = false; }
        return detectedFolia;
    }

    public boolean isFolia() { return folia; }

    public boolean owns(Player player) {
        return folia ? Bukkit.isOwnedByCurrentRegion(player) : Bukkit.isPrimaryThread();
    }

    public boolean owns(Location location) {
        return folia ? Bukkit.isOwnedByCurrentRegion(location) : Bukkit.isPrimaryThread();
    }

    public void atPlayer(Player player, Runnable action) {
        if (!plugin.running()) return;
        if (owns(player)) action.run();
        else later(player, action);
    }

    public synchronized void later(Player player, Runnable action) {
        if (!plugin.running()) return;
        if (folia) {
            Pending ticket = new Pending();
            pending.add(ticket);
            ScheduledTask task = player.getScheduler().runDelayed(plugin, scheduled -> {
                pending.remove(ticket);
                if (plugin.running() && player.isOnline()) action.run();
            }, () -> pending.remove(ticket), 1L);
            ticket.attach(task);
            if (task == null) pending.remove(ticket);
        } else Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.running() && player.isOnline()) action.run();
        }, 1L);
    }

    public void async(Runnable action) {
        if (!plugin.running()) return;
        if (folia) Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            if (plugin.running()) action.run();
        });
        else Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (plugin.running()) action.run();
        });
    }

    public synchronized void close() {
        pending.forEach(Pending::cancel);
        pending.clear();
        if (folia) {
            Bukkit.getAsyncScheduler().cancelTasks(plugin);
            Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        } else Bukkit.getScheduler().cancelTasks(plugin);
    }
}
