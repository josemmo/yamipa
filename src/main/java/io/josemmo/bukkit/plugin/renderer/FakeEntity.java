package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.injector.player.PlayerInjectionHandler;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.utils.Internals;
import io.josemmo.bukkit.plugin.utils.Logger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.Field;

public abstract class FakeEntity {
    private static final Logger LOGGER = Logger.getLogger("FakeEntity");
    private static final ProtocolManager connection = ProtocolLibrary.getProtocolManager();
    private static PlayerInjectionHandler playerInjectionHandler = null;
    private static boolean ready = false;

    static {
        try {
            for (Field field : connection.getClass().getDeclaredFields()) {
                if (field.getType().equals(PlayerInjectionHandler.class)) {
                    field.setAccessible(true);
                    playerInjectionHandler = (PlayerInjectionHandler) field.get(connection);
                    break;
                }
            }
            if (playerInjectionHandler == null) {
                throw new RuntimeException("No valid candidate field found in ProtocolManager");
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to get PlayerInjectionHandler from ProtocolLib", e);
        }
    }

    /**
     * Wait for ProtocolLib to be ready
     * <p>
     * NOTE: Will wait synchronously, blocking the invoker thread
     */
    public static synchronized void waitForProtocolLib() {
        if (ready) {
            // ProtocolLib is ready
            return;
        }

        int retry = 0;
        while (true) {
            try {
                WrappedDataWatcher.Registry.get(Byte.class);
                ready = true;
                break;
            } catch (Exception e) {
                if (++retry > 20) {
                    // Exhausted max. retries
                    throw e;
                }
                tryToSleep(200);
            }
        }
    }

    /**
     * Try to sleep
     * <p>
     * NOTE: Will wait synchronously, blocking the invoker thread
     * @param ms Delay in milliseconds
     */
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
            if (playerInjectionHandler == null) { // Use single-threaded packet sending if reflection failed
                connection.sendServerPacket(player, packet);
            } else { // Use non-blocking packet sending if available (faster, the expected case)
                playerInjectionHandler.sendServerPacket(player, packet, null, false);
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
        if (Internals.MINECRAFT_VERSION < 19.4f) {
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
