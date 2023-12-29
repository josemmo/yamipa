package io.josemmo.bukkit.plugin.commands;

import io.josemmo.bukkit.plugin.YamipaPlugin;
import io.josemmo.bukkit.plugin.renderer.FakeImage;
import io.josemmo.bukkit.plugin.renderer.ImageRenderer;
import io.josemmo.bukkit.plugin.renderer.ItemService;
import io.josemmo.bukkit.plugin.storage.ImageFile;
import io.josemmo.bukkit.plugin.utils.Logger;
import io.josemmo.bukkit.plugin.utils.Permissions;
import io.josemmo.bukkit.plugin.utils.SelectBlockTask;
import io.josemmo.bukkit.plugin.utils.ActionBar;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.*;

public class ImageCommand {
    private static final int ITEMS_PER_PAGE = 9;
    private static final Logger LOGGER = Logger.getLogger("ImageCommand");

    public static void showHelp(@NotNull CommandSender s, @NotNull String commandName) {
        String cmd = "/" + commandName;
        s.sendMessage(ChatColor.BOLD + "=== Yamipa Plugin Help ===");
        s.sendMessage(ChatColor.AQUA + cmd + ChatColor.RESET + " - Show this help");
        if (s.hasPermission("yamipa.command.clear") || s.hasPermission("yamipa.clear")) {
            s.sendMessage(ChatColor.AQUA + cmd + " clear <x z w> <r> [<player>]" + ChatColor.RESET + " - Remove placed images");
        }
        if (s.hasPermission("yamipa.command.describe") || s.hasPermission("yamipa.describe")) {
            s.sendMessage(ChatColor.AQUA + cmd + " describe" + ChatColor.RESET + " - Describe placed image");
        }
        if (s.hasPermission("yamipa.command.download") || s.hasPermission("yamipa.download")) {
            s.sendMessage(ChatColor.AQUA + cmd + " download <url> <filename>" + ChatColor.RESET + " - Download image");
        }
        if (s.hasPermission("yamipa.command.give") || s.hasPermission("yamipa.give")) {
            s.sendMessage(ChatColor.AQUA + cmd + " give <p> <filename> <#> <w> [<h>] [<f>]" + ChatColor.RESET + " - Give items");
        }
        if (s.hasPermission("yamipa.command.list") || s.hasPermission("yamipa.list")) {
            s.sendMessage(ChatColor.AQUA + cmd + " list [<page>]" + ChatColor.RESET + " - List all images");
        }
        if (s.hasPermission("yamipa.command.place") || s.hasPermission("yamipa.place")) {
            s.sendMessage(ChatColor.AQUA + cmd + " place <filename> <w> [<h>] [<f>]" + ChatColor.RESET + " - Place image");
        }
        if (s.hasPermission("yamipa.command.remove.own") || s.hasPermission("yamipa.remove")) {
            s.sendMessage(ChatColor.AQUA + cmd + " remove" + ChatColor.RESET + " - Remove a single placed image");
        }
        if (s.hasPermission("yamipa.command.top") || s.hasPermission("yamipa.top")) {
            s.sendMessage(ChatColor.AQUA + cmd + " top" + ChatColor.RESET + " - List players with the most images");
        }
    }

    public static void listImages(@NotNull CommandSender sender, int page) {
        String[] filenames = YamipaPlugin.getInstance().getStorage().getAllFilenames();
        int numOfImages = filenames.length;

        // Are there any images available?
        if (numOfImages == 0) {
            sender.sendMessage(ChatColor.RED + "No images found in the images directory");
            return;
        }

        // Is the page number valid?
        int firstImageIndex = Math.max(page-1, 0) * ITEMS_PER_PAGE;
        if (firstImageIndex >= numOfImages) {
            sender.sendMessage(ChatColor.RED + "Page " + page + " not found");
            return;
        }

        // Render list of images
        int stopImageIndex = (page == 0) ? numOfImages : Math.min(numOfImages, firstImageIndex+ITEMS_PER_PAGE);
        if (page > 0) {
            int maxPage = (int) Math.ceil((float) numOfImages / ITEMS_PER_PAGE);
            sender.sendMessage("=== Page " + page + " out of " + maxPage + " ===");
        }
        for (int i=firstImageIndex; i<stopImageIndex; ++i) {
            sender.sendMessage(ChatColor.GOLD + filenames[i]);
        }
    }

