package io.josemmo.bukkit.plugin.commands.arguments;

import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.ImageRenderer;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class PlacedByArgument extends StringArgument {
    /**
     * Placed by Argument constructor
     * @param name Argument name
     */
    public PlacedByArgument(@NotNull String name) {
        super(name);
    }

    @Override
    public @NotNull RequiredArgumentBuilder<?, ?> build() {
        return super.build().suggests(this::getSuggestions);
    }

    @Override
    public @NotNull Object parse(@NotNull CommandSender sender, @NotNull Object rawValue) throws CommandSyntaxException {
        OfflinePlayer player = getAllowedValues().get((String) rawValue);
        if (player == null) {
            throw newException("Expected player with placed images (name or UUID)");
        }
        return player;
    }

    private @NotNull CompletableFuture<Suggestions> getSuggestions(
        @NotNull CommandContext<?> ctx,
        @NotNull SuggestionsBuilder builder
    ) {
        getAllowedValues().keySet().forEach(builder::suggest);
        return builder.buildFuture();
    }

    private @NotNull Map<String, OfflinePlayer> getAllowedValues() {
        Map<String, OfflinePlayer> values = new HashMap<>();
        ImageRenderer renderer = YamipaPlugin.getInstance().getRenderer();
        for (OfflinePlayer player : renderer.getPlayersWithPlacedImages()) {
            values.put(player.getUniqueId().toString(), player);
            if (player.getName() != null) {
                values.put(player.getName(), player);
            }
        }
        return values;
    }
}
