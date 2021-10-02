package io.josemmo.bukkit.plugin.storage;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.renderer.FakeMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class ImageFile {
    public static final String CACHE_EXT = "cache";
    public static final byte[] CACHE_SIGNATURE = new byte[] {0x59, 0x4d, 0x50}; // "YMP"
    public static final int CACHE_VERSION = 1;
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private final Map<String, FakeMap[][][]> cache = new HashMap<>();
    private final Map<String, Set<FakeImage>> subscribers = new HashMap<>();
    private final String name;
    private final String path;

    /**
     * Class constructor
     * @param name Image file name
     * @param path Path to image file
     */
    protected ImageFile(@NotNull String name, @NotNull String path) {
        this.name = name;
        this.path = path;
    }

    /**
     * Get image file name
     * @return Image file name
     */
    public @NotNull String getName() {
        return name;
    }

    /**
     * Get image reader
     * @return Image reader instance
     * @throws IOException if failed to get suitable image reader
     */
    private @NotNull ImageReader getImageReader() throws IOException {
        ImageInputStream inputStream = ImageIO.createImageInputStream(new File(path));
        ImageReader reader = ImageIO.getImageReaders(inputStream).next();
        reader.setInput(inputStream);
        return reader;
    }

    /**
     * Render images using Minecraft palette
     * @param  width  New width in pixels
     * @param  height New height in pixels
     * @return        Bi-dimensional array of Minecraft images (step, pixel index)
     * @throws IOException if failed to render images from file
     */
    private byte[][] renderImages(int width, int height) throws IOException {
        ImageReader reader = getImageReader();
        int numOfSteps = reader.getNumImages(true);

        // Create temporal canvas
        BufferedImage tmpImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tmpGraphics = tmpImage.createGraphics();

        // Read images from file
        byte[][] renderedImages = new byte[numOfSteps][width*height];
        for (int step=0; step<numOfSteps; ++step) {
            // Resize image and paint over temporal canvas
            Image scaledImage = reader.read(step).getScaledInstance(width, height, Image.SCALE_FAST);
            tmpGraphics.drawImage(scaledImage, 0, 0, null);
            scaledImage.flush();

            // Convert RGBA pixels to Minecraft color indexes
            int[] rgbPixels = tmpImage.getRGB(
                0, 0,
                width, height,
                null, 0,
                width
            );
            final int stepButFinal = step;
            IntStream.range(0, rgbPixels.length).parallel().forEach(pixelIndex -> {
                renderedImages[stepButFinal][pixelIndex] = FakeMap.pixelToIndex(rgbPixels[pixelIndex]);
            });
        }

        // Free resources
        reader.dispose();
        tmpGraphics.dispose();
        tmpImage.flush();

        return renderedImages;
    }

    /**
     * Get original size in pixels
     * @return Dimension instance or NULL if not a valid image file
     */
    public @Nullable Dimension getSize() {
        try {
            ImageReader reader = getImageReader();
            Dimension dimension = new Dimension(reader.getWidth(0), reader.getHeight(0));
            reader.dispose();
            return dimension;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get image last modified time
     * @return Last modified time in milliseconds, zero in case of error
     */
    public long getLastModified() {
        try {
            return Files.getLastModifiedTime(Paths.get(path)).toMillis();
        } catch (Exception __) {
            return 0L;
        }
    }

    /**
     * Get maps and subscribe to maps cache
     * @param  subscriber Fake image instance requesting the maps
     * @return            Tri-dimensional array of maps (column, row, step)
     */
    public synchronized @NotNull FakeMap[][][] getMapsAndSubscribe(@NotNull FakeImage subscriber) {
        int width = subscriber.getWidth();
        int height = subscriber.getHeight();
        String cacheKey = width + "-" + height;

        // Update subscribers for given cached maps
        subscribers.computeIfAbsent(cacheKey, __ -> new HashSet<>());
        subscribers.get(cacheKey).add(subscriber);

        // Try to get maps from memory cache
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        // Try to get maps from disk cache
        String cacheFilename = name + "." + cacheKey + "." + CACHE_EXT;
        File cacheFile = Paths.get(plugin.getStorage().getCachePath(), cacheFilename).toFile();
        if (cacheFile.isFile() && cacheFile.lastModified() >= getLastModified()) {
            try {
                FakeMap[][][] maps = readMapsFromCacheFile(cacheFile, width, height);
                cache.put(cacheKey, maps);
                return maps;
            } catch (IllegalArgumentException e) {
                plugin.info("Cache file \"" + cacheFile.getAbsolutePath() + "\" is outdated and will be overwritten");
            } catch (Exception e) {
                plugin.log(Level.WARNING, "Cache file \"" + cacheFile.getAbsolutePath() + "\" is corrupted", e);
            }
        }

        // Generate maps from original image
        FakeMap[][][] matrix;
        try {
            int widthInPixels = width*FakeMap.DIMENSION;
            int heightInPixels = height*FakeMap.DIMENSION;
            byte[][] images = renderImages(widthInPixels, heightInPixels);

            // Instantiate fake maps
            FakeMap[][][] tmpMatrix = new FakeMap[width][height][images.length];
            IntStream.range(0, images.length).forEach(i -> {
                for (int col=0; col<width; col++) {
                    for (int row=0; row<height; row++) {
                        tmpMatrix[col][row][i] = new FakeMap(
                            images[i],
                            widthInPixels,
                            col*FakeMap.DIMENSION,
                            row*FakeMap.DIMENSION
                        );
                    }
                }
            });
            matrix = tmpMatrix;

            // Persist in disk cache
            try {
                writeMapsToCacheFile(matrix, cacheFile);
            } catch (IOException e) {
                plugin.log(Level.SEVERE, "Failed to write to cache file \"" + cacheFile.getAbsolutePath() + "\"", e);
            }
        } catch (Exception e) {
            matrix = FakeMap.getErrorMatrix(width, height);
            plugin.log(Level.SEVERE, "Failed to render image(s) from file \"" + path + "\"", e);
        }

        // Persist in memory cache and return
        cache.put(cacheKey, matrix);
        return matrix;
    }

    /**
     * Read maps from cache file
     * @param  file   Cache file
     * @param  width  Width in blocks
     * @param  height Height in blocks
     * @return        Tri-dimensional array of maps (column, row, step)
     * @throws IllegalArgumentException if not a valid or outdated cache file
     * @throws IOException if failed to parse cache file
     */
    private @NotNull FakeMap[][][] readMapsFromCacheFile(@NotNull File file, int width, int height) throws Exception {
        try (FileInputStream stream = new FileInputStream(file)) {
            // Validate file signature
            for (byte expectedByte : CACHE_SIGNATURE) {
                if ((byte) stream.read() != expectedByte) {
                    throw new IllegalArgumentException("Invalid file signature");
                }
            }

            // Validate version number
            if ((byte) stream.read() != CACHE_VERSION) {
                throw new IllegalArgumentException("Incompatible file format version");
            }

            // Get number of animation steps
            int numOfSteps = stream.read();
            if (numOfSteps < 1) {
                throw new IOException("Invalid number of animation steps: " + numOfSteps);
            }

            // Read pixels
            FakeMap[][][] maps = new FakeMap[width][height][numOfSteps];
            for (int col=0; col<width; ++col) {
                for (int row=0; row<height; ++row) {
                    for (int step=0; step<numOfSteps; ++step) {
                        byte[] buffer = new byte[FakeMap.DIMENSION*FakeMap.DIMENSION];
                        stream.read(buffer);
                        maps[col][row][step] = new FakeMap(buffer);
                    }
                }
            }
            return maps;
        }
    }

    /**
     * Write maps to cache file
     * @param maps   Tri-dimensional array of maps (column, row, step)
     * @param file   Cache file
     * @throws IOException if failed to write to cache file
     */
    private void writeMapsToCacheFile(@NotNull FakeMap[][][] maps, @NotNull File file) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            // Add file header
            stream.write(CACHE_SIGNATURE);   // "YMP" signature
            stream.write(CACHE_VERSION);     // Format version
            stream.write(maps[0][0].length); // Number of animation steps

            // Add pixels
            for (int col=0; col<maps.length; ++col) {
                for (int row=0; row<maps[0].length; ++row) {
                    for (int step=0; step<maps[0][0].length; ++step) {
                        stream.write(maps[col][row][step].getPixels());
                    }
                }
            }
        }
    }

    /**
     * Unsubscribe from memory cache
     * <p>
     * This method is called by FakeImage instances when they get invalidated by a WorldArea.
     * By notifying their respective source ImageFile, the latter can clear cached maps from memory when no more
     * FakeItemFrames are using them.
     * @param subscriber Fake image instance
     */
    public synchronized void unsubscribe(@NotNull FakeImage subscriber) {
        String cacheKey = subscriber.getWidth() + "-" + subscriber.getHeight();
        if (!subscribers.containsKey(cacheKey)) return;

        // Remove subscriber
        Set<FakeImage> currentSubscribers = subscribers.get(cacheKey);
        currentSubscribers.remove(subscriber);

        // Can we clear cached maps?
        if (currentSubscribers.isEmpty()) {
            subscribers.remove(cacheKey);
            cache.remove(cacheKey);
            plugin.fine("Invalidated cached maps \"" + cacheKey + "\" in ImageFile#(" + name + ")");
        }
    }

    /**
     * Invalidate cache
     * <p>
     * Removes all references to pre-generated map sets.
     * This way, next time an image is requested to be rendered, maps will be regenerated.
     */
    public synchronized void invalidate() {
        cache.clear();

        // Delete disk cache files
        File[] files = Paths.get(plugin.getStorage().getCachePath()).toFile().listFiles((__, filename) -> {
            return filename.matches(Pattern.quote(name) + "\\.[0-9]+-[0-9]+\\." + CACHE_EXT);
        });
        if (files == null) {
            plugin.warning("An error occurred when listing cache files for image \"" + name + "\"");
            return;
        }
        for (File file : files) {
            if (!file.delete()) {
                plugin.warning("Failed to delete cache file \"" + file.getAbsolutePath() + "\"");
            }
        }
    }
}
