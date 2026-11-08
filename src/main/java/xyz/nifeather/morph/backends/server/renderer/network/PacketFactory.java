package xyz.nifeather.morph.backends.server.renderer.network;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import xyz.nifeather.morph.FeatherMorphMain;
import xyz.nifeather.morph.MorphPluginObject;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.SingleWatcher;
import xyz.nifeather.morph.backends.server.renderer.network.registries.CustomEntries;
import xyz.nifeather.morph.misc.DisguiseEquipment;

import java.util.List;

/**
 * This is going to be removed!
 */
public class PacketFactory extends MorphPluginObject
{
    public static WrapperPlayServerEntityMetadata buildDiffMetaPacket(SingleWatcher watcher)
    {
        List<EntityData<?>> wrappedDataValues = new ObjectArrayList<>();
        var valuesToSent = watcher.getDirty();
        watcher.clearDirty();

        // Add our packet identifier!
        if (!watcher.readEntryOrDefault(CustomEntries.DONT_INCLUDE_PACKET_IDENTIFIER, false))
            wrappedDataValues.add(new EntityData<>(99, EntityDataTypes.STRING, MARK_DONT_PROCESS));

        valuesToSent.forEach((single, val) ->
        {
            var wrapped =  new EntityData(single.index(), single.type(), val);
            wrappedDataValues.add(wrapped);
        });

        return new WrapperPlayServerEntityMetadata(watcher.getBindingPlayer().getEntityId(), wrappedDataValues);
    }

    public static final String MARK_DONT_PROCESS = "~FEATHERMORPH GENERATED METADATA, THIS MESSAGE SHOULD BE REMOVED, OR SOMETHING MAY GONE WRONG!";

    public static WrapperPlayServerEntityMetadata buildFullMetaPacket(Player player, SingleWatcher watcher)
    {
        watcher.sync();

        List<EntityData<?>> wrappedDataValues = new ObjectArrayList<>();

        // Add our packet identifier!
        if (!watcher.readEntryOrDefault(CustomEntries.DONT_INCLUDE_PACKET_IDENTIFIER, false))
            wrappedDataValues.add(new EntityData<>(99, EntityDataTypes.STRING, MARK_DONT_PROCESS));

        var valuesToSent = watcher.getOverlayedRegistry();
        watcher.clearDirty();

        valuesToSent.forEach((index, val) ->
        {
            var sv = watcher.getSingle(index);

            if (sv == null)
                throw new IllegalArgumentException("Not SingleValue found for index " + index);

            var wrapped =  new EntityData(index, sv.type(), val);
            wrappedDataValues.add(wrapped);
        });

        return new WrapperPlayServerEntityMetadata(player.getEntityId(), wrappedDataValues);
    }

    public static List<Equipment> getPacketeventsEquipments(Player player, SingleWatcher watcher)
    {
        var shouldDisplayFakeEquip = watcher.readEntryOrDefault(CustomEntries.DISPLAY_FAKE_EQUIPMENT, false);
        DisguiseEquipment equipment = shouldDisplayFakeEquip
                ? watcher.readEntryOrDefault(CustomEntries.EQUIPMENT, DisguiseEquipment.empty())
                : DisguiseEquipment.copy(player.getEquipment());

        if (watcher.getEntityType() == org.bukkit.entity.EntityType.SULFUR_CUBE)
        {
            ItemStack item;
            if (watcher instanceof xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.types.slimemagma.SulfurCubeWatcher scw
                    && scw.getCurrentSwallowedItem() != null && !shouldDisplayFakeEquip)
            {
                item = scw.getCurrentSwallowedItem();
            }
            else
            {
                var offhand = equipment.getItem(org.bukkit.inventory.EquipmentSlot.OFF_HAND);
                if (xyz.nifeather.morph.misc.sulfurcube.CubeArchetype.isSwallowable(offhand))
                {
                    item = offhand;
                }
                else
                {
                    item = equipment.getItem(org.bukkit.inventory.EquipmentSlot.HAND);
                }
            }

            boolean swallowable = xyz.nifeather.morph.misc.sulfurcube.CubeArchetype.isSwallowable(item);
            ItemStack displayItem = (swallowable && item != null) ? item.asOne() : null;

            return sulfurCubeEquipments(displayItem);
        }

        return ProtocolEquipment.toPEEquipmentList(equipment);
    }

    /**
     * 硫磺立方體的裝備封包內容：只送 BODY（吞下的方塊），與原版伺服器相同。
     * <p>
     * 不可以附帶「主手＝空氣」等其他欄位：Geyser 會把 BODY 轉成基岩版立方體的手持物品來顯示內部方塊，
     * 若同一個封包裡還有主手欄位，Geyser 處理完後會用主手（空氣）覆蓋掉，基岩版玩家就看不到內部方塊。
     */
    public static List<Equipment> sulfurCubeEquipments(@org.jetbrains.annotations.Nullable ItemStack displayItem)
    {
        var list = new ObjectArrayList<Equipment>();
        list.add(new Equipment(com.github.retrooper.packetevents.protocol.player.EquipmentSlot.BODY,
                displayItem != null ? io.github.retrooper.packetevents.util.SpigotConversionUtil.fromBukkitItemStack(displayItem) : ProtocolEquipment.peAir));
        return list;
    }

    public static void markEquipmentPacket(WrapperPlayServerEntityEquipment wrapper)
    {
        wrapper.setEntityId(-wrapper.getEntityId());
    }

    public static boolean isEquipmentPacketOurs(WrapperPlayServerEntityEquipment wrapper)
    {
        if (wrapper.getEntityId() > 0)
            return false;

        var abs = Math.abs(wrapper.getEntityId());

        var playerFound = FeatherMorphMain.getInstance()
                .getPlatform()
                .onlinePlayersNative()
                .stream()
                .filter(p -> p.getEntityId() == abs)
                .findFirst()
                .orElse(null);

        return playerFound != null;
    }
}
