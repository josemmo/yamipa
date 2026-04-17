package io.josemmo.bukkit.plugin.renderer;

import com.comphenix.protocol.events.ListenerPriority;
import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.commands.ImageCommand;
import io.josemmo.bukkit.plugin.interaction.SelectFakeItemFrameListener;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.ActionBar;
import io.josemmo.bukkit.plugin.utils.BlockFaceWithRotation;
import io.josemmo.bukkit.plugin.utils.Logger;
import org.bukkit.*;
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

public class ItemService extends SelectFakeItemFrameListener implements Listener {
    private static final Logger LOGGER = Logger.getLogger("ItemService");
    private static final NamespacedKey NSK_FILENAME;
    private static final NamespacedKey NSK_WIDTH;
    private static final NamespacedKey NSK_HEIGHT;
    private static final NamespacedKey NSK_FLAGS;

    static {
        YamipaPlugin plugin = YamipaPlugin.getInstance(); // Only used for getting namespace, reference will be freed
        NSK_FILENAME = new NamespacedKey(plugin, "filename");
        NSK_WIDTH = new NamespacedKey(plugin, "width");
        NSK_HEIGHT = new NamespacedKey(plugin, "height");
        NSK_FLAGS = new NamespacedKey(plugin, "flags");
    }

    /**
     * Get image item
     * @param  image  Image file
     * @param  amount Stack amount
     * @param  width  Image width in blocks
     * @param  height Image height in blocks
     * @param  flags  Image flags
     * @return        Image item
     */
    @SuppressWarnings("deprecation")
    public static @NotNull ItemStack getImageItem(@NotNull ImageFile image, int amount, int width, int height, int flags) {
        ItemStack itemStack = new ItemStack(Material.ITEM_FRAME, amount);
        ItemMeta itemMeta = Objects.requireNonNull(itemStack.getItemMeta());

        // Set metadata
        PersistentDataContainer itemData = itemMeta.getPersistentDataContainer();
        itemMeta.setDisplayName(image.getFilename() + ChatColor.AQUA + " (" + width + "x" + height + ")");
        itemMeta.setLore(Collections.singletonList("Yamipa image"));
        itemData.set(NSK_FILENAME, PersistentDataType.STRING, image.getFilename());
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
        YamipaPlugin plugin = YamipaPlugin.getInstance();
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
    @SuppressWarnings("deprecation")
    public void onPlaceItem(@NotNull HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player == null) return;
        PlayerInventory inventory = player.getInventory();

        // Prepare variables to hold item metadata
        boolean isOffHand = false;
        ItemStack item = null;
        String filename = null;
        Integer width = null;
        Integer height = null;
        Integer flags = null;

        // Find placed item stack in player's inventory
        // NOTE: we cannot use `event.getItemStack()` as it's only supported since 1.17.1
        for (int i=0; i<2; i++) {
            isOffHand = (i == 1);
            item = isOffHand ? inventory.getItemInOffHand() : inventory.getItemInMainHand();

            // Get item metadata
            ItemMeta itemMeta = item.getItemMeta();
            if (itemMeta == null) continue;
            PersistentDataContainer itemData = itemMeta.getPersistentDataContainer();
            filename = itemData.get(NSK_FILENAME, PersistentDataType.STRING);
            if (filename == null) continue;

            // Found item, parse metadata
            width = itemData.get(NSK_WIDTH, PersistentDataType.INTEGER);
            height = itemData.get(NSK_HEIGHT, PersistentDataType.INTEGER);
            flags = itemData.get(NSK_FLAGS, PersistentDataType.INTEGER);
            break;
        }

        // Validate item and metadata
        if (filename == null) {
            return;
        }
        if (width == null || height == null || flags == null) {
            LOGGER.warning(player + " tried to place corrupted image item (missing width/height/flags properties)");
            return;
        }

        // Validate filename
        ImageFile image = YamipaPlugin.getInstance().getStorage().get(filename);
        if (image == null) {
            LOGGER.warning(player + " tried to place corrupted image item (\"" + filename + "\" no longer exists)");
            ActionBar.send(player, ChatColor.RED + "Image file \"" + filename + "\" no longer exists");
            return;
        }

        // Prevent item frame placing
        event.setCancelled(true);

        // Validate player permissions
        if (!player.hasPermission("yamipa.item.place")) {
            ActionBar.send(player, ChatColor.RED + "You're not allowed to place image items!");
            return;
        }

        // Try to place image in world
        // NOTE: We correct the block location to avoid off-by-one errors
        BlockFace face = event.getBlockFace();
        BlockFaceWithRotation faceWithRotation = BlockFaceWithRotation.fromPlayerEyesight(face, player.getEyeLocation());
        Location location = event.getBlock().getLocation().clone().add(face.getOppositeFace().getDirection());
        boolean success = ImageCommand.placeImageAt(player, image, width, height, flags, location, faceWithRotation);
        if (!success) {
            return;
        }

        // Decrement item from player's inventory
        if (player.getGameMode() != GameMode.CREATIVE) {
            int amount = item.getAmount();
            if (amount > 1) {
                item.setAmount(amount - 1);
            } else {
                item = new ItemStack(Material.AIR);
            }
            if (isOffHand) {
                inventory.setItemInOffHand(item);
            } else {
                inventory.setItemInMainHand(item);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    protected boolean onLeftClick(@NotNull Player player, int entityId) {
        // Attack path intentionally ignored on modern versions where USE_ENTITY attack is unreliable.
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    protected boolean onRightClick(@NotNull Player player, int entityId) {
        YamipaPlugin.getInstance().getScheduler().runInGame(() -> {
            // Get image selected by player
            FakeImage image = SelectFakeItemFrameListener.getFakeImage(player);
            if (image == null) {
                LOGGER.warning("Failed to get image selected by Player#" + player.getName());
                return;
            }

            // Ignore non-removable images
            if (!image.hasFlag(FakeImage.FLAG_REMOVABLE)) {
                ActionBar.send(player, ChatColor.RED + "This image cannot be removed");
                return;
            }

            // Validate player permissions
            if (!player.hasPermission("yamipa.item.remove.own")) {
                ActionBar.send(player, ChatColor.RED + "You're not allowed to remove image items!");
                return;
            }
            if (
                !player.getUniqueId().equals(image.getPlacedBy().getUniqueId()) &&
                !player.hasPermission("yamipa.item.remove")
            ) {
                ActionBar.send(player, ChatColor.RED + "You cannot remove image items from other players!");
                return;
            }

            // Attempt to remove image
            boolean success = ImageCommand.removeImage(player, image);
            if (!success) {
                return;
            }

            // Drop image item in front of player
            if (player.getGameMode() != GameMode.CREATIVE && image.hasFlag(FakeImage.FLAG_DROPPABLE)) {
                ImageFile imageFile = Objects.requireNonNull(image.getFile());
                ItemStack imageItem = getImageItem(imageFile, 1, image.getWidth(), image.getHeight(), image.getFlags());
                Location dropLocation = player.getEyeLocation().add(player.getLocation().getDirection().normalize());
                player.getWorld().dropItem(dropLocation, imageItem);
            }
        }, player.getLocation(), 0);
        return false;
    }

    @Override
    protected @NotNull ListenerPriority getPriority() {
        return ListenerPriority.LOWEST;
    }
}
