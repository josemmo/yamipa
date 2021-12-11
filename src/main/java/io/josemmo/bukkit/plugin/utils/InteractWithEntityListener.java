package io.josemmo.bukkit.plugin.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.ListeningWhitelist;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.EnumWrappers;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public abstract class InteractWithEntityListener implements PacketListener {
    public static final int MAX_BLOCK_DISTANCE = 5; // Server should only accept entities within a 4-block radius

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
     * Get listener priority
     * @return Listener priority
     */
    public @NotNull ListenerPriority getPriority() {
        return ListenerPriority.LOWEST;
    }

    /**
     * Register listener
     */
    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
    }

    /**
     * Unregister listener
     */
    public void unregister() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this);
    }

    @Override
    public final void onPacketReceiving(@NotNull PacketEvent event) {
        Player player = event.getPlayer();

        // Discard out-of-range packets
        List<Block> lastTwoTargetBlocks = player.getLastTwoTargetBlocks(null, MAX_BLOCK_DISTANCE);
        if (lastTwoTargetBlocks.size() != 2) return;
        Block targetBlock = lastTwoTargetBlocks.get(1);
        if (!targetBlock.getType().isOccluding()) return;

        // Get target block face
        Block adjacentBlock = lastTwoTargetBlocks.get(0);
        BlockFace targetBlockFace = targetBlock.getFace(adjacentBlock);
        if (targetBlockFace == null) return;

        // Get action
        EnumWrappers.EntityUseAction action;
        if (Internals.MINECRAFT_VERSION < 17) {
            action = event.getPacket().getEntityUseActions().read(0);
        } else {
            action = event.getPacket().getEnumEntityUseActions().read(0).getAction();
        }

        // Notify handler
        boolean allowEvent = true;
        if (action == EnumWrappers.EntityUseAction.ATTACK) {
            allowEvent = onAttack(player, targetBlock, targetBlockFace);
        } else if (action == EnumWrappers.EntityUseAction.INTERACT_AT) {
            allowEvent = onInteract(player, targetBlock, targetBlockFace);
        }

        // Cancel event (if needed)
        if (!allowEvent) {
            event.setCancelled(true);
        }
    }

    @Override
    public final void onPacketSending(PacketEvent event) {
        // Intentionally left blank
    }

    @Override
    public final ListeningWhitelist getReceivingWhitelist() {
        return ListeningWhitelist.newBuilder()
            .priority(getPriority())
            .types(PacketType.Play.Client.USE_ENTITY)
            .build();
    }

    @Override
    public final ListeningWhitelist getSendingWhitelist() {
        return ListeningWhitelist.EMPTY_WHITELIST;
    }

    @Override
    public final Plugin getPlugin() {
        return YamipaPlugin.getInstance();
    }
}
