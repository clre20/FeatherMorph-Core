package xyz.nifeather.morph.utilities;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 将磁盘写入从主线程/区域线程移到单独的 IO 线程
 * <p>
 * - 所有写入都在同一个线程中按顺序执行，因此同一文件的写入顺序与提交顺序一致<br>
 * - 对同一个 key 的多次写入会被合并，只写入最新的内容<br>
 * - 覆盖写入使用 "写临时文件 + 原子替换"，避免崩溃时留下写了一半的文件
 */
public final class AsyncFileWriter
{
    private static final Logger logger = LoggerFactory.getLogger("FeatherMorph-IO");

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r ->
    {
        var thread = new Thread(r, "FeatherMorph-IO");
        thread.setDaemon(true);
        return thread;
    });

    private record PendingWrite(String content, Supplier<File> fileResolver, @Nullable Runnable onFailure)
    {
    }

    private static final Map<String, PendingWrite> pendingWrites = new ConcurrentHashMap<>();

    private AsyncFileWriter()
    {
    }

    /**
     * 提交一个覆盖写入
     * @param key 用于合并写入的唯一标识（通常为文件路径）
     * @param content 要写入的内容
     * @param fileResolver 在 IO 线程中获取目标文件，返回 null 时放弃写入
     * @param onFailure 写入失败时在 IO 线程中调用
     */
    public static void writeLatest(String key, String content,
                                   Supplier<File> fileResolver,
                                   @Nullable Runnable onFailure)
    {
        // 如果已经有一个等待中的写入，它会在执行时读取到最新的内容，无需再次提交任务
        if (pendingWrites.put(key, new PendingWrite(content, fileResolver, onFailure)) != null)
            return;

        executor.execute(() ->
        {
            var pending = pendingWrites.remove(key);
            if (pending == null) return;

            boolean success = false;

            try
            {
                var file = pending.fileResolver().get();
                if (file != null)
                {
                    writeAtomic(file, pending.content());
                    success = true;
                }
            }
            catch (Throwable t)
            {
                logger.error("Can't write content to file '{}': {}", key, t.getMessage());
            }

            if (!success && pending.onFailure() != null)
                pending.onFailure().run();
        });
    }

    /**
     * 提交一个追加写入
     */
    public static void appendAsync(Supplier<File> fileResolver, String content)
    {
        executor.execute(() ->
        {
            try
            {
                var file = fileResolver.get();
                if (file == null) return;

                Files.writeString(file.toPath(), content, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            }
            catch (Throwable t)
            {
                logger.error("Can't append content to file: {}", t.getMessage());
            }
        });
    }

    /**
     * 在 IO 线程中执行任意任务
     */
    public static void execute(Runnable runnable)
    {
        executor.execute(runnable);
    }

    /**
     * 等待目前所有已提交的写入完成（用于关服、重载等需要确保数据落盘的场景）
     * @return 是否在超时前完成
     */
    public static boolean flush(Duration timeout)
    {
        try
        {
            executor.submit(() -> {}).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return true;
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
        catch (ExecutionException | TimeoutException | RejectedExecutionException e)
        {
            logger.warn("Timed out or failed waiting for pending file writes: {}", e.toString());
            return false;
        }
    }

    private static void writeAtomic(File file, String content) throws IOException
    {
        var target = file.toPath();

        var parent = target.getParent();
        if (parent != null)
            Files.createDirectories(parent);

        var temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);

        try
        {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException e)
        {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
