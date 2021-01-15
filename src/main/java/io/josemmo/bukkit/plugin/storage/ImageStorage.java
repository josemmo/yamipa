package io.josemmo.bukkit.plugin.storage;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ImageStorage {
    static public final long POLLING_INTERVAL = 20L * 5; // In server ticks
    static private final Logger logger = YamipaPlugin.getInstance().getLogger();
    private final String basePath;
    private final SortedMap<String, ImageFile> cachedImages = new TreeMap<>();
    private BukkitTask task;
    private WatchService watchService;

    /**
     * Class constructor
     * @param basePath Path to directory containing the images
     */
    public ImageStorage(String basePath) {
        this.basePath = basePath;
    }

    /**
     * Start instance
     * @throws SecurityException if failed to access filesystem
     * @throws Exception if failed to start watch service
     */
    public void start() throws Exception {
        // Create directory if not exists
        File directory = new File(basePath);
        if (directory.mkdirs()) {
            logger.info("Created images directory as it did not exist");
        }

        // Do initial directory listing
        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isDirectory()) continue;
            String filename = file.getName();
            cachedImages.put(filename, new ImageFile(filename, file.getAbsolutePath()));
        }
        logger.fine("Found " + cachedImages.size() + " file(s) in images directory");

        // Prepare watch service
        watchService = FileSystems.getDefault().newWatchService();
        Path directoryPath = directory.toPath();
        directoryPath.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
        );

        // Start watching for changes
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(YamipaPlugin.getInstance(), () -> {
            WatchKey watchKey = watchService.poll();
            if (watchKey == null) return;

            watchKey.pollEvents().forEach(event -> {
                WatchEvent.Kind<?> kind = event.kind();
                File file = directoryPath.resolve((Path) event.context()).toFile();
                if (file.isDirectory()) return;

                String filename = file.getName();
                synchronized (this) {
                    if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        cachedImages.remove(filename);
                        logger.fine("Detected file deletion at " + filename);
                    } else if (cachedImages.containsKey(filename)) {
                        cachedImages.get(filename).invalidate();
                        logger.fine("Detected file update at " + filename);
                    } else {
                        cachedImages.put(filename, new ImageFile(filename, file.getAbsolutePath()));
                        logger.fine("Detected file creation at " + filename);
                    }
                }
            });

            watchKey.reset();
        }, POLLING_INTERVAL, POLLING_INTERVAL);
        logger.fine("Started watching for file changes in images directory");
    }

    /**
     * Stop instance
     */
    public void stop() {
        // Cancel async task
        if (task != null) {
            task.cancel();
        }

        // Close watch service
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.log(Level.WARNING, "Failed to close watch service", e);
            }
        }
    }

    /**
     * Get all images
     * @return List of images
     */
    public synchronized SortedMap<String, ImageFile> getAll() {
        return cachedImages;
    }

    /**
     * Get image by filename
     * @param  filename Filename
     * @return          Image instance or NULL if not found
     */
    public synchronized ImageFile get(String filename) {
        return cachedImages.get(filename);
    }
}
