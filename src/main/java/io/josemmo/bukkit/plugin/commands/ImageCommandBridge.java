package io.josemmo.bukkit.plugin.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.commands.arguments.*;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.Internals;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.Objects;

public class ImageCommandBridge {
    public static final String COMMAND_NAME = "yamipa";
    public static final String[] COMMAND_ALIASES = new String[] {"image", "images"};

    /**
     * Register command
     * @param plugin Plugin instance
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void register(@NotNull YamipaPlugin plugin) {
        CommandDispatcher dispatcher = Internals.getDispatcher();

        // Register command
        LiteralCommandNode<?> commandNode = getRootCommand().build().build();
        dispatcher.getRoot().addChild(commandNode);

        // Register aliases
        for (String alias : COMMAND_ALIASES) {
            LiteralCommandNode<?> aliasNode = new LiteralCommandNode(
                alias,
                commandNode.getCommand(),
                commandNode.getRequirement(),
                commandNode,
                commandNode.getRedirectModifier(),
                commandNode.isFork()
            );
            dispatcher.getRoot().addChild(aliasNode);
        }
        plugin.fine("Registered plugin command and aliases");

        // Fix "minecraft.command.*" permissions
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            fixPermissions(COMMAND_NAME);
            for (String alias : COMMAND_ALIASES) {
                fixPermissions(alias);
            }
            plugin.fine("Fixed command permissions");
        });
    }

    private static @NotNull Command getRootCommand() {
        Command root = new Command(COMMAND_NAME);

        // Help command
        root.withRequirement(sender ->
                sender.hasPermission("yamipa.*") ||
                sender.hasPermission("yamipa.clear") ||
                sender.hasPermission("yamipa.describe") ||
                sender.hasPermission("yamipa.download") ||
                sender.hasPermission("yamipa.list") ||
                sender.hasPermission("yamipa.place") ||
                sender.hasPermission("yamipa.remove") ||
                sender.hasPermission("yamipa.top"))
            .executes((sender, __) -> {
                ImageCommand.showHelp(sender);
            });

        // Clear command
        root.addSubcommand("clear")
            .withPermission("yamipa.clear")
            .withArgument(new IntegerArgument("x"))
            .withArgument(new IntegerArgument("z"))
            .withArgument(new WorldArgument("world"))
            .withArgument(new IntegerArgument("radius", 1))
            .withArgument(new PlacedByArgument("placedBy"))
            .executes((sender, args) -> {
                Location origin = new Location((World) args[2], (int) args[0], 0, (int) args[1]);
                ImageCommand.clearImages(sender, origin, (int) args[3], (OfflinePlayer) args[4]);
            });
        root.addSubcommand("clear")
            .withPermission("yamipa.clear")
            .withArgument(new IntegerArgument("x"))
            .withArgument(new IntegerArgument("z"))
            .withArgument(new WorldArgument("world"))
            .withArgument(new IntegerArgument("radius", 1))
            .executes((sender, args) -> {
                Location origin = new Location((World) args[2], (int) args[0], 0, (int) args[1]);
                ImageCommand.clearImages(sender, origin, (int) args[3], null);
            });

        // Describe command
        root.addSubcommand("describe")
            .withPermission("yamipa.describe")
            .executesPlayer((player, __) -> {
                ImageCommand.describeImage(player);
            });

        // Download command
        root.addSubcommand("download")
            .withPermission("yamipa.download")
            .withArgument(new StringArgument("url"))
            .withArgument(new StringArgument("filename"))
            .executes((sender, args) -> {
                ImageCommand.downloadImage(sender, (String) args[0], (String) args[1]);
            });

        // List subcommand
        root.addSubcommand("list")
            .withPermission("yamipa.list")
            .withArgument(new IntegerArgument("page", 1))
            .executes((sender, args) -> {
                ImageCommand.listImages(sender, (int) args[0]);
            });
        root.addSubcommand("list")
            .withPermission("yamipa.list")
            .executes((sender, __) -> {
                boolean isPlayer = (sender instanceof Player);
                ImageCommand.listImages(sender, isPlayer ? 1 : 0);
            });

        // Place subcommand
        root.addSubcommand("place")
            .withPermission("yamipa.place")
            .withArgument(new ImageFileArgument("filename"))
            .withArgument(new IntegerArgument("width", 1, FakeImage.MAX_DIMENSION))
            .withArgument(new IntegerArgument("height", 1, FakeImage.MAX_DIMENSION))
            .executesPlayer((player, args) -> {
                ImageCommand.placeImage(player, (ImageFile) args[0], (int) args[1], (int) args[2]);
            });
        root.addSubcommand("place")
            .withPermission("yamipa.place")
            .withArgument(new ImageFileArgument("filename"))
            .withArgument(new IntegerArgument("width", 1, FakeImage.MAX_DIMENSION))
            .executesPlayer((player, args) -> {
                ImageCommand.placeImage(player, (ImageFile) args[0], (int) args[1], 0);
            });

        // Remove subcommand
        root.addSubcommand("remove")
            .withPermission("yamipa.remove")
            .executesPlayer((player, __) -> {
                ImageCommand.removeImage(player);
            });

        // Top subcommand
        root.addSubcommand("top")
            .withPermission("yamipa.top")
            .executes((sender, __) -> {
                ImageCommand.showTopPlayers(sender);
            });

        return root;
    }

    private static void fixPermissions(@NotNull String commandName) {
        org.bukkit.command.Command command = Internals.getCommandMap().getCommand(commandName);
        Objects.requireNonNull(command);
        command.setPermission(null);
    }
}
