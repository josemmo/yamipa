package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.netty.manager.NetworkManagerInjector;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.utils.Internals;
import io.josemmo.bukkit.plugin.utils.Logger;
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
     * Try to sleep
     * <p>
     * NOTE: Will wait synchronously, blocking the invoker thread
     * @param ms Delay in milliseconds
     */
    @SuppressWarnings("SameParameterValue")
    protected static void tryToSleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Exception __) {
            // Fail silently
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
        if (Internals.MINECRAFT_VERSION < 1904) {
            for (PacketContainer packet : packets) {
                tryToSendPacket(player, packet);
            }
        } else {
            PacketContainer container = new PacketContainer(PacketType.Play.Server.BUNDLE);
            container.getPacketBundles().write(0, packets);
            tryToSendPacket(player, container);
        }
    }

    /**
     * Try to run asynchronous task
     * @param callback Callback to execute
     */
    protected static void tryToRunAsyncTask(@NotNull Runnable callback) {
        YamipaPlugin.getInstance().getScheduler().execute(callback);
    }
}
