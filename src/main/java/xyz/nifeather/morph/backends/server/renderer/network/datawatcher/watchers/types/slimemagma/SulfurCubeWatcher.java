package xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.types.slimemagma;

import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import xyz.nifeather.morph.backends.server.renderer.network.PacketFactory;
import xyz.nifeather.morph.backends.server.renderer.network.ProtocolEquipment;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.types.AgeableMobWatcher;
import xyz.nifeather.morph.backends.server.renderer.network.registries.CustomEntries;
import xyz.nifeather.morph.backends.server.renderer.network.registries.ValueIndex;
import xyz.nifeather.morph.misc.DisguiseEquipment;
import xyz.nifeather.morph.misc.disguiseProperty.DisguiseProperties;
import xyz.nifeather.morph.misc.disguiseProperty.SingleProperty;
import xyz.nifeather.morph.misc.disguiseProperty.values.SulfurCubePropertyCollection;
import xyz.nifeather.morph.misc.sulfurcube.CubeArchetype;

public class SulfurCubeWatcher extends AgeableMobWatcher
{
    private ItemStack currentSwallowedItem = null;

    @Nullable
    public ItemStack getCurrentSwallowedItem()
    {
        return currentSwallowedItem;
    }

    public void setSwallowedItem(@Nullable ItemStack item)
    {
        this.currentSwallowedItem = item;
        updateSwallowedItem();
    }

    public SulfurCubeWatcher(Player bindingPlayer)
    {
        super(bindingPlayer, EntityType.SULFUR_CUBE);

        this.writeEntry(CustomEntries.SLIME_SIZE_REAL, 2);
        this.writePersistent(ValueIndex.SULFUR_CUBE.SIZE, 2);
        this.writePersistent(ValueIndex.AGEABLE_MOB.IS_BABY, false);
    }

    @Override
    protected void initRegistry()
    {
        super.initRegistry();

        register(ValueIndex.SULFUR_CUBE);
    }

    @Override
    protected <X> void onPropertyWrite(SingleProperty<X> property, X value)
    {
        var properties = DisguiseProperties.INSTANCE.getCollectionOrThrow(SulfurCubePropertyCollection.class);

        if (property.equals(properties.SIZE))
        {
            var size = (int) value;
            this.writeEntry(CustomEntries.SLIME_SIZE_REAL, size);
            this.writePersistent(ValueIndex.SULFUR_CUBE.SIZE, size);
            this.writePersistent(ValueIndex.AGEABLE_MOB.IS_BABY, size == 1);
        }

        super.onPropertyWrite(property, value);
    }

    @Override
    public void writeToCompound(CompoundTag nbt)
    {
        super.writeToCompound(nbt);

        nbt.putInt("Size", readEntryOrDefault(CustomEntries.SLIME_SIZE_REAL, 2));
    }

    @Override
    protected WrapperPlayServerEntityEquipment getEquipmentPacket()
    {
        var player = getBindingPlayer();
        var shouldDisplayFakeEquip = this.readEntryOrDefault(CustomEntries.DISPLAY_FAKE_EQUIPMENT, false);
        ItemStack item;
        if (shouldDisplayFakeEquip)
        {
            var fakeEquip = this.readEntryOrDefault(CustomEntries.EQUIPMENT, DisguiseEquipment.empty());
            var fakeOffhand = fakeEquip.getItem(org.bukkit.inventory.EquipmentSlot.OFF_HAND);
            if (CubeArchetype.isSwallowable(fakeOffhand))
            {
                item = fakeOffhand;
            }
            else
            {
                item = fakeEquip.getItem(org.bukkit.inventory.EquipmentSlot.HAND);
            }
        }
        else if (currentSwallowedItem != null)
        {
            item = currentSwallowedItem;
        }
        else
        {
            item = CubeArchetype.getActiveItem(player);
        }

        boolean swallowable = CubeArchetype.isSwallowable(item);
        ItemStack displayItem = (swallowable && item != null) ? item.asOne() : null;

        var list = new ObjectArrayList<Equipment>();
        list.add(new Equipment(EquipmentSlot.BODY, displayItem != null ? SpigotConversionUtil.fromBukkitItemStack(displayItem) : ProtocolEquipment.peAir));
        list.add(new Equipment(EquipmentSlot.MAIN_HAND, ProtocolEquipment.peAir));
        list.add(new Equipment(EquipmentSlot.OFF_HAND, ProtocolEquipment.peAir));
        list.add(new Equipment(EquipmentSlot.HELMET, ProtocolEquipment.peAir));
        list.add(new Equipment(EquipmentSlot.CHEST_PLATE, ProtocolEquipment.peAir));
        list.add(new Equipment(EquipmentSlot.LEGGINGS, ProtocolEquipment.peAir));
        list.add(new Equipment(EquipmentSlot.BOOTS, ProtocolEquipment.peAir));

        var packet = new WrapperPlayServerEntityEquipment(player.getEntityId(), list);
        PacketFactory.markEquipmentPacket(packet);

        return packet;
    }

    public void updateSwallowedItem()
    {
        if (!isSilent() && isAlive())
        {
            sendPacketToAffectedPlayers(this.getEquipmentPacket());
        }
    }
}
