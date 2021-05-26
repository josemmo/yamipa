package io.josemmo.bukkit.plugin.renderer;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.utils.CsvConfiguration;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ImageRenderer implements Listener {
    public static final long SAVE_INTERVAL = 20L * 90; // In server ticks
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private final String configPath;
    private BukkitTask saveTask;
    private final AtomicBoolean hasConfigChanged = new AtomicBoolean(false);
    private final ConcurrentMap<WorldAreaId, WorldArea> worldAreas = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> imagesCountByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, WorldAreaId> playersLocation = new HashMap<>();

    /**
     * Class constructor
     * @param configPath Path to configuration file
     */
    public ImageRenderer(@NotNull String configPath) {
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
        if (!Files.isRegularFile(Paths.get(configPath))) {
            plugin.info("No placed fake images configuration file found");
            return;
        }

        // Try to load configuration
        CsvConfiguration config = new CsvConfiguration();
        try {
            config.load(configPath);
        } catch (IOException e) {
            plugin.log(Level.SEVERE, "Failed to load placed fake images from disk", e);
            return;
        }

        // Parse each row
        for (String[] row : config.getRows()) {
            try {
                String filename = row[0];
                World world = Objects.requireNonNull(plugin.getServer().getWorld(row[1]));
                double x = Integer.parseInt(row[2]);
                double y = Integer.parseInt(row[3]);
                double z = Integer.parseInt(row[4]);
                Location location = new Location(world, x, y, z);
                BlockFace face = BlockFace.valueOf(row[5]);
                Rotation rotation = Rotation.valueOf(row[6]);
                int width = Math.min(FakeImage.MAX_DIMENSION, Math.abs(Integer.parseInt(row[7])));
                int height = Math.min(FakeImage.MAX_DIMENSION, Math.abs(Integer.parseInt(row[8])));
                Date placedAt = (row.length > 9 && !row[9].equals("")) ?
                    new Date(Long.parseLong(row[9])*1000L) :
                    null;
                UUID placedById = (row.length > 10 && !row[10].equals("")) ?
                    UUID.fromString(row[10]) :
                    FakeImage.UNKNOWN_PLAYER_ID;
                OfflinePlayer placedBy = Bukkit.getOfflinePlayer(placedById);
                addImage(new FakeImage(filename, location, face, rotation, width, height, placedAt, placedBy), true);
            } catch (Exception e) {
                plugin.log(Level.SEVERE, "Invalid fake image properties: " + String.join(";", row), e);
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
            UUID placedById = fakeImage.getPlacedBy().getUniqueId();
            String[] row = new String[]{
                fakeImage.getFilename(),
                location.getChunk().getWorld().getName(),
                location.getBlockX() + "",
                location.getBlockY() + "",
                location.getBlockZ() + "",
                fakeImage.getBlockFace().name(),
                fakeImage.getRotation().name(),
                fakeImage.getWidth() + "",
                fakeImage.getHeight() + "",
                (fakeImage.getPlacedAt() == null) ? "" : (fakeImage.getPlacedAt().getTime() / 1000) + "",
                placedById.equals(FakeImage.UNKNOWN_PLAYER_ID) ? "" : placedById.toString()
            };
            config.addRow(row);
        }

        // Write to disk
        try {
            config.save(configPath);
            plugin.info("Saved placed fake images to disk");
        } catch (IOException e) {
            plugin.log(Level.SEVERE, "Failed to save placed fake images to disk", e);
        }
    }

    /**
     * Add image to renderer
     * @param image  Fake image instance
     * @param isInit TRUE if called during renderer startup, FALSE otherwise
     */
    public void addImage(@NotNull FakeImage image, boolean isInit) {
        WorldAreaId worldAreaId = image.getWorldAreaId();
        WorldArea worldArea = worldAreas.computeIfAbsent(worldAreaId, __ -> {
            plugin.fine("Created WorldArea#(" + worldAreaId + ")");
            return new WorldArea(worldAreaId);
        });
        worldArea.addImage(image);
        if (!isInit) {
            hasConfigChanged.set(true);
        }

        // Increment count of placed images by player
        UUID placedById = image.getPlacedBy().getUniqueId();
        imagesCountByPlayer.compute(placedById, (__, prev) -> (prev == null) ? 1 : prev+1);
    }

    /**
     * Add image to renderer
     * @param image Fake image instance
     */
    public void addImage(@NotNull FakeImage image) {
        addImage(image, false);
    }

    /**
     * Get image from location
     * @param  location Fake image location
     * @param  face     Fake image block face
     * @return          Fake image instance or NULL if not found
     */
    public @Nullable FakeImage getImage(@NotNull Location location, @NotNull BlockFace face) {
        WorldAreaId worldAreaId = WorldAreaId.fromLocation(location);
        WorldArea worldArea = worldAreas.get(worldAreaId);
        if (worldArea == null) {
            return null;
        }

        // Find first fake image containing the given location
        for (FakeImage image : worldArea.getImages()) {
            if (image.contains(location, face)) {
                return image;
            }
        }

        // No match found
        return null;
    }

    /**
     * Get images from area
     * @param  world World instance
     * @param  minX  Minimum X coordinate
     * @param  maxX  Maximum X coordinate
     * @param  minZ  Minimum Z coordinate
     * @param  maxZ  Maximum Z coordinate
     * @return       List of found images
     */
    public @NotNull Set<FakeImage> getImages(@NotNull World world, int minX, int maxX, int minZ, int maxZ) {
        Set<FakeImage> response = new HashSet<>();
        for (WorldArea worldArea : worldAreas.values()) {
            if (!worldArea.getId().getWorld().getName().equals(world.getName())) continue;
            for (FakeImage image : worldArea.getImages()) {
                Location loc = image.getLocation();
                if (loc.getBlockX() < minX || loc.getBlockX() > maxX) continue;
                if (loc.getBlockZ() < minZ || loc.getBlockZ() > maxZ) continue;
                response.add(image);
            }
        }
        return response;
    }

    /**
     * Remove image from renderer
     * @param image Fake image instance
     */
    public void removeImage(@NotNull FakeImage image) {
        WorldAreaId worldAreaId = image.getWorldAreaId();
        WorldArea worldArea = worldAreas.get(worldAreaId);
        if (worldArea != null) {
            worldArea.removeImage(image);
            if (!worldArea.hasImages()) {
                plugin.fine("Destroyed WorldArea#(" + worldAreaId + ")");
                worldAreas.remove(worldAreaId);
            }
        }
        hasConfigChanged.set(true);

        // Decrement count of placed images by player
        UUID placedById = image.getPlacedBy().getUniqueId();
        imagesCountByPlayer.compute(placedById, (__, prev) -> (prev != null && prev > 1) ? prev-1 : null);
    }

    /**
     * Get set of players who have placed images
     * @return Offline players
     */
    public @NotNull Set<OfflinePlayer> getPlayersWithPlacedImages() {
        return imagesCountByPlayer.keySet().stream().map(Bukkit::getOfflinePlayer).collect(Collectors.toSet());
    }

    /**
     * Get number of placed images
     * @return Number of placed images
     */
    public int size() {
        return imagesCountByPlayer.values().stream().reduce(0, Integer::sum);
    }

//    /**
//     * Get number of placed images grouped by player
//     * <p>
//     * NOTE: Response is sorted by image count (descending)
//     * @return Images count by player
//     */
//    public @NotNull Map<OfflinePlayer, Integer> getImagesCountByPlayer() {
//        List<Map.Entry<UUID, Integer>> sortedEntries = new ArrayList<>(imagesCountByPlayer.entrySet());
//        sortedEntries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
//
//        Map<OfflinePlayer, Integer> sortedMap = new LinkedHashMap<>();
//        for (Map.Entry<UUID, Integer> entry : sortedEntries) {
//            UUID playerId = entry.getKey();
//            sortedMap.put((playerId == null) ? null : Bukkit.getOfflinePlayer(playerId), entry.getValue());
//        }
//
//        return sortedMap;
//    }

    /**
     * Get available world areas from IDs
     * @param  ids World area IDs
     * @return     World area instances
     */
    private @NotNull Set<WorldArea> getWorldAreas(@NotNull WorldAreaId[] ids) {
        Set<WorldArea> instances = new HashSet<>();
        for (WorldAreaId id : ids) {
            if (!worldAreas.containsKey(id)) continue;
            instances.add(worldAreas.get(id));
        }
        return instances;
    }

    /**
     * On player location change
     * @param player   Player instance
     * @param location New player location
     */
    private void onPlayerLocationChange(@NotNull Player player, @NotNull Location location) {
        UUID uuid = player.getUniqueId();

        // Has player moved to another world area?
        WorldAreaId worldAreaId = WorldAreaId.fromLocation(location);
        WorldAreaId prevWorldAreaId = playersLocation.get(uuid);
        if (worldAreaId.equals(prevWorldAreaId)) {
            return;
        }
        playersLocation.put(uuid, worldAreaId);
        plugin.fine("Player#" + player.getName() + " moved to WorldArea#(" + worldAreaId + ")");

        // Get world areas that should be loaded/unloaded
        Set<WorldArea> desiredState = getWorldAreas(worldAreaId.getNeighborhood());
        Set<WorldArea> currentState;
        if (prevWorldAreaId == null) {
            currentState = new HashSet<>();
        } else {
            currentState = getWorldAreas(prevWorldAreaId.getNeighborhood());
        }
        Set<WorldArea> areasToLoad = new HashSet<>(desiredState);
        areasToLoad.removeAll(currentState);
        Set<WorldArea> areasToUnload = new HashSet<>(currentState);
        areasToUnload.removeAll(desiredState);

        // Load/unload world areas
        for (WorldArea area : areasToUnload) {
            area.unload(player);
            plugin.fine("Unloaded WorldArea#(" + area.getId() + ") for Player#" + player.getName());
        }
        for (WorldArea area : areasToLoad) {
            area.load(player);
            plugin.fine("Loaded WorldArea#(" + area.getId() + ") for Player#" + player.getName());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        onPlayerLocationChange(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Get player's current world area ID
        WorldAreaId worldAreaId = playersLocation.get(uuid);
        if (worldAreaId == null) return;
        playersLocation.remove(uuid);

        // Notify world areas that player quit
        Set<WorldArea> loadedWorldAreas = getWorldAreas(worldAreaId.getNeighborhood());
        for (WorldArea worldArea : loadedWorldAreas) {
            worldArea.removePlayer(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        onPlayerLocationChange(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        onPlayerLocationChange(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;
        onPlayerLocationChange(event.getPlayer(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;
        onPlayerLocationChange(event.getPlayer(), event.getTo());
    }
}
