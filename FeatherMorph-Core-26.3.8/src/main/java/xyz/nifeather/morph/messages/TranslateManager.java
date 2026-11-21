package xyz.nifeather.morph.messages;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xiamomc.pluginbase.Exceptions.NullDependencyException;
import xiamomc.pluginbase.Messages.IStrings;
import xiamomc.pluginbase.Messages.MessageStore;
import xyz.nifeather.morph.FeatherMorphMain;
import xyz.nifeather.morph.misc.ExecutionErrorException;
import xyz.nifeather.morph.utilities.PluginAssetUtils;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TranslateManager
{
    private static final TranslateManager instance = new TranslateManager();
    private final Logger logger = LoggerFactory.getLogger("FeatherMorph$TranslateManager");

    public static TranslateManager instance()
    {
        return instance;
    }

    // Locale <-> Store
    private final Map<String, OverlayingMessageStore> messageStoreMap = new ConcurrentHashMap<>();

    public TranslateManager()
    {
        init();
    }

    public static final String FALLBACK_LOCALE = "en_us";

    /**
     * 把插件內建的語言檔合併進使用者已存在的語言檔：
     * <ul>
     *     <li>缺少的項目補上內建文字</li>
     *     <li>使用者沒改過的項目（與上次內建版本相同）更新為新的內建文字</li>
     *     <li>使用者改過的項目一律保留</li>
     * </ul>
     * 「上次內建版本」存放在 messages/.bundled/&lt;語言&gt;.json，只在啟動時讀寫一次。
     */
    private void mergeLocaleFile(String lang, File messagesDirectory) throws Exception
    {
        var bundledRaw = PluginAssetUtils.getFileStringsOptional(PluginAssetUtils.langPath(lang));
        if (bundledRaw.isEmpty()) return;

        var bundled = com.google.gson.JsonParser.parseString(bundledRaw.get()).getAsJsonObject();

        var userFile = new File(messagesDirectory, lang + ".json");
        com.google.gson.JsonObject user;
        try (var reader = java.nio.file.Files.newBufferedReader(userFile.toPath(), java.nio.charset.StandardCharsets.UTF_8))
        {
            var parsed = com.google.gson.JsonParser.parseReader(reader);
            if (!parsed.isJsonObject())
            {
                logger.warn("Locale file {} is not a JSON object, skipping merge", userFile.getName());
                return;
            }
            user = parsed.getAsJsonObject();
        }

        var snapshotDir = new File(messagesDirectory, ".bundled");
        var snapshotFile = new File(snapshotDir, lang + ".json");
        com.google.gson.JsonObject snapshot = null;
        if (snapshotFile.exists())
        {
            try (var reader = java.nio.file.Files.newBufferedReader(snapshotFile.toPath(), java.nio.charset.StandardCharsets.UTF_8))
            {
                var parsed = com.google.gson.JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) snapshot = parsed.getAsJsonObject();
            }
            catch (Throwable t)
            {
                logger.warn("Unable to read locale snapshot {}, only filling missing entries", snapshotFile.getName());
            }
        }

        // 沒有快照時，若有該語言首次發布時的內建版本（基準檔），以它判斷使用者是否改過
        if (snapshot == null)
        {
            var baseline = PluginAssetUtils.getFileStringsOptional(PluginAssetUtils.assetPath + "/lang/baseline/" + lang + ".json");
            if (baseline.isPresent())
                snapshot = com.google.gson.JsonParser.parseString(baseline.get()).getAsJsonObject();
        }

        int added = 0;
        int updated = 0;
        for (var entry : bundled.entrySet())
        {
            var key = entry.getKey();
            var bundledValue = entry.getValue();

            if (!user.has(key))
            {
                user.add(key, bundledValue);
                added++;
            }
            else if (snapshot != null && snapshot.has(key)
                    && user.get(key).equals(snapshot.get(key))
                    && !user.get(key).equals(bundledValue))
            {
                user.add(key, bundledValue);
                updated++;
            }
        }

        var gson = new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        if (added > 0 || updated > 0)
        {
            java.nio.file.Files.writeString(userFile.toPath(), gson.toJson(user), java.nio.charset.StandardCharsets.UTF_8);
            logger.info("Updated locale file {}: {} added, {} updated", userFile.getName(), added, updated);
        }

        // 記錄這次的內建版本，供下次判斷使用者是否改過
        if (!snapshotDir.exists() && !snapshotDir.mkdirs())
            throw new java.io.IOException("Unable to create " + snapshotDir);

        java.nio.file.Files.writeString(snapshotFile.toPath(), gson.toJson(bundled), java.nio.charset.StandardCharsets.UTF_8);

        // 舊版會產生 .fmnew.json 供比對，合併後已不需要
        var legacyNew = new File(messagesDirectory, lang + ".fmnew.json");
        if (legacyNew.exists() && !legacyNew.delete())
            logger.warn("Unable to delete {}", legacyNew.getName());
    }

    private void init()
    {
        logger.info("Initializing translate manager");

        logger.info("Extracting default locale files... (This won't affect existing files)");
        var messagesDirectory = new File(FeatherMorphMain.getInstance().getDataFolder(), "messages");
        for (String lang : PluginAssetUtils.allSupportedLanguages())
        {
            try
            {
                var existing = new File(messagesDirectory, lang + ".json");
                if (existing.exists())
                    mergeLocaleFile(lang, messagesDirectory);
                else
                {
                    PluginAssetUtils.extractLocaleFile(lang, messagesDirectory, false);
                    mergeLocaleFile(lang, messagesDirectory); // 只會寫入快照
                }
            }
            catch (Throwable e)
            {
                logger.warn("Error occurred extracting default locale files, ignoring", e);
            }
        }
        logger.info("Done Extracting default locale files");

        // 先合併語言檔再載入，確保載入的是補齊後的內容
        setupLanguage(FALLBACK_LOCALE);

        logger.info("Done initializing translate manager");
    }

    public void setupLanguage(String locale)
    {
        var store = new OverlayingMessageStore(locale);

        try
        {
            store.load();
        }
        catch (Throwable t)
        {
            logger.error("Failed to load i18n store for language %s".formatted(locale), t);
        }

        messageStoreMap.put(locale, store);
        logger.info("Done loading i18n store for language %s".formatted(locale));
    }

    public OverlayingMessageStore lookTranslateStoreOrCreate(String locale)
    {
        if (!messageStoreMap.containsKey(locale))
            setupLanguage(locale);

        return lookupTranslateStore(locale).orElseThrow();
    }

    public Optional<OverlayingMessageStore> lookupTranslateStore(String locale)
    {
        return Optional.ofNullable(messageStoreMap.getOrDefault(locale, null));
    }

    public OverlayingMessageStore getDefaultTranslateStore()
    {
        return lookupTranslateStore(FALLBACK_LOCALE).orElseThrow(() -> new NullDependencyException("No"));
    }


    public Optional<String> lookupTranslate(String rawLocale, String messageKey)
    {
        return lookupTranslate(rawLocale, messageKey, true);
    }

    public Optional<String> lookupTranslate(String rawLocale, String messageKey, boolean allowFallback)
    {
        var locale = rawLocale.toLowerCase(Locale.ROOT);

        var translateStore = lookTranslateStoreOrCreate(rawLocale);

        var result = translateStore.lookup(messageKey);
        if (result.isPresent()) return result; // 如果存在对应的语言文件，则返回

        // 不然，如果允许我们检查fallback，则再去检查 FALLBACK_LOCALE 里对应的存储
        if (allowFallback && !translateStore.targetLocale().equals(FALLBACK_LOCALE))
        {
            var defaultResult = lookupTranslate(FALLBACK_LOCALE, messageKey);
            return defaultResult.or(() -> Optional.of("[%s:%s]".formatted(locale, messageKey)));
        }

        return result;
    }

    public void reload()
    {
        messageStoreMap.values().forEach(OverlayingMessageStore::reload);
    }

    public MessageStore<FeatherMorphMain> asFrameworkMessageStore()
    {
        return new FrameworkMessageStoreBinding(this);
    }

    public static class FrameworkMessageStoreBinding extends MessageStore<FeatherMorphMain>
    {
        private final TranslateManager translate;

        public FrameworkMessageStoreBinding(TranslateManager translateManager)
        {
            this.translate = translateManager;
            storingObject = new HashMap<>();
        }

        @Override
        protected List<Class<? extends IStrings>> getStrings()
        {
            return List.of();
        }

        @Override
        protected String getPluginNamespace()
        {
            return FeatherMorphMain.getMorphNameSpace();
        }

        @Override
        public void initializeStorage(boolean noReload)
        {
        }

        @Override
        public void addMissingStrings()
        {
        }

        @Override
        public String get(String key, @Nullable String defaultValue, @Nullable String locale)
        {
            return translate.lookupTranslate("override", key, false)
                    .orElseGet(() -> translate.lookupTranslate(locale == null ? FALLBACK_LOCALE : locale, key).orElse(key));
        }
    }
}
