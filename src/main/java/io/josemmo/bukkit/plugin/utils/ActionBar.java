package io.josemmo.bukkit.plugin.utils;

import com.comphenix.protocol.ProtocolLibrary;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.packets.ActionBarPacket;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.concurrent.ScheduledFuture;

public class ActionBar {
    private static final Logger LOGGER = Logger.getLogger("ActionBar");
    private final Player player;
    private String message;
    private @Nullable ScheduledFuture<?> task = null;

    private ActionBar(@NotNull Player player, @NotNull String message) {
        this.player = player;
        this.message = message;
    }

    /**
     * Send action bar message
     * @param player  Player who will receive the message
     * @param message Message to send
     */
    public static void send(@NotNull Player player, @NotNull String message) {
        ActionBar instance = new ActionBar(player, message);
        instance.sendOnce();
    }

    /**
     * Keep sending action bar message
     * @param  player  Player who will receive the message
     * @param  message Message to send
     * @return         New instance
     */
    public static @NotNull ActionBar repeat(@NotNull Player player, @NotNull String message) {
        ActionBar instance = new ActionBar(player, message);
        instance.start();
        return instance;
    }

    /**
     * Set message
     * @param  message Message
     * @return         This instance
     */
    public ActionBar setMessage(@NotNull String message) {
        this.message = message;
        return sendOnce();
    }

    /**
     * Clear message
     * @return This instance
     */
    public ActionBar clear() {
        message = "";
        stop();
        return sendOnce();
    }

    /**
     * Send message once
     * @return This instance
     */
    public ActionBar sendOnce() {
        ActionBarPacket actionBarPacket = new ActionBarPacket();
        actionBarPacket.setText(message);
        try {
            ProtocolLibrary.getProtocolManager().sendServerPacket(player, actionBarPacket);
        } catch (Exception e) {
            LOGGER.severe("Failed to send ActionBar to " + player.getName(), e);
        }
        return this;
    }

    /**
     * Start sending message indefinitely
     * @return This instance
     */
    public ActionBar start() {
        if (task == null) {
            task = YamipaPlugin.getInstance().getScheduler().runInterval(this::sendOnce, 0, 2000);
        }
        return this;
    }

    /**
     * Stop sending message indefinitely
     * @return This instance
     */
    public ActionBar stop() {
        if (task != null) {
            task.cancel(true);
            task = null;
        }
        return this;
    }
}
