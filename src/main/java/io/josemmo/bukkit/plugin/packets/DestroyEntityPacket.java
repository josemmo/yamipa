package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import io.josemmo.bukkit.plugin.utils.Internals;
import org.jetbrains.annotations.NotNull;

public class DestroyEntityPacket extends PacketContainer {
    public DestroyEntityPacket() {
        super(PacketType.Play.Server.ENTITY_DESTROY);
    }

    public @NotNull DestroyEntityPacket setId(int id) {
        if (Internals.MINECRAFT_VERSION < 17) {
            getIntegerArrays().write(0, new int[]{id});
        } else {
            getIntegers().write(0, id);
        }
        return this;
    }
}
