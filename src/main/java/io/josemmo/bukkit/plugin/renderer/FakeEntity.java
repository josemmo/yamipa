package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.bukkit.entity.Player;
import java.util.logging.Level;

public abstract class FakeEntity {
    protected static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private static final ProtocolManager connection = ProtocolLibrary.getProtocolManager();

    /**
     * Try to send packet
     * @param player Player who will receive the packet
     * @param packet Packet to send
     */
    protected static void tryToSendPacket(Player player, PacketContainer packet) {
        try {
            connection.sendServerPacket(player, packet);
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to send FakeEntity packet", e);
        }
    }
}
