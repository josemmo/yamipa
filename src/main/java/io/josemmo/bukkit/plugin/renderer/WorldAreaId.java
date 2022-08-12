package io.josemmo.bukkit.plugin.renderer;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;

/**
 * World Area IDs represent groups of 16 chunks arranged in a 4x4 square.
 */
public class WorldAreaId {
    private final World world;
    private final int x;
    private final int z;
    private WorldAreaId[] neighborhood = null;

    /**
     * Get ID from location
     * @param  location Location instance
     * @return          World area ID
     */
    public static @NotNull WorldAreaId fromLocation(@NotNull Location location) {
        Chunk chunk = location.getChunk();
        return new WorldAreaId(chunk.getWorld(), chunk.getX() >> 2, chunk.getZ() >> 2);
    }

    /**
     * Class constructor
     * @param world World instance
     * @param x     World area X coordinate
     * @param z     World area Z coordinate
     */
    public WorldAreaId(@NotNull World world, int x, int z) {
        this.world = world;
        this.x = x;
        this.z = z;
    }

    /**
     * Get world instance
     * @return World instance
     */
    public @NotNull World getWorld() {
        return world;
    }

    /**
     * Get nearby world area IDs in view distance (plus this one)
     * @return List of neighbors
     */
    public @NotNull WorldAreaId[] getNeighborhood() {
        // Get value from cache (if available)
        if (neighborhood != null) {
            return neighborhood;
        }

        // Calculate neighborhood size from world's view distance
        int size = world.getViewDistance() / 4;

        // Size 3 (16-chunks radius)
        // ···XXX···
        // ·XXXXXXX·
        // ·XXXXXXX·
        // XXXXXXXXX
        // XXXXOXXXX
        // XXXXXXXXX
        // ·XXXXXXX·
        // ·XXXXXXX·
        // ···XXX···
        if (size == 3) {
            neighborhood = new WorldAreaId[]{
                new WorldAreaId(world, x-1, z-4),
                new WorldAreaId(world, x, z-4),
                new WorldAreaId(world, x+1, z-4),

                new WorldAreaId(world, x-3, z-3),
                new WorldAreaId(world, x-2, z-3),
                new WorldAreaId(world, x-1, z-3),
                new WorldAreaId(world, x, z-3),
                new WorldAreaId(world, x+1, z-3),
                new WorldAreaId(world, x+2, z-3),
                new WorldAreaId(world, x+3, z-3),

                new WorldAreaId(world, x-3, z-2),
                new WorldAreaId(world, x-2, z-2),
                new WorldAreaId(world, x-1, z-2),
                new WorldAreaId(world, x, z-2),
                new WorldAreaId(world, x+1, z-2),
                new WorldAreaId(world, x+2, z-2),
                new WorldAreaId(world, x+3, z-2),

                new WorldAreaId(world, x-4, z-1),
                new WorldAreaId(world, x-3, z-1),
                new WorldAreaId(world, x-2, z-1),
                new WorldAreaId(world, x-1, z-1),
                new WorldAreaId(world, x, z-1),
                new WorldAreaId(world, x+1, z-1),
                new WorldAreaId(world, x+2, z-1),
                new WorldAreaId(world, x+3, z-1),
                new WorldAreaId(world, x+4, z-1),

                new WorldAreaId(world, x-4, z),
                new WorldAreaId(world, x-3, z),
                new WorldAreaId(world, x-2, z),
                new WorldAreaId(world, x-1, z),
                this,
                new WorldAreaId(world, x+1, z),
                new WorldAreaId(world, x+2, z),
                new WorldAreaId(world, x+3, z),
                new WorldAreaId(world, x+4, z),

                new WorldAreaId(world, x-4, z+1),
                new WorldAreaId(world, x-3, z+1),
                new WorldAreaId(world, x-2, z+1),
                new WorldAreaId(world, x-1, z+1),
                new WorldAreaId(world, x, z+1),
                new WorldAreaId(world, x+1, z+1),
                new WorldAreaId(world, x+2, z+1),
                new WorldAreaId(world, x+3, z+1),
                new WorldAreaId(world, x+4, z+1),

                new WorldAreaId(world, x-3, z+2),
                new WorldAreaId(world, x-2, z+2),
                new WorldAreaId(world, x-1, z+2),
                new WorldAreaId(world, x, z+2),
                new WorldAreaId(world, x+1, z+2),
                new WorldAreaId(world, x+2, z+2),
                new WorldAreaId(world, x+3, z+2),

                new WorldAreaId(world, x-3, z+3),
                new WorldAreaId(world, x-2, z+3),
                new WorldAreaId(world, x-1, z+3),
                new WorldAreaId(world, x, z+3),
                new WorldAreaId(world, x+1, z+3),
                new WorldAreaId(world, x+2, z+3),
                new WorldAreaId(world, x+3, z+3),

                new WorldAreaId(world, x-1, z+4),
                new WorldAreaId(world, x, z+4),
                new WorldAreaId(world, x+1, z+4),
            };
            return neighborhood;
        }

        // Size 2 (12-chunks radius)
        // ··XXX··
        // ·XXXXX·
        // XXXXXXX
        // XXXOXXX
        // XXXXXXX
        // ·XXXXX·
        // ··XXX··
        if (size == 2) {
            neighborhood = new WorldAreaId[]{
                new WorldAreaId(world, x-1, z-3),
                new WorldAreaId(world, x, z-3),
                new WorldAreaId(world, x+1, z-3),

                new WorldAreaId(world, x-2, z-2),
                new WorldAreaId(world, x-1, z-2),
                new WorldAreaId(world, x, z-2),
                new WorldAreaId(world, x+1, z-2),
                new WorldAreaId(world, x+2, z-2),

                new WorldAreaId(world, x-3, z-1),
                new WorldAreaId(world, x-2, z-1),
                new WorldAreaId(world, x-1, z-1),
                new WorldAreaId(world, x, z-1),
                new WorldAreaId(world, x+1, z-1),
                new WorldAreaId(world, x+2, z-1),
                new WorldAreaId(world, x+3, z-1),

                new WorldAreaId(world, x-3, z),
                new WorldAreaId(world, x-2, z),
                new WorldAreaId(world, x-1, z),
                this,
                new WorldAreaId(world, x+1, z),
                new WorldAreaId(world, x+2, z),
                new WorldAreaId(world, x+3, z),

                new WorldAreaId(world, x-3, z+1),
                new WorldAreaId(world, x-2, z+1),
                new WorldAreaId(world, x-1, z+1),
                new WorldAreaId(world, x, z+1),
                new WorldAreaId(world, x+1, z+1),
                new WorldAreaId(world, x+2, z+1),
                new WorldAreaId(world, x+3, z+1),

                new WorldAreaId(world, x-2, z+2),
                new WorldAreaId(world, x-1, z+2),
                new WorldAreaId(world, x, z+2),
                new WorldAreaId(world, x+1, z+2),
                new WorldAreaId(world, x+2, z+2),

                new WorldAreaId(world, x-1, z+3),
                new WorldAreaId(world, x, z+3),
                new WorldAreaId(world, x+1, z+3),
            };
            return neighborhood;
        }

        //// Size 1 (8-chunks radius)
        // ·XXX·
        // XXXXX
        // XXOXX
        // XXXXX
        // ·XXX·
        if (size == 1) {
            neighborhood = new WorldAreaId[]{
                new WorldAreaId(world, x-1, z-2),
                new WorldAreaId(world, x, z-2),
                new WorldAreaId(world, x+1, z-2),

                new WorldAreaId(world, x-2, z-1),
                new WorldAreaId(world, x-1, z-1),
                new WorldAreaId(world, x, z-1),
                new WorldAreaId(world, x+1, z-1),
                new WorldAreaId(world, x+2, z-1),

                new WorldAreaId(world, x-2, z),
                new WorldAreaId(world, x-1, z),
                this,
                new WorldAreaId(world, x+1, z),
                new WorldAreaId(world, x+2, z),

                new WorldAreaId(world, x-2, z+1),
                new WorldAreaId(world, x-1, z+1),
                new WorldAreaId(world, x, z+1),
                new WorldAreaId(world, x+1, z+1),
                new WorldAreaId(world, x+2, z+1),

                new WorldAreaId(world, x-1, z+2),
                new WorldAreaId(world, x, z+2),
                new WorldAreaId(world, x+1, z+2),
            };
            return neighborhood;
        }

        //// Size 0 (4x4 chunks)
        // ·X·
        // XOX
        // ·X·
        neighborhood = new WorldAreaId[]{
            new WorldAreaId(world, x, z-1),

            new WorldAreaId(world, x-1, z),
            this,
            new WorldAreaId(world, x+1, z),

            new WorldAreaId(world, x, z+1),
        };
        return neighborhood;
    }

    @Override
    public boolean equals(@Nullable Object o) {
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
    public @NotNull String toString() {
        return world.getName() + "," + x + "," + z;
    }
}
