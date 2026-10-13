package xyz.nifeather.morph.misc.mobs.goal.impl;

import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.Goal;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.entity.CraftMob;
import org.bukkit.entity.*;
import org.jetbrains.annotations.Nullable;
import xyz.nifeather.morph.MorphManager;
import xyz.nifeather.morph.misc.DisguiseState;
import xyz.nifeather.morph.utilities.EntityTypeUtils;
import xyz.nifeather.morph.utilities.FoliaThreadUtils;

import java.util.EnumSet;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一定程度上复刻了 {@link net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal}
 */
public class MorphNearestAttackableGoal extends Goal
{
    private final Mob mob;
    private final MorphManager morphManager;

    public MorphNearestAttackableGoal(Mob mob, MorphManager morphManager)
    {
        this.mob = mob;
        this.morphManager = morphManager;

        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Nullable
    private LivingEntity targetedEntity;

    @Nullable
    private DisguiseState cachedTargetEntityState;

    /**
     * Checks if this goal should be activated
     *
     * @return if this goal should be activated
     */
    @Override
    public boolean canUse()
    {
        if (mob instanceof Tameable tameable && tameable.isTamed())
            return false;

        if (targetedEntity != null)
            return targetStillValidForGoal();

        if (ThreadLocalRandom.current().nextInt(10) != 0)
            return false;

        var newTarget = findTarget();

        this.targetedEntity = newTarget;
        return newTarget != null;
    }

    private boolean targetStillValidForGoal()
    {
        var target = targetedEntity;
        if (target == null) return false;

        if (!FoliaThreadUtils.isTickThreadFor(target))
            return false;

        boolean shouldStopThisGoal;

        // 当满足以下任一条件时，取消仇恨：
        // 处于不同的世界
        // 目标超过跟随距离
        // 玩家不在线
        // 玩家不是生存模式
        shouldStopThisGoal = FoliaThreadUtils.notInSameRegion(mob.getLocation(), target.getLocation());
        shouldStopThisGoal = shouldStopThisGoal || target.getLocation().distance(mob.getLocation()) > getFollowRange();

        if (target instanceof Player targetPlayer)
        {
            var gamemode = targetPlayer.getGameMode();

            shouldStopThisGoal = shouldStopThisGoal
                    || !targetPlayer.isOnline()
                    || gamemode.isInvulnerable();

            // Check for cached DisguiseState:
            // If the player has not changed their disguise, we can continue checking whether the mob should hostile to the disguise.
            // If the player has changed their disguise, we should cancel and find other target next time `canUse` is called.
            var state = cachedTargetEntityState;
            if (state != null && !state.disposed())
                shouldStopThisGoal = shouldStopThisGoal || EntityTypeUtils.panicsFrom(mob.getType(), state.getEntityType());
            else
                shouldStopThisGoal = true;
        }

        return !shouldStopThisGoal;
    }

    private double getFollowRange()
    {
        var followRangeAttribute = mob.getAttribute(Attribute.FOLLOW_RANGE);
        return followRangeAttribute == null ? 16 : followRangeAttribute.getValue();
    }

    private LivingEntity findTarget()
    {
        // 性能优化：此Goal会被添加到所有生物上，因此不再扫描生物周围的所有实体，
        //          而是只遍历当前处于伪装中的玩家（通常远少于周围实体数量）
        if (morphManager.noActiveDisguises())
            return null;

        double followRange = getFollowRange();
        var mobType = mob.getType();
        var mobWorld = mob.getWorld();
        var mobLocation = mob.getLocation();

        Player found = null;
        DisguiseState foundState = null;
        double foundDistance = Double.MAX_VALUE;

        for (var state : morphManager.activeDisguisesView())
        {
            if (state.disposed()) continue;

            var candidate = state.getPlayer();
            if (!candidate.isOnline() || !FoliaThreadUtils.isTickThreadFor(candidate)) continue;
            if (!mobWorld.equals(candidate.getWorld())) continue;

            var gamemode = candidate.getGameMode();
            if (gamemode != GameMode.SURVIVAL && gamemode != GameMode.ADVENTURE) continue;

            // 与 getNearbyEntities(range, range, range) 的判定范围保持一致
            var loc = candidate.getLocation();
            double dx = Math.abs(loc.getX() - mobLocation.getX());
            double dy = Math.abs(loc.getY() - mobLocation.getY());
            double dz = Math.abs(loc.getZ() - mobLocation.getZ());
            if (dx > followRange || dy > followRange || dz > followRange) continue;

            if (!EntityTypeUtils.hostiles(mobType, state.getEntityType())) continue;

            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < foundDistance)
            {
                found = candidate;
                foundState = state;
                foundDistance = distance;
            }
        }

        if (found != null)
            cachedTargetEntityState = foundState;

        return found;
    }

    /**
     * Called when this goal gets activated
     */
    @Override
    public void start()
    {
        mob.setTarget(targetedEntity);
    }

    /**
     * Called when this goal gets stopped
     */
    @Override
    public void stop()
    {
        var target = targetedEntity;
        if (target == null) return;

        if (target.equals(mob.getTarget()))
            mob.setTarget(null);

        // Fix IronGolem still angry at player after this target stopped
        var mobHandle = ((CraftMob)mob).getHandle();
        if (mobHandle instanceof NeutralMob neutralMob)
            neutralMob.stopBeingAngry();

        targetedEntity = null;
        cachedTargetEntityState = null;
    }
}
