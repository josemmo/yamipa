package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.BukkitConverters;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import io.josemmo.bukkit.plugin.utils.Internals;
import org.bukkit.Rotation;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class EntityMetadataPacket extends PacketContainer {
    private static final boolean USE_DATA_WATCHER;
    private static final int ITEM_INDEX;
    private static final int ROTATION_INDEX;
    private WrappedDataWatcher dataWatcher; // For <= 1.19.2 - lazy initialization
    private final List<WrappedDataValue> values = new ArrayList<>(); // For >= 1.19.3

    static {
        USE_DATA_WATCHER = (Internals.MINECRAFT_VERSION < 19.3);
        if (Internals.MINECRAFT_VERSION < 17) { // Minecraft 1.16.x
            ITEM_INDEX = 7;
        } else if (Internals.MINECRAFT_VERSION < 21.6) { // Minecraft 1.17.x to 1.21.5
            ITEM_INDEX = 8;
        } else { // Minecraft 1.21.6 and upwards
            ITEM_INDEX = 9;
        }
        ROTATION_INDEX = ITEM_INDEX + 1;
    }

    public EntityMetadataPacket() {
        super(PacketType.Play.Server.ENTITY_METADATA);
    }

    /**
     * Get or create WrappedDataWatcher instance safely
     */
    private WrappedDataWatcher getDataWatcher() {
        if (dataWatcher == null) {
            dataWatcher = new WrappedDataWatcher();
        }
        return dataWatcher;
    }

    public @NotNull EntityMetadataPacket setId(int id) {
        getIntegers().write(0, id);
        return this;
    }

    public @NotNull EntityMetadataPacket setFlags(byte flags) {
        WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get((Type) Byte.class);
        if (USE_DATA_WATCHER) {
            getDataWatcher().setObject(new WrappedDataWatcher.WrappedDataWatcherObject(0, serializer), flags);
        } else {
            values.add(new WrappedDataValue(0, serializer, flags));
        }
        return this;
    }

    public @NotNull EntityMetadataPacket setInvisible(boolean invisible) {
        int flags = invisible ? 0x20 : 0x00;
        return setFlags((byte) flags);
    }

    public @NotNull EntityMetadataPacket setItem(@NotNull ItemStack item) {
        WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.getItemStackSerializer(false);
        if (USE_DATA_WATCHER) {
            getDataWatcher().setObject(new WrappedDataWatcher.WrappedDataWatcherObject(ITEM_INDEX, serializer), item);
        } else {
            values.add(new WrappedDataValue(
                ITEM_INDEX,
                serializer,
                BukkitConverters.getItemStackConverter().getGeneric(item)
            ));
        }
        return this;
    }

    public @NotNull EntityMetadataPacket setRotation(@NotNull Rotation rotation) {
        WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get((Type) Integer.class);
        if (USE_DATA_WATCHER) {
            getDataWatcher().setObject(
                new WrappedDataWatcher.WrappedDataWatcherObject(ROTATION_INDEX, serializer),
                rotation.ordinal()
            );
        } else {
            values.add(new WrappedDataValue(ROTATION_INDEX, serializer, rotation.ordinal()));
        }
        return this;
    }

    public @NotNull EntityMetadataPacket build() {
        if (USE_DATA_WATCHER) {
            getWatchableCollectionModifier().write(0, getDataWatcher().getWatchableObjects());
        } else {
            getDataValueCollectionModifier().write(0, values);
        }
        return this;
    }
}
