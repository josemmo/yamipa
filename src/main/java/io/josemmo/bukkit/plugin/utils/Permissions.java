package io.josemmo.bukkit.plugin.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.internal.platform.WorldGuardPlatform;
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

    public static boolean canEditBlock(@NotNull Player player, @NotNull Location location) {
        // Check WorldGuard flags
        if (worldGuard != null) {
            WorldGuardPlatform platform = worldGuard.getPlatform();
            LocalPlayer wrappedPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
            boolean canEdit = platform.getRegionContainer().createQuery().testBuild(
                BukkitAdapter.adapt(location),
                wrappedPlayer
            );
            boolean canBypass = platform.getSessionManager().hasBypass(
                wrappedPlayer,
                BukkitAdapter.adapt(location.getWorld())
            );
            if (!canEdit && !canBypass) {
                return false;
            }
        }

        // Check GriefPrevention permissions
        if (griefPrevention != null) {
            YamipaPlugin plugin = YamipaPlugin.getInstance();
            Callable<Boolean> canEditCallable = () -> griefPrevention.allowBuild(player, location) == null;
            try {
                Boolean canEdit = Bukkit.isPrimaryThread() ?
                    canEditCallable.call() :
                    Bukkit.getScheduler().callSyncMethod(plugin, canEditCallable).get();
                if (!canEdit) {
                    return false;
                }
            } catch (Exception e) {
                plugin.log(Level.SEVERE, "Failed to get player permissions from GriefPrevention", e);
                return false;
            }
        }

        // Passed all checks, player can edit this block
        return true;
    }
}
