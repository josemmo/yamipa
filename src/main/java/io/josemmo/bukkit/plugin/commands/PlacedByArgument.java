package io.josemmo.bukkit.plugin.commands;

import dev.jorel.commandapi.arguments.StringArgument;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.ImageRenderer;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Set;
import java.util.TreeSet;

public class PlacedByArgument extends StringArgument {
    public PlacedByArgument(@NotNull String nodeName) {
        super(nodeName);
        overrideSuggestions(__ -> {
            ImageRenderer renderer = YamipaPlugin.getInstance().getRenderer();
            Set<String> suggestions = new TreeSet<>();

            // Get nicknames and UUID for every player that has at least one placed image
            for (OfflinePlayer player : renderer.getPlayersWithPlacedImages()) {
                suggestions.add(player.getUniqueId().toString());
                if (player.getName() != null) {
                    suggestions.add(player.getName());
                }
            }

            return suggestions.toArray(new String[0]);
        });
    }

    /**
     * Get offline player instance from argument string
     * @param  argument Argument string
     * @return          Offline player or NULL if not found
     */
    public static @Nullable OfflinePlayer getPlayer(@NotNull String argument) {
        ImageRenderer renderer = YamipaPlugin.getInstance().getRenderer();
        for (OfflinePlayer player : renderer.getPlayersWithPlacedImages()) {
            if (argument.equals(player.getName()) || argument.equals(player.getUniqueId().toString())) {
                return player;
            }
        }
        return null;
    }
}
