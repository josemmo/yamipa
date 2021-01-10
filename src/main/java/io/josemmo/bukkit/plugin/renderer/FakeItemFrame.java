package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import de.tr7zw.nbtapi.NBTItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeItemFrame extends FakeEntity {
    public static final int MIN_FRAME_ID = Integer.MAX_VALUE / 4;
    public static final int MAX_FRAME_ID = Integer.MAX_VALUE;

    private static final AtomicInteger lastFrameId = new AtomicInteger(MAX_FRAME_ID);
    private final int id;
    private final Location location;
    private final FakeMap map;

    /**
     * Get next unused item frame ID
     * @return Next unused item frame ID
     */
    private static int getNextId() {
        return lastFrameId.updateAndGet(lastId -> {
            if (lastId >= MAX_FRAME_ID) {
                return MIN_FRAME_ID;
            }
            return lastId + 1;
        });
    }

    /**
     * Class constructor
     * @param location Frame location
     * @param map      Fake map to render
     */
    public FakeItemFrame(Location location, FakeMap map) {
        this.id = getNextId();
        this.location = location;
        this.map = map;
        // TODO
        logger.info("Created FakeItemFrame#" + this.id + " using FakeMap#" + this.map.getId()); // TODO: change log level
    }

    /**
     * Spawn item frame in player's client
     * @param player Player instance
     */
    public void spawn(Player player) {
        // Create item frame entity
        PacketContainer framePacket = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        framePacket.getEntityTypeModifier()
            .write(0, EntityType.ITEM_FRAME);
        framePacket.getIntegers()
            .write(0, id)
            .write(1, 0)
            .write(2, 0)
            .write(3, 0)
            .write(4, 0) // Pitch angle // TODO: change value
            .write(5, 0) // Yaw angle // TODO: change value
            .write(6, 0);
        framePacket.getUUIDs()
            .write(0, UUID.randomUUID());
        framePacket.getDoubles()
            .write(0, location.getX())
            .write(1, location.getY())
            .write(2, location.getZ());
        tryToSendPacket(player, framePacket);

        // Create and attach filled map
        NBTItem mapNbt = new NBTItem(new ItemStack(Material.FILLED_MAP));
        mapNbt.setInteger("map", map.getId());
        ItemStack mapItemStack = mapNbt.getItem();

        WrappedDataWatcher dataWatcher = new WrappedDataWatcher();
        WrappedDataWatcher.Serializer byteSerializer = WrappedDataWatcher.Registry.get(Byte.class);
        WrappedDataWatcher.Serializer itemStackSerializer = WrappedDataWatcher.Registry.getItemStackSerializer(false);
        dataWatcher.setObject(
            new WrappedDataWatcher.WrappedDataWatcherObject(0, byteSerializer),
            (byte) 0x20 // Invisible
        );
        dataWatcher.setObject(
            new WrappedDataWatcher.WrappedDataWatcherObject(7, itemStackSerializer),
            mapItemStack
        );

        PacketContainer mapPacket = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        mapPacket.getIntegers().write(0, id);
        mapPacket.getWatchableCollectionModifier().write(0, dataWatcher.getWatchableObjects());
        tryToSendPacket(player, mapPacket);

        // Send map pixels
        map.sendPixels(player);
    }

    /**
     * Destroy item frame from player's client
     * @param player Player instance
     */
    public void destroy(Player player) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        packet.getIntegerArrays().write(0, new int[]{id});
        tryToSendPacket(player, packet);
    }
}
