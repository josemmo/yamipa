package io.josemmo.bukkit.plugin.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.concurrent.Callable;
import java.util.logging.Level;

public class Permissions {
    @Nullable private static WorldGuard worldGuard = null;
    @Nullable private static GriefPrevention griefPrevention = null;

    static {
        try {
            worldGuard = WorldGuard.getInstance();
        } catch (NoClassDefFoundError __) {
            // WorldGuard is not installed
        }

        try {
            griefPrevention = GriefPrevention.instance;
        } catch (NoClassDefFoundError __) {
            // GriefPrevention is not installed
        }
    }

    /**
     * Can build at this block
     * @param  player   Player instance
     * @param  location Block location
     * @return          Whether player can build or not
     */
    public static boolean canBuild(@NotNull Player player, @NotNull Location location) {
        return queryWorldGuard(player, location, Flags.BLOCK_PLACE)
            && queryGriefPrevention(player, location, true);
    }

    /**
     * Can destroy this block
     * @param  player   Player instance
     * @param  location Block location
     * @return          Whether player can destroy or not
     */
    public static boolean canDestroy(@NotNull Player player, @NotNull Location location) {
        return queryWorldGuard(player, location, Flags.BLOCK_BREAK)
            && queryGriefPrevention(player, location, false);
    }

    private static boolean queryWorldGuard(@NotNull Player player, @NotNull Location location, @NotNull StateFlag flag) {
        if (worldGuard == null) {
            return true;
        }
        WorldGuardPlatform platform = worldGuard.getPlatform();
        LocalPlayer wrappedPlayer = WorldGuardPlugin.inst().wrapPlayer(player);

        // Grant if bypass permission is enabled
        boolean hasBypass = platform.getSessionManager().hasBypass(
            wrappedPlayer,
            BukkitAdapter.adapt(location.getWorld())
        );
        if (hasBypass) {
            return true;
        }

        // Check permission (note "BUILD" flag must always be present)
        return platform.getRegionContainer().createQuery()
            .testState(BukkitAdapter.adapt(location), wrappedPlayer, Flags.BUILD, flag);
    }

    private static boolean queryGriefPrevention(@NotNull Player player, @NotNull Location location, boolean isBuild) {
        if (griefPrevention == null) {
            return true;
        }
        YamipaPlugin plugin = YamipaPlugin.getInstance();

        // Build callable depending on permission to check
        Callable<Boolean> canEditCallable = isBuild ?
            () -> griefPrevention.allowBuild(player, location) == null :
            () -> griefPrevention.allowBreak(player, location.getBlock(), location) == null;

        // Check permission from primary thread
        try {
            return Bukkit.isPrimaryThread() ?
                canEditCallable.call() :
                Bukkit.getScheduler().callSyncMethod(plugin, canEditCallable).get();
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to get player permissions from GriefPrevention", e);
            return false;
        }
    }
}
