package io.josemmo.bukkit.plugin.renderer;

import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.DirectionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import java.util.function.BiFunction;

public class FakeImage extends FakeEntity {
    public static final int MAX_DIMENSION = 30; // In blocks
    public static final UUID UNKNOWN_PLAYER_ID = new UUID(0, 0);
    private final String filename;
    private final Location location;
    private final BlockFace face;
    private final Rotation rotation;
    private final int width;
    private final int height;
    private final Date placedAt;
    private final OfflinePlayer placedBy;
    private final BiFunction<Integer, Integer, Vector> getLocationVector;
    private FakeItemFrame[][] frames;

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
     * Class constructor
     * @param filename Image filename
     * @param location Top-left corner where image will be placed
     * @param face     Block face
     * @param rotation Image rotation
     * @param width    Width in blocks
     * @param height   Height in blocks
     * @param placedAt Placed at
     * @param placedBy Placed by
     */
    public FakeImage(
        @NotNull String filename,
        @NotNull Location location,
        @NotNull BlockFace face,
        @NotNull Rotation rotation,
        int width,
        int height,
        @Nullable Date placedAt,
        @NotNull OfflinePlayer placedBy
    ) {
        this.filename = filename;
        this.location = location;
        this.face = face;
        this.rotation = rotation;
        this.width = width;
        this.height = height;
        this.placedAt = placedAt;
        this.placedBy = placedBy;

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

        plugin.fine("Created FakeImage#(" + location + "," + face + ") from ImageFile#(" + filename + ")");
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
        return plugin.getStorage().get(filename);
    }

    /**
     * Get location instance
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
     * Get the world area IDs where this image is located
     * @return Array of world area IDs
     */
    public @NotNull WorldAreaId[] getWorldAreaIds() {
        WorldAreaId topLeft = WorldAreaId.fromLocation(location);
        WorldAreaId bottomRight = WorldAreaId.fromLocation(
            location.clone().add(getLocationVector.apply(width-1, height-1))
        );
        if (topLeft.equals(bottomRight)) {
            return new WorldAreaId[]{topLeft};
        }
        return new WorldAreaId[]{topLeft, bottomRight};
    }

    /**
     * Verify whether a point is contained in the image plane
     * @param  location Location instance (only for coordinates)
     * @param  face     Block face
     * @return          TRUE for contained, FALSE otherwise
     */
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
     * Load item frames and maps
     */
    private void load() {
        ImageFile file = getFile();
        FakeMap[][] maps;
        if (file == null) {
            maps = FakeMap.getErrorMatrix(width, height);
            plugin.warning("File \"" + filename + "\" does not exist");
        } else {
            maps = file.getMapsAndSubscribe(this);
        }

        // Generate frames
        frames = new FakeItemFrame[width][height];
        for (int col=0; col<width; col++) {
            for (int row=0; row<height; row++) {
                Location frameLocation = location.clone().add(getLocationVector.apply(col, row));
                frames[col][row] = new FakeItemFrame(frameLocation, face, rotation, maps[col][row]);
            }
        }
    }

    /**
     * Spawn image for a player
     * @param player Player instance
     */
    public void spawn(@NotNull Player player) {
        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.runTaskAsynchronously(plugin, () -> {
            if (frames == null) load();
            scheduler.runTask(plugin, () -> {
                for (FakeItemFrame[] col : frames) {
                    for (FakeItemFrame frame : col) {
                        frame.spawn(player);
                    }
                }
            });
        });
    }

    /**
     * Destroy image for a player
     * @param player Player instance
     */
    public void destroy(@NotNull Player player) {
        if (frames == null) return;
        for (FakeItemFrame[] col : frames) {
            for (FakeItemFrame frame : col) {
                frame.destroy(player);
            }
        }
    }

    /**
     * Invalidate cache
     * <p>
     * Removes all item frames associated with this image.
     */
    public void invalidate() {
        frames = null;

        // Notify invalidation to source ImageFile
        ImageFile file = getFile();
        if (file != null) {
            file.unsubscribe(this);
        }

        plugin.fine("Invalidated FakeImage#(" + location + "," + face + ")");
    }
}
