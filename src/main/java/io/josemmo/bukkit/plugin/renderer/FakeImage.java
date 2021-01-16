package io.josemmo.bukkit.plugin.renderer;

import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.DirectionUtils;
import org.bukkit.Location;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

public class FakeImage extends FakeEntity {
    public static final int MAX_DIMENSION = 10;
    private final ImageFile image; // TODO: delete this instance when file gets deleted
    private final Location location;
    private final BlockFace face;
    private final Rotation rotation;
    private final int width;
    private final int height;

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
                return Rotation.CLOCKWISE_45;
            case SOUTH:
                return Rotation.CLOCKWISE;
            case WEST:
                return Rotation.CLOCKWISE_135;
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
     * Spawn image for a player
     * @param player Player instance
     */
    public void spawn(Player player) {
        // TODO: not implemented
    }

    /**
     * Destroy image for a player
     * @param player Player instance
     */
    public void destroy(Player player) {
        // TODO: not implemented
    }
}
