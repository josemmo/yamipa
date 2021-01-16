package io.josemmo.bukkit.plugin.renderer;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WorldArea {
    public static final int CHUNK_RATIO = 2; // 2^2=4 (4x4 chunks)
    private final Set<FakeImage> fakeImages = ConcurrentHashMap.newKeySet();
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
     * Contains fake images
     * @return TRUE if instance contains fake images, FALSE otherwise
     */
    public boolean hasImages() {
        return fakeImages.isEmpty();
    }

    /**
     * Get all fake images
     * @return Set of fake images
     */
    public Set<FakeImage> getImages() {
        return fakeImages;
    }

    /**
     * Add fake image
     * @param image Fake image instance
     */
    public void addImage(FakeImage image) {
        fakeImages.add(image);
        for (Player player : players) {
            image.spawn(player);
        }
    }

    /**
     * Remove fake image
     * @param image Fake image instance
     */
    public void removeImage(FakeImage image) {
        fakeImages.remove(image);
        for (Player player : players) {
            image.destroy(player);
        }
    }

    /**
     * Load for player
     * @param player Player instance
     */
    public void load(Player player) {
        players.add(player);
        for (FakeImage image : fakeImages) {
            image.spawn(player);
        }
    }

    /**
     * Remove player from world area
     * @param player Player instance
     */
    public void removePlayer(Player player) {
        players.remove(player);
        if (players.isEmpty()) {
            for (FakeImage image : fakeImages) {
                image.invalidate();
            }
        }
    }

    /**
     * Unload for player
     * @param player Player instance
     */
    public void unload(Player player) {
        for (FakeImage image : fakeImages) {
            image.destroy(player);
        }
        removePlayer(player);
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
