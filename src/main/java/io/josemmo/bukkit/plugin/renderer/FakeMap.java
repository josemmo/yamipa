package io.josemmo.bukkit.plugin.renderer;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import io.josemmo.bukkit.plugin.utils.Logger;
import org.bukkit.entity.Player;
import org.bukkit.map.MapPalette;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.awt.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeMap extends FakeEntity {
    public static final int DIMENSION = 128;
    private static final int MIN_MAP_ID = Integer.MAX_VALUE / 4;
    private static final int MAX_MAP_ID = Integer.MAX_VALUE;
    private static final int RESEND_THRESHOLD = 60*5; // Seconds after sending pixels when resending should be avoided
    private static final Logger LOGGER = Logger.getLogger("FakeMap");
    private static final AtomicInteger LAST_MAP_ID = new AtomicInteger(MIN_MAP_ID);
    private static @Nullable FakeMap ERROR_INSTANCE;
    private final int id;
    private final byte[] pixels;
    private final ConcurrentMap<UUID, Long> lastPlayerSendTime = new ConcurrentHashMap<>();

    /**
     * Get next unused map ID
     * @return Next unused map ID
     */
    private static int getNextId() {
        return LAST_MAP_ID.updateAndGet(lastId -> {
            if (lastId == MIN_MAP_ID) {
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
        if (ERROR_INSTANCE == null) {
            byte[] pixels = new byte[DIMENSION * DIMENSION];
            Arrays.fill(pixels, pixelToIndex(Color.RED.getRGB()));
            ERROR_INSTANCE = new FakeMap(pixels);
        }
        return ERROR_INSTANCE;
    }

    /**
     * Get matrix of error maps
     * @param  width  Width in blocks
     * @param  height Height in blocks
     * @return        Fake maps
     */
    public static @NotNull FakeMap[][][] getErrorMatrix(int width, int height) {
        FakeMap[] errorMaps = new FakeMap[] {getErrorInstance()};
        FakeMap[][][] matrix = new FakeMap[width][height][1];
        for (FakeMap[][] column : matrix) {
            Arrays.fill(column, errorMaps);
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

        LOGGER.fine("Created FakeMap#" + this.id);
    }

    /**
     * Class constructor
     * @param pixels Array of Minecraft color indexes
     */
    public FakeMap(byte[] pixels) {
        this.id = getNextId();
        this.pixels = pixels;
        LOGGER.fine("Created FakeMap#" + this.id);
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
        LOGGER.fine("Granted sending pixels for FakeMap#" + id + " to Player#" + player.getName());
        return true;
    }

    /**
     * Get map pixels packet
     * @return Map pixels packet
     */
    public @NotNull WrapperPlayServerMapData getPixelsPacket() {
        WrapperPlayServerMapData data = new WrapperPlayServerMapData(
            id, (byte) 0, false, false, new ArrayList<>(), DIMENSION, DIMENSION, 0, 0, pixels
        );
        return data;
    }
}
