package xyz.nifeather.morph.mirror;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xiamomc.pluginbase.Annotations.Initializer;
import xiamomc.pluginbase.Bindables.Bindable;
import xyz.nifeather.morph.FeatherMorphMain;
import xyz.nifeather.morph.MorphPluginObject;
import xyz.nifeather.morph.config.ConfigOptions;
import xyz.nifeather.morph.config.MorphConfigManager;
import xyz.nifeather.morph.events.InteractionMirrorProcessor;
import xyz.nifeather.morph.mirror.impl.executors.ByNameExecutor;
import xyz.nifeather.morph.mirror.impl.executors.ByRangeExecutor;
import xyz.nifeather.morph.mirror.impl.executors.BySightExecutor;
import xyz.nifeather.morph.mirror.impl.simulator.FallbackOperationHandle;
import xyz.nifeather.morph.mirror.impl.simulator.MannequinOperationHandle;
import xyz.nifeather.morph.mirror.impl.simulator.PlayerOperationHandle;
import xyz.nifeather.morph.storage.DirectoryStorage;
import xyz.nifeather.morph.storage.mirrorlogging.MirrorSingleEntry;
import xyz.nifeather.morph.storage.mirrorlogging.OperationType;
import xyz.nifeather.morph.utilities.AsyncFileWriter;

