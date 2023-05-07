package io.josemmo.bukkit.plugin.renderer;

import io.josemmo.bukkit.plugin.packets.MapDataPacket;
import org.bukkit.entity.Player;
import org.bukkit.map.MapPalette;
import org.jetbrains.annotations.NotNull;
import java.awt.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeMap extends FakeEntity {
    public static final int DIMENSION = 128;
    public static final int MIN_MAP_ID = Integer.MAX_VALUE / 4;
    public static final int MAX_MAP_ID = Integer.MAX_VALUE;
    public static final int RESEND_THRESHOLD = 60*5; // Seconds after sending pixels when resending should be avoided
    private static final AtomicInteger lastMapId = new AtomicInteger(-1);
    private static FakeMap errorInstance;
    private final int id;
    private final byte[] pixels;
    private final ConcurrentMap<UUID, Long> lastPlayerSendTime = new ConcurrentHashMap<>();

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
    @SuppressWarnings("deprecation")
    public static byte pixelToIndex(int pixel) {
        return MapPalette.matchColor(new Color(pixel, true));
    }

    /**
     * Get map instance to show in case of error
     * @return Error instance
     */
    private static @NotNull FakeMap getErrorInstance() {
        if (errorInstance == null) {
            byte[] pixels = new byte[DIMENSION * DIMENSION];
            Arrays.fill(pixels, pixelToIndex(Color.RED.getRGB()));
            errorInstance = new FakeMap(pixels);
        }
        return errorInstance;
    }

    /**
     * Get matrix of error maps
     * @param  width  Width in blocks
     * @param  height Height in blocks
     * @return        Fake maps container
     */
    public static @NotNull FakeMapsContainer getErrorMatrix(int width, int height) {
        FakeMap[] errorMaps = new FakeMap[] {getErrorInstance()};
        FakeMap[][][] matrix = new FakeMap[width][height][1];
        for (FakeMap[][] column : matrix) {
            Arrays.fill(column, errorMaps);
        }
        return new FakeMapsContainer(matrix, 0);
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
     * Class constructor
     * @param pixels Array of Minecraft color indexes
     */
    public FakeMap(byte[] pixels) {
        this.id = getNextId();
        this.pixels = pixels;
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
     * Request re-send of map pixels
     * @param  player Player who is expected to receive pixels
     * @return        Whether re-send authorization was granted or not
     */
    public boolean requestResend(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        long now = Instant.now().getEpochSecond();

        // Has enough time passed since last re-send?
        long last = lastPlayerSendTime.getOrDefault(uuid, 0L);
        if ((now-last) <= RESEND_THRESHOLD && (player.getLastPlayed()/1000) < last) {
            return false;
        }

        // Authorize re-send and update latest timestamp
        lastPlayerSendTime.put(uuid, now);
        plugin.fine("Granted sending pixels for FakeMap#" + id + " to Player#" + player.getName());
        return true;
    }

    /**
     * Get map pixels packet
     * @return Map pixels packet
     */
    public @NotNull MapDataPacket getPixelsPacket() {
        MapDataPacket mapDataPacket = new MapDataPacket();
        mapDataPacket.setId(id)
            .setScale(0) // Fully zoomed-in
            .setLocked(true)
            .setArea(DIMENSION, DIMENSION, 0, 0)
            .setPixels(pixels);
        return mapDataPacket;
    }
}
