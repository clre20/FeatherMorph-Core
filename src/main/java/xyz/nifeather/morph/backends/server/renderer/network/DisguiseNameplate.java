package xyz.nifeather.morph.backends.server.renderer.network;

import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.util.Vector3f;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.Display;
import org.jetbrains.annotations.Nullable;
import org.slf4j.LoggerFactory;
import xyz.nifeather.morph.config.ConfigOptions;
import xyz.nifeather.morph.config.MorphConfigManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 玩家偽裝的「偽裝」名牌標記。
 * <p>
 * 以一個只存在於封包中的文字展示實體（TextDisplay）騎乘在偽裝玩家身上來顯示。
 * 原版客戶端在玩家身上有乘客時不會繪製該玩家的名牌，因此原本的名字會被這個文字展示實體取代，
 * 文字展示實體會顯示「名字 + 標記」。
 */
public final class DisguiseNameplate
{
    private DisguiseNameplate()
    {
    }

    public static final String BYPASS_PERMISSION = "feathermorph.nameplate.bypass";

    public enum Position
    {
        ABOVE, BELOW, SUFFIX;

        public static Position parse(@Nullable String raw)
        {
            if (raw == null) return SUFFIX;

            return switch (raw.trim().toLowerCase(Locale.ROOT))
            {
                case "above" -> ABOVE;
                case "below" -> BELOW;
                default -> SUFFIX;
            };
        }
    }

    //region 實體資料 index（執行期從伺服器讀取，避免寫死 index 造成客戶端斷線）

    private static final int TRANSLATION_INDEX;
    private static final int BILLBOARD_INDEX;
    private static final int TEXT_INDEX;
    private static final int BACKGROUND_INDEX;
    private static final int STYLE_FLAGS_INDEX;
    private static final boolean AVAILABLE;

    static
    {
        int translation = -1, billboard = -1, text = -1, background = -1, flags = -1;
        boolean ok;

        try
        {
            translation = accessorId(Display.class, "DATA_TRANSLATION_ID");
            billboard = accessorId(Display.class, "DATA_BILLBOARD_RENDER_CONSTRAINTS_ID");
            text = accessorId(Display.TextDisplay.class, "DATA_TEXT_ID");
            background = accessorId(Display.TextDisplay.class, "DATA_BACKGROUND_COLOR_ID");
            flags = accessorId(Display.TextDisplay.class, "DATA_STYLE_FLAGS_ID");
            ok = true;
        }
        catch (Throwable t)
        {
            ok = false;
            LoggerFactory.getLogger("FeatherMorph").warn("Unable to resolve TextDisplay data indexes, disguise nameplate marker is disabled.", t);
        }

        TRANSLATION_INDEX = translation;
        BILLBOARD_INDEX = billboard;
        TEXT_INDEX = text;
        BACKGROUND_INDEX = background;
        STYLE_FLAGS_INDEX = flags;
        AVAILABLE = ok;
    }

    private static int accessorId(Class<?> clazz, String fieldName) throws ReflectiveOperationException
    {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        return ((EntityDataAccessor<?>) field.get(null)).id();
    }

    //endregion

    //region 設定

    /**
     * @return 功能是否啟用（設定檔開啟，且伺服器版本支援）
     */
    public static boolean isEnabled()
    {
        if (!AVAILABLE) return false;

        var config = MorphConfigManager.getInstance();
        return config != null && Boolean.TRUE.equals(config.getOrDefault(ConfigOptions.NAMEPLATE_MARKER_ENABLED));
    }

    private static Position position()
    {
        var config = MorphConfigManager.getInstance();
        return Position.parse(config == null ? null : config.getOrDefault(ConfigOptions.NAMEPLATE_MARKER_POSITION));
    }

    private static volatile String cachedRawText;
    private static volatile Component cachedMarker = Component.empty();

