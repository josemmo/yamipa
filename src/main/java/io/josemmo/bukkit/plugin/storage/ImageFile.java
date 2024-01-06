package io.josemmo.bukkit.plugin.storage;

import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.renderer.FakeItemFrame;
import io.josemmo.bukkit.plugin.utils.Logger;
import org.jetbrains.annotations.Blocking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ImageFile extends SynchronizedFile {
    private static final Logger LOGGER = Logger.getLogger("ImageFile");
    private final ConcurrentHashMap<String, Lock> locks = new ConcurrentHashMap<>();
    private final Map<String, CachedMapsFile> cache = new HashMap<>();
    private final Map<String, Set<FakeImage>> subscribers = new HashMap<>();
    private final String filename;
    private @Nullable Dimension size;

    /**
     * Class constructor
     * @param filename Image filename
     * @param path     Path to image file
     */
    protected ImageFile(@NotNull String filename, @NotNull Path path) {
        super(path);
        this.filename = filename;
    }

    /**
     * Get image filename
     * @return Image filename
     */
    public @NotNull String getFilename() {
        return filename;
    }

    /**
     * Get original size in pixels
     * @return Dimension instance or NULL if not a valid image file
     */
    public synchronized @Nullable Dimension getSize() {
        if (size != null) {
            return size;
        }
        try (ImageInputStream inputStream = ImageIO.createImageInputStream(read())) {
            ImageReader reader = ImageIO.getImageReaders(inputStream).next();
            reader.setInput(inputStream);
            size = new Dimension(reader.getWidth(0), reader.getHeight(0));
            reader.dispose();
            return size;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Get maps and subscribe to them
     * @param  subscriber Fake image instance requesting the maps
     * @return            Cached maps
     */
    @Blocking
    public @NotNull CachedMapsFile getMapsAndSubscribe(@NotNull FakeImage subscriber) {
        int width = subscriber.getWidth();
        int height = subscriber.getHeight();
        String cacheKey = width + "-" + height;

        // Prevent rendering the same image/dimensions pair multiple times
        Lock lock = locks.computeIfAbsent(cacheKey, __ -> new ReentrantLock());
        lock.lock();

        // Get cached maps without locking this instance
        CachedMapsFile maps = cache.get(cacheKey);
        if (maps == null) {
            maps = CachedMapsFile.from(this, width, height);
        }

        // Update state of this instance
        synchronized (this) {
            if (cache.get(cacheKey) != maps) {
                cache.put(cacheKey, maps);
            }
            subscribers.computeIfAbsent(cacheKey, __ -> new HashSet<>()).add(subscriber);
            lock.unlock();
            locks.remove(cacheKey);
            return maps;
        }
    }

    /**
     * Unsubscribe from memory cache
     * <p>
     * This method is called by {@link FakeImage} instances when they get invalidated by a world area change.
     * By notifying their respective source {@link ImageFile}, the latter can clear cached maps from memory when no
     * more {@link FakeItemFrame}s are using them.
     * @param subscriber Fake image instance
     */
    public synchronized void unsubscribe(@NotNull FakeImage subscriber) {
        String cacheKey = subscriber.getWidth() + "-" + subscriber.getHeight();
        if (!subscribers.containsKey(cacheKey)) {
            // Not subscribed to this image file
            return;
        }

        // Remove subscriber
        Set<FakeImage> currentSubscribers = subscribers.get(cacheKey);
        currentSubscribers.remove(subscriber);

        // Can we clear cached maps?
        if (currentSubscribers.isEmpty()) {
            subscribers.remove(cacheKey);
            cache.remove(cacheKey);
            LOGGER.fine("Invalidated cached maps \"" + cacheKey + "\" in ImageFile#(" + filename + ")");
        }
    }

    /**
     * Invalidate cache
     * <p>
     * Removes all references to cached map instances.
     * This way, next time an image is requested to be rendered, maps will be regenerated.
     */
    public synchronized void invalidate() {
        size = null;
        cache.clear();
        CachedMapsFile.deleteAll(this);
    }
}
