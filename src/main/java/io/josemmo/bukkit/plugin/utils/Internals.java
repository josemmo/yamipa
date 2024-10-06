package io.josemmo.bukkit.plugin.utils;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.ProtocolVersion;
import com.github.retrooper.packetevents.util.PEVersion;
import com.mojang.brigadier.CommandDispatcher;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Internals {
    public static final ServerVersion MINECRAFT_VERSION;
    private static final CommandDispatcher<?> DISPATCHER;
    private static final CommandMap COMMAND_MAP;
    private static @Nullable Method GET_BUKKIT_SENDER_METHOD = null;

    static {
        try {
            // Get Minecraft version
            MINECRAFT_VERSION = PacketEvents.getAPI().getServerManager().getVersion();

            // Get "org.bukkit.craftbukkit.CraftServer" references
            Server obcInstance = Bukkit.getServer();
            Class<?> obcClass = obcInstance.getClass();

            // Get "net.minecraft.server.MinecraftServer" references
            Object nmsInstance = obcClass.getDeclaredMethod("getServer").invoke(obcInstance);
            Class<?> nmsClass = nmsInstance.getClass().getSuperclass();

            if (MINECRAFT_VERSION.isNewerThanOrEquals(ServerVersion.V_1_20_5)) {
                // Get "net.minecraft.commands.Commands" references
                Object nmsCommandsInstance = nmsClass.getDeclaredMethod("getCommands").invoke(nmsInstance);
                Class<?> nmsCommandsClass = nmsCommandsInstance.getClass();

                // Get "com.mojang.brigadier.CommandDispatcher" instance
                Field nmsDispatcherField = nmsCommandsClass.getDeclaredField("dispatcher");
                nmsDispatcherField.setAccessible(true);
                DISPATCHER = (CommandDispatcher<?>) nmsDispatcherField.get(nmsCommandsInstance);
            } else {
                // Get "net.minecraft.server.CommandDispatcher" references
                Object nmsDispatcherInstance = nmsClass.getDeclaredField("vanillaCommandDispatcher").get(nmsInstance);
                Class<?> nmsDispatcherClass = nmsDispatcherInstance.getClass();

                // Get "com.mojang.brigadier.CommandDispatcher" instance
                Method getDispatcherMethod = nmsDispatcherClass.getDeclaredMethod("a");
                getDispatcherMethod.setAccessible(true);
                DISPATCHER = (CommandDispatcher<?>) getDispatcherMethod.invoke(nmsDispatcherInstance);
            }

            // Get command map instance
            Field commandMapField = obcClass.getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            COMMAND_MAP = (CommandMap) commandMapField.get(obcInstance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get internal classes due to incompatible Minecraft server", e);
        }
    }

    /**
     * Get Brigadier command dispatcher instance
     * @return Command dispatcher instance
     */
    public static @NotNull CommandDispatcher<?> getDispatcher() {
        return DISPATCHER;
    }

    /**
     * Get Bukkit command map instance
     * @return Command map instance
     */
    public static @NotNull CommandMap getCommandMap() {
        return COMMAND_MAP;
    }

    /**
     * Get Bukkit sender from Brigadier context source
     * @param  source Brigadier command context source
     * @return        Command sender instance
     */
    public static @NotNull CommandSender getBukkitSender(@NotNull Object source) {
        try {
            if (GET_BUKKIT_SENDER_METHOD == null) {
                GET_BUKKIT_SENDER_METHOD = source.getClass().getDeclaredMethod("getBukkitSender");
            }
            return (CommandSender) GET_BUKKIT_SENDER_METHOD.invoke(source);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract Bukkit sender from source", e);
        }
    }
}
