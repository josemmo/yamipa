package io.josemmo.bukkit.plugin.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.Location2DArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public class ImageCommandBridge {
    /**
     * Register command
     */
    public static void register() {
        CommandAPICommand group = new CommandAPICommand("image")
            .withRequirement(sender -> sender.hasPermission("yamipa.*") ||
                sender.hasPermission("yamipa.clear") ||
                sender.hasPermission("yamipa.describe") ||
                sender.hasPermission("yamipa.download") ||
                sender.hasPermission("yamipa.list") ||
                sender.hasPermission("yamipa.place") ||
                sender.hasPermission("yamipa.remove"))
            .withAliases("yamipa", "images");

        // Help command
        group.executes((sender, __) -> {
            ImageCommand.showHelp(sender);
        });

        // List command
        CommandAPICommand list = new CommandAPICommand("list")
            .withPermission("yamipa.list")
            .executes((sender, __) -> {
                boolean isPlayer = (sender instanceof Player);
                ImageCommand.listImages(sender, isPlayer ? 1 : 0);
            });
        group.withSubcommand(list);

        // List command (with page argument)
        CommandAPICommand listWithPage = new CommandAPICommand("list")
            .withPermission("yamipa.list")
            .withArguments(new IntegerArgument("page", 1))
            .executes((sender, args) -> {
                ImageCommand.listImages(sender, (int) args[0]);
            });
        group.withSubcommand(listWithPage);

        // Download command
        CommandAPICommand download = new CommandAPICommand("download")
            .withPermission("yamipa.download")
            .withArguments(new TextArgument("url"))
            .withArguments(new TextArgument("filename"))
            .executes((sender, args) -> {
                ImageCommand.downloadImage(sender, (String) args[0], (String) args[1]);
            });
        group.withSubcommand(download);

        // Place command (only width)
        CommandAPICommand placeOnlyWidth = new CommandAPICommand("place")
            .withPermission("yamipa.place")
            .withArguments(getFilenameArgument())
            .withArguments(new IntegerArgument("width", 1, FakeImage.MAX_DIMENSION))
            .executesPlayer((sender, args) -> {
                ImageCommand.placeImage(sender, (String) args[0], (int) args[1], 0);
            });
        group.withSubcommand(placeOnlyWidth);

        // Place command
        CommandAPICommand place = new CommandAPICommand("place")
            .withPermission("yamipa.place")
            .withArguments(getFilenameArgument())
            .withArguments(new IntegerArgument("width", 1, FakeImage.MAX_DIMENSION))
            .withArguments(new IntegerArgument("height", 1, FakeImage.MAX_DIMENSION))
            .executesPlayer((sender, args) -> {
                ImageCommand.placeImage(sender, (String) args[0], (int) args[1], (int) args[2]);
            });
        group.withSubcommand(place);

        // Remove command
        CommandAPICommand remove = new CommandAPICommand("remove")
            .withPermission("yamipa.remove")
            .executesPlayer((sender, __) -> {
                ImageCommand.removeImage(sender);
            });
        group.withSubcommand(remove);

        // Clear command
        CommandAPICommand clear = new CommandAPICommand("clear")
            .withPermission("yamipa.clear")
            .withArguments(new Location2DArgument("origin"))
            .withArguments(new IntegerArgument("radius", 1))
            .executes((sender, args) -> {
                ImageCommand.clearImages(sender, (Location) args[0], (int) args[1], null);
            });
        group.withSubcommand(clear);

        // Clear command (with placed by)
        CommandAPICommand clearPlacedBy = new CommandAPICommand("clear")
            .withPermission("yamipa.clear")
            .withArguments(new Location2DArgument("origin"))
            .withArguments(new IntegerArgument("radius", 1))
            .withArguments(new PlacedByArgument("placedBy"))
            .executes((sender, args) -> {
                OfflinePlayer player = PlacedByArgument.getPlayer((String) args[2]);
                if (player == null) {
                    sender.sendMessage(ChatColor.RED + "That player does not have any placed images!");
                } else {
                    ImageCommand.clearImages(sender, (Location) args[0], (int) args[1], player);
                }
            });
        group.withSubcommand(clearPlacedBy);

        // Describe command
        CommandAPICommand describe = new CommandAPICommand("describe")
            .withPermission("yamipa.describe")
            .executesPlayer((sender, __) -> {
                ImageCommand.describeImage(sender);
            });
        group.withSubcommand(describe);

        // Commit commands
        group.register();
    }

    /**
     * Get filename argument
     * @return Filename argument
     */
    private static Argument getFilenameArgument() {
        return new TextArgument("filename")
            .overrideSuggestions(__ -> YamipaPlugin.getInstance().getStorage().getAllFilenames());
    }
}
