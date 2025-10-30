package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import io.josemmo.bukkit.plugin.utils.Internals;
import org.jetbrains.annotations.NotNull;
import java.util.Collections;

public class DestroyEntityPacket extends PacketContainer {
    public DestroyEntityPacket() {
        super(PacketType.Play.Server.ENTITY_DESTROY);
    }

    public @NotNull DestroyEntityPacket setId(int id) {
        if (Internals.MINECRAFT_VERSION < 1700) { // Minecraft 1.16.x
            getIntegerArrays().write(0, new int[]{id});
        } else if (Internals.MINECRAFT_VERSION == 1700) { // Minecraft 1.17
            getIntegers().write(0, id);
        } else { // Minecraft 1.17.x
            getIntLists().write(0, Collections.singletonList(id));
        }
        return this;
    }
}
