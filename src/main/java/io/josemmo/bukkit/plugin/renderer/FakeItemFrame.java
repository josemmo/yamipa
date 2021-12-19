package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.utility.MinecraftReflection;
import com.comphenix.protocol.wrappers.nbt.NbtCompound;
import com.comphenix.protocol.wrappers.nbt.NbtFactory;
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
    private final FakeMap[] maps;

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
     * @param maps     Fake maps to animate
     */
    public FakeItemFrame(@NotNull Location location, @NotNull BlockFace face, @NotNull Rotation rotation, @NotNull FakeMap[] maps) {
        this.id = getNextId();
        this.location = location;
        this.face = face;
        this.rotation = rotation;
        this.maps = maps;
        plugin.fine("Created FakeItemFrame#" + this.id + " using " + this.maps.length + " FakeMap(s)");
    }

    /**
     * Spawn empty item frame in player's client
     * @param player Player instance
     */
    public void spawn(@NotNull Player player) {
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
        framePacket.setId(id)
            .setEntityType(EntityType.ITEM_FRAME)
            .setPosition(x, y, z)
            .setRotation(pitch, yaw)
            .setData(orientation);
        tryToSendPacket(player, framePacket);
        plugin.fine("Spawned FakeItemFrame#" + this.id + " for Player#" + player.getName());

        // Send pixels for all linked maps
        for (FakeMap map : maps) {
            map.sendPixels(player);
        }
    }

    /**
     * Send frame of animation to player
     * @param player Player instance
     * @param step   Map step to send
     */
    public void render(@NotNull Player player, int step) {
        // Create and attach filled map
        ItemStack itemStack = MinecraftReflection.getBukkitItemStack(new ItemStack(Material.FILLED_MAP));
        NbtCompound itemStackNbt = NbtFactory.ofCompound("tag");
        itemStackNbt.put("map", maps[step].getId());
        NbtFactory.setItemTag(itemStack, itemStackNbt);

        // Build entity metadata packet
        EntityMetadataPacket mapPacket = new EntityMetadataPacket();
        mapPacket.setId(id)
            .setInvisible(true)
            .setItem(itemStack)
            .setRotation(rotation)
            .build();

        // Send animation status update
        tryToSendPacket(player, mapPacket);
    }

    /**
     * Destroy item frame from player's client
     * @param player Player instance
     */
    public void destroy(@NotNull Player player) {
        DestroyEntityPacket destroyPacket = new DestroyEntityPacket();
        destroyPacket.setId(id);
        tryToSendPacket(player, destroyPacket);
        plugin.fine("Destroyed FakeItemFrame#" + this.id + " for Player#" + player.getName());
    }
}
