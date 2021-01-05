package io.josemmo.bukkit.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class YamipaPlugin extends JavaPlugin {
    @Override
    public void onEnable() {
        getLogger().info("Enabled YamipaPlugin");
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabled YamipaPlugin");
    }
}
