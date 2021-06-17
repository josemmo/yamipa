package io.josemmo.bukkit.plugin.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListeningWhitelist;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.EnumWrappers;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
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
    public SelectBlockTask(@NotNull Player player) {
        this.player = player;
    }

    /**
     * Set on success callback
     * @param callback Success callback
     */
    public void onSuccess(@Nullable BiConsumer<@NotNull Location, @NotNull BlockFace> callback) {
        this.success = callback;
    }

    /**
     * Set on failure (e.g. canceled) callback
     * @param callback Failure callback
     */
    public void onFailure(@Nullable Runnable callback) {
        this.failure = callback;
    }

    /**
     * Run task
     * @param helpMessage Help message for the player
     */
    public void run(@NotNull String helpMessage) {
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
            ProtocolLibrary.getProtocolManager().addPacketListener(listener);
            plugin.fine("Created PlayerInteractionListener singleton");
        }

        // Start task
        instances.put(uuid, this);
        actionBarTask = ActionBar.repeat(player, ChatColor.GREEN + helpMessage + ChatColor.RESET +
            " - " + ChatColor.RED + "Left click to cancel");
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
            ProtocolLibrary.getProtocolManager().removePacketListener(listener);
            listener = null;
            plugin.fine("Destroyed PlayerInteractionListener singleton");
        }
    }

    /**
     * Internal listener for handling player events
     */
    private static class PlayerInteractionListener implements Listener, PacketListener {
        @EventHandler
        public void onPlayerInteraction(PlayerInteractEvent event) {
            Action action = event.getAction();
            Block block = event.getClickedBlock();

            // Get task responsible for handling this event
            UUID uuid = event.getPlayer().getUniqueId();
            SelectBlockTask task = instances.get(uuid);
            if (task == null) return;

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

        @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
        public void onPlayerQuit(PlayerQuitEvent event) {
            UUID uuid = event.getPlayer().getUniqueId();
            SelectBlockTask task = instances.get(uuid);
            if (task != null) {
                task.cancel();
            }
        }

        @Override
        public void onPacketReceiving(PacketEvent event) {
            EnumWrappers.EntityUseAction action = event.getPacket().getEntityUseActions().read(0);
            Player player = event.getPlayer();

            // Get task responsible for handling this event
            UUID uuid = player.getUniqueId();
            SelectBlockTask task = instances.get(uuid);
            if (task == null) return;

            // Player left clicked an entity
            if (action == EnumWrappers.EntityUseAction.ATTACK) {
                event.setCancelled(true);
                task.cancel();
                if (task.failure != null) {
                    task.failure.run();
                }
                return;
            }

            // Player right clicked an entity
            if (action == EnumWrappers.EntityUseAction.INTERACT_AT) {
                int maxDistance = 5; // Server should only accept entities within a 4-block radius
                List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, maxDistance);
                if (lastTwoTargetBlocks.size() != 2) return;
                Block targetBlock = lastTwoTargetBlocks.get(1);
                Block adjacentBlock = lastTwoTargetBlocks.get(0);
                if (!targetBlock.getType().isOccluding()) return;

                BlockFace targetBlockFace = targetBlock.getFace(adjacentBlock);
                event.setCancelled(true);
                task.cancel();
                if (task.success != null) {
                    task.success.accept(targetBlock.getLocation(), targetBlockFace);
                }
            }
        }

        @Override
        public void onPacketSending(PacketEvent event) {
            // Intentionally left blank
        }

        @Override
        public ListeningWhitelist getReceivingWhitelist() {
            return ListeningWhitelist.newBuilder().types(PacketType.Play.Client.USE_ENTITY).build();
        }

        @Override
        public ListeningWhitelist getSendingWhitelist() {
            return ListeningWhitelist.EMPTY_WHITELIST;
        }

        @Override
        public Plugin getPlugin() {
            return plugin;
        }
    }
}
