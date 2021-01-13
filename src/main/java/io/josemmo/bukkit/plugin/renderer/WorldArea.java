package io.josemmo.bukkit.plugin.renderer;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.HashSet;
import java.util.Set;

public class WorldArea {
    public static final int CHUNK_RATIO = 2; // 2^2=4 (4x4 chunks)
    private final String id;
    private final Set<Player> players = new HashSet<>();

    /**
     * Get ID from location
     * @param  location Location instance
     * @return          World area ID
     */
    public static String getId(Location location) {
        Chunk chunk = location.getChunk();
        return chunk.getWorld().getName() + "," +
            (chunk.getX() >> CHUNK_RATIO) + "," +
            (chunk.getZ() >> CHUNK_RATIO);
    }

    /**
     * Class constructor
     * @param location Location instance
     */
    public WorldArea(Location location) {
        this.id = getId(location);
    }

    /**
     * Load for player
     * @param player Player instance
     */
    public void load(Player player) {
        players.add(player);
        // TODO: not implemented
    }

    /**
     * Remove player from world area
     * @param player Player instance
     */
    public void removePlayer(Player player) {
        players.remove(player);
        // TODO: not implemented
    }

    /**
     * Unload for player
     * @param player Player instance
     */
    public void unload(Player player) {
        removePlayer(player);
        // TODO: not implemented
    }

    /**
     * Unload for all players
     */
    public void unload() {
        for (Player player : players) {
            unload(player);
        }
    }
}
