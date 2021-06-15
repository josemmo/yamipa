package io.josemmo.bukkit.plugin.utils;

import com.mojang.brigadier.CommandDispatcher;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class Internals {
    private static final CommandDispatcher<?> DISPATCHER;
    private static final CommandMap COMMAND_MAP;
    private static final Method GET_BUKKIT_SENDER_METHOD;

    static {
        try {
            // Get "org.bukkit.craftbukkit.CraftServer" references
            Server obcInstance = Bukkit.getServer();
            Class<?> obcClass = obcInstance.getClass();

            // Get "net.minecraft.server.MinecraftServer" references
            Object nmsInstance = obcClass.getDeclaredMethod("getServer").invoke(obcInstance);
            Class<?> nmsClass = nmsInstance.getClass().getSuperclass();

            // Get "net.minecraft.server.CommandDispatcher" references
            Object nmsDispatcherInstance = nmsClass.getDeclaredMethod("getCommandDispatcher").invoke(nmsInstance);
            Class<?> nmsDispatcherClass = nmsDispatcherInstance.getClass();

            // Get Brigadier dispatcher instance
            Method getDispatcherMethod = nmsDispatcherClass.getDeclaredMethod("a");
            getDispatcherMethod.setAccessible(true);
            DISPATCHER = (CommandDispatcher<?>) getDispatcherMethod.invoke(nmsDispatcherInstance);

            // Get command map instance
            Field commandMapField = obcClass.getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            COMMAND_MAP = (CommandMap) commandMapField.get(obcInstance);

            // Get CommandListenerWrapper.getBukkitSender() method
            Class<?> clwClass = Class.forName(nmsDispatcherClass.getPackage().getName() + ".CommandListenerWrapper");
            GET_BUKKIT_SENDER_METHOD = clwClass.getDeclaredMethod("getBukkitSender");
            GET_BUKKIT_SENDER_METHOD.setAccessible(true);
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
            return (CommandSender) GET_BUKKIT_SENDER_METHOD.invoke(source);
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract bukkit sender from source", e);
        }
    }
}
