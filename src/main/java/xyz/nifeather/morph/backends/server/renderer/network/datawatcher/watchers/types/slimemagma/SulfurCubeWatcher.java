package xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.types.slimemagma;

import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xyz.nifeather.morph.backends.server.renderer.network.PacketFactory;
import xyz.nifeather.morph.backends.server.renderer.network.ProtocolEquipment;
import xyz.nifeather.morph.backends.server.renderer.network.registries.CustomEntries;
import xyz.nifeather.morph.misc.DisguiseEquipment;
import xyz.nifeather.morph.misc.sulfurcube.CubeArchetype;

public class SulfurCubeWatcher extends AbstractSlimeWatcher
{
    public SulfurCubeWatcher(Player bindingPlayer)
    {
        super(bindingPlayer, EntityType.SULFUR_CUBE);
    }

    @Override
    protected WrapperPlayServerEntityEquipment getEquipmentPacket()
    {
        var player = getBindingPlayer();
        var shouldDisplayFakeEquip = this.readEntryOrDefault(CustomEntries.DISPLAY_FAKE_EQUIPMENT, false);
        ItemStack item = shouldDisplayFakeEquip
                ? this.readEntryOrDefault(CustomEntries.EQUIPMENT, DisguiseEquipment.empty()).getItem(org.bukkit.inventory.EquipmentSlot.HAND)
                : player.getInventory().getItemInMainHand();

        boolean swallowable = CubeArchetype.isSwallowable(item);

        var list = new ObjectArrayList<Equipment>();
        list.add(new Equipment(EquipmentSlot.BODY, swallowable ? SpigotConversionUtil.fromBukkitItemStack(item) : ProtocolEquipment.peAir));
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
