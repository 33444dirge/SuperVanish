package dev.philippcmd.supervanish;

import de.myzelyam.api.vanish.*;
import dev.philippcmd.supervanish.api.VanishStateChangeEvent;
import dev.philippcmd.supervanish.listeners.VanishTabCompleteListener;
import dev.philippcmd.supervanish.stealth.Patrol;
import dev.philippcmd.supervanish.stealth.SilentChest;
import dev.philippcmd.supervanish.utils.SchedulerUtil;
import dev.philippcmd.supervanish.utils.VanishManager;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.server.TabCompleteEvent;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UpstreamIntegrationTest {
    @TempDir Path directory;
    Main plugin;
    SchedulerUtil tasks;
    Patrol patrol;
    VanishManager manager;
    Player target;
    Player viewer;
    MockedStatic<Bukkit> bukkit;
    List<Event> events;
    Consumer<Event> onEvent;

    @BeforeEach void setUp() {
        plugin = mock(Main.class);
        tasks = mock(SchedulerUtil.class);
        patrol = mock(Patrol.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("SuperVanish-test"));
        when(plugin.tasks()).thenReturn(tasks);
        when(plugin.patrol()).thenReturn(patrol);
        when(tasks.owns(any(Player.class))).thenReturn(true);
        when(patrol.enabled(anyString())).thenReturn(true);
        // The real save path is exercised explicitly; no background threads in this fixture.
        manager = new VanishManager(plugin);
        target = player("HiddenAlice");
        viewer = player("Bob");
        events = new ArrayList<>();
        onEvent = event -> {};
        PluginManager plugins = mock(PluginManager.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(plugins);
        doAnswer(call -> {
            Event event = call.getArgument(0);
            events.add(event);
            onEvent.accept(event);
            return null;
        }).when(plugins).callEvent(any(Event.class));
    }

    @AfterEach void tearDown() { if (bukkit != null) bukkit.close(); }

    Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    void join() {
        manager.handlePlayerJoin(target);
        manager.handlePlayerJoin(viewer);
    }

    @Test void hideAndShowPublishUpstreamAndCjmEventsInOrder() {
        onEvent = event -> {
            if (event instanceof PlayerHideEvent || event instanceof PlayerVanishStateChangeEvent state && state.isVanishing())
                assertFalse(manager.isVanished(target));
            if (event instanceof PostPlayerHideEvent) assertTrue(manager.isVanished(target));
            if (event instanceof PostPlayerShowEvent) assertFalse(manager.isVanished(target));
        };
        assertTrue(manager.toggleVanish(target));
        assertFalse(manager.toggleVanish(target));
        assertEquals(List.of(PlayerHideEvent.class, PlayerVanishStateChangeEvent.class,
                VanishStateChangeEvent.class, PostPlayerHideEvent.class,
                PlayerShowEvent.class, PlayerVanishStateChangeEvent.class,
                VanishStateChangeEvent.class, PostPlayerShowEvent.class),
                events.stream().map(Object::getClass).toList());
    }

    @Test void cancellingHidePreventsAllMutationsAndPostEvents() {
        onEvent = event -> { if (event instanceof PlayerHideEvent hide) hide.setCancelled(true); };
        assertFalse(manager.toggleVanish(target));
        assertFalse(manager.isVanished(target));
        assertEquals(1, events.size());
        verify(target, never()).setMetadata(anyString(), any());
    }

    @Test void cancellingStateChangePreventsHideAndMetadata() {
        onEvent = event -> { if (event instanceof PlayerVanishStateChangeEvent state) state.setCancelled(true); };
        assertFalse(manager.toggleSuperVanish(target));
        assertEquals(2, events.size());
        assertFalse(manager.isVanished(target));
        verify(target, never()).setMetadata(anyString(), any());
    }

    @Test void cancellingShowPreservesModeAndDoesNotRunUnvanishListeners() {
        manager.toggleSuperVanish(target);
        Runnable restored = mock(Runnable.class);
        manager.onUnvanish(player -> restored.run());
        events.clear();
        onEvent = event -> { if (event instanceof PlayerShowEvent show) show.setCancelled(true); };
        assertTrue(manager.setMode(target, null));
        assertTrue(manager.isSuperVanished(target));
        assertEquals(1, events.size());
        verifyNoInteractions(restored);
    }

    @Test void cancellingStateChangePreservesExistingSuperVanish() {
        manager.toggleSuperVanish(target);
        events.clear();
        onEvent = event -> { if (event instanceof PlayerVanishStateChangeEvent state) state.setCancelled(true); };
        assertTrue(manager.setMode(target, null));
        assertTrue(manager.isSuperVanished(target));
        assertEquals(2, events.size());
    }

    @Test void modeSwitchDoesNotDuplicateBoundaryEvents() {
        manager.toggleVanish(target);
        events.clear();
        assertTrue(manager.toggleSuperVanish(target));
        assertTrue(manager.isSuperVanished(target));
        assertTrue(events.isEmpty());
        assertTrue(manager.toggleVanish(target));
        assertTrue(events.isEmpty());
    }

    @Test void listenerReentryIsRejectedAndGuardIsReleased() {
        onEvent = event -> { if (event instanceof PlayerHideEvent) assertFalse(manager.toggleSuperVanish(target)); };
        assertTrue(manager.toggleVanish(target));
        assertEquals(VanishManager.Mode.VANISH, manager.mode(target.getUniqueId()));
        assertFalse(manager.toggleVanish(target));
        assertEquals(8, events.size());
    }

    @Test void postEventPreservesListenerSilentFlag() {
        onEvent = event -> {
            if (event instanceof PlayerHideEvent hide) hide.setSilent(false);
            if (event instanceof PlayerShowEvent show) show.setSilent(false);
        };
        manager.toggleVanish(target);
        manager.toggleVanish(target);
        assertFalse(events.stream().filter(PostPlayerHideEvent.class::isInstance)
                .map(PostPlayerHideEvent.class::cast).findFirst().orElseThrow().isSilent());
        assertFalse(events.stream().filter(PostPlayerShowEvent.class::isInstance)
                .map(PostPlayerShowEvent.class::cast).findFirst().orElseThrow().isSilent());
    }

    @Test void ownerCheckRunsBeforeAnyEvents() {
        when(tasks.owns(target)).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> manager.toggleVanish(target));
        assertTrue(events.isEmpty());
    }

    @Test void completionFollowsNormalGrantsAndAlwaysHidesSuperMode() {
        join();
        manager.toggleVanish(target);
        var listener = new VanishTabCompleteListener(manager);
        var event = new TabCompleteEvent(viewer, "/msg H", List.of("hiddenALICE", "Other"));
        listener.onTabComplete(event);
        assertEquals(List.of("Other"), event.getCompletions());
        assertTrue(manager.addVanishViewer(target, viewer));
        event = new TabCompleteEvent(viewer, "/msg H", List.of("HiddenAlice", "Other"));
        listener.onTabComplete(event);
        assertEquals(2, event.getCompletions().size());
        manager.toggleSuperVanish(target);
        assertFalse(manager.addVanishViewer(target, viewer));
        listener.onTabComplete(event);
        assertEquals(List.of("Other"), event.getCompletions());
        assertTrue(manager.hiddenNamesFor(target.getUniqueId()).isEmpty());
    }

    @Test void consoleCompletionsAreUnaffectedAndQuitClearsSnapshot() {
        join();
        manager.toggleVanish(target);
        var listener = new VanishTabCompleteListener(manager);
        var event = new TabCompleteEvent(mock(CommandSender.class), "/msg H", List.of("HiddenAlice"));
        listener.onTabComplete(event);
        assertEquals(List.of("HiddenAlice"), event.getCompletions());
        manager.handlePlayerQuit(target);
        assertTrue(manager.hiddenNamesFor(viewer.getUniqueId()).isEmpty());
    }

    @Test void metadataUsesOnlyThisPluginAndRespectsConfig() {
        manager.toggleVanish(target);
        verify(target).setMetadata(eq("vanished"), argThat(value -> value.getOwningPlugin() == plugin && value.asBoolean()));
        when(patrol.enabled("publish-vanish-metadata")).thenReturn(false);
        manager.syncVanishMetadata(target);
        verify(target).removeMetadata("vanished", plugin);
    }

    @Test void originalPlayersFileRoundTripsBothModes() {
        manager.toggleVanish(target);
        manager.toggleSuperVanish(viewer);
        manager.close();
        var yaml = YamlConfiguration.loadConfiguration(directory.resolve("players.yml").toFile());
        assertEquals(List.of(target.getUniqueId().toString()), yaml.getStringList("vanished"));
        assertEquals(List.of(viewer.getUniqueId().toString()), yaml.getStringList("super-vanished"));
        var restored = new VanishManager(plugin);
        assertEquals(VanishManager.Mode.VANISH, restored.mode(target.getUniqueId()));
        assertTrue(restored.isSuperVanished(viewer));
    }

    @Test void originalChestCoverageIncludesEveryCopperVariant() {
        for (String name : List.of("CHEST", "TRAPPED_CHEST", "COPPER_CHEST", "EXPOSED_COPPER_CHEST",
                "WEATHERED_COPPER_CHEST", "OXIDIZED_COPPER_CHEST", "WAXED_COPPER_CHEST",
                "WAXED_EXPOSED_COPPER_CHEST", "WAXED_WEATHERED_COPPER_CHEST", "WAXED_OXIDIZED_COPPER_CHEST")) {
            assertTrue(SilentChest.supports(Material.valueOf(name)), name);
        }
        assertFalse(SilentChest.supports(Material.BARREL));
        assertFalse(SilentChest.supports(null));
    }
}
