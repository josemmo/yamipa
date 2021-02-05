package io.josemmo.bukkit.plugin.commands;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.FakeImage;

public class ImageCommandBridge {
    /**
     * Register command
     */
    public static void register() {
        CommandAPICommand group = new CommandAPICommand("image").withAliases("yamipa", "images");

        // Help command
        group.executes((sender, __) -> {
            ImageCommand.showHelp(sender);
        });

        // List command
        CommandAPICommand list = new CommandAPICommand("list")
            .withPermission("yamipa.list")
            .executes((sender, __) -> {
                ImageCommand.listImages(sender, 1);
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

        // Remove command (in radius)
        CommandAPICommand removeInRadius = new CommandAPICommand("remove")
            .withPermission("yamipa.remove.radius")
            .withArguments(new IntegerArgument("radius", 1))
            .executesPlayer((sender, args) -> {
                ImageCommand.removeImagesInRadius(sender, (int) args[0]);
            });
        group.withSubcommand(removeInRadius);

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
