package io.josemmo.bukkit.plugin;

import com.google.common.io.ByteStreams;
import io.josemmo.bukkit.plugin.commands.ImageCommandBridge;
import io.josemmo.bukkit.plugin.renderer.FakeEntity;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.renderer.ImageRenderer;
import io.josemmo.bukkit.plugin.renderer.ItemService;
import io.josemmo.bukkit.plugin.storage.ImageStorage;
import io.josemmo.bukkit.plugin.web.JettyServer;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import java.util.logging.Level;

public class YamipaPlugin extends JavaPlugin {
    public static final int BSTATS_PLUGIN_ID = 10243;
    private static YamipaPlugin instance;

    private boolean verbose;
    private ImageStorage storage;
    private ImageRenderer renderer;
    private ItemService itemService;
    private ScheduledExecutorService scheduler;

    private boolean uploadEnabled = false;
    private String uploadHostname = "localhost";
    private int uploadPort = 8877;
    private String uploadFormHtml;

    public static String uploadUrl = "http://localhost:8877/upload";

    /**
     * Get plugin instance
     * @return Plugin instance
     */
    public static @NotNull YamipaPlugin getInstance() {
        return instance;
    }

    /**
     * Get image storage instance
     * @return Image storage instance
     */
    public @NotNull ImageStorage getStorage() {
        return storage;
    }

    /**
     * Get image renderer instance
     * @return Image renderer instance
     */
    public @NotNull ImageRenderer getRenderer() {
        return renderer;
    }

    /**
     * Get internal tasks scheduler
     * @return Tasks scheduler
     */
    public @NotNull ScheduledExecutorService getScheduler() {
        return scheduler;
    }

    /**
     * Get configuration value
     * @param path         Configuration key path
     * @param defaultValue Default value
     * @return             Configuration value
     */
    private @NotNull String getConfigValue(@NotNull String path, @NotNull String defaultValue) {
        String value = getConfig().getString(path);
        return (value == null) ? defaultValue : value;
    }

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {

        readConfig();

        // Initialize logger
        verbose = getConfig().getBoolean("verbose");
        if (verbose) {
            info("Running on VERBOSE mode");
        }

        // Register plugin commands
        ImageCommandBridge.register(this);

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
        boolean animateImages = getConfig().getBoolean("animate-images", true);
        if (animateImages) {
            FakeImage.enableAnimation();
            info("Enabled image animation support");
        }
        renderer = new ImageRenderer(basePath.resolve(dataPath).toString());
        renderer.start();

        // Create image item service
        itemService = new ItemService();
        itemService.start();

        // Create thread pool
        scheduler = Executors.newScheduledThreadPool(6);

        // Warm-up ProtocolLib
        fine("Waiting for ProtocolLib to be ready...");
        scheduler.execute(() -> {
            FakeEntity.waitForProtocolLib();
            fine("ProtocolLib is now ready");
        });

        if(uploadEnabled) {
            // Start an embedded webserver on the configured port
            try {
                JettyServer jettyServer = new JettyServer(uploadFormHtml, uploadHostname, uploadPort, getLogger(), storage);
                jettyServer.start();
            } catch(Exception e) {
                warning("Failed to start image upload webserver: " + e + ", upload disabled!");
                e.printStackTrace();
                uploadEnabled = false;
            }
        }

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
        metrics.addCustomChart(new SimplePie("animate_images", () -> FakeImage.isAnimationEnabled() ? "true" : "false"));
        metrics.addCustomChart(new SimplePie("number_of_image_files", () -> toStats.apply(storage.size())));
        metrics.addCustomChart(new SimplePie("number_of_placed_images", () -> toStats.apply(renderer.size())));
    }

    @Override
    public void onDisable() {
        // Stop plugin components
        storage.stop();
        renderer.stop();
        itemService.stop();
        storage = null;
        renderer = null;
        itemService = null;

        // Stop internal scheduler
        scheduler.shutdownNow();
        scheduler = null;

        // Remove Bukkit listeners and tasks
        HandlerList.unregisterAll(this);
        Bukkit.getScheduler().cancelTasks(this);
    }

    /**
     * Log message
     * @param level   Record level
     * @param message Message
     * @param e       Throwable instance, NULL to ignore
     */
    public void log(@NotNull Level level, @NotNull String message, @Nullable Throwable e) {
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
    public void log(@NotNull Level level, @NotNull String message) {
        log(level, message, null);
    }

    /**
     * Log warning message
     * @param message Message
     */
    public void warning(@NotNull String message) {
        log(Level.WARNING, message);
    }

    /**
     * Log info message
     * @param message Message
     */
    public void info(@NotNull String message) {
        log(Level.INFO, message);
    }

    /**
     * Log fine message
     * @param message Message
     */
    public void fine(@NotNull String message) {
        log(Level.FINE, message);
    }

    private void readConfig() {
        saveDefaultConfig();
        uploadEnabled = getConfig().getBoolean("uploadEnabled", false);
        if(uploadEnabled) {
            uploadHostname = getConfig().getString("uploadHostname", "localhost");
            uploadPort = getConfig().getInt("uploadPort", 8877);
            uploadUrl = "http://" + uploadHostname + ":" + uploadPort + "/upload";
            try {
                InputStream htmlFormIn = getResource("uploadForm.html");
                uploadFormHtml = new String(ByteStreams.toByteArray(htmlFormIn));
            } catch(IOException e) {
                warning("Failed to load upload HTML template: " + e + ", upload is disabled!");
                uploadEnabled = false;
            }
        }
    }
}
