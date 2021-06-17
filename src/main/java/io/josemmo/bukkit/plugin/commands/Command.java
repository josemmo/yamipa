package io.josemmo.bukkit.plugin.commands;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.josemmo.bukkit.plugin.commands.arguments.Argument;
import io.josemmo.bukkit.plugin.utils.Internals;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class Command {
    private final String name;
    private final List<Argument> arguments = new ArrayList<>();
    private Predicate<CommandSender> requirementHandler = __ -> true;
    private BiConsumer<CommandSender, Object[]> executesHandler = null;
    private BiConsumer<Player, Object[]> executesPlayerHandler = null;
    private final List<Command> subcommands = new ArrayList<>();

    /**
     * Command constructor
     * @param name Command literal name
     */
    public Command(@NotNull String name) {
        this.name = name;
    }

    /**
     * Add argument to command
     * @param  argument Argument instance
     * @return          This instance
     */
    public @NotNull Command withArgument(@NotNull Argument argument) {
        arguments.add(argument);
        return this;
    }

    /**
     * Add requirement handler to this command
     * @param  handler Handler to determine command availability
     * @return         This instance
     */
    public @NotNull Command withRequirement(@NotNull Predicate<CommandSender> handler) {
        requirementHandler = handler;
        return this;
    }

    /**
     * Add permission requirement to this command
     * @param  permission Permission name
     * @return            This instance
     */
    public @NotNull Command withPermission(@NotNull String permission) {
        return withRequirement(sender -> sender.hasPermission(permission));
    }

    /**
     * Add handler that will be executed when the command gets called
     * @param  handler Command handler
     * @return         This instance
     */
    public @NotNull Command executes(@NotNull BiConsumer<CommandSender, Object[]> handler) {
        executesHandler = handler;
        return this;
    }

    /**
     * Add handler that will be executed when the command gets called by a player
     * @param  handler Command handler
     * @return         This instance
     */
    public @NotNull Command executesPlayer(@NotNull BiConsumer<Player, Object[]> handler) {
        executesPlayerHandler = handler;
        return this;
    }

    /**
     * Add subcommand to this command
     * @param  name Subcommand literal name
     * @return      The new subcommand
     */
    public @NotNull Command addSubcommand(@NotNull String name) {
        Command subcommand = new Command(name);
        subcommands.add(subcommand);
        return subcommand;
    }

    /**
     * Build command and all of its children
     * @return Literal argument builder instance
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public @NotNull LiteralArgumentBuilder<?> build() {
        LiteralArgumentBuilder<?> root = LiteralArgumentBuilder.literal(name);

        // Add arguments and execution handler
        buildElement(root, 0);

        // Add subcommands
        for (Command subcommand : subcommands) {
            root.then((ArgumentBuilder) subcommand.build());
        }

        return root;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private @NotNull ArgumentBuilder buildElement(@NotNull ArgumentBuilder parent, int argIndex) {
        // Chain command elements from the bottom-up
        if (argIndex < arguments.size()) {
            parent.then(buildElement(arguments.get(argIndex).build(), argIndex+1)).executes(ctx -> {
                CommandSender sender = Internals.getBukkitSender(ctx.getSource());
                sender.sendMessage(ChatColor.RED + "Missing required arguments");
                return 1;
            });
            return parent;
        }

        // Attach requirement handler to last command element
        parent.requires(source -> {
            CommandSender sender = Internals.getBukkitSender(source);
            return requirementHandler.test(sender);
        });

        // Attach execution handler to last command element
        if (executesPlayerHandler != null) {
            parent.executes(ctx -> {
                CommandSender sender = Internals.getBukkitSender(ctx.getSource());
                if (sender instanceof Player) {
                    executesPlayerHandler.accept((Player) sender, getArgumentValues(sender, ctx));
                } else {
                    sender.sendMessage(ChatColor.RED + "Only in-game players can execute this command!");
                }
                return 0;
            });
        } else if (executesHandler != null) {
            parent.executes(ctx -> {
                CommandSender sender = Internals.getBukkitSender(ctx.getSource());
                executesHandler.accept(sender, getArgumentValues(sender, ctx));
                return 0;
            });
        }
        return parent;
    }

    private @NotNull Object[] getArgumentValues(
        @NotNull CommandSender sender,
        @NotNull CommandContext<?> ctx
    ) throws CommandSyntaxException {
        List<Object> argValues = new ArrayList<>();
        for (Argument argument : arguments) {
            Object value = ctx.getArgument(argument.getName(), Object.class);
            value = argument.parse(sender, value);
            argValues.add(value);
        }
        return argValues.toArray(new Object[0]);
    }
}
