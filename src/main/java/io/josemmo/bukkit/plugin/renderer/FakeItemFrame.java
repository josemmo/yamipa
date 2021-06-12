package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.events.PacketContainer;
import de.tr7zw.changeme.nbtapi.NBTItem;
import io.josemmo.bukkit.plugin.packets.DestroyEntityPacket;
import io.josemmo.bukkit.plugin.packets.EntityMetadataPacket;
import io.josemmo.bukkit.plugin.packets.SpawnEntityPacket;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeItemFrame extends FakeEntity {
    public static final int MIN_FRAME_ID = Integer.MAX_VALUE / 4;
    public static final int MAX_FRAME_ID = Integer.MAX_VALUE;
    private static final AtomicInteger lastFrameId = new AtomicInteger(MAX_FRAME_ID);
    private final int id;
    private final Location location;
    private final BlockFace face;
    private final Rotation rotation;
    private final FakeMap map;
    private PacketContainer[] spawnPackets;

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
     * @param face     Block face
     * @param rotation Frame rotation
     * @param map      Fake map to render
     */
    public FakeItemFrame(@NotNull Location location, @NotNull BlockFace face, @NotNull Rotation rotation, @NotNull FakeMap map) {
        this.id = getNextId();
        this.location = location;
        this.face = face;
        this.rotation = rotation;
        this.map = map;
        plugin.fine("Created FakeItemFrame#" + this.id + " using FakeMap#" + this.map.getId());
    }

    /**
     * Generate spawn packets
     */
    private void generateSpawnPackets() {
        spawnPackets = new PacketContainer[2];

        // Calculate frame position in relation to target block
        double x = location.getBlockX();
        double y = location.getBlockY();
        double z = location.getBlockZ();
        int pitch = 0;
        int yaw = 0;
        int orientation = 3;
        switch (face) {
            case UP:
                ++y;
                pitch = -64;
                orientation = 1;
                break;
            case DOWN:
                --y;
                pitch = 64;
                orientation = 0;
                break;
            case EAST:
                ++x;
                yaw = -64;
                orientation = 5;
                break;
            case WEST:
                --x;
                yaw = 64;
                orientation = 4;
                break;
            case NORTH:
                --z;
                yaw = 128;
                orientation = 2;
                break;
            case SOUTH:
                ++z;
        }

        // Create item frame entity
        SpawnEntityPacket framePacket = new SpawnEntityPacket();
        spawnPackets[0] = framePacket.setId(id)
            .setEntityType(EntityType.ITEM_FRAME)
            .setPosition(x, y, z)
            .setRotation(pitch, yaw)
            .setData(orientation);

        // Create and attach filled map
        NBTItem mapNbt = new NBTItem(new ItemStack(Material.FILLED_MAP));
        mapNbt.setInteger("map", map.getId());
        ItemStack mapItemStack = mapNbt.getItem();

        EntityMetadataPacket mapPacket = new EntityMetadataPacket();
        spawnPackets[1] = mapPacket.setId(id)
            .setInvisible(true)
            .setItem(mapItemStack)
            .setRotation(rotation)
            .build();
    }

    /**
     * Spawn item frame in player's client
     * @param player Player instance
     */
    public void spawn(@NotNull Player player) {
        if (spawnPackets == null) {
            generateSpawnPackets();
        }
        for (PacketContainer packet : spawnPackets) {
            tryToSendPacket(player, packet);
        }
        map.sendPixels(player);
    }

    /**
     * Destroy item frame from player's client
     * @param player Player instance
     */
    public void destroy(@NotNull Player player) {
        DestroyEntityPacket destroyPacket = new DestroyEntityPacket();
        destroyPacket.setId(id);
        tryToSendPacket(player, destroyPacket);
    }
}
