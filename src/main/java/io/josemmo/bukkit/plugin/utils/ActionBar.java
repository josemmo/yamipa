package io.josemmo.bukkit.plugin.utils;

import com.comphenix.protocol.ProtocolLibrary;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.packets.ActionBarPacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import java.util.logging.Level;

public class ActionBar {
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();

    /**
     * Send action bar message
     * @param player  Player who will receive the message
     * @param message Message to send
     */
    public static void send(@NotNull Player player, @NotNull String message) {
        ActionBarPacket actionBarPacket = new ActionBarPacket();
        actionBarPacket.setText(message);
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, actionBarPacket);
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
    public static @NotNull BukkitTask repeat(@NotNull Player player, @NotNull String message) {
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> ActionBar.send(player, message), 0L, 40L);
    }
}
