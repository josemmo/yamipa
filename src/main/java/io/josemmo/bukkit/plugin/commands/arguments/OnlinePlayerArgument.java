package io.josemmo.bukkit.plugin.commands.arguments;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class OnlinePlayerArgument extends StringArgument {
    /**
     * Online player argument constructor
     * @param name Argument name
     */
    public OnlinePlayerArgument(@NotNull String name) {
        super(name);
    }

    @Override
    public @NotNull CompletableFuture<Suggestions> suggest(@NotNull CommandSender sender, @NotNull SuggestionsBuilder builder) {
        getAllowedValues().keySet().forEach(builder::suggest);
        return builder.buildFuture();
    }

    @Override
    public @NotNull Object parse(@NotNull CommandSender sender, @NotNull Object rawValue) throws CommandSyntaxException {
        Player player = getAllowedValues().get((String) rawValue);
        if (player == null) {
            throw newException("Expected online player (name or UUID)");
        }
        return player;
    }

    private @NotNull Map<String, Player> getAllowedValues() {
        Map<String, Player> values = new HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            values.put(player.getUniqueId().toString(), player);
            values.put(player.getName(), player);
        }
        return values;
    }
}
