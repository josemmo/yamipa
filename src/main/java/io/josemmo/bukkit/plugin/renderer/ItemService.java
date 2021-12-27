package io.josemmo.bukkit.plugin.renderer;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.commands.ImageCommand;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.InteractWithEntityListener;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.Objects;

public class ItemService extends InteractWithEntityListener implements Listener {
    private static final YamipaPlugin plugin = YamipaPlugin.getInstance();
    private static final NamespacedKey NSK_FILENAME = new NamespacedKey(plugin, "filename");
    private static final NamespacedKey NSK_WIDTH = new NamespacedKey(plugin, "width");
    private static final NamespacedKey NSK_HEIGHT = new NamespacedKey(plugin, "height");
    private static final NamespacedKey NSK_FLAGS = new NamespacedKey(plugin, "flags");

    /**
     * Get image item
     * @param  image  Image file
     * @param  amount Stack amount
     * @param  width  Image width in blocks
     * @param  height Image height in blocks
     * @param  flags  Image flags
     * @return        Image item
     */
    public static @NotNull ItemStack getImageItem(@NotNull ImageFile image, int amount, int width, int height, int flags) {
        ItemStack itemStack = new ItemStack(Material.ITEM_FRAME, amount);
        ItemMeta itemMeta = Objects.requireNonNull(itemStack.getItemMeta());

        // Set metadata
        PersistentDataContainer itemData = itemMeta.getPersistentDataContainer();
        itemMeta.setDisplayName(image.getName() + ChatColor.AQUA + " (" + width + "x" + height + ")");
        itemMeta.setLore(Collections.singletonList("Yamipa image"));
        itemData.set(NSK_FILENAME, PersistentDataType.STRING, image.getName());
        itemData.set(NSK_WIDTH, PersistentDataType.INTEGER, width);
        itemData.set(NSK_HEIGHT, PersistentDataType.INTEGER, height);
        itemData.set(NSK_FLAGS, PersistentDataType.INTEGER, flags);
        itemStack.setItemMeta(itemMeta);

        return itemStack;
    }

    /**
     * Start service
     */
    public void start() {
        register();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Stop service
     */
    public void stop() {
        unregister();
        HandlerList.unregisterAll(this);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCraftItem(@NotNull PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        for (@Nullable ItemStack item : inventory.getMatrix()) {
            if (item == null) continue;

            // Get metadata from item
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta == null) continue;

            // Prevent crafting recipes with image items
            if (itemMeta.getPersistentDataContainer().has(NSK_FILENAME, PersistentDataType.STRING)) {
                inventory.setResult(new ItemStack(Material.AIR));
                break;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onPlaceItem(@NotNull HangingPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemStack();
        if (player == null || item == null) return;

        // Get metadata from item
        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return;
        PersistentDataContainer itemData = itemMeta.getPersistentDataContainer();
        String filename = itemData.get(NSK_FILENAME, PersistentDataType.STRING);
        if (filename == null) return;
        Integer width = itemData.get(NSK_WIDTH, PersistentDataType.INTEGER);
        Integer height = itemData.get(NSK_HEIGHT, PersistentDataType.INTEGER);
        Integer flags = itemData.get(NSK_FLAGS, PersistentDataType.INTEGER);
        if (width == null || height == null || flags == null) {
            plugin.warning(player + " tried to place corrupted image item (missing width/height/flags properties)");
            return;
        }

        // Validate filename
        ImageFile image = YamipaPlugin.getInstance().getStorage().get(filename);
        if (image == null) {
            plugin.warning(player + " tried to place corrupted image item (\"" + filename + "\" no longer exists)");
            player.sendMessage(ChatColor.RED + "Image file \"" + filename + "\" no longer exists");
            return;
        }

        // Prevent item frame placing
        event.setCancelled(true);

        // Try to place image in world
        Location location = event.getBlock().getLocation();
        boolean success = ImageCommand.placeImage(player, image, width, height, flags, location, event.getBlockFace());
        if (!success) return;

        // Decrement item from player's inventory
        if (player.getGameMode() == GameMode.CREATIVE) return;
        PlayerInventory inventory = player.getInventory();
        int itemIndex = inventory.first(item);
        int amount = item.getAmount();
        if (amount > 1) {
            item.setAmount(amount - 1);
            inventory.setItem(itemIndex, item);
        } else {
            inventory.setItem(itemIndex, new ItemStack(Material.AIR));
        }
    }

    @Override
    public boolean onAttack(@NotNull Player player, @NotNull Block block, @NotNull BlockFace face) {
        ImageRenderer renderer = plugin.getRenderer();
        Location location = block.getLocation();

        // Has the player clicked a removable placed image?
        FakeImage image = renderer.getImage(location, face);
        if (image == null || !image.hasFlag(FakeImage.FLAG_REMOVABLE)) return true;

        // Remove image from renderer
        renderer.removeImage(image);

        // Drop image item
        if (player.getGameMode() == GameMode.SURVIVAL && image.hasFlag(FakeImage.FLAG_DROPPABLE)) {
            ImageFile imageFile = Objects.requireNonNull(image.getFile());
            ItemStack imageItem = getImageItem(imageFile, 1, image.getWidth(), image.getHeight(), image.getFlags());
            Location dropLocation = location.clone().add(0.5, -0.5, 0.5).add(face.getDirection());
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                block.getWorld().dropItem(dropLocation, imageItem);
            });
        }

        return false;
    }

    @Override
    public boolean onInteract(@NotNull Player player, @NotNull Block block, @NotNull BlockFace face) {
        // Intentionally left blank
        return true;
    }
}
