package io.josemmo.bukkit.plugin.utils;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

public class SelectBlockTask {
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private static final Map<UUID, SelectBlockTask> instances = new HashMap<>();
    private static PlayerInteractionListener listener = null;
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
        if (instances.containsKey(uuid)) {
            player.sendMessage(ChatColor.RED + "You already have a pending action!");
            return;
        }

        // Create listener singleton if needed
        if (listener == null) {
            listener = new PlayerInteractionListener();
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
            plugin.fine("Created PlayerInteractionListener singleton");
        }

        // Start task
        instances.put(uuid, this);
        actionBarTask = ActionBar.repeat(player, ChatColor.GREEN + helpMessage + ChatColor.RESET +
            " — " + ChatColor.RED + "Left click to cancel");
    }

    /**
     * Cancel task
     */
    public void cancel() {
        if (actionBarTask != null) {
            actionBarTask.cancel();
        }
        instances.remove(player.getUniqueId());

        // Destroy listener singleton if no more active tasks
        if (instances.isEmpty()) {
            HandlerList.unregisterAll(listener);
            listener = null;
            plugin.fine("Destroyed PlayerInteractionListener singleton");
        }
    }

    /**
     * Internal listener for handling player events
     */
    private static class PlayerInteractionListener implements Listener {
        @EventHandler
        public void onPlayerInteraction(PlayerInteractEvent event) {
            Action action = event.getAction();
            Block block = event.getClickedBlock();

            // Get task responsible for handling this event
            UUID uuid = event.getPlayer().getUniqueId();
            SelectBlockTask task = instances.get(uuid);
            if (task == null) {
                plugin.warning("Received orphan PlayerInteractEvent from player " + uuid);
                return;
            }

            // Player canceled the task
            if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                task.cancel();
                if (task.failure != null) {
                    task.failure.run();
                }
                return;
            }

            // Player selected a block
            if (action == Action.RIGHT_CLICK_BLOCK && block != null) {
                event.setCancelled(true);
                task.cancel();
                if (task.success != null) {
                    task.success.accept(block.getLocation(), event.getBlockFace());
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onPlayerQuit(PlayerQuitEvent event) {
            UUID uuid = event.getPlayer().getUniqueId();
            SelectBlockTask task = instances.get(uuid);
            if (task == null) {
                plugin.warning("Received orphan PlayerQuitEvent from player " + uuid);
                return;
            }
            task.cancel();
        }
    }
}
