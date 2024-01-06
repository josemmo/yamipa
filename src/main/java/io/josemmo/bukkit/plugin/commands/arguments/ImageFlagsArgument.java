package io.josemmo.bukkit.plugin.commands.arguments;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class ImageFlagsArgument extends Argument {
    private final int defaultFlags;

    /**
     * Image File Argument constructor
     * @param name         Argument name
     * @param defaultFlags Default flags
     */
    public ImageFlagsArgument(@NotNull String name, int defaultFlags) {
        super(name);
        this.defaultFlags = defaultFlags;
    }

    @Override
    public @NotNull RequiredArgumentBuilder<?, ?> build() {
        return RequiredArgumentBuilder.argument(name, StringArgumentType.greedyString());
    }

    @Override
    public @NotNull CompletableFuture<Suggestions> suggest(@NotNull CommandSender sender, @NotNull SuggestionsBuilder builder) {
        String input = builder.getRemaining().replaceAll("[^A-Z+\\-,]", "");
        int lastIndex = Collections.max(
            Arrays.asList(input.lastIndexOf(","), input.lastIndexOf("+"), input.lastIndexOf("-"))
        );
        input = input.substring(0, lastIndex+1);

        // Add suggestions
        String[] values = new String[] {"ANIM", "REMO", "DROP", "GLOW"};
        for (String value : values) {
            builder.suggest(input + value);
        }

        return builder.buildFuture();
    }

    @Override
    public @NotNull Object parse(@NotNull CommandSender sender, @NotNull Object rawValue) throws CommandSyntaxException {
        String value = (String) rawValue;
        int parsedValue = defaultFlags;

        // Load flags from parts
        String[] parts = value.split(",");
        for (String part : parts) {
            // Parse modifier
            String modifier = "";
            if (part.startsWith("+") || part.startsWith("-")) {
                modifier = part.substring(0, 1);
                part = part.substring(1);
            }

            // Parse flag name
            int flag;
            switch (part) {
                case "ANIM":
                    flag = FakeImage.FLAG_ANIMATABLE;
                    break;
                case "REMO":
                    flag = FakeImage.FLAG_REMOVABLE;
                    break;
                case "DROP":
                    flag = FakeImage.FLAG_DROPPABLE;
                    break;
                case "GLOW":
                    flag = FakeImage.FLAG_GLOWING;
                    break;
                default:
                    throw newException("Unrecognized flag \"" + part + "\"");
            }

            // Apply flag to value
            if (modifier.equals("+")) {
                parsedValue |= flag;
            } else if (modifier.equals("-")) {
                parsedValue &= ~flag;
            } else {
                parsedValue = flag;
            }
        }

        return parsedValue;
    }
}
