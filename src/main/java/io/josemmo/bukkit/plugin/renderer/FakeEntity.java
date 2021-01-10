package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketContainer;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.bukkit.entity.Player;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class FakeEntity {
    protected static final Logger logger = YamipaPlugin.getInstance().getLogger();
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
            logger.log(Level.SEVERE, "Failed to send FakeEntity packet", e);
        }
    }
}
