package io.josemmo.bukkit.plugin.utils;

import java.lang.reflect.Constructor;
import java.util.function.IntFunction;

import org.jetbrains.annotations.Nullable;

import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.reflect.ExactReflection;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.utility.MinecraftReflection;

public class WrappedMapId {

	private static final @Nullable Class<?> MAP_ID_CLASS = MinecraftReflection.getNullableNMS("world.level.saveddata.maps.MapId");
	private static final @Nullable IntFunction<?> MAP_ID_CONSTRUTOR = getMapIdConstructor();

	private static IntFunction<Object> getMapIdConstructor() {
		if (MAP_ID_CLASS == null) {
			return null;
		}

		Constructor<?> constructor = ExactReflection.fromClass(MAP_ID_CLASS, true)
			.findConstructor(int.class);
		if (constructor == null) {
			return null;
		}

		return (mapId) -> {
			try {
				return constructor.newInstance(mapId);
			} catch (Exception e) {
				throw new RuntimeException("An error occurred while creating a MapId", e);
			}
		};
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static boolean trySetMapId(PacketContainer packetContainer, int mapId) {
		if (MAP_ID_CLASS == null || MAP_ID_CONSTRUTOR == null) {
			return false;
		}

		StructureModifier mapIds = packetContainer.getSpecificModifier(MAP_ID_CLASS);
		mapIds.write(0, MAP_ID_CONSTRUTOR.apply(mapId));

		return true;
	}
}
