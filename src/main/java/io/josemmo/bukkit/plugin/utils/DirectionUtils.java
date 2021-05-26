package io.josemmo.bukkit.plugin.utils;

import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

public class DirectionUtils {
    /**
     * Get cardinal direction
     * @param  yaw Yaw angle
     * @return     Cardinal direction
     */
    public static @NotNull BlockFace getCardinalDirection(float yaw) {
        if (yaw < 0) {
            yaw += 360;
        }
        if (yaw >= 315 || yaw < 45) {
            return BlockFace.SOUTH;
        } else if (yaw < 135) {
            return BlockFace.WEST;
        } else if (yaw < 225) {
            return BlockFace.NORTH;
        } else if (yaw < 315) {
            return BlockFace.EAST;
        }
        return BlockFace.NORTH;
    }
}
