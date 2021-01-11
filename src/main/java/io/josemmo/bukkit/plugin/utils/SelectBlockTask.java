package io.josemmo.bukkit.plugin.utils;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

public class SelectBlockTask implements Listener {
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private static final ConcurrentMap<UUID, Boolean> hasAnotherTask = new ConcurrentHashMap<>();
    private final Player player;
    private BiConsumer<Location, BlockFace> success;
    private Runnable failure;
    private BukkitTask actionBarTask;

    /**
     * Class constructor
     * @param player Target player instance
     */
    public SelectBlockTask(Player player) {
        this.player = player;
    }

    /**
     * Set on success callback
     * @param callback Success callback
     */
    public void onSuccess(BiConsumer<Location, BlockFace> callback) {
        this.success = callback;
    }

    /**
     * Set on failure (e.g. canceled) callback
     * @param callback Failure callback
     */
    public void onFailure(Runnable callback) {
        this.failure = callback;
    }

    /**
     * Run task
     * @param helpMessage Help message for the player
     */
    public void run(String helpMessage) {
        UUID uuid = player.getUniqueId();

        // Has this player another active task?
        if (hasAnotherTask.getOrDefault(uuid, false)) {
            player.sendMessage(ChatColor.RED + "You already have a pending action!");
            return;
        }

        // Start task
        hasAnotherTask.put(uuid, true);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        actionBarTask = ActionBar.repeat(player, ChatColor.GREEN + helpMessage + ChatColor.RESET +
            " — " + ChatColor.RED + "Left click to cancel");
    }

    /**
     * Cancel task
     */
    public void cancel() {
        HandlerList.unregisterAll(this);
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        hasAnotherTask.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerInteraction(PlayerInteractEvent event) {
        Action action = event.getAction();
        Block block = event.getClickedBlock();

        // Player canceled the task
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            cancel();
            if (failure != null) {
                failure.run();
            }
            return;
        }

        // Player selected a block
        if (action == Action.RIGHT_CLICK_BLOCK && block != null) {
            event.setCancelled(true);
            cancel();
            if (success != null) {
                success.accept(block.getLocation(), event.getBlockFace());
            }
        }
    }
}
