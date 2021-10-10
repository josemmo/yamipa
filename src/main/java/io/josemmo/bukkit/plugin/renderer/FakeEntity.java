package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.jetbrains.annotations.NotNull;
import java.util.logging.Level;

public abstract class FakeEntity {
    protected static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private static final ProtocolManager connection = ProtocolLibrary.getProtocolManager();

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
        try {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, callback);
        } catch (IllegalPluginAccessException e) {
            // Server is shutting down and is not accepting more tasks, ignore
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to run async task", e);
        }
    }
}
