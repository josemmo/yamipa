package io.josemmo.bukkit.plugin.interaction;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.ListeningWhitelist;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.events.PacketListener;
import com.comphenix.protocol.wrappers.EnumWrappers;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.renderer.FakeItemFrame;
import io.josemmo.bukkit.plugin.renderer.ImageRenderer;
import io.josemmo.bukkit.plugin.utils.MinecraftVersion;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SelectFakeItemFrameListener implements PacketListener {
    private static final int MAX_BLOCK_DISTANCE = 5;

    /**
     * Get fake image instance for player
     * @param  player Player pointing at the image
     * @return        Fake image instance or <code>null</code> if not found
     */
    public static @Nullable FakeImage getFakeImage(@NotNull Player player) {
        ImageRenderer renderer = YamipaPlugin.getInstance().getRenderer();

        // Get the closest fake image within player's line of sight
        BlockIterator iterator = new BlockIterator(player, MAX_BLOCK_DISTANCE);
        Block previousBlock = null;
        while (iterator.hasNext()) {
            Block currentBlock = iterator.next();
            if (previousBlock != null) {
                BlockFace currentBlockFace = currentBlock.getFace(previousBlock);
                if (currentBlockFace != null) {
                    FakeImage image = renderer.getImage(currentBlock.getLocation(), currentBlockFace);
                    if (image != null) {
                        return image;
                    }
                }
            }
            previousBlock = currentBlock;
        }

        // No fake image found
        return null;
    }

    /**
     * On left click (attack)
     * @param  player   Initiating player
     * @param  entityId Entity ID
     * @return          Whether to allow original event or not
     */
    protected abstract boolean onLeftClick(@NotNull Player player, int entityId);

    /**
     * On right click (interact)
     * @param  player   Initiating player
     * @param  entityId Entity ID
     * @return          Whether to allow original event or not
     */
    protected abstract boolean onRightClick(@NotNull Player player, int entityId);

    /**
     * Get listener priority
     * @return Listener priority
     */
    protected abstract @NotNull ListenerPriority getPriority();

    /**
     * Register listener
     */
    public final void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(this);
    }

    /**
     * Unregister listener
     */
    public final void unregister() {
        ProtocolLibrary.getProtocolManager().removePacketListener(this);
    }

    @Override
    public final void onPacketReceiving(@NotNull PacketEvent event) {
        // Ignore events for "real" in-game entities
        int entityId = event.getPacket().getIntegers().read(0);
        if (entityId < FakeItemFrame.MIN_FRAME_ID) {
            return;
        }

        // Get action
        EnumWrappers.EntityUseAction action;
        if (MinecraftVersion.CURRENT.isAtLeast(MinecraftVersion.V1_17)) {
            action = event.getPacket().getEnumEntityUseActions().read(0).getAction();
        } else {
            action = event.getPacket().getEntityUseActions().read(0);
        }

        // Handle event
        boolean allowEvent = true;
        Player player =  event.getPlayer();
        if (action == EnumWrappers.EntityUseAction.ATTACK) {
            allowEvent = onLeftClick(player, entityId);
        } else if (action == EnumWrappers.EntityUseAction.INTERACT_AT || action == EnumWrappers.EntityUseAction.INTERACT) {
            allowEvent = onRightClick(player, entityId);
        }

        // Cancel event (if needed)
        if (!allowEvent) {
            event.setCancelled(true);
        }
    }

    @Override
    public final void onPacketSending(@NotNull PacketEvent event) {
        // Intentionally left blank
    }

    @Override
    public final @NotNull ListeningWhitelist getReceivingWhitelist() {
        return ListeningWhitelist.newBuilder()
            .priority(getPriority())
            .types(PacketType.Play.Client.USE_ENTITY)
            .build();
    }

    @Override
    public final @NotNull ListeningWhitelist getSendingWhitelist() {
        return ListeningWhitelist.EMPTY_WHITELIST;
    }

    @Override
    public final @NotNull Plugin getPlugin() {
        return YamipaPlugin.getInstance();
    }
}
