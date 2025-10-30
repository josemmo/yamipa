package io.josemmo.bukkit.plugin.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.StructureCache;
import com.comphenix.protocol.reflect.ExactReflection;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;
import io.josemmo.bukkit.plugin.utils.Internals;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.ParameterizedType;
import java.util.Optional;

public class MapDataPacket extends PacketContainer {
    private static final int LOCKED_INDEX;
    private static final @Nullable Constructor<?> MAP_ID_CONSTRUCTOR;
    private @Nullable StructureModifier<?> mapDataModifier;

    static {
        LOCKED_INDEX = (Internals.MINECRAFT_VERSION < 1700) ? 1 : 0;
        if (Internals.MINECRAFT_VERSION < 2005) {
            MAP_ID_CONSTRUCTOR = null;
        } else {
            Class<?> mapIdClass = MinecraftReflection.getNullableNMS("world.level.saveddata.maps.MapId");
            MAP_ID_CONSTRUCTOR = ExactReflection.fromClass(mapIdClass, true).findConstructor(int.class);
        }
    }

    public MapDataPacket() {
        super(PacketType.Play.Server.MAP);
        getModifier().writeDefaults();

        if (Internals.MINECRAFT_VERSION < 1700) {
            getBooleans().write(0, false); // Disable tracking position
        } else if (Internals.MINECRAFT_VERSION < 2005) {
            Class<?> mapDataType = getModifier().getField(4).getType();
            Object mapDataInstance = getModifier().read(4);
            mapDataModifier = new StructureModifier<>(mapDataType).withTarget(mapDataInstance);
        } else {
            ParameterizedType genericType = (ParameterizedType) getModifier().getField(4).getGenericType();
            Class<?> mapDataType = (Class<?>) genericType.getActualTypeArguments()[0];
            Object mapDataInstance = StructureCache.newInstance(mapDataType);
            getModifier().write(3, Optional.empty());
            getModifier().write(4, Optional.of(mapDataInstance));
            mapDataModifier = new StructureModifier<>(mapDataType).withTarget(mapDataInstance);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public @NotNull MapDataPacket setId(int id) {
        if (MAP_ID_CONSTRUCTOR == null) {
            getIntegers().write(0, id);
        } else {
            try {
                Class<?> mapIdClass = MAP_ID_CONSTRUCTOR.getDeclaringClass();
                Object mapIdInstance = MAP_ID_CONSTRUCTOR.newInstance(id);
                ((StructureModifier) getSpecificModifier(mapIdClass)).write(0, mapIdInstance);
            } catch (Exception e) {
                throw new RuntimeException("Failed to instantiate MapId for map #" + id);
            }
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
