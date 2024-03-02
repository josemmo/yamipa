package io.josemmo.bukkit.plugin.renderer;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * World Area IDs represent groups of 16 chunks arranged in a 4x4 square.
 */
public class WorldAreaId {
    private static boolean USE_WORLD_VIEW_DISTANCE = true;
    private static final Map<String, Integer> SIZES_PER_WORLD = new HashMap<>();
    private final World world;
    private final int x;
    private final int z;
    private WorldAreaId[] neighborhood = null;

    static {
        try {
            World.class.getMethod("getViewDistance");
        } catch (Exception e) {
            USE_WORLD_VIEW_DISTANCE = false;
        }
    }

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
     * @return Array of neighbors
     */
    public @NotNull WorldAreaId[] getNeighborhood() {
        // Get value from cache (if available)
        if (neighborhood != null) {
            return neighborhood;
        }

        // Calculate neighborhood size from world's view distance
        // NOTE: World size is cached to prevent issues with plugins that modify it at runtime
        int size = SIZES_PER_WORLD.computeIfAbsent(world.getName(), (__) -> {
            int distance = USE_WORLD_VIEW_DISTANCE ? world.getViewDistance() : Bukkit.getServer().getViewDistance();
            return distance / 4;
        });

        // Size 0 (1+1+1=3)
        // ·|·
        // XOX
        // ·|·
        if (size == 0) {
            neighborhood = new WorldAreaId[]{
                new WorldAreaId(world, x, z-1),

                new WorldAreaId(world, x-1, z),
                this,
                new WorldAreaId(world, x+1, z),

                new WorldAreaId(world, x, z+1),
            };
            return neighborhood;
        }

        // Size 1 (2+1+2=5)
        // ·X|X·
        // XX|XX
        // XXOXX
        // XX|XX
        // ·X|X·
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

        // Size 2 (3+1+3=7)
        // ··X|X··
        // ·XX|XX·
        // XXX|XXX
        // XXXOXXX
        // XXX|XXX
        // ·XX|XX·
        // ··X|X··
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

        // Size 3 (4+1+4=9)
        // ···X|X···
        // ·XXX|XXX·
        // ·XXX|XXX·
        // XXXX|XXXX
        // XXXXOXXXX
        // XXXX|XXXX
        // ·XXX|XXX·
        // ·XXX|XXX·
        // ···X|X···
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

        // Size ≥4 (5+1+5=11)
        // ···XX|XX···
        // ··XXX|XXX··
        // ·XXXX|XXXX·
        // XXXXX|XXXXX
        // XXXXX|XXXXX
        // XXXXXOXXXXX
        // XXXXX|XXXXX
        // XXXXX|XXXXX
        // ·XXXX|XXXX·
        // ··XXX|XXX··
        // ···XX|XX···
        neighborhood = new WorldAreaId[]{
            new WorldAreaId(world, x-2, z-5),
            new WorldAreaId(world, x-1, z-5),
            new WorldAreaId(world, x, z-5),
            new WorldAreaId(world, x+1, z-5),
            new WorldAreaId(world, x+2, z-5),

            new WorldAreaId(world, x-3, z-4),
            new WorldAreaId(world, x-2, z-4),
            new WorldAreaId(world, x-1, z-4),
            new WorldAreaId(world, x, z-4),
            new WorldAreaId(world, x+1, z-4),
            new WorldAreaId(world, x+2, z-4),
            new WorldAreaId(world, x+3, z-4),

            new WorldAreaId(world, x-4, z-3),
            new WorldAreaId(world, x-3, z-3),
            new WorldAreaId(world, x-2, z-3),
            new WorldAreaId(world, x-1, z-3),
            new WorldAreaId(world, x, z-3),
            new WorldAreaId(world, x+1, z-3),
            new WorldAreaId(world, x+2, z-3),
            new WorldAreaId(world, x+3, z-3),
            new WorldAreaId(world, x+4, z-3),

            new WorldAreaId(world, x-5, z-2),
            new WorldAreaId(world, x-4, z-2),
            new WorldAreaId(world, x-3, z-2),
            new WorldAreaId(world, x-2, z-2),
            new WorldAreaId(world, x-1, z-2),
            new WorldAreaId(world, x, z-2),
            new WorldAreaId(world, x+1, z-2),
            new WorldAreaId(world, x+2, z-2),
            new WorldAreaId(world, x+3, z-2),
            new WorldAreaId(world, x+4, z-2),
            new WorldAreaId(world, x+5, z-2),

            new WorldAreaId(world, x-5, z-1),
            new WorldAreaId(world, x-4, z-1),
            new WorldAreaId(world, x-3, z-1),
            new WorldAreaId(world, x-2, z-1),
            new WorldAreaId(world, x-1, z-1),
            new WorldAreaId(world, x, z-1),
            new WorldAreaId(world, x+1, z-1),
            new WorldAreaId(world, x+2, z-1),
            new WorldAreaId(world, x+3, z-1),
            new WorldAreaId(world, x+4, z-1),
            new WorldAreaId(world, x+5, z-1),

            new WorldAreaId(world, x-5, z),
            new WorldAreaId(world, x-4, z),
            new WorldAreaId(world, x-3, z),
            new WorldAreaId(world, x-2, z),
            new WorldAreaId(world, x-1, z),
            this,
            new WorldAreaId(world, x+1, z),
            new WorldAreaId(world, x+2, z),
            new WorldAreaId(world, x+3, z),
            new WorldAreaId(world, x+4, z),
            new WorldAreaId(world, x+5, z),

            new WorldAreaId(world, x-5, z+1),
            new WorldAreaId(world, x-4, z+1),
            new WorldAreaId(world, x-3, z+1),
            new WorldAreaId(world, x-2, z+1),
            new WorldAreaId(world, x-1, z+1),
            new WorldAreaId(world, x, z+1),
            new WorldAreaId(world, x+1, z+1),
            new WorldAreaId(world, x+2, z+1),
            new WorldAreaId(world, x+3, z+1),
            new WorldAreaId(world, x+4, z+1),
            new WorldAreaId(world, x+5, z+1),

            new WorldAreaId(world, x-5, z+2),
            new WorldAreaId(world, x-4, z+2),
            new WorldAreaId(world, x-3, z+2),
            new WorldAreaId(world, x-2, z+2),
            new WorldAreaId(world, x-1, z+2),
            new WorldAreaId(world, x, z+2),
            new WorldAreaId(world, x+1, z+2),
            new WorldAreaId(world, x+2, z+2),
            new WorldAreaId(world, x+3, z+2),
            new WorldAreaId(world, x+4, z+2),
            new WorldAreaId(world, x+5, z+2),

            new WorldAreaId(world, x-4, z+3),
            new WorldAreaId(world, x-3, z+3),
            new WorldAreaId(world, x-2, z+3),
            new WorldAreaId(world, x-1, z+3),
            new WorldAreaId(world, x, z+3),
            new WorldAreaId(world, x+1, z+3),
            new WorldAreaId(world, x+2, z+3),
            new WorldAreaId(world, x+3, z+3),
            new WorldAreaId(world, x+4, z+3),

            new WorldAreaId(world, x-3, z+4),
            new WorldAreaId(world, x-2, z+4),
            new WorldAreaId(world, x-1, z+4),
            new WorldAreaId(world, x, z+4),
            new WorldAreaId(world, x+1, z+4),
            new WorldAreaId(world, x+2, z+4),
            new WorldAreaId(world, x+3, z+4),

            new WorldAreaId(world, x-2, z+5),
            new WorldAreaId(world, x-1, z+5),
            new WorldAreaId(world, x, z+5),
            new WorldAreaId(world, x+1, z+5),
            new WorldAreaId(world, x+2, z+5),
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
