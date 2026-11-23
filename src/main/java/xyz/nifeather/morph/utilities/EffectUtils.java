package xyz.nifeather.morph.utilities;

import org.bukkit.potion.PotionEffect;

public final class EffectUtils
{
    private EffectUtils()
    {
    }

    /**
     * 插件給的藥水效果一律多給的時間（tick）。
     * <p>
     * 基岩版（Geyser）在效果快結束或剛好過期又被補上時，效果圖示與畫面（例如夜視）會閃爍；
     * 多給 1 秒，讓效果在下一次刷新前不會進入快結束的狀態。
     */
    public static final int EXTRA_DURATION_TICKS = 20;

    /**
     * 回傳多給 {@link #EXTRA_DURATION_TICKS} 的效果；無限時間的效果維持原樣。
     */
    public static PotionEffect extended(PotionEffect effect)
    {
        if (effect.isInfinite()) return effect;

        return effect.withDuration(effect.getDuration() + EXTRA_DURATION_TICKS);
    }
}
