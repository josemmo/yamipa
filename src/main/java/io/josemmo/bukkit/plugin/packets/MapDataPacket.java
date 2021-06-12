package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import org.jetbrains.annotations.NotNull;

public class MapDataPacket extends PacketContainer {
    public MapDataPacket() {
        super(PacketType.Play.Server.MAP);
        getModifier().writeDefaults();
    }

    public @NotNull MapDataPacket setId(int id) {
        getIntegers().write(0, id);
        return this;
    }

    public @NotNull MapDataPacket setArea(int columns, int rows, int x, int z) {
        getIntegers()
            .write(1, x)
            .write(2, z)
            .write(3, columns)
            .write (4, rows);
        return this;
    }

    public @NotNull MapDataPacket setScale(int scale) {
        getBytes().write(0, (byte) scale);
        return this;
    }

    public @NotNull MapDataPacket setLocked(boolean locked) {
        getBooleans().write(1, locked);
        return this;
    }

    public @NotNull MapDataPacket setTrackingPosition(boolean trackingPosition) {
        getBooleans().write(0, trackingPosition);
        return this;
    }

    public @NotNull MapDataPacket setPixels(byte[] pixels) {
        getByteArrays().write(0, pixels);
        return this;
    }
}
