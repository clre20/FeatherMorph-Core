package xyz.nifeather.morph.storage.playerdata;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.nifeather.morph.FeatherMorphMain;
import xyz.nifeather.morph.interfaces.IManagePlayerData;
import xyz.nifeather.morph.misc.DisguiseMeta;
import xyz.nifeather.morph.misc.DisguiseTypes;
import xyz.nifeather.morph.storage.DirectoryJsonBasedStorage;
import xyz.nifeather.morph.utilities.AsyncFileWriter;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class PlayerDataStoreNew extends DirectoryJsonBasedStorage<PlayerMeta> implements IManagePlayerData
{
    private static final PlayerMeta defaultMeta = new PlayerMeta();

    public PlayerDataStoreNew()
    {
        super("playerdata");

        var packageVersion = this.getPackageVersion();

        if (packageVersion < TARGET_PACKAGE_VERSION)
            update(packageVersion);

        defaultMeta.uniqueId = UUID.fromString("0-0-0-0-0");
        defaultMeta.playerName = "~RESERVED META, SHOULD NOT BE USED~";
    }

    private static final int TARGET_PACKAGE_VERSION = PackageVersions.INITIAL;

    private void update(int currentVersion)
    {
        if (currentVersion < PackageVersions.INITIAL)
        {
            var legacyDataFile = new File(this.plugin.getDataFolder(), "data.json");

            if (legacyDataFile.exists())
                migrateFromLegacyStorage();
        }

        setPackageVersion(TARGET_PACKAGE_VERSION);
    }

    @SuppressWarnings("removal")
    private void migrateFromLegacyStorage()
    {
        logger.info("Migrating player data...");

        var legacyStorage = new LegacyPlayerDataStore();
        legacyStorage.initializeStorage();

        legacyStorage.getAll().forEach(this::save);

        var file = legacyStorage.file();
        var success = file.renameTo(new File(file.getParent(), "data.json.old"));

        if (!success)
            logger.info("Can't rename 'data.json' to 'data.json.old', but it's not a big deal, I guess...");


        logger.info("Done migrating player data!");
    }

    /**
     * 上次成功提交写入的内容，用于跳过没有变化的写入
     */
    private final Map<UUID, String> lastSavedJson = new ConcurrentHashMap<>();

    /**
     * 将玩家数据写入磁盘
     * @apiNote 序列化在调用线程中进行（保证数据快照一致），实际的磁盘写入在 IO 线程中异步执行；
     *          如果内容和上次写入的完全相同，则跳过此次写入
     */
    public void save(PlayerMeta playerMeta)
    {
        var uuid = playerMeta.uniqueId;

        if (uuid == null)
        {
            logger.warn("Found a PlayerMeta that doesn't have an UUID! Ignoring...");
            return;
        }

        String json = gson.toJson(playerMeta);

        if (json.equals(lastSavedJson.get(uuid)))
            return;

        lastSavedJson.put(uuid, json);

        var path = this.getPath(uuid.toString()) + ".json";

        AsyncFileWriter.writeLatest("playerdata:" + path, json,
                () ->
                {
                    var file = this.directoryStorage.getFile(path, true);
                    if (file == null)
                        logger.warn("Cannot save disguise configuration for " + uuid);

                    return file;
                },
                () -> lastSavedJson.remove(uuid, json)); // 写入失败时允许下次重试
    }

    @Override
    protected PlayerMeta getDefault()
    {
        return defaultMeta;
    }

    //region IManagePlayerData

    private final Map<String, DisguiseMeta> cachedMetas = new ConcurrentHashMap<>();

    /**
     * 获取包含某一玩家的玩家名的伪装信息
     *
     * @param rawString 原始ID
     * @return 伪装信息
     * @apiNote 如果原始ID不是有效ID，则会返回null
     */
    @Override
    public @NotNull DisguiseMeta getDisguiseMeta(String rawString)
    {
        var cached = cachedMetas.getOrDefault(rawString, null);
        if (cached != null) return cached;

        var type = DisguiseTypes.fromId(rawString);

        var meta = new DisguiseMeta(rawString, type);
        cachedMetas.put(rawString, meta);

        return meta;
    }

    @Override
    public void clearCache()
    {
        super.clearCache();
        cachedMetas.clear();
    }

    /**
     * 获取某一玩家所有可用的伪装
     *
     * @param player 目标玩家
     * @return 目标玩家拥有的伪装
     */
    @Override
    public List<DisguiseMeta> getAvailableDisguisesFor(Player player)
    {
        return getPlayerMeta(player).getUnlockedDisguises();
    }

    @Override
    public CompletableFuture<PlayerMeta> loadPlayerDataAsync(UUID uuid)
    {
        var offline = Bukkit.getOfflinePlayer(uuid);
        return CompletableFuture.completedFuture(this.getPlayerMeta(offline));
    }

    /**
     * 将伪装授予某一玩家
     *
     * @param player             要授予的玩家
     * @param disguiseIdentifier 伪装ID
     * @return 添加是否成功（伪装是否可用或玩家是否已经拥有目标伪装）
     */
    @Override
    public boolean grantMorphToPlayer(Player player, String disguiseIdentifier)
    {
        var playerMeta = this.getPlayerMeta(player);
        var disguiseMeta = this.getDisguiseMeta(disguiseIdentifier);

        if (disguiseMeta == null) return false;

        if (playerMeta.getUnlockedDisguiseIdentifiers()
                .stream()
                .anyMatch(str -> str.equalsIgnoreCase(disguiseIdentifier)))
        {
            return false;
        }

        playerMeta.addDisguise(disguiseMeta);
        save(playerMeta);

        return true;
    }

    /**
     * 从某一玩家剥离伪装
     *
     * @param player             要授予的玩家
     * @param disguiseIdentifier 伪装ID
     * @return 添加是否成功（伪装是否可用或玩家是否已经拥有目标伪装）
     */
    @Override
    public boolean revokeMorphFromPlayer(Player player, String disguiseIdentifier)
    {
        var playerMeta = getPlayerMeta(player);
        var match = playerMeta.getUnlockedDisguises()
                .stream()
                .filter(meta -> meta.equals(disguiseIdentifier))
                .findFirst()
                .orElse(null);

        if (match == null) return false;

        playerMeta.removeDisguise(match);

        return true;
    }

    private final Map<UUID, PlayerMeta> trackedPlayerMetaMap = new ConcurrentHashMap<>();

    private boolean isDefaultMeta(@Nullable PlayerMeta meta)
    {
        return meta == null || meta.equals(defaultMeta);
    }

    /**
     * 获取玩家的伪装配置
     *
     * @param player 目标玩家
     * @return 伪装信息
     */
    @Override
    public @NotNull PlayerMeta getPlayerMeta(OfflinePlayer player)
    {
        var uuid = player.getUniqueId();

        var tracked = trackedPlayerMetaMap.getOrDefault(uuid, null);
        if (tracked != null) return tracked;

        var storedMeta = this.get(uuid.toString());

        // Don't process default meta
        if (!isDefaultMeta(storedMeta))
        {
            storedMeta.playerName = player.getName();
            initializePlayerMeta(storedMeta, uuid);

            // 记录从磁盘读取到的内容，这样关服时没有变化的玩家数据不会被重复写入
            lastSavedJson.putIfAbsent(uuid, gson.toJson(storedMeta));

            trackedPlayerMetaMap.put(uuid, storedMeta);

            return storedMeta;
        }

        var metaInstance = new PlayerMeta();
        metaInstance.uniqueId = player.getUniqueId();
        metaInstance.playerName = player.getName();
        initializePlayerMeta(metaInstance, uuid);

        trackedPlayerMetaMap.put(uuid, metaInstance);

        return metaInstance;
    }

    private void initializePlayerMeta(PlayerMeta meta, UUID matchingUUID)
    {
        meta.uniqueId = matchingUUID;

        if (FeatherMorphMain.getInstance().debugOutputEnabled())
            logger.info("Doing init for " + meta);

        //要设置给c.unlockedDisguises的列表
        var list = new ObjectArrayList<DisguiseMeta>();

        //原始列表
        var unlockedDisguiseIdentifiers = new ObjectArrayList<>(meta.getUnlockedDisguiseIdentifiers());

        //先对原始列表排序
        unlockedDisguiseIdentifiers.sort(null);

        //然后逐个添加
        unlockedDisguiseIdentifiers.forEach(disguiseId ->
        {
            var type = DisguiseTypes.fromId(disguiseId);

            if (type != null)
                list.add(new DisguiseMeta(disguiseId, DisguiseTypes.fromId(disguiseId)));
            else
                logger.warn("Unknown disguise identifier data '%s' owned by '%s'".formatted(disguiseId, matchingUUID));
        });

        //设置可用的伪装列表并对其加锁
        meta.setUnlockedDisguises(list);
    }

    @Override
    public boolean reload()
    {
        // 确保之前提交的写入已经落盘，避免重新从磁盘读取到旧数据
        AsyncFileWriter.flush(Duration.ofSeconds(10));

        clearCache();
        lastSavedJson.clear();
        trackedPlayerMetaMap.clear();

        return true;
    }

    @Override
    public boolean save()
    {
        // 只有内容发生变化的玩家数据才会真正写入磁盘
        this.trackedPlayerMetaMap.forEach((uuid, meta) -> this.save(meta));

        // 此方法会在关服时调用，因此需要等待所有写入完成
        return AsyncFileWriter.flush(Duration.ofSeconds(30));
    }

    //endregion IManagePlayerData

    @Override
    public List<PlayerMeta> getRange(List<UUID> list)
    {
        List<PlayerMeta> metaList = new ObjectArrayList<>();

        list.forEach(uuid ->
        {
            var existing = getPlayerMeta(Bukkit.getOfflinePlayer(uuid));
            metaList.add(existing);
        });

        return metaList;
    }

    public static class PackageVersions
    {
        public static final int INITIAL = 1;
    }
}
