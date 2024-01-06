package io.josemmo.bukkit.plugin.storage;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.renderer.FakeMap;
import io.josemmo.bukkit.plugin.utils.Logger;
import org.jetbrains.annotations.NotNull;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class CachedMapsFile extends SynchronizedFile {
    private static final String CACHE_EXT = "cache";
    private static final byte[] CACHE_SIGNATURE = new byte[] {0x59, 0x4d, 0x50}; // "YMP"
    private static final int CACHE_VERSION = 1;
    private static final Logger LOGGER = Logger.getLogger("CachedMapsFile");
    private final ImageFile imageFile;
    private final int width;
    private final int height;
    private FakeMap[][][] maps;
    private int delay;

    /**
     * Create instance from image file
     * @param  imageFile Image file instance
     * @param  width     Width in blocks
     * @param  height    Height in blocks
     * @return           Cached maps instance
     */
    public static @NotNull CachedMapsFile from(@NotNull ImageFile imageFile, int width, int height) {
        Path cachePath = YamipaPlugin.getInstance().getStorage().getCachePath();
        Path path = cachePath.resolve(imageFile.getFilename() + "." + width + "-" + height + "." + CACHE_EXT);
        return new CachedMapsFile(path, imageFile, width, height);
    }

    /**
     * Delete all cached maps files associated to an image file
     * @param imageFile Image file instance
     */
    public static void deleteAll(@NotNull ImageFile imageFile) {
        String relativeFilename = imageFile.getFilename();
        Path cachePath = YamipaPlugin.getInstance().getStorage().getCachePath();
        File baseDirectory = cachePath.resolve(relativeFilename).getParent().toFile();
        String cachePattern = Pattern.quote(Paths.get(relativeFilename).getFileName().toString()) +
            "\\.[0-9]+-[0-9]+\\." + CACHE_EXT;

        // Find cache files to delete
        if (!baseDirectory.exists()) {
            // Cache subdirectory does not exist, no need to delete files
            return;
        }
        File[] files = baseDirectory.listFiles((__, item) -> item.matches(cachePattern));
        if (files == null) {
            LOGGER.warning("An error occurred when listing cache files for image \"" + relativeFilename + "\"");
            return;
        }

        // Delete disk cache files
        for (File file : files) {
            if (!file.delete()) {
                LOGGER.warning("Failed to delete cache file \"" + file.getAbsolutePath() + "\"");
            }
        }
    }

    /**
     * Class constructor
     * @param path      Path to cached maps file in disk
     * @param imageFile Image file associated to these maps
     * @param width     Width in blocks
     * @param height    Height blocks
     */
    private CachedMapsFile(@NotNull Path path, @NotNull ImageFile imageFile, int width, int height) {
        super(path);
        this.imageFile = imageFile;
        this.width = width;
        this.height = height;
        load();
    }

    /**
     * Get maps
     * @return Tri-dimensional array of maps (column, row, step)
     */
    public @NotNull FakeMap[][][] getMaps() {
        return maps;
    }

    /**
     * Get delay between steps
     * @return Delay in 50ms intervals or <code>0</code> if not applicable
     */
    public int getDelay() {
        return delay;
    }

    /**
     * Load maps
     */
    private void load() {
        // Try to load maps from disk
        if (exists() && getLastModified() > imageFile.getLastModified()) {
            try {
                loadFromDisk();
                return;
            } catch (IllegalArgumentException e) {
                LOGGER.info("Cache file \"" + path + "\" is outdated and will be overwritten");
            } catch (Exception e) {
                LOGGER.warning("Cache file \"" + path + "\" is corrupted", e);
            }
        }

        // Generate maps from image file
        try {
            generateFromImage();
            tryToWriteToDisk();
            return;
        } catch (Exception e) {
            LOGGER.severe("Failed to render image step(s) from file \"" + path + "\"", e);
        }

        // Fallback to error matrix
        maps = FakeMap.getErrorMatrix(width, height);
        delay = 0;
    }

    /**
     * Load data from disk
     * @throws IllegalArgumentException if cache file is outdated
     * @throws IOException if cache file is corrupted
     */
    private void loadFromDisk() throws IllegalArgumentException, IOException {
        try (RandomAccessFile stream = read()) {
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

            // Update instance state
            this.maps = maps;
            this.delay = delay;
        }
    }

    /**
     * Generate data from image
     * @throws IOException if an I/O error occurred
     * @throws RuntimeException if failed to render image steps
     */
    private void generateFromImage() throws IOException, RuntimeException {
        int widthInPixels = width * FakeMap.DIMENSION;
        int heightInPixels = height * FakeMap.DIMENSION;

        // Render image steps in Minecraft color palette
        List<byte[]> renderedImages = new ArrayList<>();
        Map<Integer, Integer> delays = new HashMap<>();
        try (ImageInputStream inputStream = ImageIO.createImageInputStream(imageFile.read())) {
            ImageReader reader = ImageIO.getImageReaders(inputStream).next();
            reader.setInput(inputStream);
            String format = reader.getFormatName().toLowerCase();

            // Create temporary canvas
            int originalWidth = reader.getWidth(0);
            int originalHeight = reader.getHeight(0);
            BufferedImage tmpImage = new BufferedImage(originalWidth, originalHeight, BufferedImage.TYPE_4BYTE_ABGR);
            Graphics2D tmpGraphics = tmpImage.createGraphics();
            tmpGraphics.setBackground(new Color(0, 0, 0, 0));

            // Create temporary scaled canvas (for resizing)
            BufferedImage tmpScaledImage = new BufferedImage(widthInPixels, heightInPixels, BufferedImage.TYPE_INT_ARGB);
            Graphics2D tmpScaledGraphics = tmpScaledImage.createGraphics();
            tmpScaledGraphics.setBackground(new Color(0, 0, 0, 0));

            // Read images from file
            for (int step=0; step<FakeImage.MAX_STEPS; ++step) {
                try {
                    // Extract step metadata
                    int imageLeft = 0;
                    int imageTop = 0;
                    boolean disposePrevious = false;
                    if (format.equals("gif")) {
                        IIOMetadata metadata = reader.getImageMetadata(step);
                        String formatName = metadata.getNativeMetadataFormatName();
                        IIOMetadataNode metadataRoot = (IIOMetadataNode) metadata.getAsTree(formatName);
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
                        tmpScaledGraphics.clearRect(0, 0, widthInPixels, heightInPixels);
                    }

                    // Paint step image over temporary canvas
                    BufferedImage image = reader.read(step);
                    tmpGraphics.drawImage(image, imageLeft, imageTop, null);
                    image.flush();

                    // Resize image and get pixels
                    tmpScaledGraphics.drawImage(tmpImage, 0, 0, widthInPixels, heightInPixels, null);
                    int[] rgbaPixels = ((DataBufferInt) tmpScaledImage.getRaster().getDataBuffer()).getData();

                    // Convert RGBA pixels to Minecraft color indexes
                    byte[] renderedImage = new byte[widthInPixels * heightInPixels];
                    IntStream.range(0, rgbaPixels.length)
                        .parallel()
                        .forEach(pixelIndex -> renderedImage[pixelIndex] = FakeMap.pixelToIndex(rgbaPixels[pixelIndex]));
                    renderedImages.add(renderedImage);
                } catch (IndexOutOfBoundsException __) {
                    // No more steps to read
                    break;
                }
            }

            // Free resources
            reader.dispose();
            tmpGraphics.dispose();
            tmpImage.flush();
            tmpScaledGraphics.dispose();
            tmpScaledImage.flush();
        }

        // Get most occurring delay (mode)
        int delay = 0;
        if (renderedImages.size() > 1) {
            delay = Collections.max(delays.entrySet(), Map.Entry.comparingByValue()).getKey();
            delay = Math.round(delay * 0.2f); // (delay * 10) / 50
            delay = Math.min(Math.max(delay, FakeImage.MIN_DELAY), FakeImage.MAX_DELAY);
        }

        // Instantiate fake maps from image steps
        FakeMap[][][] maps = new FakeMap[width][height][renderedImages.size()];
        IntStream.range(0, renderedImages.size()).forEach(i -> {
            for (int col=0; col<width; col++) {
                for (int row=0; row<height; row++) {
                    maps[col][row][i] = new FakeMap(
                        renderedImages.get(i),
                        widthInPixels,
                        col*FakeMap.DIMENSION,
                        row*FakeMap.DIMENSION
                    );
                }
            }
        });

        // Update instance state
        this.maps = maps;
        this.delay = delay;
    }

    /**
     * Try to write data to disk
     */
    private void tryToWriteToDisk() {
        mkdirs();
        try (RandomAccessFile stream = write()) {
            int numOfSteps = maps[0][0].length;

            // Add file header
            stream.write(CACHE_SIGNATURE); // "YMP" signature
            stream.write(CACHE_VERSION);   // Format version
            stream.write(numOfSteps & 0xff);        // Number of animation steps (first byte)
            stream.write((numOfSteps >> 8) & 0xff); // Number of animation steps (second byte)
            if (numOfSteps > 1) {
                stream.write(delay);
            }

            // Add pixels
            //noinspection ForLoopReplaceableByForEach
            for (int col=0; col<maps.length; ++col) {
                for (int row=0; row<maps[0].length; ++row) {
                    for (int step=0; step<numOfSteps; ++step) {
                        stream.write(maps[col][row][step].getPixels());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.severe("Failed to write to cache file \"" + path + "\"", e);
        }
    }
}
