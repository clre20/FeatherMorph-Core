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
        int entityId = packet.getEntityId();

        // 直接透過實體 ID 查找 Watcher（支援 O(1) 查找、墓碑過渡與回退匹配）
        var watcher = registry.findWatcherByEntityId(entityId);
        if (watcher == null)
            return;

        var sourcePlayer = watcher.getBindingPlayer();

        // 獲取接收封包的目標玩家，只攔截發給其他人的 Meta（發給玩家本人的 Meta 不做偽裝轉換）
        var targetPlayer = packetEvent.getPlayer();
        if (targetPlayer != null && sourcePlayer != null && sourcePlayer.equals(targetPlayer))
            return;

        // 如果該實體屬於剛解除偽裝的過渡期（Tombstone）：
        // 說明服務端已將實體判定為玩家，但周圍客戶端可能尚未收到/處理 DestroyEntities 銷毀封包，
        // 客戶端記憶體中仍是舊的偽裝外觀（例如終界龍）。
        // 若此時讓服務端發出的原生玩家皮膚等元數據（如 index 16 = Byte 127）送達客戶端，
        // 客戶端會因型態不符（終界龍 index 16 為 Integer）立即拋出 Protocol Error 斷線。
        // 因此在墓碑期內直接攔截取消該實體的元數據封包，杜絕協定衝突！
        if (registry.isTombstoned(entityId))
        {
            packetEvent.setCancelled(true);
            return;
        }

        try
        {
            watcher.handleEntityMetadataPacket(packet);
        }
        catch (Throwable t)
        {
            // 核心防禦：如果轉換或重構過程中發生任何例外，絕對不可放行原始玩家元數據給客戶端，
            // 直接取消此封包，防止客戶端因收到錯誤型態的 DataWatcher 欄位而崩潰！
            packetEvent.setCancelled(true);
            handleException(sourcePlayer, watcher, t);
        }
    }
}