import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ExecutorHub extends MorphPluginObject
{
    private final ConcurrentHashMap<String, IExecutor<Player, ItemStack, Action>> executorMap = new ConcurrentHashMap<>();

    public ExecutorHub()
    {
        registerExecutor(InteractionMirrorProcessor.InteractionMirrorSelectionMode.BY_NAME, new ByNameExecutor(this));
        registerExecutor(InteractionMirrorProcessor.InteractionMirrorSelectionMode.BY_SIGHT, new BySightExecutor(this));
        registerExecutor(InteractionMirrorProcessor.InteractionMirrorSelectionMode.BY_RANGE, new ByRangeExecutor(this));
    }

    public final Bindable<Boolean> logOperations = new Bindable<>(false);
    public final Bindable<Integer> cleanUpDate = new Bindable<>(3);

    public final Bindable<Integer> controlRange = new Bindable<>(0);

    @Initializer
    private void load(MorphConfigManager config)
    {
        this.addSchedule(this::update);

        config.bind(logOperations, ConfigOptions.MIRROR_LOG_OPERATION);
        config.bind(cleanUpDate, ConfigOptions.MIRROR_LOG_CLEANUP_DATE);
        config.bind(controlRange, ConfigOptions.MIRROR_CONTROL_DISTANCE);

        initOperationHandleMap();
    }

    private void update()
    {
        this.addSchedule(this::update);

        if (plugin.getCurrentTick() % (5 * 20) == 0)
            pushToLoggingBase();
    }

    public void registerExecutor(String name, IExecutor<Player, ItemStack, Action> executor)
    {
        executorMap.put(name, executor);
    }

    @Nullable
    public IExecutor<Player, ItemStack, Action> getExecutor(String name)
    {
        return executorMap.getOrDefault(name.toUpperCase(), null);
    }

    public void executeIfExists(String executorName, Consumer<IExecutor<Player, ItemStack, Action>> consumer)
    {
        var executor = getExecutor(executorName);

        if (executor != null)
            consumer.accept(executor);
    }

    // Player <-> Name
    private final Map<Player, String> mirrorMap = new ConcurrentHashMap<>();

    public void registerControl(Player player, @Nullable String targetName)
    {
        if (targetName == null || targetName.isBlank())
            mirrorMap.remove(player);
        else
            mirrorMap.put(player, targetName);
    }

    public void unregisterControl(Player player)
    {
        registerControl(player, null);
    }

    @Nullable
    public String getControl(Player player)
    {
        return mirrorMap.getOrDefault(player, null);
    }

    public int getControlDistance()
    {
        return controlRange.get();
    }

    //region MirrorableEntity lookup

    private final Map<EntityType, IOperationHandle<?>> operationHandleMap = new ConcurrentHashMap<>();

    private void initOperationHandleMap()
    {
        operationHandleMap.put(EntityType.PLAYER, new PlayerOperationHandle());
        operationHandleMap.put(EntityType.MANNEQUIN, new MannequinOperationHandle());
    }

    /**
     * @return {@link FallbackOperationHandle} if no simulator matched for the giving entity
     */
    @NotNull
    public <E extends LivingEntity> IOperationHandle<E> lookupOperationHandle(E mirrorableEntity)
    {
        for (Map.Entry<EntityType, IOperationHandle<?>> entry : operationHandleMap.entrySet())
        {
            if (entry.getKey().equals(mirrorableEntity.getType()))
                return (IOperationHandle<E>) entry.getValue();
        }

        if (FeatherMorphMain.getInstance().debugOutputEnabled())
            logger.error("No simulator found for entity %s".formatted(mirrorableEntity));

        return (IOperationHandle<E>) FallbackOperationHandle.INSTANCE;
    }

    //endregion MirrorableEntity lookup

    //region Operation Logging

    private final DirectoryStorage logStore = new DirectoryStorage("logs");

    private final Map<Player, Stack<MirrorSingleEntry>> tempEntries = new Object2ObjectOpenHashMap<>();

    private final SimpleDateFormat logFileTimeFormat = new SimpleDateFormat("yyyy-MM-dd");

    private File loggingTargetFile;

    private String currentLogDate = "0000-00-00";

    private void cleanUpLogFiles(int days)
    {
        if (days <= 0) return;

        var files = logStore.getFiles("mirror-[0-9]{4}-[0-9]{2}-[0-9]{2}.log");
        var calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -days);

        //todo: Replace this with a nicer implementation
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        var targetDate = calendar.getTime();

        for (File file : files)
        {
            //[0:mirror] [1:YYYY] [2:MM] [3:dd] [4:.log]
            var splitName = file.getName().split("-");
            if (splitName.length < 4) continue;

            var formattedName = "%s-%s-%s".formatted(splitName[1], splitName[2], splitName[3]);

            if (formattedName.equals(currentLogDate)) continue;

            Date date;

            try
            {
                date = logFileTimeFormat.parse(formattedName);
            }
            catch (ParseException e)
            {
                logger.error("Unable to determine creation date for InteractionMirror log file '%s'".formatted(file.getName()), e);
                continue;
            }

            if (date.after(targetDate)) continue;

            logger.info("Removing InteractionMirror log '%s' as it's older than %s day(s)".formatted(file.getName(), days));

            if (!file.delete())
                logger.warn("Unable to remove file: Unknown error");
        }
    }

    private void updateTargetFile()
    {
        var targetLogDate = logFileTimeFormat.format(new Date(System.currentTimeMillis()));
        var createNew = !targetLogDate.equals(currentLogDate) || loggingTargetFile == null;

        if (!createNew) return;

        // 只在切换日志文件（启动后首次写入或跨天）时清理旧日志，而不是每次写入都扫描目录
        cleanUpLogFiles(cleanUpDate.get());

        this.currentLogDate = targetLogDate;
        this.loggingTargetFile = logStore.getFile("mirror-%s.log".formatted(targetLogDate), true);
    }

    /**
     * 将缓存的操作记录写入日志文件
     * @apiNote 字符串在调用线程中构建，文件的创建、清理和写入都在 IO 线程中执行，避免阻塞主线程
     */
    public void pushToLoggingBase()
    {
        if (logStore.initializeFailed())
            return;

        String content;

        synchronized (tempEntries)
        {
            if (tempEntries.isEmpty())
                return;

            var dateFormat = new SimpleDateFormat("HH:mm:ss");
            var builder = new StringBuilder();

            tempEntries.forEach((p, stack) ->
            {
                for (var entry : stack)
                {
                    builder.append("[%s] %s triggered operation %s for player %s repeating %s time(s).\n"
                            .formatted(dateFormat.format(new Date(entry.timeMills())),
                                    entry.playerName(), entry.operationType(),
                                    entry.targetPlayerName(), entry.repeatingTimes()));
                }
            });

            this.tempEntries.clear();
            content = builder.toString();
        }

        // 只在 IO 线程中访问 loggingTargetFile / currentLogDate
        AsyncFileWriter.appendAsync(() ->
        {
            // 每次写入前都检查日期，以便跨天时切换到新的日志文件
            updateTargetFile();
            return this.loggingTargetFile;
        }, content);
    }

    @NotNull
    private MirrorSingleEntry getOrCreateEntryFor(Player player, LivingEntity targetPlayer, OperationType type)
    {
        synchronized (tempEntries)
        {
            var playerStack = tempEntries.getOrDefault(player, null);

            if (playerStack == null)
            {
                playerStack = new Stack<>();
                tempEntries.put(player, playerStack);
            }

            MirrorSingleEntry entry = null;

            if (!playerStack.isEmpty())
            {
                var peek = playerStack.peek();
                if (peek.uuid().equals(player.getUniqueId().toString())
                        && peek.targetPlayerName().equals(targetPlayer.getName())
                        && peek.operationType() == type)
                {
                    entry = peek;
                }
            }

            if (entry != null) return entry;

            entry = new MirrorSingleEntry(player.getName(), player.getUniqueId().toString(), targetPlayer.getName(), type, 0, System.currentTimeMillis());
            playerStack.push(entry);

            return entry;
        }
    }

    public void logOperation(Player source, LivingEntity targetPlayer, OperationType type)
    {
        if (!logOperations.get()) return;

        var entry = getOrCreateEntryFor(source, targetPlayer, type);
        entry.increaseRepeatingTimes();
    }

    //endregion Operation Logging
}
