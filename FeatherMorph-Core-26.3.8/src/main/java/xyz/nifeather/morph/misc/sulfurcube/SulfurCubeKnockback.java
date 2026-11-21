package xyz.nifeather.morph.misc.sulfurcube;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SulfurCubeArchetype;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * 原版 {@code SulfurCube#knockback} 的擊退計算（裝著方塊且有攻擊來源時）。
 * <p>
 * 回傳的是「要加到目前速度上的變化量」，對應原版
 * {@code setDeltaMovement(dm.x - hx, dm.y + v * 1.2, dm.z - hz)}，
 * 可直接交給 Paper 的 {@code EntityKnockbackEvent#setKnockback}。
 */
public final class SulfurCubeKnockback
{
    private SulfurCubeKnockback()
    {
    }

    private static final float HORIZONTAL_HIT_ANGLE_SCALE = 1.6F;
    private static final float VERTICAL_HIT_ANGLE_SCALE = 0.5F;
    private static final float VERTICAL_POSITION_ANGLE_SCALE = 0.8F;

    public static Vec3 compute(Entity target, Entity attacker,
                               SulfurCubeArchetype.KnockbackModifiers modifiers,
                               double xd, double zd, float damage, double knockbackResistance)
    {
        float horizontalPower = modifiers.horizontalPower();
        float verticalPower = modifiers.verticalPower();

        var attackerEye = attacker.getEyePosition();
        var attackerLook = attacker.getLookAngle().normalize();
        var targetCenter = target.getBoundingBox().getCenter();

        var newAngle = applyHorizontalHitAngleScale(new Vec2((float) xd, (float) zd), attackerEye, attackerLook, targetCenter);

        var newPower = applyVerticalHitAnglePowerTransfer(horizontalPower, verticalPower,
                attackerEye, attackerLook, targetCenter, target.getBbHeight());

        newPower = applyVerticalPositionAnglePowerRotation(newPower.x, newPower.y,
                horizontalPower, verticalPower, attacker.position(), target.position());

        horizontalPower = newPower.x;
        verticalPower = newPower.y;

        float powerMultiplier = Mth.sqrt(Math.max(0, damage));
        horizontalPower *= powerMultiplier;
        verticalPower *= powerMultiplier;

        horizontalPower *= (float) (1.0 - knockbackResistance);
        verticalPower *= (float) (1.0 - knockbackResistance);

        horizontalPower *= 0.4F;
        horizontalPower = Mth.clamp(horizontalPower, -128.0F, 128.0F);
        verticalPower = Mth.clamp(verticalPower, -128.0F, 128.0F);

        var horizontal = new Vec3(newAngle.x, 0.0, newAngle.y);
        horizontal = horizontal.lengthSqr() < 1.0E-7 ? Vec3.ZERO : horizontal.normalize().scale(horizontalPower);

        var result = new Vec3(-horizontal.x, verticalPower * 1.2, -horizontal.z);

        // 退化的角度可能算出 NaN / Infinity，送給客戶端會被踢出；此時不施加擊退
        if (!Double.isFinite(result.x) || !Double.isFinite(result.y) || !Double.isFinite(result.z))
            return Vec3.ZERO;

        return result;
    }

    private static Vec2 applyHorizontalHitAngleScale(Vec2 originalAngle, Vec3 attackerPosition, Vec3 attackerAimDirection, Vec3 targetCenter)
    {
        var attackerToTarget = targetCenter.subtract(attackerPosition).normalize();
        float angleDiff = (float) Math.atan2(
                attackerAimDirection.x * attackerToTarget.z - attackerAimDirection.z * attackerToTarget.x,
                attackerAimDirection.x * attackerToTarget.x + attackerAimDirection.z * attackerToTarget.z
        );

        return originalAngle.rotate(angleDiff * HORIZONTAL_HIT_ANGLE_SCALE);
    }

    private static Vec2 applyVerticalHitAnglePowerTransfer(float horizontalPower, float verticalPower,
                                                           Vec3 attackerPosition, Vec3 attackerAimDirection,
                                                           Vec3 targetCenteredPosition, float targetHeight)
    {
        float targetHalfHeight = 0.5F * targetHeight;
        var targetTopPos = targetCenteredPosition.add(0.0, targetHalfHeight, 0.0);
        var targetBottomPos = targetCenteredPosition.add(0.0, -targetHalfHeight, 0.0);
        var attackerToTargetTop = targetTopPos.subtract(attackerPosition).normalize();
        var attackerToTargetBottom = targetBottomPos.subtract(attackerPosition).normalize();

        float verticalHitAngleFactor = (float) Mth.clampedMap(attackerAimDirection.y, attackerToTargetTop.y, attackerToTargetBottom.y, -1.0, 1.0);
        float transferredPowerRatio = Math.abs(verticalHitAngleFactor * VERTICAL_HIT_ANGLE_SCALE);
        if (verticalHitAngleFactor < 0.0F)
            transferredPowerRatio = -transferredPowerRatio;

        return new Vec2(horizontalPower * (1.0F - transferredPowerRatio), verticalPower * (1.0F + transferredPowerRatio));
    }

    private static Vec2 applyVerticalPositionAnglePowerRotation(float horizontalPower, float verticalPower,
                                                                float originalHorizontalPower, float originalVerticalPower,
                                                                Vec3 attackerFeetPosition, Vec3 targetFeetPosition)
    {
        var attackerFeetToTargetFeet = targetFeetPosition.subtract(attackerFeetPosition);
        float verticalPositionAngle = (float) Math.atan2(-attackerFeetToTargetFeet.y, attackerFeetToTargetFeet.horizontalDistance());

        var rotatedPower = new Vec2(horizontalPower, verticalPower).rotate(-verticalPositionAngle * VERTICAL_POSITION_ANGLE_SCALE);

        float horizontalRatio = originalHorizontalPower > 0.0F ? Mth.abs(rotatedPower.x) / originalHorizontalPower : 0.0F;
        float verticalRatio = originalVerticalPower > 0.0F ? Mth.abs(rotatedPower.y) / originalVerticalPower : 0.0F;
        float maxRatio = Math.max(horizontalRatio, verticalRatio);
        if (maxRatio > 1.0F)
            rotatedPower = rotatedPower.scale(1.0F / maxRatio);

        return rotatedPower;
    }
}
