package xyz.nifeather.morph.misc.mobs.goal.impl;

import com.destroystokyo.paper.entity.Pathfinder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftMob;
import org.bukkit.entity.*;
import org.jetbrains.annotations.Nullable;
import xyz.nifeather.morph.MorphManager;
import xyz.nifeather.morph.RevealingHandler;
import xyz.nifeather.morph.misc.NmsRecord;
import xyz.nifeather.morph.utilities.EntityTypeUtils;
import xyz.nifeather.morph.utilities.FoliaThreadUtils;

import java.util.EnumSet;
import java.util.List;

/**
 * 一定程度上复刻了 NMS 中的 {@link net.minecraft.world.entity.ai.goal.AvoidEntityGoal}
 */
public abstract class MorphBasicAvoidPlayerGoal<M extends Mob> extends Goal
{
    protected final M mob;
    protected final RevealingHandler revealingHandler;
    protected final MorphManager morphManager;
    protected final double detectDistance;
    protected final double walkSpeed;
    protected final double sprintSpeed;

    public MorphBasicAvoidPlayerGoal(M bindingMob,
                                     RevealingHandler revealingHandler,
                                     MorphManager morphManager,
                                     double detectDistance,
                                     double walkSpeed, double sprintSpeed)
    {
        this.mob = bindingMob;
        this.revealingHandler = revealingHandler;
        this.morphManager = morphManager;
        this.detectDistance = detectDistance;
        this.walkSpeed = walkSpeed;
        this.sprintSpeed = sprintSpeed;

        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Nullable
    private Entity entityToAvoid;

    @Override
    public boolean canUse()
    {
        entityToAvoid = findEntityToAvoid();
        if (entityToAvoid == null) return false;

        var mobLocation = mob.getLocation();
        var avoidingLocation = entityToAvoid.getLocation();

        if (FoliaThreadUtils.notInSameRegion(mobLocation, avoidingLocation))
            return false;

        this.path = findEscapePath();
        return path != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return !mob.getPathfinder().hasPath();
    }

    @Nullable
    private Entity findEntityToAvoid()
    {
        boolean avoidPlayerByDefault = mobPanicFromPlayerByDefault();

        // 性能优化：如果此生物默认不躲避玩家，且没有任何玩家处于伪装中，那么无需进行任何查找
        if (!avoidPlayerByDefault && morphManager.noActiveDisguises())
            return null;

        var nmsMob = ((CraftMob) mob).getHandle();
        var trackingDistance = (nmsMob.getBbWidth() / 2d) + detectDistance;
        var searchBox = nmsMob.getBoundingBox().inflate(trackingDistance, 3, trackingDistance);

        List<Player> nearbyPlayers = new ObjectArrayList<>();

        if (avoidPlayerByDefault)
        {
            // 只查找玩家，而不是像 getNearbyEntities 那样遍历范围内的所有实体并创建 Bukkit 包装
            for (var nmsPlayer : nmsMob.level().getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, searchBox))
            {
                if (nmsPlayer.getBukkitEntity() instanceof Player bukkitPlayer && FoliaThreadUtils.isTickThreadFor(bukkitPlayer))
                    nearbyPlayers.add(bukkitPlayer);
            }
        }
        else
        {
            // 此生物只会躲避特定伪装，因此只需要遍历伪装中的玩家
            var mobWorld = mob.getWorld();
            for (var state : morphManager.activeDisguisesView())
            {
                if (state.disposed()) continue;

                var candidate = state.getPlayer();
                if (!candidate.isOnline() || !FoliaThreadUtils.isTickThreadFor(candidate)) continue;
                if (!mobWorld.equals(candidate.getWorld())) continue;

                if (NmsRecord.ofPlayer(candidate).getBoundingBox().intersects(searchBox))
                    nearbyPlayers.add(candidate);
            }
        }

        if (nearbyPlayers.isEmpty())
            return null;

        Entity found = null;
        double currentDistance = Double.MAX_VALUE;
        var mobLocation = mob.getLocation();

        for (Player player : nearbyPlayers)
        {
            var gamemode = player.getGameMode();
            if (gamemode == GameMode.SPECTATOR || gamemode == GameMode.CREATIVE)
                continue;

            // Since we are in the same region, it should be safe to compare directly...
            var distance = player.getLocation().distanceSquared(mobLocation);
            if (distance > currentDistance)
                continue;

            // Check if player's disguise is what this mob panic from.
            // If not, since it's disguised, we should skip this player.
            var state = morphManager.getDisguiseStateFor(player);
            if (state != null)
            {
                // Player already revealed, we should not panic from it anyway.
                if (revealingHandler.shouldMobsAwareRevealed(player))
                    continue;

                if (EntityTypeUtils.panicsFrom(mob.getType(), state.getEntityType()))
                {
                    found = player;
                    break;
                }

                continue;
            }

            if (avoidPlayerByDefault)
            {
                found = player;
                currentDistance = distance;
            }
        }

        return found;
    }

    protected boolean mobPanicFromPlayerByDefault()
    {
        return EntityTypeUtils.panicsFrom(mob.getType(), EntityType.PLAYER);
    }

    @Nullable
    private Pathfinder.PathResult path;

    @Nullable
    private Pathfinder.PathResult findEscapePath()
    {
        if (entityToAvoid == null)
            return null;

        var nmsMob = (PathfinderMob) ((CraftMob) mob).getHandle();
        var playerToAvoid = ((CraftEntity) entityToAvoid).getHandle();
        Vec3 nmsTarget = DefaultRandomPos.getPosAway(nmsMob, 16, 7, playerToAvoid.position());

        if (nmsTarget == null)
            return null;

        var escapeLocation = new Location(mob.getWorld(), nmsTarget.x(), nmsTarget.y(), nmsTarget.z());
        return mob.getPathfinder().findPath(escapeLocation);
    }

    @Override
    public void start()
    {
        if (path != null)
            this.mob.getPathfinder().moveTo(path, sprintSpeed);
        else
            this.mob.getPathfinder().stopPathfinding();
    }

    @Override
    public void tick()
    {
        // I'm just too lazy to check if comparing distance would trigger async catch,
        // so let's not check for entities that's not on the same thread.
        //
        // ⬇️ Let's keep this null check
        if (entityToAvoid == null || path == null || !FoliaThreadUtils.isTickThreadFor(entityToAvoid)) return;

        var mobLocation = mob.getLocation();
        var avoidingLocation = entityToAvoid.getLocation();

        if (FoliaThreadUtils.notInSameRegion(mobLocation, avoidingLocation))
            return;

        var pathfinder = mob.getPathfinder();
        // 与原版 AvoidEntityGoal#tick() 一致：distanceToSqr < 49 (即7格内) 时疾跑
        if (mobLocation.distanceSquared(avoidingLocation) < 49)
            pathfinder.moveTo(path, sprintSpeed);
        else
            pathfinder.moveTo(path, walkSpeed);
    }

    @Override
    public void stop()
    {
        entityToAvoid = null;
        path = null;
    }
}
