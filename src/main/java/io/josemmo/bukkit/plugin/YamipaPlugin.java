package io.josemmo.bukkit.plugin;

import dev.jorel.commandapi.CommandAPI;
import io.josemmo.bukkit.plugin.commands.ImageCommand;
import io.josemmo.bukkit.plugin.renderer.ImageRenderer;
import io.josemmo.bukkit.plugin.storage.ImageStorage;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Path;
import java.util.logging.Level;

public class YamipaPlugin extends JavaPlugin {
    private static YamipaPlugin instance;
    private boolean verbose;
    private ImageStorage storage;
    private ImageRenderer renderer;

    /**
     * Get plugin instance
     * @return Plugin instance
     */
    public static YamipaPlugin getInstance() {
        return instance;
    }

    /**
     * Get image storage instance
     * @return Image storage instance
     */
    public ImageStorage getStorage() {
        return storage;
    }

    /**
     * Get image renderer instance
     * @return Image renderer instance
     */
    public ImageRenderer getRenderer() {
        return renderer;
    }

    @Override
    public void onLoad() {
        instance = this;
        CommandAPI.registerCommand(ImageCommand.class);
    }

    @Override
    public void onEnable() {
        // Initialize logger
        verbose = getConfig().getBoolean("verbose");
        if (verbose) {
            info("Running on VERBOSE mode");
        }

        // Read plugin configuration paths
        Path basePath = getDataFolder().toPath();
        String imagesPath = getConfig().getString("images-path");
        if (imagesPath == null) imagesPath = "images";
        String dataPath = getConfig().getString("data-path");
        if (dataPath == null) dataPath = "images.dat";

        // Create image storage
        storage = new ImageStorage(basePath.resolve(imagesPath).toString());
        try {
            storage.start();
        } catch (Exception e) {
            log(Level.SEVERE, "Failed to initialize image storage", e);
        }

        // Create image renderer
        renderer = new ImageRenderer(basePath.resolve(dataPath).toString());
        renderer.start();
    }

    @Override
    public void onDisable() {
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
        storage.stop();
        renderer.stop();
        storage = null;
        renderer = null;
    }

    /**
     * Log message
     * @param level   Record level
     * @param message Message
     * @param e       Throwable instance, NULL to ignore
     */
    public void log(Level level, String message, Throwable e) {
        // Fix log level
        if (level.intValue() < Level.INFO.intValue()) {
            if (!verbose) return;
            level = Level.INFO;
        }

        // Proxy record to real logger
        if (e == null) {
            getLogger().log(level, message);
        } else {
            getLogger().log(level, message, e);
        }
    }

    /**
     * Log message
     * @param level   Record level
     * @param message Message
     */
    public void log(Level level, String message) {
        log(level, message, null);
    }

    /**
     * Log severe message
     * @param message Message
     */
    public void severe(String message) {
        log(Level.SEVERE, message);
    }

    /**
     * Log warning message
     * @param message Message
     */
    public void warning(String message) {
        log(Level.WARNING, message);
    }

    /**
     * Log info message
     * @param message Message
     */
    public void info(String message) {
        log(Level.INFO, message);
    }

    /**
     * Log fine message
     * @param message Message
     */
    public void fine(String message) {
        log(Level.FINE, message);
    }
}
