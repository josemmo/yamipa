package io.josemmo.bukkit.plugin;

import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIConfig;
import io.josemmo.bukkit.plugin.commands.ImageCommandBridge;
import io.josemmo.bukkit.plugin.renderer.ImageRenderer;
import io.josemmo.bukkit.plugin.storage.ImageStorage;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.logging.Level;

public class YamipaPlugin extends JavaPlugin {
    public static final int BSTATS_PLUGIN_ID = 10243;
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

    /**
     * Get configuration value
     * @param path         Configuration key path
     * @param defaultValue Default value
     * @return             Configuration value
     */
    private String getConfigValue(String path, String defaultValue) {
        String value = getConfig().getString(path);
        return (value == null) ? defaultValue : value;
    }

    @Override
    public void onLoad() {
        instance = this;
        CommandAPI.onLoad(new CommandAPIConfig());
        ImageCommandBridge.register();
    }

    @Override
    public void onEnable() {
        CommandAPI.onEnable(this);

        // Initialize logger
        verbose = getConfig().getBoolean("verbose");
        if (verbose) {
            info("Running on VERBOSE mode");
        }

        // Read plugin configuration paths
        Path basePath = getDataFolder().toPath();
        String imagesPath = getConfigValue("images-path", "images");
        String cachePath = getConfigValue("cache-path", "cache");
        String dataPath = getConfigValue("data-path", "images.dat");

        // Create image storage
        storage = new ImageStorage(
            basePath.resolve(imagesPath).toString(),
            basePath.resolve(cachePath).toString()
        );
        try {
            storage.start();
        } catch (Exception e) {
            log(Level.SEVERE, "Failed to initialize image storage", e);
        }

        // Create image renderer
        renderer = new ImageRenderer(basePath.resolve(dataPath).toString());
        renderer.start();

        // Initialize bStats
        Function<Integer, String> toStats = number -> {
            if (number >= 1000) return "1000+";
            if (number >= 500) return "500-999";
            if (number >= 100) return "100-499";
            if (number >= 50) return "50-99";
            if (number >= 10) return "10-49";
            return "0-9";
        };
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new Metrics.SimplePie("number_of_image_files", () -> toStats.apply(storage.size())));
        metrics.addCustomChart(new Metrics.SimplePie("number_of_placed_images", () -> toStats.apply(renderer.size())));
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
