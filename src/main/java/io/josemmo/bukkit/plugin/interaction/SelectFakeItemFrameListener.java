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
import io.josemmo.bukkit.plugin.utils.Internals;
import io.josemmo.bukkit.plugin.utils.Logger;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.BlockIterator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class SelectFakeItemFrameListener implements PacketListener {
    private static final int MAX_BLOCK_DISTANCE = 5;
    private static final Logger LOGGER = Logger.getLogger("SelectFakeItemFrameListener");
    private static boolean hasWarnedActionFallback = false;
    private static boolean hasWarnedEntityIdFallback = false;

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
        Player player = event.getPlayer();

        // Ignore events for "real" in-game entities
        Integer entityId = getEntityId(event);
        if (entityId != null && entityId < FakeItemFrame.MIN_FRAME_ID) {
            return;
        }

        // When entity ID cannot be read (protocol wrapper drift), only continue
        // if player is actually aiming at one of our fake images.
        if (entityId == null && getFakeImage(player) == null) {
            return;
        }

        // Get action
        EnumWrappers.EntityUseAction action = getEntityUseAction(event);

        // Handle event
        boolean allowEvent = true;
        if (action == EnumWrappers.EntityUseAction.ATTACK) {
            allowEvent = onLeftClick(player, (entityId != null) ? entityId : -1);
        } else if (action == EnumWrappers.EntityUseAction.INTERACT_AT || action == EnumWrappers.EntityUseAction.INTERACT) {
            allowEvent = onRightClick(player, (entityId != null) ? entityId : -1);
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

    private @NotNull EnumWrappers.EntityUseAction getEntityUseAction(@NotNull PacketEvent event) {
        if (Internals.MINECRAFT_VERSION < 1700) {
            try {
                EnumWrappers.EntityUseAction action = event.getPacket().getEntityUseActions().read(0);
                if (action != null) {
                    return action;
                }
            } catch (Throwable __) {
                // Continue with fallback parser
            }

            warnActionFallback("Failed to parse legacy USE_ENTITY action directly, using inferred action");
            return inferEntityUseAction(event);
        }

        Throwable primaryError = null;
        try {
            // Prefer this accessor first: it's the most stable across ProtocolLib versions.
            EnumWrappers.EntityUseAction action = event.getPacket().getEntityUseActions().read(0);
            if (action != null) {
                return action;
            }
        } catch (Throwable e) {
            primaryError = e;
        }

        EnumWrappers.EntityUseAction inferredAction = inferEntityUseAction(event);
        if (primaryError != null) {
            warnActionFallback("Failed to parse USE_ENTITY action directly, using inferred action", primaryError);
        }
        return inferredAction;
    }

    private @Nullable Integer getEntityId(@NotNull PacketEvent event) {
        try {
            // Modern versions expose the target entity as a VarInt in this modifier.
            return event.getPacket().getIntegers().read(0);
        } catch (Throwable e) {
            warnEntityIdFallback("Failed to read USE_ENTITY target entity ID from packet", e);
            return null;
        }
    }

    private @NotNull EnumWrappers.EntityUseAction inferEntityUseAction(@NotNull PacketEvent event) {
        // 1) ATTACK packets do not carry a hand value
        try {
            if (event.getPacket().getHands().readSafely(0) == null) {
                return EnumWrappers.EntityUseAction.ATTACK;
            }
        } catch (Throwable __) {
            // Ignore and continue with the next heuristic
        }

        // 2) INTERACT_AT carries a target position vector
        try {
            if (event.getPacket().getVectors().readSafely(0) != null) {
                return EnumWrappers.EntityUseAction.INTERACT_AT;
            }
        } catch (Throwable __) {
            // Ignore and continue with the next heuristic
        }

        // 3) Remaining handled interactions are treated as INTERACT
        return EnumWrappers.EntityUseAction.INTERACT;
    }

    private void warnActionFallback(@NotNull String message, @NotNull Throwable e) {
        if (!hasWarnedActionFallback) {
            hasWarnedActionFallback = true;
            LOGGER.warning(message, e);
        }
    }

    private void warnActionFallback(@NotNull String message) {
        if (!hasWarnedActionFallback) {
            hasWarnedActionFallback = true;
            LOGGER.warning(message);
        }
    }

    private void warnEntityIdFallback(@NotNull String message, @NotNull Throwable e) {
        if (!hasWarnedEntityIdFallback) {
            hasWarnedEntityIdFallback = true;
            LOGGER.warning(message, e);
        }
    }

}
