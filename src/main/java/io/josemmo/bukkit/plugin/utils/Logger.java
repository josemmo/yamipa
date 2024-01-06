package io.josemmo.bukkit.plugin.utils;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.logging.Level;

/**
 * Custom logging class used by components from the plugin.
 * It is <i>instance agnostic</i>, that is, the same logger instance will work regardless of whether the plugin
 * instance has changed (for example, because of the plugin being restarted by a plugin manager).
 */
public class Logger {
    private final @Nullable String name;

    /**
     * Get logger instance
     * @param  name Logger name
     * @return      Logger instance
     */
    public static @NotNull Logger getLogger(@NotNull String name) {
        return new Logger(name);
    }

    /**
     * Get logger instance
     * @return Logger instance
     */
    public static @NotNull Logger getLogger() {
        return new Logger(null);
    }

    /**
     * Class constructor
     * @param name Optional logger name
     */
    private Logger(@Nullable String name) {
        this.name = name;
    }

    /**
     * Log message
     * @param level   Record level
     * @param message Message
     * @param e       Optional throwable to log
     */
    private void log(@NotNull Level level, @NotNull String message, @Nullable Throwable e) {
        YamipaPlugin plugin = YamipaPlugin.getInstance();

        // Handle verbose logging levels
        if (level.intValue() < Level.INFO.intValue()) {
            if (!plugin.isVerbose()) return;
            level = Level.INFO;
        }

        // Add logger name to message
        if (name != null) {
            message = "[" + name + "] " + message;
        }

        // Proxy record to real logger
        if (e == null) {
            plugin.getLogger().log(level, message);
        } else {
            plugin.getLogger().log(level, message, e);
        }
    }

    /**
     * Log severe message
     * @param message Message
     * @param e       Throwable to log
     */
    public void severe(@NotNull String message, @NotNull Throwable e) {
        log(Level.SEVERE, message, e);
    }

    /**
     * Log warning message
     * @param message Message
     * @param e       Throwable to log
     */
    public void warning(@NotNull String message, @NotNull Throwable e) {
        log(Level.WARNING, message, e);
    }

    /**
     * Log warning message
     * @param message Message
     */
    public void warning(@NotNull String message) {
        log(Level.WARNING, message, null);
    }

    /**
     * Log info message
     * @param message Message
     */
    public void info(@NotNull String message) {
        log(Level.INFO, message, null);
    }

    /**
     * Log fine message
     * @param message Message
     */
    public void fine(@NotNull String message) {
        log(Level.FINE, message, null);
    }
}
