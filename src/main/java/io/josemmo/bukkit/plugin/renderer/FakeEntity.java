package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.netty.manager.NetworkManagerInjector;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import io.josemmo.bukkit.plugin.utils.Logger;
import io.josemmo.bukkit.plugin.utils.MinecraftVersion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Field;

public abstract class FakeEntity {
    private static final Logger LOGGER = Logger.getLogger("FakeEntity");
    private static final ProtocolManager CONNECTION = ProtocolLibrary.getProtocolManager();
    private static @Nullable NetworkManagerInjector NETWORK_MANAGER_INJECTOR;

    static {
        try {
            for (Field field : CONNECTION.getClass().getDeclaredFields()) {
                if (field.getType().equals(NetworkManagerInjector.class)) {
                    field.setAccessible(true);
                    NETWORK_MANAGER_INJECTOR = (NetworkManagerInjector) field.get(CONNECTION);
                    break;
                }
            }
            if (NETWORK_MANAGER_INJECTOR == null) {
                throw new RuntimeException("No valid candidate field found in ProtocolManager");
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to get NetworkManagerInjector from ProtocolLib", e);
        }
    }

    /**
     * Initialize ProtocolLib
     * <p>
     * Fix for ProtocolLib not being ready when the first network packets are being sent by the library.
     * Without calling this method, some packets are missed when the first player joins the server since boot,
     * causing images to not be rendered.
     */
    public static void initialize() {
        LOGGER.fine("Initializing ProtocolLib...");
        try {
            WrappedDataWatcher.Registry.getVectorSerializer();
            LOGGER.fine("ProtocolLib is now ready");
        } catch (Exception e) {
            LOGGER.severe("Failed to initialize ProtocolLib, images may not render for first player joining", e);
        }
    }

    /**
     * Try to send packet
     * @param player Player who will receive the packet
     * @param packet Packet to send
     */
    protected static void tryToSendPacket(@NotNull Player player, @NotNull PacketContainer packet) {
        try {
            if (NETWORK_MANAGER_INJECTOR == null) { // Use single-threaded packet sending if reflection failed
                CONNECTION.sendServerPacket(player, packet);
            } else { // Use non-blocking packet sending if available (faster, the expected case)
                NETWORK_MANAGER_INJECTOR.getInjector(player).sendClientboundPacket(
                    packet.getHandle(),
                    null,
                    false
                );
            }
        } catch (IllegalStateException e) {
            // Server is shutting down and cannot send the packet, ignore
        } catch (Exception e) {
            LOGGER.severe("Failed to send FakeEntity packet", e);
        }
    }

    /**
     * Try to send several packets
     * @param player  Player who will receive the packets
     * @param packets Packets to send
     */
    protected static void tryToSendPackets(@NotNull Player player, @NotNull Iterable<PacketContainer> packets) {
        if (MinecraftVersion.CURRENT.isAtLeast(MinecraftVersion.V1_19_4)) {
            PacketContainer container = new PacketContainer(PacketType.Play.Server.BUNDLE);
            container.getPacketBundles().write(0, packets);
            tryToSendPacket(player, container);
        } else {
            for (PacketContainer packet : packets) {
                tryToSendPacket(player, packet);
            }
        }
    }
}
