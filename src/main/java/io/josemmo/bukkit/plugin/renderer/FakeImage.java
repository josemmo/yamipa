package io.josemmo.bukkit.plugin.renderer;

import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.storage.CachedMapsFile;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.DirectionUtils;
import io.josemmo.bukkit.plugin.utils.Logger;
import io.josemmo.bukkit.plugin.utils.Permissions;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class FakeImage extends FakeEntity {
    private static final Logger LOGGER = Logger.getLogger("FakeImage");

    // Image constants
    public static final int MAX_STEPS = 500; // For animated images
    public static final int MIN_DELAY = 1; // Minimum step delay in 50ms intervals (50ms / 50ms)
    public static final int MAX_DELAY = 50; // Maximum step delay in 50ms intervals (5000ms / 50ms)
    public static final UUID UNKNOWN_PLAYER_ID = new UUID(0, 0);

    // Flags
    public static final int FLAG_ANIMATABLE = 1; // Whether image is allowed to animate multiple steps
    public static final int FLAG_REMOVABLE = 2; // Whether image can be removed by a player using the interact button
    public static final int FLAG_DROPPABLE = 4; // Whether image will drop an image item when removed by a player
    public static final int FLAG_GLOWING = 8; // Whether image glows in the dark
    public static final int DEFAULT_PLACE_FLAGS = FLAG_ANIMATABLE;
    public static final int DEFAULT_GIVE_FLAGS = FLAG_ANIMATABLE | FLAG_REMOVABLE | FLAG_DROPPABLE;

    // Instance properties
    private final String filename;
    private final Location location;
    private final BlockFace face;
    private final Rotation rotation;
    private final int width;
    private final int height;
    private final Date placedAt;
    private final OfflinePlayer placedBy;
    private final int flags;
    private final BiFunction<Integer, Integer, Vector> getLocationVector;
    private final Set<Player> observingPlayers = new HashSet<>();
    private @Nullable Runnable onLoadedListener = null;

    // Generated values
    private boolean loading = false;
    private FakeItemFrame[] frames = null;
    private int delay = 0; // Delay between steps in 50ms intervals, "0" for N/A
    private int numOfSteps = -1;  // Total number of animation steps

    // Animation task attributes
    private @Nullable ScheduledFuture<?> task;
    private int currentStep = -1; // Current animation step

    /**
     * Get image rotation from player eyesight
     * @param  face     Image block face
     * @param  location Player eye location
     * @return          Image rotation
     */
    public static @NotNull Rotation getRotationFromPlayerEyesight(@NotNull BlockFace face, @NotNull Location location) {
        // Images placed on N/S/E/W faces never have rotation
        if (face != BlockFace.UP && face != BlockFace.DOWN) {
            return Rotation.NONE;
        }

        // Top and down images depend on where player is looking
        BlockFace eyeDirection = DirectionUtils.getCardinalDirection(location.getYaw());
        switch (eyeDirection) {
            case EAST:
                return (face == BlockFace.DOWN) ? Rotation.CLOCKWISE_135 : Rotation.CLOCKWISE_45;
            case SOUTH:
                return Rotation.CLOCKWISE;
            case WEST:
                return (face == BlockFace.DOWN) ? Rotation.CLOCKWISE_45 : Rotation.CLOCKWISE_135;
            default:
                return Rotation.NONE;
        }
    }

    /**
     * Get maximum image dimension
     * @param  sender Sender instance
     * @return        Maximum image dimension in blocks
     */
    public static int getMaxImageDimension(@NotNull CommandSender sender) {
        if (sender instanceof Player) {
            String rawValue = Permissions.getVariable("yamipa-max-image-dimension", (Player) sender);
            if (rawValue != null) {
                try {
                    return Integer.parseInt(rawValue);
                } catch (NumberFormatException __) {
                    LOGGER.warning("Max. image dimension for " + sender + " is not a valid integer: \"" + rawValue + "\"");
                }
            }
        }
        return YamipaPlugin.getInstance().getRenderer().getMaxImageDimension();
    }

    /**
     * Get proportional height
     * @param  sizeInPixels Image file dimension in pixels
     * @param  sender       Sender instance
     * @param  width        Desired width in blocks
     * @return              Height in blocks (capped at maximum image dimension for sender)
     */
    public static int getProportionalHeight(@NotNull Dimension sizeInPixels, @NotNull CommandSender sender, int width) {
        float imageRatio = (float) sizeInPixels.height / sizeInPixels.width;
        int height = Math.round(width * imageRatio);
        height = Math.min(height, getMaxImageDimension(sender));
        return height;
    }

    /**
     * Class constructor
     * @param filename  Image filename
     * @param location  Top-left corner where image will be placed
     * @param face      Block face
     * @param rotation  Image rotation
     * @param width     Width in blocks
     * @param height    Height in blocks
     * @param placedAt  Placed at
     * @param placedBy  Placed by
     * @param flags     Flags
     */
    public FakeImage(
        @NotNull String filename,
        @NotNull Location location,
        @NotNull BlockFace face,
        @NotNull Rotation rotation,
        int width,
        int height,
        @Nullable Date placedAt,
        @NotNull OfflinePlayer placedBy,
        int flags
    ) {
        this.filename = filename;
        this.location = location;
        this.face = face;
        this.rotation = rotation;
        this.width = width;
        this.height = height;
        this.placedAt = placedAt;
        this.placedBy = placedBy;
        this.flags = flags;

        // Define function for retrieving item frame positional vector from <row,column> pair
        if (face == BlockFace.SOUTH) {
            getLocationVector = (col, row) -> new Vector(col, -row, 0);
        } else if (face == BlockFace.NORTH) {
            getLocationVector = (col, row) -> new Vector(-col, -row, 0);
        } else if (face == BlockFace.EAST) {
            getLocationVector = (col, row) -> new Vector(0, -row, -col);
        } else if (face == BlockFace.WEST) {
            getLocationVector = (col, row) -> new Vector(0, -row, col);
        } else if (face == BlockFace.UP) {
            if (rotation == Rotation.CLOCKWISE_45) {
                getLocationVector = (col, row) -> new Vector(-row, 0, col);
            } else if (rotation == Rotation.CLOCKWISE_135) {
                getLocationVector = (col, row) -> new Vector(row, 0, -col);
            } else if (rotation == Rotation.CLOCKWISE) {
                getLocationVector = (col, row) -> new Vector(-col, 0, -row);
            } else { // Rotation.NONE
                getLocationVector = (col, row) -> new Vector(col, 0, row);
            }
        } else { // BlockFace.DOWN
            if (rotation == Rotation.CLOCKWISE_45) {
                getLocationVector = (col, row) -> new Vector(-row, 0, -col);
            } else if (rotation == Rotation.CLOCKWISE_135) {
                getLocationVector = (col, row) -> new Vector(row, 0, col);
            } else if (rotation == Rotation.CLOCKWISE) {
                getLocationVector = (col, row) -> new Vector(-col, 0, row);
            } else { // Rotation.NONE
                getLocationVector = (col, row) -> new Vector(col, 0, -row);
            }
        }

        LOGGER.fine("Created FakeImage#(" + location + "," + face + ") from ImageFile#(" + filename + ")");
    }

    /**
     * Get image filename
     * @return Image filename
     */
    public @NotNull String getFilename() {
        return filename;
    }

    /**
     * Get image file instance
     * @return Image file instance or NULL if not found
     */
    public @Nullable ImageFile getFile() {
        return YamipaPlugin.getInstance().getStorage().get(filename);
    }

    /**
     * Get top-left corner of image
     * @return Location instance
     */
    public @NotNull Location getLocation() {
        return location;
    }

    /**
     * Get image block face
     * @return Image block face
     */
    public @NotNull BlockFace getBlockFace() {
        return face;
    }

    /**
     * Get image rotation
     * @return Image rotation
     */
    public @NotNull Rotation getRotation() {
        return rotation;
    }

    /**
     * Get image width in blocks
     * @return Image width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Get image height in blocks
     * @return Image height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Get placed at date
     * @return Placed at date
     */
    public @Nullable Date getPlacedAt() {
        return placedAt;
    }

    /**
     * Get placed by player
     * @return Placed by player instance
     */
    public @NotNull OfflinePlayer getPlacedBy() {
        return placedBy;
    }

    /**
     * Get flags
     * @return Flags
     */
    public int getFlags() {
        return flags;
    }

    /**
     * Has flag
     * @param  flag Flag to check
     * @return      Whether instance has given flag or not
     */
    public boolean hasFlag(int flag) {
        return ((flags & flag) == flag);
    }

    /**
     * Get image delay
     * @return Image delay in 50ms intervals
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Get all block locations covered by the image
     * @return Array of location instances
     */
    public @NotNull Location[] getAllLocations() {
        Location[] locations = new Location[width*height];
        for (int row=0; row<height; row++) {
            for (int col=0; col<width; col++) {
                locations[row*width + col] = location.clone().add(getLocationVector.apply(col, row));
            }
        }
        return locations;
    }

    /**
     * Get the world area IDs where this image is located
     * @return Array of world area IDs
     */
    public @NotNull WorldAreaId[] getWorldAreaIds() {
        Set<WorldAreaId> corners = new HashSet<>();
        corners.add(WorldAreaId.fromLocation(location));
        corners.add(WorldAreaId.fromLocation(location.clone().add(getLocationVector.apply(width-1, 0))));
        corners.add(WorldAreaId.fromLocation(location.clone().add(getLocationVector.apply(0, height-1))));
        corners.add(WorldAreaId.fromLocation(location.clone().add(getLocationVector.apply(width-1, height-1))));
        return corners.toArray(new WorldAreaId[0]);
    }

    /**
     * Verify whether a point is contained in the image plane
     * @param  location Location instance (only for coordinates)
     * @param  face     Block face
     * @return          TRUE for contained, FALSE otherwise
     */
    @SuppressWarnings("RedundantIfStatement")
    public boolean contains(@NotNull Location location, @NotNull BlockFace face) {
        // Is point facing the same plane as the image?
        if (face != this.face) {
            return false;
        }

        // Get sorted plane edges
        Location topLeft = this.location;
        Location bottomRight = this.location.clone().add(getLocationVector.apply(width-1, height-1));
        int[] x = new int[]{topLeft.getBlockX(), bottomRight.getBlockX()};
        int[] y = new int[]{topLeft.getBlockY(), bottomRight.getBlockY()};
        int[] z = new int[]{topLeft.getBlockZ(), bottomRight.getBlockZ()};
        Arrays.sort(x);
        Arrays.sort(y);
        Arrays.sort(z);

        // Is point located inside the plane limits?
        int[] point = new int[] {location.getBlockX(), location.getBlockY(), location.getBlockZ()};
        if (point[0] < x[0] || point[0] > x[1]) return false;
        if (point[1] < y[0] || point[1] > y[1]) return false;
        if (point[2] < z[0] || point[2] > z[1]) return false;
        return true;
    }

    /**
     * Set on loaded listener
     * <p>
     * Defines a single-use listener that gets called when the fake image finishes loading,
     * at which point the listener is automatically unregistered.
     * @param onLoadedListener Listener
     */
    public void setOnLoadedListener(@NotNull Runnable onLoadedListener) {
        this.onLoadedListener = onLoadedListener;
    }

    /**
     * Load generated instance attributes
     */
    private void load() {
        ImageFile file = getFile();

        // Get maps to use
        FakeMap[][][] maps;
        if (file == null) {
            maps = FakeMap.getErrorMatrix(width, height);
            LOGGER.warning("File \"" + filename + "\" does not exist");
        } else {
            CachedMapsFile cachedMapsFile = file.getMapsAndSubscribe(this);
            maps = cachedMapsFile.getMaps();
            delay = cachedMapsFile.getDelay();
        }
        numOfSteps = maps[0][0].length;

        // Generate frames
        FakeItemFrame[] newFrames = new FakeItemFrame[width*height];
        boolean glowing = hasFlag(FLAG_GLOWING);
        for (int col=0; col<width; col++) {
            for (int row=0; row<height; row++) {
                Location frameLocation = location.clone().add(getLocationVector.apply(col, row));
                newFrames[height*col+row] = new FakeItemFrame(frameLocation, face, rotation, glowing, maps[col][row]);
            }
        }
        frames = newFrames;

        // Start animation task (if needed)
        YamipaPlugin plugin = YamipaPlugin.getInstance();
        boolean isAnimationEnabled = plugin.getRenderer().isAnimationEnabled();
        if (isAnimationEnabled && task == null && hasFlag(FLAG_ANIMATABLE) && numOfSteps > 1) {
            task = plugin.getScheduler().scheduleAtFixedRate(
                this::nextStep,
                0,
                delay*50L,
                TimeUnit.MILLISECONDS
            );
            LOGGER.fine("Spawned animation task for FakeImage#(" + location + "," + face + ")");
        }

        // Notify listener
        if (onLoadedListener != null) {
            onLoadedListener.run();
            onLoadedListener = null;
        }
    }

    /**
     * Spawn image for a player
     * @param player Player instance
     */
    public void spawn(@NotNull Player player) {
        LOGGER.fine("Received request to spawn FakeImage#(" + location + "," + face + ") for Player#" + player.getName());

        // Send pixels if instance is already loaded
        if (frames != null) {
            spawnOnceLoaded(player);
            return;
        }

        // Wait for instance to load if loading
        if (loading) {
            tryToRunAsyncTask(() -> {
                while (loading) {
                    tryToSleep(5);
                }
                spawnOnceLoaded(player);
            });
            return;
        }

        // Instance needs to be loaded
        loading = true;
        tryToRunAsyncTask(() -> {
            load();
            loading = false;
            spawnOnceLoaded(player);
        });
    }

    /**
     * Spawn image for a player (once instance has been loaded)
     * @param player Player instance
     */
    private void spawnOnceLoaded(@NotNull Player player) {
        String playerName = player.getName();
        observingPlayers.add(player);

        // Prepare packets to send
        List<PacketWrapper<?>> packets = new ArrayList<>();
        for (FakeItemFrame frame : frames) {
            packets.add(frame.getSpawnPacket());
            packets.addAll(frame.getRenderPackets(player, 0));
            LOGGER.fine("Spawned FakeItemFrame#" + frame.getId() + " for Player#" + playerName);
        }

        // Send packets
        tryToSendPackets(player, packets);
    }

    /**
     * Destroy image for all players
     */
    public void destroy() {
        if (frames != null) {
            destroy(null);
        }
    }

    /**
     * Destroy image a player
     * @param player Player instance or NULL for all observing players
     */
    public void destroy(@Nullable Player player) {
        LOGGER.fine(
            "Received request to destroy FakeImage#(" + location + "," + face + ") for " +
            (player == null ? "all players" : "Player#" + player.getName())
        );

        // Send packets to destroy item frames
        if (frames != null) {
            Set<Player> targets = (player == null) ? observingPlayers : Collections.singleton(player);
            for (Player target : targets) {
                String targetName = target.getName();
                List<PacketWrapper<?>> packets = new ArrayList<>();
                for (FakeItemFrame frame : frames) {
                    packets.add(frame.getDestroyPacket());
                    LOGGER.fine("Destroyed FakeItemFrame#" + frame.getId() + " for Player#" + targetName);
                }
                tryToSendPackets(target, packets);
            }
        }

        // Remove player from observing players
        if (player == null) {
            observingPlayers.clear();
        } else {
            observingPlayers.remove(player);
        }

        // Invalidate instance (if no more players)
        if (observingPlayers.isEmpty()) {
            invalidate();
        }
    }

    /**
     * Notify player quit from server
     * @param player Player instance
     */
    public void notifyPlayerQuit(@NotNull Player player) {
        observingPlayers.remove(player);
        if (observingPlayers.isEmpty()) {
            invalidate();
        }
    }

    /**
     * Invalidate instance
     * <p>
     * Removes all item frames associated with this image, among other things.
     */
    private void invalidate() {
        // Destroy animation task
        if (task != null) {
            task.cancel(true);
            task = null;
            currentStep = -1;
            LOGGER.fine("Destroyed animation task for FakeImage#(" + location + "," + face + ")");
        }

        // Free array of fake item frames
        frames = null;
        loading = false;
        LOGGER.fine("Invalidated FakeImage#(" + location + "," + face + ")");

        // Notify invalidation to source ImageFile
        ImageFile file = getFile();
        if (file != null) {
            file.unsubscribe(this);
        }
    }

    /**
     * Send next animation step to all registered players
     */
    private void nextStep() {
        currentStep = (currentStep + 1) % numOfSteps;
        try {
            for (Player player : observingPlayers) {
                List<PacketWrapper<?>> packets = new ArrayList<>();
                for (FakeItemFrame frame : frames) {
                    packets.addAll(frame.getRenderPackets(player, currentStep));
                }
                tryToSendPackets(player, packets);
            }
        } catch (ConcurrentModificationException e) {
            // We can safely ignore this exception as it will just result
            // in a dropped step (all `observingPlayers` modifications are
            // called from the same thread).
        }
    }
}
