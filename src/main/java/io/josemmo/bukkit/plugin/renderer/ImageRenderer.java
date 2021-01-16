package io.josemmo.bukkit.plugin.renderer;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.CsvConfiguration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Rotation;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImageRenderer implements Listener {
    static public final long SAVE_INTERVAL = 20L * 90; // In server ticks
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    static private final Logger logger = plugin.getLogger();
    private final String configPath;
    private BukkitTask saveTask;
    private final AtomicBoolean hasConfigChanged = new AtomicBoolean(false);
    private final ConcurrentMap<String, WorldArea> worldAreas = new ConcurrentHashMap<>();
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
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveConfig, SAVE_INTERVAL, SAVE_INTERVAL);
    }

    /**
     * Stop instance
     */
    public void stop() {
        HandlerList.unregisterAll(this);

        // Destroy images from remote clients
        for (WorldArea worldArea : worldAreas.values()) {
            worldArea.unload();
        }

        // Persist configuration
        if (saveTask != null) {
            saveTask.cancel();
        }
        saveConfig();
    }

    /**
     * Load configuration from disk
     */
    private void loadConfig() {
        CsvConfiguration config = new CsvConfiguration();

        // Try to load configuration
        try {
            config.load(configPath);
        } catch (IOException e) {
            logger.warning("Failed to load placed fake images from disk");
            return;
        }

        // Parse each row
        for (String[] row : config.getRows()) {
            if (row.length != 9) continue;
            try {
                ImageFile imageFile = Objects.requireNonNull(plugin.getStorage().get(row[0]));
                World world = Objects.requireNonNull(plugin.getServer().getWorld(row[1]));
                double x = Integer.parseInt(row[2]);
                double y = Integer.parseInt(row[3]);
                double z = Integer.parseInt(row[4]);
                Location location = new Location(world, x, y, z);
                BlockFace face = BlockFace.valueOf(row[5]);
                Rotation rotation = Rotation.valueOf(row[6]);
                int width = Math.min(FakeImage.MAX_DIMENSION, Math.abs(Integer.parseInt(row[7])));
                int height = Math.min(FakeImage.MAX_DIMENSION, Math.abs(Integer.parseInt(row[8])));
                addImage(new FakeImage(imageFile, location, face, rotation, width, height), true);
            } catch (Exception e) {
                logger.warning("Invalid fake image properties: " + String.join(";", row));
            }
        }
    }

    /**
     * Save configuration to disk
     */
    private void saveConfig() {
        if (!hasConfigChanged.get()) return;

        // Get all fake images
        Set<FakeImage> fakeImages = new HashSet<>();
        for (WorldArea worldArea : worldAreas.values()) {
            fakeImages.addAll(worldArea.getImages());
        }

        // Placed here so, if another change comes while saving, we don't lose those changes (will be saved later)
        hasConfigChanged.set(false);

        // Export to configuration properties
        CsvConfiguration config = new CsvConfiguration();
        for (FakeImage fakeImage : fakeImages) {
            Location location = fakeImage.getLocation();
            String[] row = new String[]{
                fakeImage.getFile().getName(),
                location.getChunk().getWorld().getName(),
                location.getBlockX() + "",
                location.getBlockY() + "",
                location.getBlockZ() + "",
                fakeImage.getBlockFace().name(),
                fakeImage.getRotation().name(),
                fakeImage.getWidth() + "",
                fakeImage.getHeight() + ""
            };
            config.addRow(row);
        }

        // Write to disk
        try {
            config.save(configPath);
            logger.info("Saved placed fake images to disk");
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to save placed fake images to disk", e);
        }
    }

    /**
     * Add image to renderer
     * @param fakeImage Fake image instance
     * @param isInit    TRUE if called during renderer startup, FALSE otherwise
     */
    public void addImage(FakeImage fakeImage, boolean isInit) {
        for (String worldAreaId : fakeImage.getWorldAreaIds()) {
            WorldArea worldArea = worldAreas.computeIfAbsent(worldAreaId, __ -> new WorldArea());
            worldArea.addImage(fakeImage);
        }
        if (!isInit) {
            hasConfigChanged.set(true);
        }
    }

    /**
     * Add image to renderer
     * @param fakeImage Fake image instance
     */
    public void addImage(FakeImage fakeImage) {
        addImage(fakeImage, false);
    }

    /**
     * Get image from location
     * @param  location Fake image location
     * @param  face     Fake image block face
     * @return          Fake image instance or NULL if not found
     */
    public FakeImage getImage(Location location, BlockFace face) {
        String worldAreaId = WorldArea.getId(location);
        WorldArea worldArea = worldAreas.get(worldAreaId);
        if (worldArea == null) {
            return null;
        }

        // Find first fake image containing the given location
        for (FakeImage image : worldArea.getImages()) {
            // TODO: is "location" inside "image"?
        }

        // No match found
        return null;
    }

    /**
     * Remove image from renderer
     * @param image Fake image instance
     */
    public void removeImage(FakeImage image) {
        for (String worldAreaId : image.getWorldAreaIds()) {
            WorldArea worldArea = worldAreas.get(worldAreaId);
            if (worldArea != null) {
                worldArea.removeImage(image);
                // TODO: remove world area is it has no more fake images
            }
        }
        hasConfigChanged.set(true);
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
