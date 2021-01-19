package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.entity.Player;
import org.bukkit.map.MapPalette;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeMap extends FakeEntity {
    public static final int DIMENSION = 128;
    public static final int MIN_MAP_ID = 10000;
    public static final int MAX_MAP_ID = 32767;
    private static final AtomicInteger lastMapId = new AtomicInteger(-1);
    private static FakeMap errorInstance;
    private final int id;
    private final byte[] pixels;

    /**
     * Get next unused map ID
     * @return Next unused map ID
     */
    private static int getNextId() {
        return lastMapId.updateAndGet(lastId -> {
            if (lastId <= MIN_MAP_ID) {
                return MAX_MAP_ID;
            }
            return lastId - 1;
        });
    }

    /**
     * Pixel to Minecraft color index
     * @param  pixel RGBA pixel value
     * @return       Closest Minecraft color index
     */
    public static byte pixelToIndex(int pixel) {
        return MapPalette.matchColor(new Color(pixel, true));
    }

    /**
     * Get map instance to show in case of error
     * @return Error instance
     */
    private static FakeMap getErrorInstance() {
        if (errorInstance == null) {
            byte[] pixels = new byte[DIMENSION * DIMENSION];
            Arrays.fill(pixels, pixelToIndex(Color.RED.getRGB()));
            errorInstance = new FakeMap(pixels, DIMENSION, 0, 0);
        }
        return errorInstance;
    }

    /**
     * Get matrix of error maps
     * @param  width  Width in blocks
     * @param  height Height in blocks
     * @return        Error matrix
     */
    public static FakeMap[][] getErrorMatrix(int width, int height) {
        FakeMap errorMap = getErrorInstance();
        FakeMap[][] matrix = new FakeMap[width][height];
        for (FakeMap[] column : matrix) {
            Arrays.fill(column, errorMap);
        }
        return matrix;
    }

    /**
     * Class constructor
     * @param pixels   Array of Minecraft color indexes
     * @param scanSize Original image width
     * @param startX   Initial X pixel coordinate
     * @param startY   Initial Y pixel coordinate
     */
    public FakeMap(byte[] pixels, int scanSize, int startX, int startY) {
        this.id = getNextId();

        // Copy square of pixels to this instance
        this.pixels = new byte[DIMENSION*DIMENSION];
        for (int y=0; y<DIMENSION; y++) {
            System.arraycopy(pixels, startX+(startY+y)*scanSize, this.pixels, y*DIMENSION, DIMENSION);
        }

        plugin.fine("Created FakeMap#" + this.id);
    }

    /**
     * Get map ID
     * @return Map ID
     */
    public int getId() {
        return id;
    }

    /**
     * Get raw pixels
     * @return Array of Minecraft color indexes
     */
    public byte[] getPixels() {
        return pixels;
    }

    /**
     * Send map pixels to player
     * @param player Player instance
     */
    public void sendPixels(Player player) {
        PacketContainer mapDataPacket = new PacketContainer(PacketType.Play.Server.MAP);
        mapDataPacket.getModifier().writeDefaults();
        mapDataPacket.getIntegers()
            .write(0, id)
            .write(1, 0) // X
            .write(2, 0) // Z
            .write(3, DIMENSION) // Columns
            .write (4, DIMENSION); // Rows
        mapDataPacket.getBytes()
            .write(0, (byte) 0); // Scale (fully zoomed-in map)
        mapDataPacket.getBooleans()
            .write(0, false) // Tracking Position
            .write(1, true); // Locked
        mapDataPacket.getByteArrays()
            .write(0, pixels);
        tryToSendPacket(player, mapDataPacket);

        plugin.fine("Sent pixels for FakeMap#" + id + " to Player#" + player.getName());
    }
}
