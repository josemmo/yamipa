package io.josemmo.bukkit.plugin.commands.arguments;

import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public abstract class Argument {
    protected final String name;

    /**
     * Argument constructor
     * @param name Argument name
     */
    public Argument(@NotNull String name) {
        this.name = name;
    }

    /**
     * Get argument name
     * @return Argument name
     */
    public @NotNull String getName() {
        return name;
    }

    /**
     * Build argument
     * @return Required Argument Builder instance
     */
    public abstract @NotNull RequiredArgumentBuilder<?, ?> build();

    /**
     * Parse argument value
     * @param  sender   Command sender
     * @param  rawValue Argument value provided by Brigadier
     * @return          Parsed argument value
     */
    public @NotNull Object parse(@NotNull CommandSender sender, @NotNull Object rawValue) throws CommandSyntaxException {
        return rawValue;
    }

    protected @NotNull CommandSyntaxException newException(@NotNull String message) {
        return new SimpleCommandExceptionType(new LiteralMessage(message)).create();
    }
}
