package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.logging.Level;

public abstract class FakeEntity {
    protected static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private static final ProtocolManager connection = ProtocolLibrary.getProtocolManager();
    private static boolean ready = false;

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
            connection.sendServerPacket(player, packet);
        } catch (IllegalStateException e) {
            // Server is shutting down and cannot send the packet, ignore
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to send FakeEntity packet", e);
        }
    }

    /**
     * Try to run asynchronous task
     * @param callback Callback to execute
     */
    protected static void tryToRunAsyncTask(@NotNull Runnable callback) {
        plugin.getScheduler().execute(callback);
    }
}
