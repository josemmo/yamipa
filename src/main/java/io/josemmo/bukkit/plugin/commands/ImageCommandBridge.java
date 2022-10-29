package io.josemmo.bukkit.plugin.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.commands.arguments.*;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.Internals;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ImageCommandBridge {
    public static final String COMMAND_NAME = "yamipa";
    public static final String[] COMMAND_ALIASES = new String[] {"image", "images", "upload"};

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
        root.withPermission(
                "yamipa.command.clear", "yamipa.clear",
                "yamipa.command.describe", "yamipa.describe",
                "yamipa.command.download", "yamipa.download",
                "yamipa.command.give", "yamipa.give",
                "yamipa.command.list", "yamipa.list",
                "yamipa.command.place", "yamipa.place",
                "yamipa.command.remove.own", "yamipa.remove",
                "yamipa.command.top", "yamipa.top",
                "yamipa.command.upload", "yamipa.upload"
            )
            .executes((sender, args) -> {
                ImageCommand.showHelp(sender, (String) args[0]);
            });

        // Clear command
        root.addSubcommand("clear")
            .withPermission("yamipa.command.clear", "yamipa.clear")
            .withArgument(new IntegerArgument("x"))
            .withArgument(new IntegerArgument("z"))
            .withArgument(new WorldArgument("world"))
            .withArgument(new IntegerArgument("radius", 1))
            .withArgument(new PlacedByArgument("placedBy"))
            .executes((sender, args) -> {
                Location origin = new Location((World) args[3], (int) args[1], 0, (int) args[2]);
                ImageCommand.clearImages(sender, origin, (int) args[4], (OfflinePlayer) args[5]);
            });
        root.addSubcommand("clear")
            .withPermission("yamipa.command.clear", "yamipa.clear")
            .withArgument(new IntegerArgument("x"))
            .withArgument(new IntegerArgument("z"))
            .withArgument(new WorldArgument("world"))
            .withArgument(new IntegerArgument("radius", 1))
            .executes((sender, args) -> {
                Location origin = new Location((World) args[3], (int) args[1], 0, (int) args[2]);
                ImageCommand.clearImages(sender, origin, (int) args[4], null);
            });

        // Describe command
        root.addSubcommand("describe")
            .withPermission("yamipa.command.describe", "yamipa.describe")
            .executesPlayer((player, __) -> {
                ImageCommand.describeImage(player);
            });

        // Download command
        root.addSubcommand("download")
            .withPermission("yamipa.command.download", "yamipa.download")
            .withArgument(new StringArgument("url"))
            .withArgument(new StringArgument("filename"))
            .executes((sender, args) -> {
                ImageCommand.downloadImage(sender, (String) args[1], (String) args[2]);
            });

        // Upload command
        root.addSubcommand("upload")
            .withPermission("yamipa.command.upload", "yamipa.upload")
            .executesPlayer((player, __) -> {
                String message = YamipaPlugin.uploadUrl;
                TextComponent pre = new TextComponent("Upload link: ");
                TextComponent text = new TextComponent(message);
                text.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder().append(pre).append(text).create()));
                text.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, YamipaPlugin.uploadUrl));
                player.spigot().sendMessage(text);
            });

        // Give subcommand
        root.addSubcommand("give")
            .withPermission("yamipa.command.give", "yamipa.give")
            .withArgument(new OnlinePlayerArgument("player"))
            .withArgument(new ImageFileArgument("filename"))
            .withArgument(new IntegerArgument("amount", 1, 64))
            .withArgument(new IntegerArgument("width", 1, FakeImage.MAX_DIMENSION))
            .withArgument(new IntegerArgument("height", 1, FakeImage.MAX_DIMENSION))
            .withArgument(new ImageFlagsArgument("flags", FakeImage.DEFAULT_GIVE_FLAGS))
            .executes((sender, args) -> {
                ImageCommand.giveImageItems(sender, (Player) args[1], (ImageFile) args[2], (int) args[3],
                    (int) args[4], (int) args[5], (int) args[6]);
            });
        root.addSubcommand("give")
            .withPermission("yamipa.command.give", "yamipa.give")
            .withArgument(new OnlinePlayerArgument("player"))
            .withArgument(new ImageFileArgument("filename"))
            .withArgument(new IntegerArgument("amount", 1, 64))
            .withArgument(new IntegerArgument("width", 1, FakeImage.MAX_DIMENSION))
            .withArgument(new IntegerArgument("height", 1, FakeImage.MAX_DIMENSION))
            .executes((sender, args) -> {
                ImageCommand.giveImageItems(sender, (Player) args[1], (ImageFile) args[2], (int) args[3],
                    (int) args[4], (int) args[5], FakeImage.DEFAULT_GIVE_FLAGS);
            });
        root.addSubcommand("give")
            .withPermission("yamipa.command.give", "yamipa.give")
            .withArgument(new OnlinePlayerArgument("player"))
            .withArgument(new ImageFileArgument("filename"))
            .withArgument(new IntegerArgument("amount", 1, 64))
            .withArgument(new IntegerArgument("width", 1, FakeImage.MAX_DIMENSION))
            .executes((sender, args) -> {
                ImageCommand.giveImageItems(sender, (Player) args[1], (ImageFile) args[2], (int) args[3],
                    (int) args[4], 0, FakeImage.DEFAULT_GIVE_FLAGS);
            });

        // List subcommand
        root.addSubcommand("list")
            .withPermission("yamipa.command.list", "yamipa.list")
            .withArgument(new IntegerArgument("page", 1))
            .executes((sender, args) -> {
                ImageCommand.listImages(sender, (int) args[1]);
            });
        root.addSubcommand("list")
            .withPermission("yamipa.command.list", "yamipa.list")
            .executes((sender, __) -> {
                boolean isPlayer = (sender instanceof Player);
                ImageCommand.listImages(sender, isPlayer ? 1 : 0);
            });

        // Place subcommand
        root.addSubcommand("place")
            .withPermission("yamipa.command.place", "yamipa.place")
            .withArgument(new ImageFileArgument("filename"))
            .withArgument(new IntegerArgument("width", 1, FakeImage.MAX_DIMENSION))
            .withArgument(new IntegerArgument("height", 1, FakeImage.MAX_DIMENSION))
            .withArgument(new ImageFlagsArgument("flags", FakeImage.DEFAULT_PLACE_FLAGS))
            .executesPlayer((player, args) -> {
                ImageCommand.placeImage(player, (ImageFile) args[1], (int) args[2], (int) args[3], (int) args[4]);
            });
        root.addSubcommand("place")
            .withPermission("yamipa.command.place", "yamipa.place")
            .withArgument(new ImageFileArgument("filename"))
            .withArgument(new IntegerArgument("width", 1, FakeImage.MAX_DIMENSION))
            .withArgument(new IntegerArgument("height", 1, FakeImage.MAX_DIMENSION))
            .executesPlayer((player, args) -> {
                ImageCommand.placeImage(player, (ImageFile) args[1], (int) args[2], (int) args[3],
                    FakeImage.DEFAULT_PLACE_FLAGS);
            });
        root.addSubcommand("place")
            .withPermission("yamipa.command.place", "yamipa.place")
            .withArgument(new ImageFileArgument("filename"))
            .withArgument(new IntegerArgument("width", 1, FakeImage.MAX_DIMENSION))
            .executesPlayer((player, args) -> {
                ImageCommand.placeImage(player, (ImageFile) args[1], (int) args[2], 0,
                    FakeImage.DEFAULT_PLACE_FLAGS);
            });

        // Remove subcommand
        root.addSubcommand("remove")
            .withPermission("yamipa.command.remove.own", "yamipa.remove")
            .executesPlayer((player, __) -> {
                ImageCommand.removeImage(player);
            });

        // Top subcommand
        root.addSubcommand("top")
            .withPermission("yamipa.command.top", "yamipa.top")
            .executes((sender, __) -> {
                ImageCommand.showTopPlayers(sender);
            });

        return root;
    }

    private static void fixPermissions(@NotNull String commandName) {
        org.bukkit.command.Command command = Internals.getCommandMap().getCommand(commandName);
        if (command != null) { // Command may have been aliased to null in "commands.yml"
            command.setPermission(null);
        }
    }
}
