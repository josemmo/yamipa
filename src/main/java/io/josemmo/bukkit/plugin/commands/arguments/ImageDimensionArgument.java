package io.josemmo.bukkit.plugin.commands.arguments;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ImageDimensionArgument extends IntegerArgument {
    /**
     * Dimension Argument constructor
     * @param name Argument name
     */
    public ImageDimensionArgument(@NotNull String name) {
        super(name, 1);
    }

    @Override
    public @NotNull Object parse(@NotNull CommandSender sender, @NotNull Object rawValue) throws CommandSyntaxException {
        int maxDimension = FakeImage.getMaxImageDimension(sender);
        int value = (int) rawValue;
        if (value > maxDimension) {
            throw newException("Image cannot be larger than " + maxDimension + "x" + maxDimension);
        }
        return value;
    }
}
