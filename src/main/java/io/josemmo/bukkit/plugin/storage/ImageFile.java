package io.josemmo.bukkit.plugin.storage;

import com.comphenix.protocol.wrappers.Pair;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.renderer.FakeMap;
import io.josemmo.bukkit.plugin.renderer.FakeMapsContainer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class ImageFile {
    public static final String CACHE_EXT = "cache";
    public static final byte[] CACHE_SIGNATURE = new byte[] {0x59, 0x4d, 0x50}; // "YMP"
    public static final int CACHE_VERSION = 1;
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private final ConcurrentHashMap<String, Lock> locks = new ConcurrentHashMap<>();
    private final Map<String, FakeMapsContainer> cache = new HashMap<>();
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
     * @return        Pair of bi-dimensional array of Minecraft images (step, pixel index) and delay between steps
     * @throws IOException if failed to render images from file
     */
    private @NotNull Pair<byte[][], Integer> renderImages(int width, int height) throws IOException {
        ImageReader reader = getImageReader();
        String format = reader.getFormatName().toLowerCase();

        // Create temporary canvas
        int originalWidth = reader.getWidth(0);
        int originalHeight = reader.getHeight(0);
        BufferedImage tmpImage = new BufferedImage(originalWidth, originalHeight, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D tmpGraphics = tmpImage.createGraphics();
        tmpGraphics.setBackground(new Color(0, 0, 0, 0));

        // Create temporary scaled canvas (for resizing)
        BufferedImage tmpScaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D tmpScaledGraphics = tmpScaledImage.createGraphics();
        tmpScaledGraphics.setBackground(new Color(0, 0, 0, 0));

        // Read images from file
        List<byte[]> renderedImages = new ArrayList<>();
        Map<Integer, Integer> delays = new HashMap<>();
        for (int step=0; step<FakeImage.MAX_STEPS; ++step) {
            try {
                // Extract step metadata
                int imageLeft = 0;
                int imageTop = 0;
                boolean disposePrevious = false;
                if (format.equals("gif")) {
                    IIOMetadata metadata = reader.getImageMetadata(step);
                    IIOMetadataNode metadataRoot = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
                    for (int i=0; i<metadataRoot.getLength(); ++i) {
                        String nodeName = metadataRoot.item(i).getNodeName();
                        if (nodeName.equalsIgnoreCase("ImageDescriptor")) {
                            IIOMetadataNode descriptorNode = (IIOMetadataNode) metadataRoot.item(i);
                            imageLeft = Integer.parseInt(descriptorNode.getAttribute("imageLeftPosition"));
                            imageTop = Integer.parseInt(descriptorNode.getAttribute("imageTopPosition"));
                        } else if (nodeName.equalsIgnoreCase("GraphicControlExtension")) {
                            IIOMetadataNode controlExtensionNode = (IIOMetadataNode) metadataRoot.item(i);
                            int delay = Integer.parseInt(controlExtensionNode.getAttribute("delayTime"));
                            delays.compute(delay, (__, count) -> (count == null) ? 1 : count + 1);
                            disposePrevious = controlExtensionNode.getAttribute("disposalMethod").startsWith("restore");
                        }
                    }
                }

                // Clear temporary canvases (if needed)
                if (disposePrevious) {
                    tmpGraphics.clearRect(0, 0, originalWidth, originalHeight);
                    tmpScaledGraphics.clearRect(0, 0, width, height);
                }

                // Paint step image over temporary canvas
                BufferedImage image = reader.read(step);
                tmpGraphics.drawImage(image, imageLeft, imageTop, null);
                image.flush();

                // Resize image and get pixels
                tmpScaledGraphics.drawImage(tmpImage, 0, 0, width, height, null);
                int[] rgbaPixels = ((DataBufferInt) tmpScaledImage.getRaster().getDataBuffer()).getData();

                // Convert RGBA pixels to Minecraft color indexes
                byte[] renderedImage = new byte[width*height];
                IntStream.range(0, rgbaPixels.length).parallel().forEach(pixelIndex -> {
                    renderedImage[pixelIndex] = FakeMap.pixelToIndex(rgbaPixels[pixelIndex]);
                });
                renderedImages.add(renderedImage);
            } catch (IndexOutOfBoundsException __) {
                // No more steps to read
                break;
            }
        }

        // Get most occurring delay (mode)
        int delay = 0;
        if (renderedImages.size() > 1) {
            delay = Collections.max(delays.entrySet(), Map.Entry.comparingByValue()).getKey();
            delay = Math.round(delay * 0.2f); // (delay * 10) / 50
            delay = Math.min(Math.max(delay, FakeImage.MIN_DELAY), FakeImage.MAX_DELAY);
        }

        // Free resources
        reader.dispose();
        tmpGraphics.dispose();
        tmpImage.flush();
        tmpScaledGraphics.dispose();
        tmpScaledImage.flush();

        return new Pair<>(renderedImages.toArray(new byte[0][0]), delay);
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
     * @return            Fake maps container
     */
    public @NotNull FakeMapsContainer getMapsAndSubscribe(@NotNull FakeImage subscriber) {
        int width = subscriber.getWidth();
        int height = subscriber.getHeight();
        String cacheKey = width + "-" + height;

        // Prevent rendering the same image/dimensions pair multiple times
        Lock lock = locks.computeIfAbsent(cacheKey, __ -> new ReentrantLock());
        lock.lock();

        // Execute code
        FakeMapsContainer container;
        try {
            container = getMapsAndSubscribe(subscriber, cacheKey, width, height);
        } finally {
            lock.unlock();
        }

        return container;
    }

    /**
     * Get maps and subscribe to maps cache (internal)
     * @param  subscriber Fake image instance requesting the maps
     * @param  cacheKey   Maps cache key
     * @param  width      Desired image width in pixels
     * @param  height     Desired image height in pixels
     * @return            Fake maps container
     */
    private @NotNull FakeMapsContainer getMapsAndSubscribe(
        @NotNull FakeImage subscriber,
        @NotNull String cacheKey,
        int width,
        int height
    ) {
        // Update subscribers for given cached maps
        subscribers.computeIfAbsent(cacheKey, __ -> new HashSet<>()).add(subscriber);

        // Try to get maps from memory cache
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }

        // Try to get maps from disk cache
        String cacheFilename = name + "." + cacheKey + "." + CACHE_EXT;
        File cacheFile = Paths.get(plugin.getStorage().getCachePath(), cacheFilename).toFile();
        if (cacheFile.isFile() && cacheFile.lastModified() >= getLastModified()) {
            try {
                FakeMapsContainer container = readMapsFromCacheFile(cacheFile, width, height);
                cache.put(cacheKey, container);
                return container;
            } catch (IllegalArgumentException e) {
                plugin.info("Cache file \"" + cacheFile.getAbsolutePath() + "\" is outdated and will be overwritten");
            } catch (Exception e) {
                plugin.log(Level.WARNING, "Cache file \"" + cacheFile.getAbsolutePath() + "\" is corrupted", e);
            }
        }

        // Generate maps from original image
        FakeMapsContainer container;
        try {
            int widthInPixels = width*FakeMap.DIMENSION;
            int heightInPixels = height*FakeMap.DIMENSION;
            Pair<byte[][], Integer> res = renderImages(widthInPixels, heightInPixels);
            byte[][] images = res.getFirst();
            int delay = res.getSecond();

            // Instantiate fake maps
            FakeMap[][][] matrix = new FakeMap[width][height][images.length];
            IntStream.range(0, images.length).forEach(i -> {
                for (int col=0; col<width; col++) {
                    for (int row=0; row<height; row++) {
                        matrix[col][row][i] = new FakeMap(
                            images[i],
                            widthInPixels,
                            col*FakeMap.DIMENSION,
                            row*FakeMap.DIMENSION
                        );
                    }
                }
            });

            // Persist in disk cache
            container = new FakeMapsContainer(matrix, delay);
            try {
                writeMapsToCacheFile(container, cacheFile);
            } catch (IOException e) {
                plugin.log(Level.SEVERE, "Failed to write to cache file \"" + cacheFile.getAbsolutePath() + "\"", e);
            }
        } catch (Exception e) {
            container = FakeMap.getErrorMatrix(width, height);
            plugin.log(Level.SEVERE, "Failed to render image(s) from file \"" + path + "\"", e);
        }

        // Persist in memory cache and return
        cache.put(cacheKey, container);
        return container;
    }

    /**
     * Read maps from cache file
     * @param  file   Cache file
     * @param  width  Width in blocks
     * @param  height Height in blocks
     * @return        Fake maps container
     * @throws IllegalArgumentException if not a valid or outdated cache file
     * @throws IOException if failed to parse cache file
     */
    private @NotNull FakeMapsContainer readMapsFromCacheFile(@NotNull File file, int width, int height) throws Exception {
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
            int numOfSteps = stream.read() | (stream.read() << 8);
            if (numOfSteps < 1 || numOfSteps > FakeImage.MAX_STEPS) {
                throw new IOException("Invalid number of animation steps: " + numOfSteps);
            }

            // Get delay between steps
            int delay = 0;
            if (numOfSteps > 1) {
                delay = stream.read();
                if (delay < FakeImage.MIN_DELAY || delay > FakeImage.MAX_DELAY) {
                    throw new IOException("Invalid delay between steps: " + delay);
                }
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

            return new FakeMapsContainer(maps, delay);
        }
    }

    /**
     * Write maps to cache file
     * @param container Fake maps container
     * @param file      Cache file
     * @throws IOException if failed to write to cache file
     */
    private void writeMapsToCacheFile(@NotNull FakeMapsContainer container, @NotNull File file) throws IOException {
        try (FileOutputStream stream = new FileOutputStream(file)) {
            FakeMap[][][] maps = container.getFakeMaps();
            int numOfSteps = maps[0][0].length;

            // Add file header
            stream.write(CACHE_SIGNATURE); // "YMP" signature
            stream.write(CACHE_VERSION);   // Format version
            stream.write(numOfSteps & 0xff);        // Number of animation steps (first byte)
            stream.write((numOfSteps >> 8) & 0xff); // Number of animation steps (second byte)
            if (numOfSteps > 1) {
                stream.write(container.getDelay());
            }

            // Add pixels
            for (int col=0; col<maps.length; ++col) {
                for (int row=0; row<maps[0].length; ++row) {
                    for (int step=0; step<numOfSteps; ++step) {
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
