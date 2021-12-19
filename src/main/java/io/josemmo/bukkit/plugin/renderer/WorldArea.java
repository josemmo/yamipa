package io.josemmo.bukkit.plugin.renderer;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A World Area is a group of 16 chunks arranged in a 4x4 square.
 */
public class WorldArea {
    private final WorldAreaId id;
    private final Set<FakeImage> fakeImages = ConcurrentHashMap.newKeySet();
    private final Set<Player> players = new HashSet<>();

    /**
     * Class constructor
     * @param id World area ID
     */
    public WorldArea(@NotNull WorldAreaId id) {
        this.id = id;

        // Initialize current list of players in this area
        for (Player player : id.getWorld().getPlayers()) {
            WorldAreaId playerWorldAreaId = WorldAreaId.fromLocation(player.getLocation());
            if (id.equals(playerWorldAreaId)) {
                players.add(player);
            }
        }
    }

    /**
     * Get world area ID
     * @return World area ID
     */
    public @NotNull WorldAreaId getId() {
        return id;
    }

    /**
     * Contains fake images
     * @return TRUE if instance contains fake images, FALSE otherwise
     */
    public boolean hasImages() {
        return !fakeImages.isEmpty();
    }

    /**
     * Get all fake images
     * @return Set of fake images
     */
    public @NotNull Set<FakeImage> getImages() {
        return fakeImages;
    }

    /**
     * Add fake image
     * @param image Fake image instance
     */
    public void addImage(@NotNull FakeImage image) {
        fakeImages.add(image);
    }

    /**
     * Remove fake image
     * @param image Fake image instance
     */
    public void removeImage(@NotNull FakeImage image) {
        fakeImages.remove(image);
    }

    /**
     * Load for player
     * @param player Player instance
     */
    public void load(@NotNull Player player) {
        players.add(player);
        for (FakeImage image : fakeImages) {
            image.spawn(player);
        }
    }

    /**
     * Remove player from world area
     * @param player Player instance
     */
    public void removePlayer(@NotNull Player player) {
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
    public void unload(@NotNull Player player) {
        for (FakeImage image : fakeImages) {
            image.destroy(player);
        }
        removePlayer(player);
    }

    /**
     * Unload for all players
     */
    public void unload() {
        for (Player player : new ArrayList<>(players)) {
            unload(player);
        }
    }
}
