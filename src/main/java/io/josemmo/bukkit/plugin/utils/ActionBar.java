package io.josemmo.bukkit.plugin.utils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.logging.Level;

public class ActionBar {
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();

    /**
     * Send action bar message
     * @param player  Player who will receive the message
     * @param message Message to send
     */
    public static void send(Player player, String message) {
        PacketContainer titlePacket = new PacketContainer(PacketType.Play.Server.TITLE);
        titlePacket.getTitleActions().write(0, EnumWrappers.TitleAction.ACTIONBAR);
        titlePacket.getChatComponents().write(0, WrappedChatComponent.fromText(message));
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, titlePacket);
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to send ActionBar to " + player.getName(), e);
        }
    }

    /**
     * Keep sending action bar message
     * @param  player  Player who will receive the message
     * @param  message Message to send
     * @return         New task instance
     */
    public static BukkitTask repeat(Player player, String message) {
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> ActionBar.send(player, message), 0L, 40L);
    }
}
