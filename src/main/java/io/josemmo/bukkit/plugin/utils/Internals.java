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
    /**
     * Minecraft version in a normalized integer format suitable for range comparisons.
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
    private static final Pattern LEGACY_VERSION_PATTERN = Pattern.compile("\\(MC: ([0-9]+(?:\\.[0-9]+){1,2})(?:\\s+[^)]*)?\\)");

    static {
        try {
            // Get Minecraft version
            MINECRAFT_VERSION = getNormalizedMinecraftVersion();

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

    /**
     * Get Minecraft version in normalized integer format
     * @return Minecraft version
     */
    private static int getNormalizedMinecraftVersion() throws ReflectiveOperationException {
        String version;

        // Modern Bukkit/Paper exposes the game version directly
        try {
            Method getMinecraftVersionMethod = Server.class.getMethod("getMinecraftVersion");
            version = (String) getMinecraftVersionMethod.invoke(Bukkit.getServer());
        } catch (NoSuchMethodException __) {
            version = null;
        }

        // Fallback to parsing the implementation version string for older servers
        if (version == null || version.isEmpty()) {
            Matcher matcher = LEGACY_VERSION_PATTERN.matcher(Bukkit.getVersion());
            if (!matcher.find()) {
                throw new IllegalStateException("Could not determine Minecraft version from: " + Bukkit.getVersion());
            }
            version = matcher.group(1);
        }

        String[] parts = version.split("\\.");
        if (parts.length < 2) {
            throw new IllegalStateException("Unsupported Minecraft version format: " + version);
        }

        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = (parts.length >= 3) ? Integer.parseInt(parts[2]) : 0;

        // Legacy releases used the fixed "1.x.y" format. Newer releases use "<year>.<drop>[.<hotfix>]".
        // Existing packet guards only care about the primary release line, so modern hotfix numbers are ignored.
        return (major == 1) ? minor * 100 + patch : major * 100 + minor;
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
