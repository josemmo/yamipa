package io.josemmo.bukkit.plugin.renderer;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.utils.CsvConfiguration;
import io.josemmo.bukkit.plugin.utils.Logger;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleMoveEvent;
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
import java.util.stream.Collectors;

public class ImageRenderer implements Listener {
    public static final long SAVE_INTERVAL = 20L * 90; // In server ticks
    private static final Logger LOGGER = Logger.getLogger("ImageRenderer");
    private final String configPath;
    private BukkitTask saveTask;
    private final AtomicBoolean hasConfigChanged = new AtomicBoolean(false);
    private final ConcurrentMap<WorldAreaId, Set<FakeImage>> images = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Integer> imagesCountByPlayer = new ConcurrentHashMap<>();
    private final Map<Player, WorldAreaId> playersLocation = new HashMap<>();

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
        YamipaPlugin plugin = YamipaPlugin.getInstance();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        saveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveConfig, SAVE_INTERVAL, SAVE_INTERVAL);
    }

    /**
     * Stop instance
     */
    public void stop() {
        HandlerList.unregisterAll(this);

        // Destroy images from remote clients
        for (Set<FakeImage> fakeImagesPart : images.values()) {
            for (FakeImage fakeImage : fakeImagesPart) {
                fakeImage.destroy();
            }
        }

        // Persist configuration
        if (saveTask != null) {
            saveTask.cancel();
        }
        saveConfig();

        // Clear dangling references
        images.clear();
        imagesCountByPlayer.clear();
        playersLocation.clear();
    }

    /**
     * Load configuration from disk
     */
    private void loadConfig() {
        if (!Files.isRegularFile(Paths.get(configPath))) {
            LOGGER.info("No placed fake images configuration file found");
            return;
        }

        // Try to load configuration
        CsvConfiguration config = new CsvConfiguration();
        try {
            config.load(configPath);
        } catch (IOException e) {
            LOGGER.severe("Failed to load placed fake images from disk", e);
            return;
        }

        // Parse each row
        for (String[] row : config.getRows()) {
            try {
                String filename = row[0];
                World world = Objects.requireNonNull(YamipaPlugin.getInstance().getServer().getWorld(row[1]));
                double x = Integer.parseInt(row[2]);
                double y = Integer.parseInt(row[3]);
                double z = Integer.parseInt(row[4]);
                Location location = new Location(world, x, y, z);
                BlockFace face = BlockFace.valueOf(row[5]);
                Rotation rotation = Rotation.valueOf(row[6]);
                int width = Math.min(FakeImage.MAX_DIMENSION, Math.abs(Integer.parseInt(row[7])));
                int height = Math.min(FakeImage.MAX_DIMENSION, Math.abs(Integer.parseInt(row[8])));
                Date placedAt = (row.length > 9 && !row[9].isEmpty()) ?
                    new Date(Long.parseLong(row[9])*1000L) :
                    null;
                UUID placedById = (row.length > 10 && !row[10].isEmpty()) ?
                    UUID.fromString(row[10]) :
                    FakeImage.UNKNOWN_PLAYER_ID;
                OfflinePlayer placedBy = Bukkit.getOfflinePlayer(placedById);
                int flags = (row.length > 11) ?
                    Math.max(Integer.parseInt(row[11]), 0) :
                    FakeImage.DEFAULT_PLACE_FLAGS;
                FakeImage fakeImage = new FakeImage(filename, location, face, rotation, width, height,
                    placedAt, placedBy, flags);
                addImage(fakeImage, true);
            } catch (Exception e) {
                LOGGER.severe("Invalid fake image properties: " + String.join(";", row), e);
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
        for (Set<FakeImage> fakeImagesPart : images.values()) {
            fakeImages.addAll(fakeImagesPart);
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
                placedById.equals(FakeImage.UNKNOWN_PLAYER_ID) ? "" : placedById.toString(),
                fakeImage.getFlags() + ""
            };
            config.addRow(row);
        }

        // Write to disk
        try {
            config.save(configPath);
            LOGGER.info("Saved placed fake images to disk");
        } catch (IOException e) {
            LOGGER.severe("Failed to save placed fake images to disk", e);
        }
    }

    /**
     * Add image to renderer
     * @param image  Fake image instance
     * @param isInit TRUE if called during renderer startup, FALSE otherwise
     */
    public void addImage(@NotNull FakeImage image, boolean isInit) {
        WorldAreaId[] imageWorldAreaIds = image.getWorldAreaIds();

        // Add image to renderer
        for (WorldAreaId worldAreaId : imageWorldAreaIds) {
            images.computeIfAbsent(worldAreaId, __ -> {
                LOGGER.fine("Created WorldArea#(" + worldAreaId + ")");
                return ConcurrentHashMap.newKeySet();
            }).add(image);
        }

        // Set configuration changed flag
        if (!isInit) {
            hasConfigChanged.set(true);
        }

        // Increment count of placed images by player
        UUID placedById = image.getPlacedBy().getUniqueId();
        imagesCountByPlayer.compute(placedById, (__, prev) -> (prev == null) ? 1 : prev+1);

        // Spawn image in players nearby
        for (Player player : getPlayersInViewDistance(imageWorldAreaIds)) {
            image.spawn(player);
        }
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
        Set<FakeImage> candidateImages = images.get(worldAreaId);
        if (candidateImages == null) {
            return null;
        }

        // Find first fake image containing the given location
        for (FakeImage image : candidateImages) {
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
        for (Map.Entry<WorldAreaId, Set<FakeImage>> entry : images.entrySet()) {
            if (!entry.getKey().getWorld().getName().equals(world.getName())) continue;
            for (FakeImage image : entry.getValue()) {
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
        WorldAreaId[] imageWorldAreaIds = image.getWorldAreaIds();

        // Destroy image from all players nearby
        image.destroy();

        // Remove image from renderer
        for (WorldAreaId worldAreaId : imageWorldAreaIds) {
            Set<FakeImage> worldAreaImages = images.get(worldAreaId);
            worldAreaImages.remove(image);
            if (worldAreaImages.isEmpty()) {
                LOGGER.fine("Destroyed WorldArea#(" + worldAreaId + ")");
                images.remove(worldAreaId);
            }
        }

        // Set configuration changed flag
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

    /**
     * Get number of placed images grouped by player
     * <p>
     * NOTE: Response is sorted by image count (descending)
     * @return Images count by player
     */
    public @NotNull Map<OfflinePlayer, Integer> getImagesCountByPlayer() {
        List<Map.Entry<UUID, Integer>> sortedEntries = new ArrayList<>(imagesCountByPlayer.entrySet());
        sortedEntries.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));

        Map<OfflinePlayer, Integer> sortedMap = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> entry : sortedEntries) {
            sortedMap.put(Bukkit.getOfflinePlayer(entry.getKey()), entry.getValue());
        }

        return sortedMap;
    }

    /**
     * Get players in view distance of the provided world area IDs
     * @param  ids World area IDs
     * @return     Players inside those world areas
     */
    private @NotNull Set<Player> getPlayersInViewDistance(@NotNull WorldAreaId[] ids) {
        Set<WorldAreaId> neighborhood = new HashSet<>();
        for (WorldAreaId worldAreaId : ids) {
            Collections.addAll(neighborhood, worldAreaId.getNeighborhood());
        }

        Set<Player> players = new HashSet<>();
        for (Map.Entry<Player, WorldAreaId> entry : playersLocation.entrySet()) {
            if (neighborhood.contains(entry.getValue())) {
                players.add(entry.getKey());
            }
        }

        return players;
    }

    /**
     * Get images in view distance from world area ID
     * @param  worldAreaId World area ID
     * @return             Set of fake images
     */
    private @NotNull Set<FakeImage> getImagesInViewDistance(@NotNull WorldAreaId worldAreaId) {
        Set<FakeImage> response = new HashSet<>();
        for (WorldAreaId target : worldAreaId.getNeighborhood()) {
            Set<FakeImage> targetImages = images.get(target);
            if (targetImages != null) {
                response.addAll(targetImages);
            }
        }
        return response;
    }

    /**
     * On player location change
     * @param player   Player instance
     * @param location New player location
     */
    private void onPlayerLocationChange(@NotNull Player player, @NotNull Location location) {
        // Ignore NPC events from other plugins
        if (player.hasMetadata("NPC")) {
            LOGGER.fine("Ignored NPC event from Player#" + player.getName());
            return;
        }

        // Has player moved to another world area?
        WorldAreaId worldAreaId = WorldAreaId.fromLocation(location);
        WorldAreaId prevWorldAreaId = playersLocation.get(player);
        if (worldAreaId.equals(prevWorldAreaId)) {
            return;
        }
        playersLocation.put(player, worldAreaId);
        LOGGER.fine("Player#" + player.getName() + " moved to WorldArea#(" + worldAreaId + ")");

        // Get images that should be spawned/destroyed
        Set<FakeImage> desiredState = getImagesInViewDistance(worldAreaId);
        Set<FakeImage> currentState = (prevWorldAreaId == null) ? new HashSet<>() : getImagesInViewDistance(prevWorldAreaId);
        Set<FakeImage> imagesToLoad = new HashSet<>(desiredState);
        imagesToLoad.removeAll(currentState);
        Set<FakeImage> imagesToUnload = new HashSet<>(currentState);
        imagesToUnload.removeAll(desiredState);

        // Spawn/destroy images
        for (FakeImage image : imagesToUnload) {
            image.destroy(player);
        }
        for (FakeImage image : imagesToLoad) {
            image.spawn(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        onPlayerLocationChange(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // Get player's current world area ID
        WorldAreaId worldAreaId = playersLocation.get(player);
        if (worldAreaId == null) return;
        playersLocation.remove(player);

        // Notify world areas that player quit
        for (FakeImage image : getImagesInViewDistance(worldAreaId)) {
            image.notifyPlayerQuit(player);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        onPlayerLocationChange(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;

        // Wait until next server tick before handling location change
        // This is necessary as teleport events get fired *before* teleporting the player
        YamipaPlugin plugin = YamipaPlugin.getInstance();
        Bukkit.getScheduler().runTask(plugin, () -> onPlayerLocationChange(event.getPlayer(), event.getTo()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;
        onPlayerLocationChange(event.getPlayer(), event.getTo());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onVehicleMove(@NotNull VehicleMoveEvent event) {
        List<Entity> passengers = event.getVehicle().getPassengers();
        if (passengers.isEmpty()) return;
        if (event.getFrom().getChunk().equals(event.getTo().getChunk())) return;
        for (Entity passenger : passengers) {
            if (passenger instanceof Player) {
                onPlayerLocationChange((Player) passenger, event.getTo());
            }
        }
    }
}
