package io.josemmo.bukkit.plugin.commands.arguments;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import org.jetbrains.annotations.NotNull;

public class IntegerArgument extends Argument {
    private final int min;
    private final int max;

    /**
     * Integer Argument constructor
     * @param name Argument name
     * @param min  Minimum allowed value
     * @param max  Maximum allowed value
     */
    public IntegerArgument(@NotNull String name, int min, int max) {
        super(name);
        this.min = min;
        this.max = max;
    }

    /**
     * Integer Argument constructor
     * @param name Argument name
     * @param min  Minimum allowed value
     */
    public IntegerArgument(@NotNull String name, int min) {
        this(name, min, Integer.MAX_VALUE);
    }

    /**
     * Integer Argument constructor
     * @param name Argument name
     */
    public IntegerArgument(@NotNull String name) {
        this(name, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    @Override
    public @NotNull RequiredArgumentBuilder<?, ?> build() {
        return RequiredArgumentBuilder.argument(name, IntegerArgumentType.integer(min, max));
    }
}
