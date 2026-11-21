package xyz.nifeather.morph.backends.server.renderer.utilties;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.GameType;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.SingleWatcher;
import xyz.nifeather.morph.misc.NmsRecord;

import java.util.List;

public class WatcherUtils
{
    public static CompoundTag buildCompoundFromWatcher(SingleWatcher watcher)
    {
        var tag = new CompoundTag();

        watcher.writeToCompound(tag);

        return tag;
    }

    public static List<Player> getAffectedPlayers(@NotNull Player sourcePlayer)
    {
        // 性能优化：直接使用实体追踪器中正在追踪此玩家的玩家列表，
        //          而不是每次都遍历整个世界的玩家并计算距离（大型服务器上为 O(世界玩家数)）
        //          只有正在追踪此玩家的客户端才会拥有伪装实体，因此结果与之前一致甚至更准确
        var players = new ObjectArrayList<Player>(sourcePlayer.getTrackedBy());
        players.remove(sourcePlayer);

        if (NmsRecord.ofPlayer(sourcePlayer).gameMode.getGameModeForPlayer() == GameType.SPECTATOR)
        {
            players.removeIf(bukkitPlayer ->
                    NmsRecord.ofPlayer(bukkitPlayer).gameMode.getGameModeForPlayer() != GameType.SPECTATOR);
        }

        return players;
    }
}
