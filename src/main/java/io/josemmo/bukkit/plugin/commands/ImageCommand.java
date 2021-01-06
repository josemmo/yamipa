package io.josemmo.bukkit.plugin.commands;

import dev.jorel.commandapi.annotations.*;
import dev.jorel.commandapi.annotations.arguments.AStringArgument;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command("image")
@Alias({"yamipa", "images"})
public class ImageCommand {
    @Default
    public static void showHelp(CommandSender sender) {
        sender.sendMessage("-------------------- Yamipa Help --------------------");
        sender.sendMessage("/image                            Show this help     ");
        sender.sendMessage("/image list                       List all images    ");
        sender.sendMessage("/image download <url> <filename>  Download image     ");
        sender.sendMessage("/image place <filename>           Place image        ");
        sender.sendMessage("/image remove                     Remove placed image");
        sender.sendMessage("-----------------------------------------------------");
    }

    @Subcommand("list")
    @Permission("yamipa.list")
    public static void listImages(Player player) {
        // TODO: not implemented
    }

    @Subcommand("download")
    @Permission("yamipa.download")
    public static void downloadImage(Player player, @AStringArgument String url, @AStringArgument String filename) {
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
