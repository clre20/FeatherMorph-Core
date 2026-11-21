package xyz.nifeather.morph.backends.server.renderer.network.listeners;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import xyz.nifeather.morph.backends.server.renderer.network.DisguiseNameplate;

import java.util.Arrays;

/**
 * 讓「偽裝」名牌一直跟著偽裝玩家：
 * <ul>
 *     <li>伺服器送出玩家的乘客封包時（有東西騎上/離開玩家），把名牌加回乘客列表，否則名牌會掉下來、原本的名字會重新出現</li>
 *     <li>伺服器移除玩家實體時（離開視野、換世界、離線），一起移除名牌，避免名牌留在原地</li>
 * </ul>
 */
public class NameplatePacketListener extends ProtocolListener
{
    @Override
    public String getIdentifier()
    {
        return "nameplate_listener";
    }

    @Override
    public void onPacketSend(PacketSendEvent event)
    {
        // 沒有人在使用名牌標記時，完全不做任何事
        if (DisguiseNameplate.noneActive())
            return;

        var type = event.getPacketType();

        if (type == PacketType.Play.Server.SET_PASSENGERS)
            onSetPassengers(event);
        else if (type == PacketType.Play.Server.DESTROY_ENTITIES)
            onDestroyEntities(event);
    }

    private void onSetPassengers(PacketSendEvent event)
    {
        var wrapper = new WrapperPlayServerSetPassengers(event);
        int vehicleId = wrapper.getEntityId();

        int nameplateId = DisguiseNameplate.nameplateOf(vehicleId);
        if (nameplateId == -1)
            return;

        // 偽裝者自己的客戶端沒有名牌實體
        if (event.getUser().getEntityId() == vehicleId)
            return;

        var passengers = wrapper.getPassengers();
        for (int id : passengers)
        {
            if (id == nameplateId)
                return;
        }

        var newPassengers = Arrays.copyOf(passengers, passengers.length + 1);
        newPassengers[passengers.length] = nameplateId;
        wrapper.setPassengers(newPassengers);
    }

    private void onDestroyEntities(PacketSendEvent event)
    {
        var wrapper = new WrapperPlayServerDestroyEntities(event);
        var ids = wrapper.getEntityIds();

        int[] extra = null;
        int extraCount = 0;

        for (int id : ids)
        {
            int nameplateId = DisguiseNameplate.nameplateOf(id);
            if (nameplateId == -1)
                continue;

            if (extra == null)
                extra = new int[ids.length];

            extra[extraCount++] = nameplateId;
        }

        if (extra == null)
            return;

        var newIds = Arrays.copyOf(ids, ids.length + extraCount);
        System.arraycopy(extra, 0, newIds, ids.length, extraCount);
        wrapper.setEntityIds(newIds);
    }
}
