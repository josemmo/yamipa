package io.josemmo.bukkit.plugin.commands;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.renderer.ImageRenderer;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.SelectBlockTask;
import io.josemmo.bukkit.plugin.utils.ActionBar;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginDescriptionFile;
import java.awt.Dimension;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public class ImageCommand {
    public static final int ITEMS_PER_PAGE = 9;

    public static void showHelp(CommandSender s) {
        s.sendMessage(ChatColor.BOLD + "=== Yamipa Plugin Help ===");
        s.sendMessage(ChatColor.AQUA + "/image" + ChatColor.RESET + " — Show this help");
        s.sendMessage(ChatColor.AQUA + "/image describe" + ChatColor.RESET + " — Describe placed image");
        s.sendMessage(ChatColor.AQUA + "/image download <url> <filename>" + ChatColor.RESET + " — Download image");
        s.sendMessage(ChatColor.AQUA + "/image list [<page>]" + ChatColor.RESET + " — List all images");
        s.sendMessage(ChatColor.AQUA + "/image place <filename>" + ChatColor.RESET + " — Place image");
        s.sendMessage(ChatColor.AQUA + "/image remove" + ChatColor.RESET + " — Remove a single placed image");
        s.sendMessage(ChatColor.AQUA + "/image remove <radius> [<placed-by>]" + ChatColor.RESET + " — Remove placed images");
    }

    public static void listImages(CommandSender sender, int page) {
        String[] filenames = YamipaPlugin.getInstance().getStorage().getAllFilenames();
        int numOfImages = filenames.length;

        // Are there any images available?
        if (numOfImages == 0) {
            sender.sendMessage(ChatColor.RED + "No images found in the images directory");
            return;
        }

        // Is the page number valid?
        int firstImageIndex = Math.max(page-1, 0) * ITEMS_PER_PAGE;
        if (firstImageIndex >= numOfImages) {
            sender.sendMessage(ChatColor.RED + "Page " + page + " not found");
            return;
        }

        // Render list of images
        int stopImageIndex = (page == 0) ? numOfImages : Math.min(numOfImages, firstImageIndex+ITEMS_PER_PAGE);
        if (page > 0) {
            int maxPage = (int) Math.ceil((float) numOfImages / ITEMS_PER_PAGE);
            sender.sendMessage("=== Page " + page + " out of " + maxPage + " ===");
        }
        for (int i=firstImageIndex; i<stopImageIndex; ++i) {
            sender.sendMessage("" + ChatColor.GOLD + filenames[i]);
        }
    }

    public static void downloadImage(CommandSender sender, String url, String filename) {
        YamipaPlugin plugin = YamipaPlugin.getInstance();

        // Validate destination file
        Path basePath = Paths.get(plugin.getStorage().getBasePath());
        Path destPath = basePath.resolve(filename);
        if (!destPath.getParent().equals(basePath)) {
            sender.sendMessage(ChatColor.RED + "Not a valid destination filename");
            return;
        }
        if (destPath.toFile().exists()) {
            sender.sendMessage(ChatColor.RED + "There's already a file with that name");
            return;
        }

        // Download remote URL
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URLConnection conn = new URL(url).openConnection();
                PluginDescriptionFile desc = plugin.getDescription();
                conn.setRequestProperty("User-Agent", desc.getName() + "/" + desc.getVersion());
                sender.sendMessage("Downloading file...");
                Files.copy(conn.getInputStream(), destPath);
                sender.sendMessage(ChatColor.GREEN + "Done!");
            } catch (MalformedURLException e) {
                sender.sendMessage(ChatColor.RED + "The remote URL is not valid");
            } catch (IOException e) {
                sender.sendMessage(ChatColor.RED + "An error occurred trying to download the remote file");
            }
        });
    }

    public static void placeImage(
        Player player,
        String filename,
        int width,
        int height
    ) {
        YamipaPlugin plugin = YamipaPlugin.getInstance();

        // Get image instance
        ImageFile image = plugin.getStorage().get(filename);
        if (image == null) {
            player.sendMessage(ChatColor.RED + "The requested file does not exist");
            return;
        }

        // Get image size in blocks
        Dimension sizeInPixels = image.getSize();
        if (sizeInPixels == null) {
            player.sendMessage(ChatColor.RED + "The requested file is not a valid image");
            return;
        }
        if (height == 0) {
            float imageRatio = (float) sizeInPixels.height / sizeInPixels.width;
            height = Math.round(width * imageRatio);
            height = Math.min(height, FakeImage.MAX_DIMENSION);
        }
        final int finalHeight = height;

        // Ask player where to place image
        SelectBlockTask task = new SelectBlockTask(player);
        task.onSuccess((location, face) -> {
            FakeImage existingImage = plugin.getRenderer().getImage(location, face);
            if (existingImage != null) {
                ActionBar.send(player, ChatColor.RED + "There's already an image there!");
                return;
            }

            ActionBar.send(player, "");
            Rotation rotation = FakeImage.getRotationFromPlayerEyesight(face, player.getEyeLocation());
            FakeImage fakeImage = new FakeImage(image.getName(), location, face, rotation,
                width, finalHeight, new Date(), player);
            plugin.getRenderer().addImage(fakeImage);
        });
        task.onFailure(() -> ActionBar.send(player, ChatColor.RED + "Image placing canceled"));
        task.run("Right click a block to continue");
    }

    public static void removeImage(Player player) {
        ImageRenderer renderer = YamipaPlugin.getInstance().getRenderer();

        // Ask user to select fake image
        SelectBlockTask task = new SelectBlockTask(player);
        task.onSuccess((location, face) -> {
            FakeImage image = renderer.getImage(location, face);
            if (image == null) {
                ActionBar.send(player, ChatColor.RED + "That is not a valid image!");
            } else {
                ActionBar.send(player, "");
                renderer.removeImage(image);
            }
        });
        task.onFailure(() -> ActionBar.send(player, ChatColor.RED + "Image removing canceled"));
        task.run("Right click an image to continue");
    }

    public static void removeImagesInRadius(Player player, int radius, Player placedBy) {
        ImageRenderer renderer = YamipaPlugin.getInstance().getRenderer();

        // Get images in area
        Location loc = player.getLocation();
        Set<FakeImage> images = renderer.getImages(
            player.getWorld(),
            loc.getBlockX()-radius+1,
            loc.getBlockX()+radius-1,
            loc.getBlockZ()-radius+1,
            loc.getBlockZ()+radius-1
        );

        // Filter out images not placed by targeted player
        if (placedBy != null) {
            UUID target = placedBy.getUniqueId();
            images.removeIf(image -> {
                UUID thisTarget = (image.getPlacedBy() == null) ? null : image.getPlacedBy().getUniqueId();
                return !target.equals(thisTarget);
            });
        }

        // Remove found images
        for (FakeImage image : images) {
            renderer.removeImage(image);
        }
        player.sendMessage("Removed " + images.size() + " placed image(s)");
    }

    public static void describeImage(Player player) {
        ImageRenderer renderer = YamipaPlugin.getInstance().getRenderer();

        // Ask user to select fake image
        SelectBlockTask task = new SelectBlockTask(player);
        task.onSuccess((location, face) -> {
            FakeImage image = renderer.getImage(location, face);
            if (image == null) {
                ActionBar.send(player, ChatColor.RED + "That is not a valid image!");
                return;
            }
            ActionBar.send(player, "");

            // Send placed image information to player
            String dateStr = (image.getPlacedAt() == null) ?
                ChatColor.GRAY + "Some point in time" :
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(image.getPlacedAt());
            String playerStr;
            if (image.getPlacedBy() == null) {
                playerStr = ChatColor.GRAY + "Someone";
            } else if (image.getPlacedBy().getName() == null) {
                playerStr = ChatColor.DARK_AQUA + image.getPlacedBy().getUniqueId().toString();
            } else {
                playerStr = image.getPlacedBy().getName();
            }
            player.sendMessage("");
            player.sendMessage(ChatColor.GOLD + "Filename: " + ChatColor.RESET + image.getFilename());
            player.sendMessage(ChatColor.GOLD + "World: " + ChatColor.RESET +
                image.getLocation().getChunk().getWorld().getName());
            player.sendMessage(ChatColor.GOLD + "Coordinates: " + ChatColor.RESET +
                image.getLocation().getBlockX() + ", " +
                image.getLocation().getBlockY() + ", " +
                image.getLocation().getBlockZ());
            player.sendMessage(ChatColor.GOLD + "Block Face: " + ChatColor.RESET + image.getBlockFace());
            player.sendMessage(ChatColor.GOLD + "Rotation: " + ChatColor.RESET + image.getRotation());
            player.sendMessage(ChatColor.GOLD + "Dimensions: " + ChatColor.RESET +
                image.getWidth() + "x" + image.getHeight() + " blocks");
            player.sendMessage(ChatColor.GOLD + "Placed At: " + ChatColor.RESET + dateStr);
            player.sendMessage(ChatColor.GOLD + "Placed By: " + ChatColor.RESET + playerStr);
        });
        task.onFailure(() -> ActionBar.send(player, ChatColor.RED + "Image describing canceled"));
        task.run("Right click the image to describe");
    }
}
