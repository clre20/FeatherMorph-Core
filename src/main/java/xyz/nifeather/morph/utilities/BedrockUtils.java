package xyz.nifeather.morph.utilities;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/**
 * 判斷玩家是否為基岩版玩家（透過 Geyser / Floodgate 連線）。
 * <p>
 * 以反射呼叫 Floodgate 或 Geyser 的 API，伺服器沒有安裝時一律視為 Java 版玩家，不需要額外依賴。
 */
public final class BedrockUtils
{
    private static final Logger logger = LoggerFactory.getLogger("FeatherMorph$BedrockUtils");

    private BedrockUtils()
    {
    }

    /** 回傳 boolean 的檢查函式：(UUID) -> boolean；null 表示伺服器上沒有可用的 API。 */
    @Nullable
    private static volatile MethodHandle checker;

    private static volatile boolean initialized;

    public static boolean isBedrockPlayer(@Nullable Player player)
    {
        if (player == null) return false;

        var handle = getChecker();
        if (handle == null) return false;

        try
        {
            return (boolean) handle.invokeExact(player.getUniqueId());
        }
        catch (Throwable t)
        {
            return false;
        }
    }

    @Nullable
    private static MethodHandle getChecker()
    {
        if (initialized) return checker;

        synchronized (BedrockUtils.class)
        {
            if (initialized) return checker;

            checker = createFloodgateChecker();
            if (checker == null)
                checker = createGeyserChecker();

            initialized = true;
            return checker;
        }
    }

    /** FloodgateApi.getInstance().isFloodgatePlayer(UUID) */
    @Nullable
    private static MethodHandle createFloodgateChecker()
    {
        var plugin = Bukkit.getPluginManager().getPlugin("floodgate");
        if (plugin == null) return null;

        try
        {
            var apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, plugin.getClass().getClassLoader());
            var lookup = MethodHandles.publicLookup();
            var instance = lookup.findStatic(apiClass, "getInstance", MethodType.methodType(apiClass)).invoke();
            var method = lookup.findVirtual(apiClass, "isFloodgatePlayer", MethodType.methodType(boolean.class, UUID.class));

            logger.info("Detected Floodgate, Bedrock players will receive Bedrock-compatible messages.");
            return method.bindTo(instance);
        }
        catch (Throwable t)
        {
            logger.warn("Floodgate is installed but its API could not be accessed: " + t);
            return null;
        }
    }

    /** GeyserApi.api().isBedrockPlayer(UUID) */
    @Nullable
    private static MethodHandle createGeyserChecker()
    {
        var plugin = Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if (plugin == null) return null;

        try
        {
            var apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi", true, plugin.getClass().getClassLoader());
            var lookup = MethodHandles.publicLookup();
            var instance = lookup.findStatic(apiClass, "api", MethodType.methodType(apiClass)).invoke();
            var method = lookup.findVirtual(apiClass, "isBedrockPlayer", MethodType.methodType(boolean.class, UUID.class));

            logger.info("Detected Geyser, Bedrock players will receive Bedrock-compatible messages.");
            return method.bindTo(instance);
        }
        catch (Throwable t)
        {
            logger.warn("Geyser is installed but its API could not be accessed: " + t);
            return null;
        }
    }
}
