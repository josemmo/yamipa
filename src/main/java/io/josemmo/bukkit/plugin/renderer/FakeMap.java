package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import org.bukkit.entity.Player;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeMap extends FakeEntity {
    public static final int DIMENSION = 128;
    public static final int MIN_MAP_ID = 10000;
    public static final int MAX_MAP_ID = 32767;

    private static final AtomicInteger lastMapId = new AtomicInteger(-1);
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
     * Class constructor
     * @param pixels Map pixels
     */
    public FakeMap(byte[] pixels) {
        this.id = getNextId();
        this.pixels = pixels;
        logger.info("Created FakeMap#" + this.id); // TODO: change log level
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
        logger.info("Sending pixels for FakeMap#" + id); // TODO: change log level
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
    }
}
