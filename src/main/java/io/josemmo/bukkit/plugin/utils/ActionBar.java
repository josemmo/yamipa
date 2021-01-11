package io.josemmo.bukkit.plugin.utils;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class ActionBar {
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();

    /**
     * Send action bar message
     * @param player  Player who will receive the message
     * @param message Message to send
     */
    public static void send(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(message));
    }

    /**
     * Keep sending action bar message
     * @param  player  Player who will receive the message
     * @param  message Message to send
     * @return         New task instance
     */
    public static BukkitTask repeat(Player player, String message) {
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            ActionBar.send(player, message);
        }, 0L, 40L);
    }
}
