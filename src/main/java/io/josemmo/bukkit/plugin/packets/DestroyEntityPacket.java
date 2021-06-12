package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import org.jetbrains.annotations.NotNull;

public class DestroyEntityPacket extends PacketContainer {
    public DestroyEntityPacket() {
        super(PacketType.Play.Server.ENTITY_DESTROY);
    }

    public @NotNull DestroyEntityPacket setId(int id) {
        getIntegerArrays().write(0, new int[]{id});
        return this;
    }
}
