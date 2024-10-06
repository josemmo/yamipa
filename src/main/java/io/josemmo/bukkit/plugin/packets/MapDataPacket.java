package io.josemmo.bukkit.plugin.packets;

import java.lang.reflect.ParameterizedType;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.StructureCache;
import com.comphenix.protocol.reflect.StructureModifier;

import io.josemmo.bukkit.plugin.utils.Internals;
import io.josemmo.bukkit.plugin.utils.WrappedMapId;

public class MapDataPacket extends PacketContainer {
    private static final int LOCKED_INDEX;
    private @Nullable StructureModifier<?> mapDataModifier;

    static {
        LOCKED_INDEX = (Internals.MINECRAFT_VERSION < 17) ? 1 : 0;
    }

    public MapDataPacket() {
        super(PacketType.Play.Server.MAP);
        getModifier().writeDefaults();

        if (Internals.MINECRAFT_VERSION < 17) {
            // Disable tracking position
            getBooleans().write(0, false);
        } else {
            // Create modifier for map data instance
            Class<?> mapDataType = getModifier().getField(4).getType();
            Object mapDataInstance = getModifier().read(4);

            // MapPatch is wrapped inside Optional since 1.20.5+
            if (mapDataInstance instanceof Optional) {
            	ParameterizedType genericType = (ParameterizedType) getModifier().getField(4).getGenericType();
            	mapDataType = (Class<?>) genericType.getActualTypeArguments()[0];

            	// Create new MapPatch instance as ProtocolLib won't initialize Optionals for us
            	mapDataInstance = StructureCache.newInstance(mapDataType);
            	getModifier().write(4, Optional.of(mapDataInstance));

            	// Set decorations Optional to an empty Optional (ProtocolLib initializes Optionals wrong)
            	getModifier().write(3, Optional.empty());
            }

            mapDataModifier = new StructureModifier<>(mapDataType).withTarget(mapDataInstance);
        }
    }

    public @NotNull MapDataPacket setId(int id) {
    	if (!WrappedMapId.trySetMapId(this, id)) {
            getIntegers().write(0, id);
    	}
        return this;
    }

    public @NotNull MapDataPacket setArea(int columns, int rows, int x, int z) {
        if (mapDataModifier == null) {
            getIntegers()
                .write(1, x)
                .write(2, z)
                .write(3, columns)
                .write (4, rows);
        } else {
            mapDataModifier.withType(Integer.TYPE)
                .write(0, x)
                .write(1, z)
                .write(2, columns)
                .write(3, rows);
        }
        return this;
    }

    public @NotNull MapDataPacket setScale(int scale) {
        getBytes().write(0, (byte) scale);
        return this;
    }

    public @NotNull MapDataPacket setLocked(boolean locked) {
        getBooleans().write(LOCKED_INDEX, locked);
        return this;
    }

    public @NotNull MapDataPacket setPixels(byte[] pixels) {
        if (mapDataModifier == null) {
            getByteArrays().write(0, pixels);
        } else {
            mapDataModifier.withType(byte[].class).write(0, pixels);
        }
        return this;
    }
}
