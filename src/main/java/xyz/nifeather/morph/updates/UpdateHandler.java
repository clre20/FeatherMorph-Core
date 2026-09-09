package xyz.nifeather.morph.updates;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class UpdateHandler extends MorphPluginObject
{
    @Resolved(shouldSolveImmediately = true)
    private FeatherMorphMain plugin;

    private final AtomicInteger requestId = new AtomicInteger(0);

    private final Bindable<Boolean> checkUpdate = new Bindable<>(true);

    @Initializer
    private void load(MorphConfigManager config)
    {
        config.bind(checkUpdate, ConfigOptions.CHECK_UPDATE);

        this.update();
    }

    private void update()
    {
        // 每三小时检查一次更新
        this.addSchedule(this::update, 3 * 60 * 60 * 20);

        if (checkUpdate.get())
            this.checkUpdate(true, null);
    }

    private volatile CompletableFuture<CheckResult> runningFuture;

    private final Object lock = new Object();

    public CompletableFuture<CheckResult> checkUpdate(boolean sendMessages,
                            @Nullable CommandSender forwardTarget)
    {
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

        newFuture.thenRun(() ->
        {
            synchronized (lock)
            {
                this.runningFuture = null;
            }
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

                // Match same major series if currentVersion has a valid major (e.g. 261.x)
                if (currentVersion.major() > 0 && ver.major() != currentVersion.major())
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
                logger.info("Already on the latest version ({}) for major {}.", currentVersion, currentVersion.major());
                return CheckResult.ALREADY_LATEST;
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
