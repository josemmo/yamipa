package io.josemmo.bukkit.plugin.commands.arguments;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.CompletableFuture;

public class ImageFileArgument extends StringArgument {
    /**
     * Image File Argument constructor
     * @param name Argument name
     */
    public ImageFileArgument(@NotNull String name) {
        super(name);
    }

    @Override
    public @NotNull RequiredArgumentBuilder<?, ?> build() {
        return super.build().suggests(this::getSuggestions);
    }

    @Override
    public @NotNull Object parse(@NotNull CommandSender sender, @NotNull Object rawValue) throws CommandSyntaxException {
        ImageFile imageFile = YamipaPlugin.getInstance().getStorage().get((String) rawValue);
        if (imageFile == null) {
            throw newException("Image file does not exist");
        }
        return imageFile;
    }

    private @NotNull CompletableFuture<Suggestions> getSuggestions(
        @NotNull CommandContext<?> ctx,
        @NotNull SuggestionsBuilder builder
    ) {
        for (String filename : YamipaPlugin.getInstance().getStorage().getAllFilenames()) {
            builder.suggest("\"" + filename.replace("\\", "/") + "\"");
        }
        return builder.buildFuture();
    }
}
