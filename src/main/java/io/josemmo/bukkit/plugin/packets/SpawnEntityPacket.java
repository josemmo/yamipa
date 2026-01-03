package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import io.josemmo.bukkit.plugin.utils.Internals;
import org.bukkit.entity.EntityType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import java.util.UUID;

public class SpawnEntityPacket extends PacketContainer {
    private static final boolean ROTATION_AS_BYTES;
    private static final int DATA_INDEX;

    static {
        if (Internals.MINECRAFT_VERSION < 1900) {
            ROTATION_AS_BYTES = false;
            DATA_INDEX = 6;
        } else {
            ROTATION_AS_BYTES = true;
            DATA_INDEX = (Internals.MINECRAFT_VERSION < 2109) ? 4 : 1;
        }
    }

    public SpawnEntityPacket() {
        super(PacketType.Play.Server.SPAWN_ENTITY);
        if (Internals.MINECRAFT_VERSION < 2109) {
            getIntegers()
                .write(1, 0)
                .write(2, 0)
                .write(3, 0);
        } else {
            getVectors().write(0, new Vector(0, 0, 0));
        }
        getUUIDs()
            .write(0, UUID.randomUUID());
    }

    public @NotNull SpawnEntityPacket setEntityType(@NotNull EntityType type) {
        getEntityTypeModifier().write(0, type);
        return this;
    }

    public @NotNull SpawnEntityPacket setId(int id) {
        getIntegers().write(0, id);
        return this;
    }

    public @NotNull SpawnEntityPacket setRotation(int pitch, int yaw) {
        if (ROTATION_AS_BYTES) {
            getBytes()
                .write(0, (byte) pitch)
                .write(1, (byte) yaw);
        } else {
            getIntegers()
                .write(4, pitch)
                .write(5, yaw);
        }
        return this;
    }

    public @NotNull SpawnEntityPacket setData(int data) {
        getIntegers().write(DATA_INDEX, data);
        return this;
    }

    public @NotNull SpawnEntityPacket setPosition(double x, double y, double z) {
        getDoubles()
            .write(0, x)
            .write(1, y)
            .write(2, z);
        return this;
    }
}
