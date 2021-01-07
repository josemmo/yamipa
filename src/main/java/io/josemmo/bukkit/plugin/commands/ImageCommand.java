package io.josemmo.bukkit.plugin.commands;

import dev.jorel.commandapi.annotations.*;
import dev.jorel.commandapi.annotations.arguments.AStringArgument;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
        SortedMap<String, ImageFile> images = YamipaPlugin.getInstance().getImageStorage().getAll();

        // Are there any images available?
        if (images.size() == 0) {
            sender.sendMessage(ChatColor.RED + "No images found in the images directory");
            return;
        }

        // Build list of images
        images.forEach((filename, image) -> {
            sender.sendMessage("" + ChatColor.GOLD + filename);
        });
    }

    @Subcommand("download")
    @Permission("yamipa.download")
    public static void downloadImage(CommandSender sender, @AStringArgument String url, @AStringArgument String filename) {
        // TODO: not implemented
    }

    @Subcommand("place")
    @Permission("yamipa.place")
    public static void placeImage(Player player, @AStringArgument String filename) {
        // TODO: not implemented
    }

    @Subcommand("remove")
    @Permission("yamipa.remove")
    public static void removeImage(Player player) {
        // TODO: not implemented
    }

}
