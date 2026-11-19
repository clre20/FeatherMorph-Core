package xyz.nifeather.morph.backends.server.renderer.network.listeners;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import xiamomc.pluginbase.Annotations.Resolved;
import xyz.nifeather.morph.backends.server.renderer.network.PacketFactory;
import xyz.nifeather.morph.backends.server.renderer.network.registries.RenderRegistry;

/**
 * Listener used to override the metadata packet, so that the client won't panic when it received player's meta but the player is disguised as a mob.
 */
public class MetaPacketListener extends ProtocolListener
{
    @Resolved(shouldSolveImmediately = true)
    private RenderRegistry registry;

    @Override
    public String getIdentifier()
    {
        return "meta_listener";
    }

    @Override
    public void onPacketSend(PacketSendEvent event)
    {
        if (event.getPacketType() != PacketType.Play.Server.ENTITY_METADATA)
            return;

        var wrapper = new WrapperPlayServerEntityMetadata(event);

        // 必須在任何提早 return 之前移除標記！
        // 封包在 netty 執行緒送出時，來源玩家可能已經取消/切換偽裝（Watcher 已從註冊表移除），
        // 若此時才 return 而沒有移除標記，index 99 的資料就會外流到客戶端，導致客戶端斷線（網路協定錯誤）。
        var data = wrapper.getEntityMetadata();
        if (PacketFactory.stripMarker(data))
        {
            wrapper.setEntityMetadata(data);
            return;
        }

        this.onMetaPacket(wrapper, event);
    }

    private void onMetaPacket(WrapperPlayServerEntityMetadata packet, PacketSendEvent packetEvent)
    {
        //获取此包的来源实体
        var sourcePlayer = getPlayerFrom(packet.getEntityId());

        // How could this be?!
        if (sourcePlayer == null)
            return;

        //然后获取此包要发送的目标玩家，只拦截其他人的Meta
        var targetPlayer = packetEvent.getPlayer();
        if (sourcePlayer.equals(targetPlayer))
            return;

        var watcher = registry.getWatcher(sourcePlayer.getUniqueId());

        if (watcher == null)
            return;

        try
        {
            watcher.handleEntityMetadataPacket(packet);
        }
        catch (Exception e)
        {
            handleException(sourcePlayer, watcher, e);
        }
    }
}
