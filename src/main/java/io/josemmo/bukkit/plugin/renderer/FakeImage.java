package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.DirectionUtils;
import org.bukkit.Location;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

public class FakeImage extends FakeEntity {
    public static final int MAX_DIMENSION = 10;
    private final ImageFile image; // TODO: delete this instance when file gets deleted
    private final Location location;
    private final BlockFace face;
    private final Rotation rotation;
    private final int width;
    private final int height;
    private final BiFunction<Integer, Integer, Vector> getLocationVector;
    private FakeItemFrame[][] frames;

    /**
     * Get image rotation from player eyesight
     * @param  face     Image block face
     * @param  location Player eye location
     * @return          Image rotation
     */
    public static Rotation getRotationFromPlayerEyesight(BlockFace face, Location location) {
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
     * @param image    Image instance
     * @param location Top-left corner where image will be placed
     * @param face     Block face
     * @param rotation Image rotation
     * @param width    Width in blocks
     * @param height   Height in blocks
     */
    public FakeImage(ImageFile image, Location location, BlockFace face, Rotation rotation, int width, int height) {
        this.image = image;
        this.location = location;
        this.face = face;
        this.rotation = rotation;
        this.width = width;
        this.height = height;

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

        logger.info("Created FakeImage#(" + location + "," + face + ")"); // TODO: remove
    }

    /**
     * Get image file instance
     * @return Image file instance
     */
    public ImageFile getFile() {
        return image;
    }

    /**
     * Get location instance
     * @return Location instance
     */
    public Location getLocation() {
        return location;
    }

    /**
     * Get image block face
     * @return Image block face
     */
    public BlockFace getBlockFace() {
        return face;
    }

    /**
     * Get image rotation
     * @return Image rotation
     */
    public Rotation getRotation() {
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
     * Get world area IDs where this image is located
     * @return Array of world area IDs
     */
    public String[] getWorldAreaIds() {
        // TODO: just a prototype, not fully implemented
        return new String[]{
            WorldArea.getId(this.location)
        };
    }

    /**
     * Load item frames and maps
     */
    public void load() {
        FakeMap[][] maps = image.getMaps(width, height);

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
    public void spawn(Player player) {
        if (frames == null) load();
        for (FakeItemFrame[] col : frames) {
            for (FakeItemFrame frame : col) {
                frame.spawn(player);
            }
        }
    }

    /**
     * Destroy image for a player
     * @param player Player instance
     */
    public void destroy(Player player) {
        if (frames == null) return;

        // Get frame IDs
        List<Integer> frameIds = new ArrayList<>(width*height);
        for (FakeItemFrame[] col : frames) {
            for (FakeItemFrame frame : col) {
                frameIds.add(frame.getId());
            }
        }

        // Send destroy packet
        PacketContainer destroyPacket = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        destroyPacket.getIntegerArrays().write(0, frameIds.stream().mapToInt(i->i).toArray());
        tryToSendPacket(player, destroyPacket);
    }

    /**
     * Invalidate cache
     * <p>
     * Removes all item frames associated with this image.
     */
    public void invalidate() {
        frames = null;
    }
}
