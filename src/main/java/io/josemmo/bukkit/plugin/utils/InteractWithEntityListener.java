package io.josemmo.bukkit.plugin.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.*;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientInteractEntity;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public abstract class InteractWithEntityListener implements PacketListener {
    public static final int MAX_BLOCK_DISTANCE = 5; // Server should only accept entities within a 4-block radius
    private static final Logger LOGGER = Logger.getLogger("InteractWithEntityListener");

    private PacketListenerCommon registeredListener;

    /**
     * On player attack listener
     * @param  player Initiating player
     * @param  block  Target block
     * @param  face   Target block face
     * @return        Whether to allow original event (<code>true</code>) or not (<code>false</code>)
     */
    public abstract boolean onAttack(@NotNull Player player, @NotNull Block block, @NotNull BlockFace face);

    /**
     * On player interact listener
     * @param  player Initiating player
     * @param  block  Target block
     * @param  face   Target block face
     * @return        Whether to allow original event (<code>true</code>) or not (<code>false</code>)
     */
    public abstract boolean onInteract(@NotNull Player player, @NotNull Block block, @NotNull BlockFace face);


    /**
     * Register listener
     */
    public void register() {
        registeredListener = PacketEvents.getAPI().getEventManager().registerListener(this, PacketListenerPriority.LOWEST);
    }

    public void register(PacketListenerPriority priority) {
        registeredListener = PacketEvents.getAPI().getEventManager().registerListener(this, priority);
    }

    /**
     * Unregister listener
     */
    public void unregister() {
        if(registeredListener == null) return;
        PacketEvents.getAPI().getEventManager().unregisterListener(registeredListener);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if(event.getPacketType() != PacketType.Play.Client.INTERACT_ENTITY) return;
        WrapperPlayClientInteractEntity packet = new WrapperPlayClientInteractEntity(event);

        Player player = event.getPlayer();

        // Discard out-of-range packets
        List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, MAX_BLOCK_DISTANCE);
        if (lastTwoTargetBlocks.size() != 2) return;
        Block targetBlock = lastTwoTargetBlocks.get(1);
        if (!targetBlock.getType().isSolid()) return;

        // Get target block face
        Block adjacentBlock = lastTwoTargetBlocks.get(0);
        BlockFace targetBlockFace = targetBlock.getFace(adjacentBlock);
        if (targetBlockFace == null) return;

        // Get action
        WrapperPlayClientInteractEntity.InteractAction action = packet.getAction();

        // Notify handler synchronously
        boolean allowEvent = true;
        try {
            if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                allowEvent = onAttack(player, targetBlock, targetBlockFace);
            } else if (action == WrapperPlayClientInteractEntity.InteractAction.INTERACT_AT) {
                allowEvent = onInteract(player, targetBlock, targetBlockFace);
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to notify entity listener handler", e);
        }

        // Cancel event (if needed)
        if (!allowEvent) {
            event.setCancelled(true);
        }
    }

    @Override
    public final void onPacketSend(PacketSendEvent event) {
        // Intentionally left blank
    }
}
