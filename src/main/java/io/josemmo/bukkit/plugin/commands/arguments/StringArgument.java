package io.josemmo.bukkit.plugin.commands.arguments;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.jetbrains.annotations.NotNull;

public class StringArgument extends Argument {
    /**
     * String Argument constructor
     * @param name Argument name
     */
    public StringArgument(@NotNull String name) {
        super(name);
    }

    @Override
    public @NotNull RequiredArgumentBuilder<?, ?> build() {
        return RequiredArgumentBuilder.argument(name, StringArgumentType.string());
    }
}