    private static Component marker()
    {
        var config = MorphConfigManager.getInstance();
        String raw = config == null ? null : config.getOrDefault(ConfigOptions.NAMEPLATE_MARKER_TEXT);
        if (raw == null) raw = ConfigOptions.NAMEPLATE_MARKER_TEXT.getDefault();

        // 只在文字變更時重新解析 MiniMessage
        if (!raw.equals(cachedRawText))
        {
            Component parsed;
            try
            {
                parsed = MiniMessage.miniMessage().deserialize(raw);
            }
            catch (Throwable t)
            {
                parsed = Component.text(raw);
            }

            cachedMarker = parsed;
            cachedRawText = raw;
        }

        return cachedMarker;
    }

    //endregion

    //region 封包內容

    private static final byte BILLBOARD_CENTER = 3;
    private static final byte FLAG_SEE_THROUGH = 0x02;
    private static final int DEFAULT_BACKGROUND = 0x40000000;

    // 文字展示實體騎在玩家頭頂；原版名牌底部約在頭頂上方 0.275 格，一行文字高約 0.25 格
    private static final float BASE_OFFSET_Y = 0.275f;
    private static final float LINE_HEIGHT = 0.25f;

    /**
     * 建立文字展示實體的完整資料
     * @param name 偽裝的名字（純文字，不解析格式）
     * @param sneaking 偽裝者是否正在潛行（潛行時與原版相同，名牌不會穿牆顯示）
     * @param invisible 偽裝者是否隱形（隱形時與原版相同，不顯示名牌）
     */
    public static List<EntityData<?>> buildMetadata(String name, boolean sneaking, boolean invisible)
    {
        var position = position();
        var nameComponent = Component.text(name, NamedTextColor.WHITE);
        var marker = marker();

        Component text;
        float offsetY = BASE_OFFSET_Y;

        switch (position)
        {
            case ABOVE -> text = Component.textOfChildren(marker, Component.newline(), nameComponent);
            case BELOW ->
            {
                text = Component.textOfChildren(nameComponent, Component.newline(), marker);
                // 多出來的一行往下放，讓名字維持在原版名牌的高度
                offsetY -= LINE_HEIGHT;
            }
            default -> text = Component.textOfChildren(nameComponent, Component.space(), marker);
        }

        List<EntityData<?>> list = new ObjectArrayList<>(5);
        list.add(new EntityData<>(TRANSLATION_INDEX, EntityDataTypes.VECTOR3F, new Vector3f(0, offsetY, 0)));
        list.add(new EntityData<>(BILLBOARD_INDEX, EntityDataTypes.BYTE, BILLBOARD_CENTER));
        list.add(new EntityData<>(TEXT_INDEX, EntityDataTypes.ADV_COMPONENT, invisible ? Component.empty() : text));
        list.add(new EntityData<>(BACKGROUND_INDEX, EntityDataTypes.INT, invisible ? 0 : DEFAULT_BACKGROUND));
        list.add(new EntityData<>(STYLE_FLAGS_INDEX, EntityDataTypes.BYTE, (sneaking || invisible) ? (byte) 0 : FLAG_SEE_THROUGH));

        return list;
    }

    //endregion

    //region 玩家實體 ID → 名牌實體 ID（給封包監聽器使用）

    private static final Map<Integer, Integer> ACTIVE = new ConcurrentHashMap<>();

    public static void track(int playerEntityId, int nameplateEntityId)
    {
        ACTIVE.put(playerEntityId, nameplateEntityId);
    }

    public static void untrack(int playerEntityId, int nameplateEntityId)
    {
        ACTIVE.remove(playerEntityId, nameplateEntityId);
    }

    public static boolean noneActive()
    {
        return ACTIVE.isEmpty();
    }

    /**
     * @return 該玩家實體對應的名牌實體 ID，沒有則回傳 -1
     */
    public static int nameplateOf(int playerEntityId)
    {
        var id = ACTIVE.get(playerEntityId);
        return id == null ? -1 : id;
    }

    public static void clear()
    {
        ACTIVE.clear();
    }

    //endregion
}
