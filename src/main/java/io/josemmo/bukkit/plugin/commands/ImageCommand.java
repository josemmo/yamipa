package io.josemmo.bukkit.plugin.commands;

import dev.jorel.commandapi.annotations.*;
import dev.jorel.commandapi.annotations.arguments.AIntegerArgument;
import dev.jorel.commandapi.annotations.arguments.AStringArgument;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.SelectBlockTask;
import io.josemmo.bukkit.plugin.utils.ActionBar;
import org.bukkit.ChatColor;
import org.bukkit.Rotation;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import java.awt.Dimension;
import java.util.SortedMap;

@Command("image")
@Alias({"yamipa", "images"})
public class ImageCommand {
    @Default
    public static void showHelp(CommandSender s) {
        s.sendMessage(ChatColor.BOLD + "=== Yamipa Plugin Help ===");
        s.sendMessage(ChatColor.AQUA + "/image" + ChatColor.RESET + " — Show this help");
        s.sendMessage(ChatColor.AQUA + "/image list" + ChatColor.RESET + " — List all images");
        s.sendMessage(ChatColor.AQUA + "/image download <url> <filename>" + ChatColor.RESET + " — Download image");
        s.sendMessage(ChatColor.AQUA + "/image place <filename>" + ChatColor.RESET + " — Place image");
        s.sendMessage(ChatColor.AQUA + "/image remove" + ChatColor.RESET + " — Remove placed image");
    }

    @Subcommand("list")
    @Permission("yamipa.list")
    public static void listImages(CommandSender sender) {
        SortedMap<String, ImageFile> images = YamipaPlugin.getInstance().getStorage().getAll();

        // Are there any images available?
        if (images.size() == 0) {
            sender.sendMessage(ChatColor.RED + "No images found in the images directory");
            return;
        }

        // Build list of images
        images.forEach((filename, __) -> sender.sendMessage("" + ChatColor.GOLD + filename));
    }

    @Subcommand("download")
    @Permission("yamipa.download")
    public static void downloadImage(CommandSender sender, @AStringArgument String url, @AStringArgument String filename) {
        // TODO: not implemented
    }

    @Subcommand("place")
    @Permission("yamipa.place")
    public static void placeImage(
        Player player,
        @AStringArgument String filename,
        @AIntegerArgument(min=1, max=FakeImage.MAX_DIMENSION) int width
    ) {
        placeImage(player, filename, width, 0);
    }

    @Subcommand("place")
    @Permission("yamipa.place")
    public static void placeImage(
        Player player,
        @AStringArgument String filename,
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
            plugin.getRenderer().addImage(new FakeImage(image, location, face, rotation, width, finalHeight));
        });
        task.onFailure(() -> ActionBar.send(player, ChatColor.RED + "Image placing canceled"));
        task.run("Right click a block to continue");
    }

    @Subcommand("remove")
    @Permission("yamipa.remove")
    public static void removeImage(Player player) {
        YamipaPlugin plugin = YamipaPlugin.getInstance();

        // Ask user to select fake image
        SelectBlockTask task = new SelectBlockTask(player);
        task.onSuccess((location, face) -> {
            FakeImage image = plugin.getRenderer().getImage(location, face);
            if (image == null) {
                ActionBar.send(player, ChatColor.RED + "That is not a valid image!");
            } else {
                ActionBar.send(player, "");
                plugin.getRenderer().removeImage(image);
            }
        });
        task.onFailure(() -> ActionBar.send(player, ChatColor.RED + "Image removing canceled"));
        task.run("Right click an image to continue");
    }
}
