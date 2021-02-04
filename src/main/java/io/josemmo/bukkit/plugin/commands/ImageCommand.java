package io.josemmo.bukkit.plugin.commands;

import dev.jorel.commandapi.annotations.*;
import dev.jorel.commandapi.annotations.arguments.AIntegerArgument;
import dev.jorel.commandapi.annotations.arguments.ATextArgument;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.renderer.ImageRenderer;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.SelectBlockTask;
import io.josemmo.bukkit.plugin.utils.ActionBar;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Rotation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.awt.Dimension;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Command("image")
@Alias({"yamipa", "images"})
public class ImageCommand {
    public static final int ITEMS_PER_PAGE = 9;

    @Default
    public static void showHelp(CommandSender s) {
        s.sendMessage(ChatColor.BOLD + "=== Yamipa Plugin Help ===");
        s.sendMessage(ChatColor.AQUA + "/image" + ChatColor.RESET + " — Show this help");
        s.sendMessage(ChatColor.AQUA + "/image list [<page>]" + ChatColor.RESET + " — List all images");
        s.sendMessage(ChatColor.AQUA + "/image download <url> <filename>" + ChatColor.RESET + " — Download image");
        s.sendMessage(ChatColor.AQUA + "/image place <filename>" + ChatColor.RESET + " — Place image");
        s.sendMessage(ChatColor.AQUA + "/image remove" + ChatColor.RESET + " — Remove placed image");
        s.sendMessage(ChatColor.AQUA + "/image remove <radius>" + ChatColor.RESET + " — Remove placed images in radius");
    }

    @Subcommand("list")
    @Permission("yamipa.list")
    public static void listImages(CommandSender sender) {
        listImages(sender, 1);
    }

    @Subcommand("list")
    @Permission("yamipa.list")
    public static void listImages(CommandSender sender, @AIntegerArgument(min=1) int page) {
        String[] filenames = YamipaPlugin.getInstance().getStorage().getAll().keySet().toArray(new String[0]);
        int numOfImages = filenames.length;

        // Are there any images available?
        if (numOfImages == 0) {
            sender.sendMessage(ChatColor.RED + "No images found in the images directory");
            return;
        }

        // Is the page number valid?
        int firstImageIndex = (page - 1) * ITEMS_PER_PAGE;
        if (firstImageIndex >= numOfImages) {
            sender.sendMessage(ChatColor.RED + "Page " + page + " not found");
            return;
        }

        // Render list of images
        int maxPage = (int) Math.ceil((float) numOfImages / ITEMS_PER_PAGE);
        sender.sendMessage("=== Page " + page + " out of " + maxPage + " ===");
        for (int i=firstImageIndex; i<Math.min(numOfImages, firstImageIndex+ITEMS_PER_PAGE); ++i) {
            sender.sendMessage("" + ChatColor.GOLD + filenames[i]);
        }
    }

    @Subcommand("download")
    @Permission("yamipa.download")
    public static void downloadImage(CommandSender sender, @ATextArgument String url, @ATextArgument String filename) {
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
                InputStream inputStream = new URL(url).openStream();
                sender.sendMessage("Downloading file...");
                Files.copy(inputStream, destPath);
                sender.sendMessage(ChatColor.GREEN + "Done!");
            } catch (MalformedURLException e) {
                sender.sendMessage(ChatColor.RED + "The remote URL is not valid");
            } catch (IOException e) {
                sender.sendMessage(ChatColor.RED + "An error occurred trying to download the remote file");
            }
        });
    }

    @Subcommand("place")
    @Permission("yamipa.place")
    public static void placeImage(
        Player player,
        @ATextArgument String filename,
        @AIntegerArgument(min=1, max=FakeImage.MAX_DIMENSION) int width
    ) {
        placeImage(player, filename, width, 0);
    }

    @Subcommand("place")
    @Permission("yamipa.place")
    public static void placeImage(
        Player player,
        @ATextArgument String filename,
        @AIntegerArgument(min=1, max=FakeImage.MAX_DIMENSION) int width,
        @AIntegerArgument(min=1, max=FakeImage.MAX_DIMENSION) int height
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
            FakeImage fakeImage = new FakeImage(image.getName(), location, face, rotation, width, finalHeight);
            plugin.getRenderer().addImage(fakeImage);
        });
        task.onFailure(() -> ActionBar.send(player, ChatColor.RED + "Image placing canceled"));
        task.run("Right click a block to continue");
    }

    @Subcommand("remove")
    @Permission("yamipa.remove")
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

    @Subcommand("remove")
    @Permission("yamipa.remove.radius")
    public static void removeImagesInRadius(Player player, @AIntegerArgument(min=1) int radius) {
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

        // Remove found images
        for (FakeImage image : images) {
            renderer.removeImage(image);
        }
        player.sendMessage("Removed " + images.size() + " placed image(s)");
    }
}
