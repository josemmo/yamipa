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
     * Get map instance to show in case of error
     * @return Error instance
     */
    public static FakeMap getErrorInstance() {
        if (errorInstance == null) {
            // TODO: use a resource file instead
            int[] pixels = new int[DIMENSION * DIMENSION];
            Arrays.fill(pixels, 0xffffffff);
            errorInstance = new FakeMap(pixels);
        }

        return errorInstance;
    }

    /**
     * Class constructor
     * @param pixels Array of RGBA pixels with DIMENSION×DIMENSION elements
     */
    public FakeMap(int[] pixels) {
        this.id = getNextId();

        // Convert RGB pixels to in-game indexes
        this.pixels = new byte[DIMENSION*DIMENSION];
        for (int x=0; x<DIMENSION; x++) {
            for (int y=0; y<DIMENSION; y++) {
                this.pixels[x + y*DIMENSION] = MapPalette.matchColor(new Color(pixels[x + y*DIMENSION], true));
            }
        }

        logger.info("Created FakeMap#" + this.id); // TODO: remove
    }

    /**
     * Get map ID
     * @return Map ID
     */
    public int getId() {
        return id;
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

        logger.info("Sent pixels for FakeMap#" + id); // TODO: remove
    }
}
