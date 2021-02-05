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

public class ImageStorage {
    static public final long POLLING_INTERVAL = 20L * 5; // In server ticks
    static private final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private final String basePath;
    private final String cachePath;
    private final SortedMap<String, ImageFile> cachedImages = new TreeMap<>();
    private BukkitTask task;
    private WatchService watchService;

    /**
     * Class constructor
     * @param basePath  Path to directory containing the images
     * @param cachePath Path to directory containing the cached image maps
     */
    public ImageStorage(String basePath, String cachePath) {
        this.basePath = basePath;
        this.cachePath = cachePath;
    }

    /**
     * Get base path
     * @return Base path
     */
    public String getBasePath() {
        return basePath;
    }

    /**
     * Get cache path
     * @return Cache path
     */
    public String getCachePath() {
        return cachePath;
    }

    /**
     * Start instance
     * @throws SecurityException if failed to access filesystem
     * @throws Exception if failed to start watch service
     */
    public void start() throws Exception {
        // Create directories if necessary
        File directory = new File(basePath);
        if (directory.mkdirs()) {
            plugin.info("Created images directory as it did not exist");
        }
        if (new File(cachePath).mkdirs()) {
            plugin.info("Created cache directory as it did not exist");
        }

        // Do initial directory listing
        for (File file : Objects.requireNonNull(directory.listFiles())) {
            if (file.isDirectory()) continue;
            String filename = file.getName();
            cachedImages.put(filename, new ImageFile(filename, file.getAbsolutePath()));
        }
        plugin.fine("Found " + cachedImages.size() + " file(s) in images directory");

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
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            WatchKey watchKey = watchService.poll();
            if (watchKey == null) return;

            watchKey.pollEvents().forEach(event -> {
                WatchEvent.Kind<?> kind = event.kind();
                File file = directoryPath.resolve((Path) event.context()).toFile();
                if (file.isDirectory()) return;

                String filename = file.getName();
                synchronized (this) {
                    if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                        ImageFile imageFile = cachedImages.get(filename);
                        if (imageFile != null) {
                            imageFile.invalidate();
                            cachedImages.remove(filename);
                        }
                        plugin.fine("Detected file deletion at " + filename);
                    } else if (cachedImages.containsKey(filename)) {
                        cachedImages.get(filename).invalidate();
                        plugin.fine("Detected file update at " + filename);
                    } else {
                        cachedImages.put(filename, new ImageFile(filename, file.getAbsolutePath()));
                        plugin.fine("Detected file creation at " + filename);
                    }
                }
            });

            watchKey.reset();
        }, POLLING_INTERVAL, POLLING_INTERVAL);
        plugin.fine("Started watching for file changes in images directory");
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
                plugin.log(Level.WARNING, "Failed to close watch service", e);
            }
        }
    }

    /**
     * Get number of images
     * @return Number of images
     */
    public synchronized int size() {
        return cachedImages.size();
    }

    /**
     * Get all image filenames
     * @return Sorted array of image filenames
     */
    public synchronized String[] getAllFilenames() {
        return cachedImages.keySet().toArray(new String[0]);
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
