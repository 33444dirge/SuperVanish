package dev.philippcmd.supervanish.stealth;

import net.coreprotect.event.CoreProtectPreLogEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** CoreProtect fires this asynchronously; no live Bukkit state may be accessed here. */
public final class CoreLogListener implements Listener {
    private final Patrol patrol;
    public CoreLogListener(Patrol patrol) { this.patrol = patrol; }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void preLog(CoreProtectPreLogEvent event) {
        if (patrol.hiddenName(event.getUser())) event.setCancelled(true);
    }
}
