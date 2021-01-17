package io.josemmo.bukkit.plugin.renderer;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import java.util.Objects;

public class WorldAreaId {
    private final World world;
    private final int x;
    private final int z;

    /**
     * Get ID from location
     * @param  location Location instance
     * @return          World area ID
     */
    public static WorldAreaId fromLocation(Location location) {
        Chunk chunk = location.getChunk();
        return new WorldAreaId(chunk.getWorld(), chunk.getX() >> 2, chunk.getZ() >> 2);
    }

    /**
     * Class constructor
     * @param world World instance
     * @param x     World area X coordinate
     * @param z     World area Z coordinate
     */
    public WorldAreaId(World world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    /**
     * Get world instance
     * @return World instance
     */
    public World getWorld() {
        return world;
    }

    /**
     * Get colliding world area IDs plus this one
     * @return List of neighbors
     */
    public WorldAreaId[] getNeighborhood() {
        return new WorldAreaId[]{
            new WorldAreaId(world, x-1, z-1),
            new WorldAreaId(world, x, z-1),
            new WorldAreaId(world, x+1, z-1),
            new WorldAreaId(world, x-1, z),
            this,
            new WorldAreaId(world, x+1, z),
            new WorldAreaId(world, x-1, z+1),
            new WorldAreaId(world, x, z+1),
            new WorldAreaId(world, x+1, z+1)
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WorldAreaId that = (WorldAreaId) o;
        return x == that.x && z == that.z && world.getName().equals(that.world.getName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, z);
    }

    @Override
    public String toString() {
        return world.getName() + "," + x + "," + z;
    }
}
