package xyz.nifeather.morph.abilities;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import xiamomc.pluginbase.Annotations.Initializer;
import xyz.nifeather.morph.abilities.impl.NoOpOptionAbility;
import xyz.nifeather.morph.misc.DisguiseState;
import xyz.nifeather.morph.skills.options.NoOpConfiguration;

public abstract class EffectMorphAbility extends NoOpOptionAbility
{
    protected abstract PotionEffect getEffect();

    protected boolean shouldApplyEffect(Player player)
    {
        return true;
    }

    @Override
    public boolean handle(Player player, DisguiseState state)
    {
        if (shouldApplyEffect(player))
        {
            if (plugin.getCurrentTick() % this.refreshInterval == 0)
                player.addPotionEffect(xyz.nifeather.morph.utilities.EffectUtils.extended(getEffect()));
        }

        return true;
    }

    @Override
    public boolean applyToPlayer(Player player, DisguiseState state)
    {
        if (super.applyToPlayer(player, state))
        {
            if (shouldApplyEffect(player))
                player.addPotionEffect(xyz.nifeather.morph.utilities.EffectUtils.extended(getEffect()));
            return true;
        }
        else
            return false;
    }

    @Override
    public boolean revokeFromPlayer(Player player, DisguiseState state)
    {
        player.removePotionEffect(getEffect().getType());
        return super.revokeFromPlayer(player, state);
    }

    @Initializer
    private void load()
    {
        // 刷新間隔依原本的時間計算；實際給的效果多 1 秒（EffectUtils），所以刷新時效果不會快到期
        var effect = this.getEffect();
        var interval = this.getRefreshInterval();

        interval = interval == -1
                ? Math.max(effect.getDuration() - 1, effect.getDuration() - 20)
                : interval;

        interval = Math.max(0, interval);

        this.refreshInterval = interval;
    }

    private int refreshInterval = -1;

    protected int getRefreshInterval()
    {
        return -1;
    }
}
