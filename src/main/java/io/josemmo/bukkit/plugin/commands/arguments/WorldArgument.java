package io.josemmo.bukkit.plugin.commands.arguments;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import java.util.concurrent.CompletableFuture;

public class WorldArgument extends StringArgument {
    /**
     * World Argument constructor
     * @param name Argument name
     */
    public WorldArgument(@NotNull String name) {
        super(name);
    }

    @Override
    public @NotNull CompletableFuture<Suggestions> suggest(@NotNull CommandSender sender, @NotNull SuggestionsBuilder builder) {
        for (World world : Bukkit.getWorlds()) {
            builder.suggest(world.getName());
        }
        return builder.buildFuture();
    }

    @Override
    public @NotNull Object parse(@NotNull CommandSender sender, @NotNull Object rawValue) throws CommandSyntaxException {
        World world = Bukkit.getWorld((String) rawValue);
        if (world == null) {
            throw newException("Expected world name");
        }
        return world;
    }
}
