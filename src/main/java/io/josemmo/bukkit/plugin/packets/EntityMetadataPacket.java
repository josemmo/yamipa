package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import org.bukkit.Rotation;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class EntityMetadataPacket extends PacketContainer {
    private final WrappedDataWatcher dataWatcher = new WrappedDataWatcher();

    public EntityMetadataPacket() {
        super(PacketType.Play.Server.ENTITY_METADATA);
    }

    public @NotNull EntityMetadataPacket setId(int id) {
        getIntegers().write(0, id);
        return this;
    }

    public @NotNull EntityMetadataPacket setFlags(byte flags) {
        WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get(Byte.class);
        dataWatcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(0, serializer), flags);
        return this;
    }

    public @NotNull EntityMetadataPacket setInvisible(boolean invisible) {
        int flags = invisible ? 0x20 : 0x00;
        return setFlags((byte) flags);
    }

    public @NotNull EntityMetadataPacket setItem(@NotNull ItemStack item) {
        WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.getItemStackSerializer(false);
        dataWatcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(7, serializer), item);
        return this;
    }

    public @NotNull EntityMetadataPacket setRotation(@NotNull Rotation rotation) {
        WrappedDataWatcher.Serializer serializer = WrappedDataWatcher.Registry.get(Integer.class);
        dataWatcher.setObject(new WrappedDataWatcher.WrappedDataWatcherObject(8, serializer), rotation.ordinal());
        return this;
    }

    public @NotNull EntityMetadataPacket build() {
        getWatchableCollectionModifier().write(0, dataWatcher.getWatchableObjects());
        return this;
    }
}
