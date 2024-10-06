package io.josemmo.bukkit.plugin.renderer;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.player.User;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBundle;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.utils.Internals;
import io.josemmo.bukkit.plugin.utils.Logger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

public abstract class FakeEntity {
    private static final Logger LOGGER = Logger.getLogger("FakeEntity");
    private static boolean READY = false;


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
    protected static void tryToSendPacket(@NotNull Player player, @NotNull PacketWrapper<?> packet) {
        try {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
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
    protected static void tryToSendPackets(@NotNull Player player, @NotNull Iterable<PacketWrapper<?>> packets) {
        if (Internals.MINECRAFT_VERSION.isOlderThan(ServerVersion.V_1_19_4)) {
            System.out.println("Version older than 1.19");
            for (PacketWrapper<?> packet : packets) {
                tryToSendPacket(player, packet);
            }
        } else {
            User user = PacketEvents.getAPI().getPlayerManager().getUser(player);
            for (PacketWrapper<?> packet : packets) {
                user.writePacket(packet);
            }
            user.flushPackets();
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
