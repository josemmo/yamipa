package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import io.josemmo.bukkit.plugin.utils.MinecraftVersion;
import org.jetbrains.annotations.NotNull;
import java.util.Collections;

public class DestroyEntityPacket extends PacketContainer {
    public DestroyEntityPacket() {
        super(PacketType.Play.Server.ENTITY_DESTROY);
    }

    public @NotNull DestroyEntityPacket setId(int id) {
        if (MinecraftVersion.CURRENT.isAtLeast(MinecraftVersion.V1_17_1)) {
            getIntLists().write(0, Collections.singletonList(id));
        } else if (MinecraftVersion.CURRENT.isAtLeast(MinecraftVersion.V1_17)) {
            getIntegers().write(0, id);
        } else {
            getIntegerArrays().write(0, new int[]{id});
        }
        return this;
    }
}
