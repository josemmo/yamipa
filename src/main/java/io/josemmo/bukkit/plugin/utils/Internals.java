package io.josemmo.bukkit.plugin.utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.comphenix.protocol.reflect.FuzzyReflection;
import com.comphenix.protocol.utility.MinecraftReflection;
import com.mojang.brigadier.CommandDispatcher;

public class Internals {
    private static final Pattern MC_VERSION_PATTERN = Pattern.compile("\\(MC: ([0-9]+(?:\\.[0-9]+){1,2})\\)");

    /**
     * Minecraft version in the form of mmpp (mm is the compatibility major, pp is the compatibility patch).
     * <p>
     * Examples:
     * <li> "1.16" becomes 1600
     * <li> "1.20.3" becomes 2003
     * <li> "1.21.10" becomes 2110
     * <li> "26.1.2" becomes 2601
     * */
    public static final int MINECRAFT_VERSION;
    public static final boolean IS_FOLIA;
    private static final CommandDispatcher<?> DISPATCHER;
    private static final CommandMap COMMAND_MAP;
    private static @Nullable Method GET_BUKKIT_SENDER_METHOD = null;

    static {
        try {
            // Get Minecraft version
            MINECRAFT_VERSION = parseMinecraftVersion(Bukkit.getVersion());

            // Detect Folia
            boolean isFolia;
            try {
                Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
                isFolia = true;
            } catch (ClassNotFoundException __) {
                isFolia = false;
            }
            IS_FOLIA = isFolia;

            // Get "org.bukkit.craftbukkit.CraftServer" references
            Server obcInstance = Bukkit.getServer();
            Class<?> obcClass = obcInstance.getClass();

            // Get "net.minecraft.server.MinecraftServer" references
            Object nmsServerInstance = obcClass.getDeclaredMethod("getServer").invoke(obcInstance);

            // Get "net.minecraft.server.CommandDispatcher" references
            Class<?> nmsDispatcherClass = MinecraftReflection.getMinecraftClass(
            	"CommandDispatcher", // Spigot <1.17
            	"commands.CommandDispatcher", // Spigot >=1.17
            	"commands.Commands" // PaperMC
            );
            Object nmsDispatcherInstance = FuzzyReflection.fromObject(nmsServerInstance, true)
            	.getMethodByReturnTypeAndParameters("getDispatcher", nmsDispatcherClass)
            	.invoke(nmsServerInstance);

            // Get "com.mojang.brigadier.CommandDispatcher" instance
            DISPATCHER = (CommandDispatcher<?>) FuzzyReflection.fromObject(nmsDispatcherInstance, true)
            	.getMethodByReturnTypeAndParameters("getDispatcher", CommandDispatcher.class)
            	.invoke(nmsDispatcherInstance);

            // Get command map instance
            Field commandMapField = obcClass.getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            COMMAND_MAP = (CommandMap) commandMapField.get(obcInstance);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get internal classes due to incompatible Minecraft server", e);
        }
    }

    private static int parseMinecraftVersion(@NotNull String rawVersion) {
        Matcher matcher = MC_VERSION_PATTERN.matcher(rawVersion);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Could not parse Minecraft version from: " + rawVersion);
        }

        String[] parts = matcher.group(1).split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid Minecraft version format: " + matcher.group(1));
        }

        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = (parts.length >= 3) ? Integer.parseInt(parts[2]) : 0;

        // Legacy format was 1.x.y; new Paper builds may expose x.y.z.
        // We keep returning mmpp so the existing compatibility checks remain valid.
        if (major == 1) {
            return minor * 100 + patch;
        }
        return major * 100 + minor;
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
