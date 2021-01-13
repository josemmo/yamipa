package io.josemmo.bukkit.plugin.renderer;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ImageRenderer implements Listener {
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private final String configPath;
    private final Map<String, WorldArea> worldAreas = new HashMap<>();
    private final Map<UUID, String> playersLocation = new HashMap<>();

    /**
     * Class constructor
     * @param configPath Path to YAML configuration file
     */
    public ImageRenderer(String configPath) {
        this.configPath = configPath;
    }

    /**
     * Start instance
     */
    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // TODO: load fake images from "configPath"
    }

    /**
     * Stop instance
     */
    public void stop() {
        HandlerList.unregisterAll(this);
        for (WorldArea worldArea : worldAreas.values()) {
            worldArea.unload();
        }
    }

    /**
     * On player location change
     * @param player   Player instance
     * @param location New player location
     */
    private void onPlayerLocationChange(Player player, Location location) {
        UUID uuid = player.getUniqueId();

        // Has player moved to another world area?
        String worldAreaId = WorldArea.getId(location);
        String prevWorldAreaId = playersLocation.get(uuid);
        if (worldAreaId.equals(prevWorldAreaId)) {
            return;
        }

        // Unload previous world area
        if (prevWorldAreaId != null && worldAreas.containsKey(prevWorldAreaId)) {
            worldAreas.get(prevWorldAreaId).unload(player);
        }

        // Update and load current world area
        playersLocation.put(uuid, worldAreaId);
        if (worldAreas.containsKey(worldAreaId)) {
            worldAreas.get(worldAreaId).load(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        onPlayerLocationChange(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Notify world area that player quit
        String worldAreaId = playersLocation.get(uuid);
        if (worldAreaId != null) {
            if (worldAreas.containsKey(worldAreaId)) {
                worldAreas.get(worldAreaId).removePlayer(player);
            }
            playersLocation.remove(uuid);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        onPlayerLocationChange(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        onPlayerLocationChange(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;

        // Player is about to change to another chunk, notify event
        onPlayerLocationChange(event.getPlayer(), event.getTo());
    }
}
