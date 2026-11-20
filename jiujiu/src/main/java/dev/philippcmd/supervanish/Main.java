package dev.philippcmd.supervanish;

import dev.philippcmd.supervanish.commands.*;
import dev.philippcmd.supervanish.listeners.*;
import dev.philippcmd.supervanish.stealth.Patrol;
import dev.philippcmd.supervanish.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin {
    private VanishManager vanishManager;
    private SchedulerUtil tasks;
    private Patrol patrol;
    private SpectatorToggleListener spectator;
    private volatile boolean active;

    @Override public void onEnable() {
        tasks = new SchedulerUtil(this);
        vanishManager = new VanishManager(this);
        active = true;
        getCommand("vanish").setExecutor(new ToggleVanishCommand(vanishManager, false));
        getCommand("supervanish").setExecutor(new ToggleVanishCommand(vanishManager, true));
        getCommand("vanish-show").setExecutor(new VanishShowCommand(vanishManager));
        getCommand("vanish-list").setExecutor(new VanishListCommand(vanishManager));
        spectator = new SpectatorToggleListener(this, vanishManager);
        vanishManager.onUnvanish(spectator::restoreIfSpectating);
        getServer().getPluginManager().registerEvents(new PlayerEventListener(vanishManager), this);
        getServer().getPluginManager().registerEvents(new VanishTabCompleteListener(vanishManager), this);
        getServer().getPluginManager().registerEvents(spectator, this);
        patrol = new Patrol(this);
        patrol.enable();
        for (var player : Bukkit.getOnlinePlayers()) tasks.atPlayer(player, () -> {
            vanishManager.handlePlayerJoin(player);
            patrol.remember(player);
        });
    }
    @Override public void onDisable() {
        active = false;
        if (tasks != null) tasks.close();
        if (patrol != null) patrol.close();
        if (spectator != null) spectator.close();
        if (vanishManager != null) vanishManager.close();
    }
    public VanishManager getVanishManager() { return vanishManager; }
    public VanishManager manager() { return vanishManager; }
    public SchedulerUtil tasks() { return tasks; }
    public Patrol patrol() { return patrol; }
    public boolean running() { return active && isEnabled(); }
}
