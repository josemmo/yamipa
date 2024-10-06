package io.josemmo.bukkit.plugin.packets;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.data.EntityMetadataProvider;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import io.josemmo.bukkit.plugin.utils.Internals;
import org.bukkit.Rotation;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class FakeImageEntityMetadataProvider implements EntityMetadataProvider {

    private final boolean invisible;
    private final ItemStack display;
    private final Rotation rotation;

    public FakeImageEntityMetadataProvider(boolean invisible, ItemStack display, Rotation rotation) {
        this.invisible = invisible;
        this.display = display;
        this.rotation = rotation;
    }

    @Override
    public List<EntityData> entityData(ClientVersion clientVersion) {
        int itemIndex = (clientVersion.isOlderThan(ClientVersion.V_1_17)) ? 7 : 8;
        int rotationIndex = itemIndex + 1;

        List<EntityData> data = new ArrayList<EntityData>();

        // Invisible
        byte invisibleFlags = (byte) (invisible ? 0x20 : 0x00);
        EntityData invisData = new EntityData(0, EntityDataTypes.BYTE, invisibleFlags);
        data.add(invisData);

        // Item
        EntityData itemData = new EntityData(itemIndex, EntityDataTypes.ITEMSTACK, SpigotConversionUtil.fromBukkitItemStack(display));
        data.add(itemData);

        // Rotation
        EntityData rotationData = new EntityData(rotationIndex, EntityDataTypes.INT, rotation.ordinal());
        data.add(rotationData);
        return data;
    }

}