    public static void downloadImage(@NotNull CommandSender sender, @NotNull String rawUrl, @NotNull String filename) {
        YamipaPlugin plugin = YamipaPlugin.getInstance();

        // Validate destination file
        Path basePath = plugin.getStorage().getBasePath();
        Path destPath = basePath.resolve(filename).normalize();
        if (!destPath.startsWith(basePath)) {
            sender.sendMessage(ChatColor.RED + "Not a valid destination filename");
            return;
        }
        if (destPath.toFile().exists()) {
            sender.sendMessage(ChatColor.RED + "There's already a file with that name");
            return;
        }

        // Validate and fix remote URL
        URL url;
        String referrer = null;
        try {
            url = new URL(rawUrl);

            // Giphy.com
            if (url.getHost().equals("giphy.com")) {
                String path = url.getPath();
                String id = path.substring(path.lastIndexOf('-')+1);
                url = new URL("https://media.giphy.com/media/" + id + "/giphy.gif");
                referrer = "https://giphy.com/";
            }

            // Imgur.com
            if (url.getHost().equals("imgur.com")) {
                String[] parts = url.getPath().replaceAll("^/|/$", "").split("/");
                if (parts.length == 2 && (parts[0].equals("a") || parts[0].equals("gallery"))) {
                    url = new URL("https://imgur.com/a/" + parts[1] + "/zip");
                    referrer = "https://imgur.com/a/" + parts[1];
                } else {
                    url = new URL("https://imgur.com/download/" + parts[parts.length-1] + "/");
                    referrer = "https://imgur.com/" + parts[parts.length-1];
                }
            }
        } catch (MalformedURLException e) {
            sender.sendMessage(ChatColor.RED + "The remote URL is not valid");
            return;
        }

        // Download and validate remote file
        final URL finalUrl = url;
        final String finalReferrer = referrer;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URLConnection conn = finalUrl.openConnection();
                PluginDescriptionFile desc = plugin.getDescription();
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("User-Agent", desc.getName() + "/" + desc.getVersion());
                if (finalReferrer != null) {
                    conn.setRequestProperty("Referer", finalReferrer);
                }

                // Download file
                sender.sendMessage("Downloading file...");
                Files.createDirectories(destPath.getParent());
                Files.copy(conn.getInputStream(), destPath);

                // Validate downloaded file
                if (ImageIO.read(destPath.toFile()) == null) {
                    throw new IllegalArgumentException("The downloaded file is not a valid image");
                }

                // Notify sender
                sender.sendMessage(ChatColor.GREEN + "Done!");
            } catch (IOException e) {
                sender.sendMessage(ChatColor.RED + "An error occurred trying to download the remote file");
                LOGGER.warning("Failed to download file from \"" + finalUrl + "\": " + e.getClass().getName());
            } catch (IllegalArgumentException e) {
                if (Files.exists(destPath) && !destPath.toFile().delete()) {
                    LOGGER.warning("Failed to delete corrupted file \"" + destPath + "\"");
                }
                sender.sendMessage(ChatColor.RED + e.getMessage());
            }
        });
    }

    public static void placeImage(
        @NotNull Player player,
        @NotNull ImageFile image,
        int width,
        int height,
        int flags
    ) {
        // Get image size in blocks
        Dimension sizeInPixels = image.getSize();
        if (sizeInPixels == null) {
            player.sendMessage(ChatColor.RED + "The requested file is not a valid image");
            return;
        }
        final int finalHeight = (height == 0) ? FakeImage.getProportionalHeight(sizeInPixels, width) : height;

        // Ask player where to place image
        SelectBlockTask task = new SelectBlockTask(player);
        task.onSuccess((location, face) -> placeImage(player, image, width, finalHeight, flags, location, face));
        task.onFailure(() -> ActionBar.send(player, ChatColor.RED + "Image placing canceled"));
        task.run("Right click a block to continue");
    }

    public static boolean placeImage(
        @NotNull Player player,
        @NotNull ImageFile image,
        int width,
        int height,
        int flags,
        @NotNull Location location,
        @NotNull BlockFace face
    ) {
        ImageRenderer renderer = YamipaPlugin.getInstance().getRenderer();

        // Create new fake image instance
        Rotation rotation = FakeImage.getRotationFromPlayerEyesight(face, player.getEyeLocation());
        FakeImage fakeImage = new FakeImage(image.getFilename(), location, face, rotation,
            width, height, new Date(), player, flags);

        // Make sure image can be placed
        for (Location loc : fakeImage.getAllLocations()) {
            if (!Permissions.canBuild(player, loc)) {
                ActionBar.send(player, ChatColor.RED + "You're not allowed to place an image here!");
                return false;
            }
            if (renderer.getImage(loc, face) != null) {
                ActionBar.send(player, ChatColor.RED + "There's already an image there!");
                return false;
            }
        }

        // Show loading status to player
        ActionBar loadingActionBar = ActionBar.repeat(player, ChatColor.AQUA + "Loading image...");
        fakeImage.setOnLoadedListener(loadingActionBar::clear);

        // Add fake image to renderer
        renderer.addImage(fakeImage);
        return true;
    }

    public static void removeImage(@NotNull Player player) {
        SelectBlockTask task = new SelectBlockTask(player);
        task.onSuccess((location, face) -> {
            FakeImage image = YamipaPlugin.getInstance().getRenderer().getImage(location, face);
            if (image == null) {
                ActionBar.send(player, ChatColor.RED + "That is not a valid image!");
                return;
            }

            // Check player's command permissions
            if (
                !player.getUniqueId().equals(image.getPlacedBy().getUniqueId()) &&
                !player.hasPermission("yamipa.command.remove") &&
                !player.hasPermission("yamipa.remove")
            ) {
                ActionBar.send(player, ChatColor.RED + "You cannot remove images from other players!");
                return;
            }

            // Attempt to remove image
            removeImage(player, image);
        });
        task.onFailure(() -> ActionBar.send(player, ChatColor.RED + "Image removing canceled"));
        task.run("Right click an image to continue");
    }

    public static boolean removeImage(@NotNull Player player, @NotNull FakeImage image) {
        // Check block permissions
        for (Location loc : image.getAllLocations()) {
            if (!Permissions.canDestroy(player, loc)) {
                ActionBar.send(player, ChatColor.RED + "You're not allowed to remove this image!");
                return false;
            }
        }

        // Trigger image removal
        YamipaPlugin.getInstance().getRenderer().removeImage(image);
        return true;
    }

    public static void clearImages(
        @NotNull CommandSender sender,
        @NotNull Location origin,
        int radius,
        @Nullable OfflinePlayer placedBy
    ) {
        ImageRenderer renderer = YamipaPlugin.getInstance().getRenderer();

        // Get images in area
        Set<FakeImage> images = renderer.getImages(
            Objects.requireNonNull(origin.getWorld()),
            origin.getBlockX()-radius+1,
            origin.getBlockX()+radius-1,
            origin.getBlockZ()-radius+1,
            origin.getBlockZ()+radius-1
        );

        // Filter out images not placed by targeted player
        if (placedBy != null) {
            UUID target = placedBy.getUniqueId();
            images.removeIf(image -> !target.equals(image.getPlacedBy().getUniqueId()));
        }

        // Filter out images outside the permission scope of the sender
        if (sender instanceof Player) {
            Player senderAsPlayer = (Player) sender;
            images.removeIf(image -> {
                for (Location loc : image.getAllLocations()) {
                    if (!Permissions.canDestroy(senderAsPlayer, loc)) {
                        return true;
                    }
                }
                return false;
            });
        }

        // Remove found images
        for (FakeImage image : images) {
            renderer.removeImage(image);
        }
        sender.sendMessage("Removed " + images.size() + " placed image(s)");
    }

    public static void describeImage(@NotNull Player player) {
        ImageRenderer renderer = YamipaPlugin.getInstance().getRenderer();

        // Ask user to select fake image
        SelectBlockTask task = new SelectBlockTask(player);
        task.onSuccess((location, face) -> {
            FakeImage image = renderer.getImage(location, face);
            if (image == null) {
                ActionBar.send(player, ChatColor.RED + "That is not a valid image!");
                return;
            }

            // Separate previous messages
            player.sendMessage("");

            // Basic information
            player.sendMessage(ChatColor.GOLD + "Filename: " + ChatColor.RESET + image.getFilename());
            player.sendMessage(ChatColor.GOLD + "World: " + ChatColor.RESET +
                image.getLocation().getChunk().getWorld().getName());
            player.sendMessage(ChatColor.GOLD + "Coordinates: " + ChatColor.RESET +
                image.getLocation().getBlockX() + ", " +
                image.getLocation().getBlockY() + ", " +
                image.getLocation().getBlockZ());
            player.sendMessage(ChatColor.GOLD + "Block Face: " + ChatColor.RESET + image.getBlockFace());
            player.sendMessage(ChatColor.GOLD + "Rotation: " + ChatColor.RESET + image.getRotation());
            player.sendMessage(ChatColor.GOLD + "Dimensions: " + ChatColor.RESET +
                image.getWidth() + "x" + image.getHeight() + " blocks");

            // Speed
            int delay = image.getDelay() * 50;
            String delayStr = (delay > 0) ? delay + " ms per step" : ChatColor.GRAY + "N/A";
            player.sendMessage(ChatColor.GOLD + "Speed: " + ChatColor.RESET + delayStr);

            // Placed At
            String dateStr = (image.getPlacedAt() == null) ?
                ChatColor.GRAY + "Some point in time" :
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z").format(image.getPlacedAt());
            player.sendMessage(ChatColor.GOLD + "Placed At: " + ChatColor.RESET + dateStr);

            // Placed By
            String playerStr;
            if (image.getPlacedBy().getUniqueId().equals(FakeImage.UNKNOWN_PLAYER_ID)) {
                playerStr = ChatColor.GRAY + "Someone";
            } else if (image.getPlacedBy().getName() == null) {
                playerStr = ChatColor.DARK_AQUA + image.getPlacedBy().getUniqueId().toString();
            } else {
                playerStr = image.getPlacedBy().getName();
            }
            player.sendMessage(ChatColor.GOLD + "Placed By: " + ChatColor.RESET + playerStr);

            // Flags
            String flagsStr = "";
            if (image.hasFlag(FakeImage.FLAG_ANIMATABLE)) {
                flagsStr += ChatColor.AQUA + "ANIM ";
            }
            if (image.hasFlag(FakeImage.FLAG_REMOVABLE)) {
                flagsStr += ChatColor.RED + "REMO ";
            }
            if (image.hasFlag(FakeImage.FLAG_DROPPABLE)) {
                flagsStr += ChatColor.LIGHT_PURPLE + "DROP ";
            }
            if (image.hasFlag(FakeImage.FLAG_GLOWING)) {
                flagsStr += ChatColor.GREEN + "GLOW ";
            }
            if (flagsStr.isEmpty()) {
                flagsStr = ChatColor.GRAY + "N/A";
            }
            player.sendMessage(ChatColor.GOLD + "Flags: " + ChatColor.RESET + flagsStr);
        });
        task.onFailure(() -> ActionBar.send(player, ChatColor.RED + "Image describing canceled"));
        task.run("Right click the image to describe");
    }

    public static void showTopPlayers(@NotNull CommandSender sender) {
        UUID senderId = (sender instanceof Player) ? ((Player) sender).getUniqueId() : null;
        Map<OfflinePlayer, Integer> stats = YamipaPlugin.getInstance().getRenderer().getImagesCountByPlayer();

        // Render header
        sender.sendMessage("=== Top players with the most placed images ===");
        if (stats.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "No one on this server has placed a single image!");
            return;
        }

        int rank = 0;
        int printedLines = 0;
        boolean hasShownSender = (senderId == null); // Assume sender has already been shown if it's not a player
        for (Map.Entry<OfflinePlayer, Integer> item : stats.entrySet()) {
            OfflinePlayer player = item.getKey();
            int value = item.getValue();
            ++rank;

            // Skip line if irrelevant
            if (player.getUniqueId().equals(senderId)) {
                hasShownSender = true;
            } else if (!hasShownSender && printedLines == ITEMS_PER_PAGE-1) {
                continue; // Leave last line empty for sender rank
            } else if (printedLines >= ITEMS_PER_PAGE) {
                break; // Stop printing players when chat is filled
            }

            // Prepare player name or UUID
            String playerName = (player.getName() == null) ?
                ChatColor.GOLD + player.getUniqueId().toString() :
                ChatColor.GREEN + player.getName();

            // Render player line
            sender.sendMessage(
                "" + ChatColor.BOLD + (rank > 1000 ? "1000+" : rank) + ChatColor.RESET + ". " +
                playerName + ChatColor.RESET +
                ChatColor.GRAY + " - " + value + " " + (value == 1 ? "image" : "images")
            );
            ++printedLines;
        }
    }

    public static void giveImageItems(
        @NotNull CommandSender sender,
        @NotNull Player player,
        @NotNull ImageFile image,
        int amount,
        int width,
        int height,
        int flags
    ) {
        // Get image size in blocks
        Dimension sizeInPixels = image.getSize();
        if (sizeInPixels == null) {
            sender.sendMessage(ChatColor.RED + "The requested file is not a valid image");
            return;
        }
        if (height == 0) {
            height = FakeImage.getProportionalHeight(sizeInPixels, width);
        }

        // Create item stack
        ItemStack itemStack = ItemService.getImageItem(image, amount, width, height, flags);

        // Add item stack to player's inventory
        player.getInventory().addItem(itemStack);
        sender.sendMessage(
            ChatColor.ITALIC + "Added " + amount + " " +
            (amount == 1 ? "image item" : "image items") +
            " to " + player.getName() + "'s inventory"
        );
    }
}
