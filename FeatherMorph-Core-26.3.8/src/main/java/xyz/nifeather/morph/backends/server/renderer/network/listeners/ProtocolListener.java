package xyz.nifeather.morph.backends.server.renderer.network.listeners;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.manager.player.PlayerManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import xiamomc.pluginbase.Annotations.Resolved;
import xyz.nifeather.morph.MorphPluginObject;
import xyz.nifeather.morph.api.FeatherMorphAPI;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.SingleWatcher;
import xyz.nifeather.morph.backends.server.renderer.network.registries.RenderRegistry;

public abstract class ProtocolListener extends MorphPluginObject implements PacketListener
{
    public abstract String getIdentifier();

    protected PlayerManager playerManager()
    {
        return PacketEvents.getAPI().getPlayerManager();
    }

    /**
     * 获取实体ID对应的、已注册伪装的玩家
     * @apiNote 性能优化：之前每个数据包都会复制一次所有在线玩家列表并线性查找，
     *          现在只在已伪装的玩家中查找（所有调用方都只处理已注册 Watcher 的玩家，因此结果一致）
     */
    @Nullable
    protected Player getPlayerFrom(int id)
    {
        return registry.findPlayerByEntityId(id);
    }

    @Resolved(shouldSolveImmediately = true)
    private RenderRegistry registry;

    protected void handleException(@Nullable Player sourcePlayer, SingleWatcher watcher, Throwable t)
    {
        boolean handled = false;
        var api = FeatherMorphAPI.instance();

        // Sometimes API would return NULL where I believe it shouldn't... D:
        if (api != null)
        {
            var state = api.directAccess().morphManager().getDisguiseStateFor(sourcePlayer);
            if (state != null)
            {
                logger.info("Failed processing packet, calling DisguiseState#handleException");
                state.handleException(t);
                handled = true;
            }
        }

        if (!handled)
        {
            // If API is not ready (where it shouldn't), unregister from render registry to prevent future chaos
            logger.error("Unhandled exception when processing packet", t);
            registry.unregister(watcher.bindingUUID);
        }
    }
}
