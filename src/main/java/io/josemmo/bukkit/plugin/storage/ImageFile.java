package io.josemmo.bukkit.plugin.storage;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.FakeMap;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class ImageFile {
    public static final String CACHE_EXT = "cache";
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private final Map<String, FakeMap[][]> cache = new HashMap<>();
    private final String name;
    private final String path;

    /**
     * Class constructor
     * @param name Image file name
     * @param path Path to image file
     */
    protected ImageFile(String name, String path) {
        this.name = name;
        this.path = path;
    }

    /**
     * Get image file name
     * @return Image file name
     */
    public String getName() {
        return name;
    }

    /**
     * Get buffered image
     * @return Buffered image
     * @throws IOException if not a valid image file
     * @throws NullPointerException if file not found
     */
    private BufferedImage getBufferedImage() throws Exception {
        return ImageIO.read(new File(path));
    }

    /**
     * Get resized buffered image
     * @param  width  New width in pixels
     * @param  height New height in pixels
     * @return        Resized buffered image
     * @throws IOException if not a valid image file
     * @throws NullPointerException if file not found
     */
    private BufferedImage getBufferedImage(int width, int height) throws Exception {
        Image tmp = getBufferedImage().getScaledInstance(width, height, Image.SCALE_FAST);
        BufferedImage resizedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        // Copy data from temporary to resized instance
        Graphics2D g2d = resizedImage.createGraphics();
        g2d.drawImage(tmp, 0, 0, null);
        g2d.dispose();

        return resizedImage;
    }

    /**
     * Get original size in pixels
     * @return Dimension instance or NULL if not a valid image file
     */
    public Dimension getSize() {
        try {
            BufferedImage image = getBufferedImage();
            return new Dimension(image.getWidth(), image.getHeight());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get maps
     * @param  width  Width in blocks
     * @param  height Height in blocks
     * @return        Bi-dimensional array of maps
     */
    public synchronized FakeMap[][] getMaps(int width, int height) {
        String cacheKey = width + "-" + height;

        // Try to get maps from memory cache
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        // Try to get maps from disk cache
        String cacheFilename = name + "." + cacheKey + "." + CACHE_EXT;
        File cacheFile = Paths.get(plugin.getStorage().getCachePath(), cacheFilename).toFile();
        if (cacheFile.isFile()) {
            try {
                FakeMap[][] maps = readMapsFromCacheFile(cacheFile, width, height);
                cache.put(cacheKey, maps);
                return maps;
            } catch (Exception e) {
                plugin.warning("Cache file \"" + cacheFile.getAbsolutePath() + "\" is corrupted");
            }
        }

        // Generate maps from original image
        FakeMap[][] matrix = new FakeMap[width][height];
        try {
            BufferedImage image = getBufferedImage(width*FakeMap.DIMENSION, height*FakeMap.DIMENSION);
            int imgWidth = image.getWidth();

            // Convert RGBA pixels to Minecraft color indexes
            int[] rgbPixels = image.getRGB(
                0, 0,
                imgWidth, image.getHeight(),
                null, 0,
                imgWidth
            );
            byte[] pixels = new byte[rgbPixels.length];
            IntStream.range(0, rgbPixels.length).parallel().forEach(i -> {
                pixels[i] = FakeMap.pixelToIndex(rgbPixels[i]);
            });

            // Instantiate fake maps
            for (int col=0; col<width; col++) {
                for (int row=0; row<height; row++) {
                    matrix[col][row] = new FakeMap(pixels, imgWidth, col*FakeMap.DIMENSION, row*FakeMap.DIMENSION);
                }
            }

            // Persist in disk cache
            try {
                writeMapsToCacheFile(matrix, width, height, cacheFile);
            } catch (IOException __) {
                plugin.warning("Failed to write to cache file \"" + cacheFile.getAbsolutePath() + "\"");
            }
        } catch (Exception __) {
            matrix = FakeMap.getErrorMatrix(width, height);
            plugin.warning("Failed to get image data from file \"" + path + "\"");
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
     * @return        Bi-dimensional array of maps
     * @throws IOException if failed to parse cache file
     */
    private FakeMap[][] readMapsFromCacheFile(File file, int width, int height) throws IOException {
        try (FileInputStream stream = new FileInputStream(file)) {
            FakeMap[][] matrix = new FakeMap[width][height];
            byte[] buffer = new byte[FakeMap.DIMENSION*FakeMap.DIMENSION];
            for (int col=0; col<width; col++) {
                for (int row=0; row<height; row++) {
                    stream.read(buffer);
                    matrix[col][row] = new FakeMap(buffer, FakeMap.DIMENSION, 0, 0);
                }
            }
            return matrix;
        }
    }

    /**
     * Write maps to cache file
     * @param maps   Bi-dimensional array of maps
     * @param width  Width in blocks
     * @param height Height in blocks
     * @param file   Cache file
     * @throws IOException if failed to write to cache file
     */
    private void writeMapsToCacheFile(FakeMap[][] maps, int width, int height, File file) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            for (int col=0; col<width; col++) {
                for (int row=0; row<height; row++) {
                    stream.write(maps[col][row].getPixels());
                }
            }
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
