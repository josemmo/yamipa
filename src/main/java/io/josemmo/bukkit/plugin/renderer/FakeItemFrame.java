package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.events.PacketContainer;
import io.josemmo.bukkit.plugin.packets.DestroyEntityPacket;
import io.josemmo.bukkit.plugin.packets.EntityMetadataPacket;
import io.josemmo.bukkit.plugin.packets.SpawnEntityPacket;
import io.josemmo.bukkit.plugin.utils.Internals;
import io.josemmo.bukkit.plugin.utils.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeItemFrame extends FakeEntity {
    public static final int MIN_FRAME_ID = Integer.MAX_VALUE / 4;
    public static final int MAX_FRAME_ID = Integer.MAX_VALUE;
    private static final boolean SUPPORTS_GLOWING = Internals.MINECRAFT_VERSION >= 1700;
    private static final Logger LOGGER = Logger.getLogger("FakeItemFrame");
    private static final AtomicInteger LAST_FRAME_ID = new AtomicInteger(MAX_FRAME_ID);
    private final int id;
    private final Location location;
    private final BlockFace face;
    private final Rotation rotation;
    private final boolean glowing;
    private final FakeMap[] maps;

    /**
     * Get next unused item frame ID
     * @return Next unused item frame ID
     */
    private static int getNextId() {
        return LAST_FRAME_ID.updateAndGet(lastId -> {
            if (lastId == MAX_FRAME_ID) {
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
     * @param glowing  Whether is glowing or regular frame
     * @param maps     Fake maps to animate
     */
    public FakeItemFrame(
        @NotNull Location location,
        @NotNull BlockFace face,
        @NotNull Rotation rotation,
        boolean glowing,
        @NotNull FakeMap[] maps
    ) {
        this.id = getNextId();
        this.location = location;
        this.face = face;
        this.rotation = rotation;
        this.glowing = glowing;
        this.maps = maps;
        LOGGER.fine("Created FakeItemFrame#" + this.id + " using " + this.maps.length + " FakeMap(s)");
    }

    /**
     * Get frame ID
     * @return Frame ID
     */
    public int getId() {
        return id;
    }

    /**
     * Get entity spawn packet
     * @return Spawn packet
     */
    public @NotNull SpawnEntityPacket getSpawnPacket() {
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
            .setEntityType((glowing && SUPPORTS_GLOWING) ? EntityType.GLOW_ITEM_FRAME : EntityType.ITEM_FRAME)
            .setPosition(x, y, z)
            .setRotation(pitch, yaw)
            .setData(orientation);

        return framePacket;
    }

    /**
     * Get frame of animation packets
     * @param player Player who is expected to receive packets (for caching reasons)
     * @param step   Map step
     */
    @SuppressWarnings("deprecation")
    public @NotNull List<PacketContainer> getRenderPackets(@NotNull Player player, int step) {
        List<PacketContainer> packets = new ArrayList<>(2);

        // Enqueue map pixels packet (if needed)
        boolean mustSendPixels = maps[step].requestResend(player);
        if (mustSendPixels) {
            packets.add(maps[step].getPixelsPacket());
        }

        // Create and attach filled map
        ItemStack itemStack = new ItemStack(Material.FILLED_MAP);
        MapMeta itemStackMeta = Objects.requireNonNull((MapMeta) itemStack.getItemMeta());
        itemStackMeta.setMapId(maps[step].getId());
        itemStack.setItemMeta(itemStackMeta);

        // Build entity metadata packet
        EntityMetadataPacket metadataPacket = new EntityMetadataPacket();
        metadataPacket.setId(id)
            .setInvisible(true)
            .setItem(itemStack)
            .setRotation(rotation)
            .build();
        packets.add(metadataPacket);

        return packets;
    }

    /**
     * Get destroy item frame packet
     * @return Destroy packet
     */
    public @NotNull DestroyEntityPacket getDestroyPacket() {
        DestroyEntityPacket destroyPacket = new DestroyEntityPacket();
        destroyPacket.setId(id);
        return destroyPacket;
    }
}
