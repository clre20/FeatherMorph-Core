package xyz.nifeather.morph.backends.server.renderer.network.registries;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.nifeather.morph.MorphPluginObject;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.SingleWatcher;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class RenderRegistry extends MorphPluginObject
{
    public record EventParameters(@Nullable Player player, SingleWatcher watcher)
    {
    }

    private final Map<Object, Consumer<EventParameters>> onRegisterConsumers = new Object2ObjectOpenHashMap<>();
    private final Map<Object, Consumer<EventParameters>> unRegisterConsumers = new Object2ObjectOpenHashMap<>();
    private final Map<Object, Consumer<EventParameters>> onRegistryChangeConsumers = new Object2ObjectOpenHashMap<>();

    public void onRegistryChange(Object source, Consumer<EventParameters> consumer)
    {
        onRegistryChangeConsumers.put(source, consumer);
    }

    public void onRegister(Object source, Consumer<EventParameters> consumer)
    {
        onRegisterConsumers.put(source, consumer);
    }

    private void callRegister(Player player, SingleWatcher watcher)
    {
        var ep = new EventParameters(player, watcher);
        onRegisterConsumers.forEach((source, consumer) -> consumer.accept(ep));
    }

    public void onUnRegister(Object source, Consumer<EventParameters> consumer)
    {
        unRegisterConsumers.put(source, consumer);
    }

    private void callUnregister(@Nullable Player player, SingleWatcher watcher)
    {
        unRegisterConsumers.forEach((source, consumer) -> consumer.accept(new EventParameters(player, watcher)));
    }

    //region Registry

    private final Map<UUID, SingleWatcher> watcherMap = new ConcurrentHashMap<>();
    private final Map<Integer, SingleWatcher> entityIdWatcherMap = new ConcurrentHashMap<>();

    // 墓碑快取（Tombstone）：記錄最近 10 秒內剛解除偽裝的 Watcher 與實體 ID
    // 解決 Netty 異步隊列中殘留的原生 Player 元數據封包在 Watcher 已解除但客戶端舊實體尚未被銷毀時外流，
    // 導致客戶端型態不符崩潰（例如終界龍收到玩家皮膚 Byte 127 崩潰）
    private record Tombstone(SingleWatcher watcher, long expireTime) {}
    private final Map<Integer, Tombstone> tombstoneMap = new ConcurrentHashMap<>();

    private void cleanExpiredTombstones()
    {
        long now = System.currentTimeMillis();
        tombstoneMap.entrySet().removeIf(entry -> entry.getValue().expireTime() < now);
    }

    public List<SingleWatcher> getWatchers()
    {
        return new ObjectArrayList<>(watcherMap.values());
    }

    @Nullable
    @Deprecated(forRemoval = true)
    public SingleWatcher getWatcher(Entity entity)
    {
        return getWatcher(entity.getUniqueId());
    }

    @Nullable
    public SingleWatcher getWatcher(UUID uuid)
    {
        return watcherMap.getOrDefault(uuid, null);
    }

    /**
     * @return 当前是否没有任何已注册的 Watcher
     */
    public boolean isEmpty()
    {
        return watcherMap.isEmpty();
    }

    /**
     * 根據實體 ID 查找 Watcher（包含正常註冊與最近解除偽裝的墓碑）
     * 支援 O(1) 映射與回退匹配，完全不依賴跨執行緒調用 Bukkit.getPlayer
     */
    @Nullable
    public SingleWatcher findWatcherByEntityId(int entityId)
    {
        var watcher = entityIdWatcherMap.get(entityId);
        if (watcher != null)
            return watcher;

        // 如果直接映射未命中，遍歷 watcherMap 進行回退匹配（防禦實體 ID 更新延遲）
        for (var w : watcherMap.values())
        {
            if (w.readEntryOrDefault(CustomEntries.SPAWN_ID, -1) == entityId
                    || (w.isActive() && w.getBindingPlayer() != null && w.getBindingPlayer().getEntityId() == entityId))
            {
                entityIdWatcherMap.put(entityId, w);
                return w;
            }
        }

        cleanExpiredTombstones();
        var tombstone = tombstoneMap.get(entityId);
        if (tombstone != null && tombstone.expireTime() >= System.currentTimeMillis())
            return tombstone.watcher();

        return null;
    }

    /**
     * 檢查指定實體 ID 是否處於已解除偽裝的墓碑過渡期
     */
    public boolean isTombstoned(int entityId)
    {
        var tombstone = tombstoneMap.get(entityId);
        return tombstone != null && tombstone.expireTime() >= System.currentTimeMillis();
    }

    /**
     * 根据实体ID查找已注册伪装的在线玩家
     * @apiNote 此方法會在網絡線程中被頻繁調用，直接從 Watcher 取得 Player，不調用 Bukkit.getPlayer 避免執行緒安全問題
     * @return 匹配的玩家，如果此实体ID不属于任何已伪装的玩家则返回 null
     */
    @Nullable
    public Player findPlayerByEntityId(int entityId)
    {
        var watcher = findWatcherByEntityId(entityId);
        if (watcher != null && watcher.isActive())
            return watcher.getBindingPlayer();

        return null;
    }

    public void unregister(Player player)
    {
        unregister(player.getUniqueId());
    }

    /**
     * @param uuid The player's UUID
     * @return A watcher that binds to the UUID, if present
     * @apiNote The watcher returned is disposed
     */
    @Nullable
    public SingleWatcher unregister(UUID uuid)
    {
        var watcher = watcherMap.remove(uuid);
        if (watcher == null) return null;

        int entityId = watcher.readEntryOrDefault(CustomEntries.SPAWN_ID, -1);
        if (entityId == -1 && watcher.isActive() && watcher.getBindingPlayer() != null)
            entityId = watcher.getBindingPlayer().getEntityId();
        if (entityId != -1)
            entityIdWatcherMap.remove(entityId);

        // 移入墓碑快取，保留 10 秒供網絡線程過渡保護
        tombstoneMap.put(entityId, new Tombstone(watcher, System.currentTimeMillis() + 10000L));

        callUnregister(Bukkit.getPlayer(uuid), watcher);

        watcher.setParentRegistry(null);
        watcher.dispose();

        return watcher;
    }

    /**
     * 注册玩家的伪装类型
     * @param player 目标玩家
     * @param registerParameters 注册参数
     * @param watcherConsumer Watcher的编辑函数，用于在注册事件前编辑Watcher的各项属性
     */
    public SingleWatcher register(@NotNull Player player, RegisterParameters registerParameters, Consumer<SingleWatcher> watcherConsumer)
        throws IllegalArgumentException
    {
        var watcher = WatcherIndex.getInstance().getWatcherForType(player, registerParameters.entityType());

        watcher.markSilent(this);

        //设定初始值
        watcher.writeEntry(CustomEntries.DISGUISE_NAME, registerParameters.name());
        watcher.writeEntry(CustomEntries.SPAWN_ID, player.getEntityId());

        watcherConsumer.accept(watcher);

        registerWithWatcher(player.getUniqueId(), watcher);
        watcher.unmarkSilent(this);

        return watcher;
    }

    /**
     * 注册UUID对应的伪装类型
     * @param uuid 目标玩家的UUID
     * @param watcher 对应的 {@link SingleWatcher}
     */
    public void registerWithWatcher(@NotNull UUID uuid, @NotNull SingleWatcher watcher) throws IllegalArgumentException
    {
        if (!watcher.bindingUUID.equals(uuid))
            throw new IllegalArgumentException("Watcher UUID doesn't match with player's UUID!");

        watcherMap.put(uuid, watcher);
        int entityId = watcher.readEntryOrDefault(CustomEntries.SPAWN_ID, -1);
        if (entityId == -1 && watcher.isActive() && watcher.getBindingPlayer() != null)
            entityId = watcher.getBindingPlayer().getEntityId();
        if (entityId != -1)
        {
            entityIdWatcherMap.put(entityId, watcher);
            tombstoneMap.remove(entityId);
        }

        watcher.setParentRegistry(this);
        callRegister(Bukkit.getPlayer(uuid), watcher);
    }

    public void reset()
    {
        watcherMap.forEach((uuid, watcher) ->
        {
            unregister(uuid);
            //callUnregister(Bukkit.getPlayer(uuid), watcher);
        });

        watcherMap.clear();
        entityIdWatcherMap.clear();
        tombstoneMap.clear();
    }

    //endregion Registry
}
