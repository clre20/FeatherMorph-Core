package xyz.nifeather.morph.updates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.craftbukkit.command.ServerCommandSender;
import org.jetbrains.annotations.Nullable;
import xiamomc.pluginbase.Annotations.Initializer;
import xiamomc.pluginbase.Annotations.Resolved;
import xiamomc.pluginbase.Bindables.Bindable;
import xiamomc.pluginbase.Messages.FormattableMessage;
import xyz.nifeather.morph.FeatherMorphMain;
import xyz.nifeather.morph.MorphPluginObject;
import xyz.nifeather.morph.config.ConfigOptions;
import xyz.nifeather.morph.config.MorphConfigManager;
import xyz.nifeather.morph.messages.MessageUtils;
import xyz.nifeather.morph.messages.strings.UpdateStrings;
import xyz.nifeather.morph.misc.permissions.CommonPermissions;
import xyz.nifeather.morph.utilities.FoliaThreadUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class UpdateHandler extends MorphPluginObject
{
    @Resolved(shouldSolveImmediately = true)
    private FeatherMorphMain plugin;

    private final AtomicInteger requestId = new AtomicInteger(0);

    private final Bindable<Boolean> checkOnStartup = new Bindable<>(true);
    private final Bindable<Boolean> notifyOnJoin = new Bindable<>(true);

    /** 檢查結果快取 10 分鐘，避免撞到 GitHub 未登入每小時 60 次的上限；不做定時輪詢。 */
    private static final long CACHE_DURATION_MILLIS = 10 * 60 * 1000L;

    /** 失敗結果只快取 1 分鐘，避免反覆重試又能較快恢復。 */
    private static final long FAIL_CACHE_DURATION_MILLIS = 60 * 1000L;

    /** 管理員登入後延遲 3 秒（60 tick）再提醒。 */
    private static final long JOIN_NOTIFY_DELAY_TICKS = 60L;

    @Nullable
    private volatile CheckResult cachedResult;
    private volatile long cachedAt;

    @Initializer
    private void load(MorphConfigManager config)
    {
        config.bind(checkOnStartup, ConfigOptions.UPDATE_CHECK_ON_STARTUP);
        config.bind(notifyOnJoin, ConfigOptions.UPDATE_NOTIFY_ON_JOIN);

        Bukkit.getPluginManager().registerEvents(new JoinListener(), plugin);

        // 啟動時檢查一次（若有 HttpHub 則由 HttpHub 集中處理，否則走內建非同步）
        if (Bukkit.getPluginManager().isPluginEnabled("HttpHub") && clre20.httpHub.api.HttpHubAPI.isAvailable())
        {
            clre20.httpHub.api.HttpHubAPI.registerUpdateCheck(plugin, "clre20/FeatherMorph-Core", plugin.getPluginMeta().getVersion(), CommonPermissions.CHECK_UPDATE, null);
        }
        else if (checkOnStartup.get())
        {
            this.checkUpdate(true, null);
        }
    }

    private final class JoinListener implements Listener
    {
        @EventHandler(priority = EventPriority.MONITOR)
        public void onJoin(PlayerJoinEvent event)
        {
            if (Bukkit.getPluginManager().isPluginEnabled("HttpHub")) return;
            if (!notifyOnJoin.get()) return;

            var player = event.getPlayer();
            if (!player.hasPermission(CommonPermissions.CHECK_UPDATE)) return;

            var uuid = player.getUniqueId();
            player.getScheduler().runDelayed(plugin, task -> notifyLater(uuid), null, JOIN_NOTIFY_DELAY_TICKS);
        }

        private void notifyLater(UUID uuid)
        {
            var player = Bukkit.getPlayer(uuid);
            if (player == null || !player.hasPermission(CommonPermissions.CHECK_UPDATE)) return;

            // 優先使用快取；快取過期才會重新查詢一次
            checkUpdate(false, null).thenAccept(result ->
            {
                if (result != CheckResult.HAS_UPDATE) return;

                var target = Bukkit.getPlayer(uuid);
                if (target != null)
                    target.getScheduler().run(plugin, t -> sendUpdateNotifyTo(target), null);
            });
        }
    }

    private volatile CompletableFuture<CheckResult> runningFuture;

    private final Object lock = new Object();

    public CompletableFuture<CheckResult> checkUpdate(boolean sendMessages,
                            @Nullable CommandSender forwardTarget)
    {
        var cached = this.cachedResult;
        var cacheDuration = cached == CheckResult.FAIL ? FAIL_CACHE_DURATION_MILLIS : CACHE_DURATION_MILLIS;
        if (cached != null && System.currentTimeMillis() - cachedAt < cacheDuration)
        {
            if (sendMessages && forwardTarget != null && cached == CheckResult.HAS_UPDATE)
                sendUpdateNotifyTo(forwardTarget);

            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<CheckResult> newFuture;
        synchronized (lock)
        {
            if (this.runningFuture != null && runningFuture.state() == Future.State.RUNNING)
                return runningFuture;
            else
                this.runningFuture = null;

            //Run async
            newFuture = doCheckUpdateAsync(sendMessages, forwardTarget);
            this.runningFuture = newFuture;
        }

        newFuture.thenAccept(result ->
        {
            synchronized (lock)
            {
                this.runningFuture = null;
            }

            this.cachedResult = result;
            this.cachedAt = System.currentTimeMillis();
        });

        return newFuture;
    }

    private CompletableFuture<CheckResult> doCheckUpdateAsync(boolean sendMessages, @Nullable CommandSender forwardTarget)
    {
        return CompletableFuture.supplyAsync(() -> doCheckUpdate(sendMessages, forwardTarget));
    }

    private CheckResult doCheckUpdate(boolean sendMessages, @Nullable CommandSender forwardTarget)
    {
        logger.info("Checking updates...");
        updateAvailable = false;

        var reqId = requestId.addAndGet(1);

        if (Bukkit.getPluginManager().isPluginEnabled("HttpHub") && clre20.httpHub.api.HttpHubAPI.isAvailable())
        {
            try
            {
                String responseBody = clre20.httpHub.api.HttpHubAPI.getStringAsync(plugin, "https://api.github.com/repos/clre20/FeatherMorph-Core/releases").join();
                return this.onUpdateReqFinish(responseBody, reqId, sendMessages, forwardTarget);
            }
            catch (Throwable t)
            {
                this.onUpdateReqFail(t, reqId);
                return CheckResult.FAIL;
            }
        }

        HttpClient httpClient = null;

        try
        {
            httpClient = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            // 1. Check GitHub Releases
            var releasesUri = new URI("https://api.github.com/repos/clre20/FeatherMorph-Core/releases");
            var request = HttpRequest.newBuilder()
                    .GET()
                    .uri(releasesUri)
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "FeatherMorph-UpdateChecker")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();
            boolean hasReleases = response.statusCode() == 200 && !responseBody.trim().equals("[]");

            // 2. If releases is empty, fallback to GitHub Tags
            if (!hasReleases)
            {
                var tagsUri = new URI("https://api.github.com/repos/clre20/FeatherMorph-Core/tags");
                var tagsReq = HttpRequest.newBuilder()
                        .GET()
                        .uri(tagsUri)
                        .timeout(Duration.ofSeconds(10))
                        .header("User-Agent", "FeatherMorph-UpdateChecker")
                        .header("Accept", "application/vnd.github.v3+json")
                        .build();

                var tagsResp = httpClient.send(tagsReq, HttpResponse.BodyHandlers.ofString());
                if (tagsResp.statusCode() == 200)
                {
                    responseBody = tagsResp.body();
                }
                else if (response.statusCode() != 200)
                {
                    logger.warn("Failed to check update: GitHub returned HTTP code {}", response.statusCode());
                    return CheckResult.FAIL;
                }
            }

            return this.onUpdateReqFinish(responseBody, reqId, sendMessages, forwardTarget);
        }
        catch (Throwable t)
        {
            this.onUpdateReqFail(t, reqId);

            return CheckResult.FAIL;
        }
        finally
        {
            if (httpClient != null)
                httpClient.close();
        }
    }

    private void onUpdateReqFail(Throwable e, int reqId)
    {
        if (this.requestId.get() != reqId)
            return;

        logger.error("Failed checking update", e);
    }

    private CheckResult onUpdateReqFinish(String responseStr, int reqId,
                                          boolean sendMessages,
                                          @Nullable CommandSender forwardTarget)
    {
        if (this.requestId.get() != reqId)
            return CheckResult.FAIL;

        try
        {
            var jsonElement = JsonParser.parseString(responseStr);
            if (!jsonElement.isJsonArray())
            {
                logger.warn("Invalid GitHub response format when checking updates.");
                return CheckResult.FAIL;
            }

            var jsonArray = jsonElement.getAsJsonArray();
            if (jsonArray.isEmpty())
            {
                logger.info("No releases or tags found on GitHub repository clre20/FeatherMorph-Core.");
                return CheckResult.ALREADY_LATEST;
            }

            var currentVersion = VersionHandling.toVersionInfo(plugin.getPluginMeta().getVersion());
            VersionHandling.VersionInfo latestMatchingVersion = null;
            String latestReleaseUrl = "https://github.com/clre20/FeatherMorph-Core/releases";

            for (var elem : jsonArray)
            {
                if (!elem.isJsonObject()) continue;
                var obj = elem.getAsJsonObject();

                if (obj.has("draft") && obj.get("draft").getAsBoolean())
                    continue;

                String tag = null;
                if (obj.has("tag_name") && !obj.get("tag_name").isJsonNull())
                    tag = obj.get("tag_name").getAsString();
                else if (obj.has("name") && !obj.get("name").isJsonNull())
                    tag = obj.get("name").getAsString();

                if (tag == null || tag.isBlank())
                    continue;

                var ver = VersionHandling.toVersionInfo(tag);
                if (ver.isInvalid())
                    continue;

                // 版本號前兩碼代表 Minecraft 版本（例如 26.3.x），只比對同一個 Minecraft 版本的 Release；
                // 舊格式的標籤（例如 v262.1.2）因為第一碼不同會被略過。
                if (currentVersion.major() > 0
                        && (ver.major() != currentVersion.major() || ver.minor() != currentVersion.minor()))
                    continue;

                if (latestMatchingVersion == null || latestMatchingVersion.compare(ver) == VersionHandling.CompareResult.INPUT_NEWER)
                {
                    latestMatchingVersion = ver;
                    if (obj.has("html_url") && !obj.get("html_url").isJsonNull())
                        latestReleaseUrl = obj.get("html_url").getAsString();
                }
            }

            if (latestMatchingVersion == null)
            {
                // 這個 Minecraft 版本在 GitHub 上還沒有任何 Release：目前的建置比 GitHub 新（開發版）
                logger.info("No release for {}.{}.x on GitHub yet, current version ({}) is newer (development build).",
                        currentVersion.major(), currentVersion.minor(), currentVersion);
                return CheckResult.CURRENT_IS_NEWER;
            }

            var compare = currentVersion.compare(latestMatchingVersion);

            if (compare == VersionHandling.CompareResult.EQUAL)
            {
                logger.info("Already on the latest version: {}", currentVersion);
                return CheckResult.ALREADY_LATEST;
            }

            if (compare == VersionHandling.CompareResult.INPUT_OLDER)
            {
                logger.info("Your version ({}) is newer than released ({})!", currentVersion, latestMatchingVersion);
                return CheckResult.CURRENT_IS_NEWER;
            }

            if (compare == VersionHandling.CompareResult.NOT_ON_SAME_CHANNEL)
                logger.info("We are not on the same channel with the latest release, assuming there is a new update!");

            // 提醒服务器关于更新的消息
            var serverOps = Bukkit.getOperators();
            var sendTargets = new ObjectArrayList<CommandSender>();

            if (forwardTarget == null)
            {
                serverOps.forEach(offlinePlayer ->
                {
                    var onlinePlayer = offlinePlayer.getPlayer();
                    if (onlinePlayer != null && onlinePlayer.hasPermission(CommonPermissions.CHECK_UPDATE))
                        sendTargets.add(onlinePlayer);
                });
            }
            else
            {
                if (!(forwardTarget instanceof ServerCommandSender || forwardTarget instanceof ConsoleCommandSender))
                    sendTargets.add(forwardTarget);
            }

            sendTargets.add(Bukkit.getConsoleSender());

            this.msgPrimary = UpdateStrings.newVersionAvailable()
                    .resolve("current", currentVersion.toString())
                    .resolve("origin", latestMatchingVersion.toString());

            this.msgSecondary = UpdateStrings.update_here()
                    .resolve("url", latestReleaseUrl);

            this.updateAvailable = true;

            if (sendMessages)
            {
                for (var sendTarget : sendTargets)
                    sendUpdateNotifyTo(sendTarget);
            }

            return CheckResult.HAS_UPDATE;
        }
        catch (Throwable t)
        {
            logger.error("Error occurred while processing response", t);
            return CheckResult.FAIL;
        }
    }

    private final FormattableMessage messageHeaderFooter = UpdateStrings.messageHeaderFooter();

    private final FormattableMessage noNewVersionAvailable = UpdateStrings.noNewVersionAvailable();

    @Nullable
    private FormattableMessage msgPrimary;

    @Nullable
    private FormattableMessage msgSecondary;

    private boolean updateAvailable = false;

    public boolean updateAvailable()
    {
        return updateAvailable;
    }

    public void sendUpdateNotifyTo(CommandSender sendTarget)
    {
        if (!updateAvailable)
        {
            MessageUtils.send(sendTarget, noNewVersionAvailable);
            return;
        }

        assert msgPrimary != null;
        assert msgSecondary != null;

        MessageUtils.send(sendTarget, messageHeaderFooter);
        MessageUtils.send(sendTarget, msgPrimary);
        MessageUtils.send(sendTarget, msgSecondary);
        MessageUtils.send(sendTarget, messageHeaderFooter);
    }

    private static class InvalidOperationException extends RuntimeException
    {
        public InvalidOperationException() {
        }

        public InvalidOperationException(String message) {
            super(message);
        }

        public InvalidOperationException(String message, Throwable cause) {
            super(message, cause);
        }

        public InvalidOperationException(Throwable cause) {
            super(cause);
        }
    }

    public enum CheckResult
    {
        HAS_UPDATE,
        ALREADY_LATEST,
        NOT_LISTED_OR_UNSUPPORTED,
        CURRENT_IS_NEWER,
        FAIL
    }
}
