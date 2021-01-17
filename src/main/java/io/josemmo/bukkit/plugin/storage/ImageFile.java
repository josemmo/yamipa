package io.josemmo.bukkit.plugin.storage;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.FakeMap;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ImageFile {
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private final ConcurrentMap<String, FakeMap[][]> cache = new ConcurrentHashMap<>();
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
     */
    private BufferedImage getBufferedImage() throws IOException {
        return ImageIO.read(new File(path));
    }

    /**
     * Get resized buffered image
     * @param  width  New width in pixels
     * @param  height New height in pixels
     * @return        Resized buffered image
     * @throws IOException if not a valid image file
     */
    private BufferedImage getBufferedImage(int width, int height) throws IOException {
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
    public FakeMap[][] getMaps(int width, int height) {
        String cacheKey = width + "," + height;

        // Try to get maps from cache
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        // Split image into maps
        FakeMap[][] matrix = new FakeMap[width][height];
        try {
            BufferedImage image = getBufferedImage(width*FakeMap.DIMENSION, height*FakeMap.DIMENSION);
            for (int col=0; col<width; col++) {
                for (int row=0; row<height; row++) {
                    int[] pixels = image.getRGB(
                        col*FakeMap.DIMENSION,
                        row*FakeMap.DIMENSION,
                        FakeMap.DIMENSION,
                        FakeMap.DIMENSION,
                        null,
                        0,
                        FakeMap.DIMENSION
                    );
                    matrix[col][row] = new FakeMap(pixels);
                }
            }
        } catch (IOException e) {
            FakeMap errorMap = FakeMap.getErrorInstance();
            for (FakeMap[] column : matrix) {
                Arrays.fill(column, errorMap);
            }
            plugin.warning("Failed to get image data from file \"" + path + "\"");
        }

        // Persist in cache and return
        cache.put(cacheKey, matrix);
        return matrix;
    }

    /**
     * Invalidate cache
     * <p>
     * Removes all references to pre-generated map sets.
     * This way, next time an image is requested to be rendered, maps will be regenerated.
     */
    public void invalidate() {
        cache.clear();
    }
}
