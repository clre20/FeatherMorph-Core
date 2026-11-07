package xyz.nifeather.morph.abilities.impl;

import com.destroystokyo.paper.event.entity.EntityKnockbackByEntityEvent;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SulfurCubeArchetype;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.damage.CraftDamageSource;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xiamomc.pluginbase.Annotations.Resolved;
import xyz.nifeather.morph.MorphManager;
import xyz.nifeather.morph.api.morphs.abilities.AbilityNames;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.types.slimemagma.SulfurCubeWatcher;
import xyz.nifeather.morph.backends.server.renderer.network.registries.RenderRegistry;
import xyz.nifeather.morph.config.ConfigOptions;
import xyz.nifeather.morph.config.MorphConfigManager;
import xyz.nifeather.morph.misc.DisguiseState;
import xyz.nifeather.morph.misc.sulfurcube.CubeArchetype;
import xyz.nifeather.morph.misc.sulfurcube.SulfurCubeKnockback;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 硫磺立方體偽裝：以玩家手持的方塊（副手優先，其次主手）作為「身體方塊」，
 * 並照原版 {@code net.minecraft.world.entity.monster.cubemob.SulfurCube} 的規則運作：
 * <ul>
 *     <li>套用所有符合型態的原版屬性修飾（彈性、摩擦、空氣阻力、擊退抗性……）</li>
 *     <li>有浮力的型態會在水 / 岩漿中浮起</li>
 *     <li>裝著方塊時：不能爬上台階、可在水下呼吸、不會凍結</li>
 *     <li>對 {@code #sulfur_cube_with_block_immune_to} 內的傷害免疫，並改用型態的擊退與受擊音效</li>
 *     <li>接觸傷害（例如高溫型態）</li>
 *     <li>爆炸型態：被火 / 燃燒的箭 / 爆炸 / 紅石訊號 / 打火石 / 火焰彈點燃，引信結束後爆炸</li>
 * </ul>
 */
public class SulfurCubePhysicsAbility extends NoOpOptionAbility
{
    @Resolved
    private MorphManager manager;

    /** 只有伺服器端渲染（PacketEvents）啟用時才存在；用戶端模組模式下為 null。 */
    @Resolved(allowNull = true)
    private RenderRegistry renderRegistry;

    @Resolved
    private MorphConfigManager config;

    /** 原版裝著方塊時 maxUpStep() 為 0；以乘法修飾把台階高度歸零。 */
    private static final AttributeModifier NO_STEP_MODIFIER = new AttributeModifier(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("feathermorph", "sulfur_cube_no_step"),
            -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

    /** 每隔幾 tick 檢查一次接觸傷害。原版受傷後有 10 tick 無敵時間，所以 5 tick 已足夠且不影響結果。 */
    private static final int CONTACT_DAMAGE_INTERVAL = 5;

    /** 每隔幾 tick 重新比對一次型態，用來吃到資料包或物品標籤的變化。 */
    private static final int ARCHETYPE_RESYNC_INTERVAL = 200;

    private static final class CubeState
    {
        @Nullable
        Material lastType;

        CubeArchetype archetype = CubeArchetype.EMPTY;

        /** 剩餘引信 tick；-1 表示未點燃。 */
        int fuse = -1;

        /** 點燃當下的爆炸資料（點燃後即使換掉手上的方塊也照樣爆炸，與原版無法剪下已點燃 TNT 一致）。 */
        @Nullable
        SulfurCubeArchetype.ExplosionData primedExplosion;

        /** 最近一次受到的傷害量，原版擊退強度會乘上 sqrt(damage)。 */
        float lastDamage = 1.0F;

        int ticks;

        boolean isPrimed()
        {
            return fuse >= 0;
        }
    }

    /** 只存 UUID，不持有 Player 實例；離線、取消偽裝、關服時清除。 */
    private final Map<UUID, CubeState> states = new ConcurrentHashMap<>();

    @Override
    public @NotNull NamespacedKey getIdentifier()
    {
        return AbilityNames.SULFUR_CUBE_PHYSICS;
    }

    // region Apply / Revoke

    @Override
    public boolean applyToPlayer(Player player, DisguiseState state)
    {
        if (!super.applyToPlayer(player, state))
            return false;

        var cubeState = new CubeState();
        states.put(player.getUniqueId(), cubeState);
        syncArchetype(player, cubeState, true);

        return true;
    }

    @Override
    public boolean revokeFromPlayer(Player player, DisguiseState state)
    {
        if (!super.revokeFromPlayer(player, state))
            return false;

        var cubeState = states.remove(player.getUniqueId());
        if (cubeState != null)
        {
            removeArchetypeModifiers(player, cubeState.archetype);
            setStepModifier(player, false);
        }

        return true;
    }

    // endregion Apply / Revoke

    @Override
    public boolean handle(Player player, DisguiseState state)
    {
        var cubeState = states.get(player.getUniqueId());
        if (cubeState == null) return true;

        cubeState.ticks++;

        syncArchetype(player, cubeState, cubeState.ticks % ARCHETYPE_RESYNC_INTERVAL == 0);

        var nmsPlayer = ((CraftPlayer) player).getHandle();

        // 引信不受手上方塊影響，點燃後一定會走完
        if (cubeState.isPrimed())
        {
            tickFuse(player, nmsPlayer, cubeState);
            if (!states.containsKey(player.getUniqueId())) return true;
        }

        var archetype = cubeState.archetype;
        if (archetype.isEmpty()) return true;

        // 原版 canBreatheUnderwater()：裝著方塊時可在水下呼吸
        if (player.getRemainingAir() < player.getMaximumAir())
            player.setRemainingAir(player.getMaximumAir());

        // 原版 canFreeze()：裝著方塊時不會凍結
        if (player.getFreezeTicks() > 0)
            player.setFreezeTicks(0);

        // 原版 travelInFluid()：有浮力的型態在水 / 岩漿中上浮
        if (archetype.isBuoyant() && (player.isInWater() || player.isInLava()))
            applyBuoyancy(player, nmsPlayer, cubeState);

        // 原版 doPush() / playerTouch() 時套用接觸傷害
        if (!archetype.contactDamages().isEmpty() && cubeState.ticks % CONTACT_DAMAGE_INTERVAL == 0)
            applyContactDamage(nmsPlayer, archetype);

        // 原版 primeWhenOnPoweredPosition()
        if (!cubeState.isPrimed() && archetype.explosion().isPresent())
        {
            var level = nmsPlayer.level();
            if (level.getBestOwnOrNeighbourSignal(BlockPos.containing(nmsPlayer.position())) != 0)
                prime(player, nmsPlayer, cubeState, false);
        }

        return true;
    }

    // region Archetype sync

    private void syncArchetype(Player player, CubeState cubeState, boolean force)
    {
        var item = CubeArchetype.getActiveItem(player);
        var type = item == null ? null : item.getType();

        if (!force && type == cubeState.lastType) return;

        var newArchetype = CubeArchetype.fromMaterial(type);
        var oldArchetype = cubeState.archetype;

        cubeState.lastType = type;

        if (newArchetype != oldArchetype)
        {
            // 與原版 collectEquipmentChanges() 相同：先移除舊型態的修飾，再加上新型態的修飾
            removeArchetypeModifiers(player, oldArchetype);
            addArchetypeModifiers(player, newArchetype);
            setStepModifier(player, !newArchetype.isEmpty());

            cubeState.archetype = newArchetype;
        }

        updateWatcherItem(player, item);
    }

    private void addArchetypeModifiers(Player player, CubeArchetype archetype)
    {
        if (archetype.isEmpty()) return;

        var nmsPlayer = ((CraftPlayer) player).getHandle();
        for (var archetypeEntry : archetype.archetypes())
        {
            for (var mod : archetypeEntry.attributeModifiers())
            {
                var instance = nmsPlayer.getAttribute(mod.attribute());
                if (instance != null)
                    instance.addOrUpdateTransientModifier(mod.modifier());
            }
        }
    }

    private void removeArchetypeModifiers(Player player, CubeArchetype archetype)
    {
        if (archetype.isEmpty()) return;

        var nmsPlayer = ((CraftPlayer) player).getHandle();
        for (var archetypeEntry : archetype.archetypes())
        {
            for (var mod : archetypeEntry.attributeModifiers())
            {
                var instance = nmsPlayer.getAttribute(mod.attribute());
                if (instance != null)
                    instance.removeModifier(mod.modifier().id());
            }
        }
    }

    private void setStepModifier(Player player, boolean enabled)
    {
        var instance = ((CraftPlayer) player).getHandle().getAttribute(Attributes.STEP_HEIGHT);
        if (instance == null) return;

        if (enabled)
            instance.addOrUpdateTransientModifier(NO_STEP_MODIFIER);
        else
            instance.removeModifier(NO_STEP_MODIFIER.id());
    }

    private void updateWatcherItem(Player player, @Nullable org.bukkit.inventory.ItemStack item)
    {
        if (renderRegistry == null) return;

        if (renderRegistry.getWatcher(player.getUniqueId()) instanceof SulfurCubeWatcher cubeWatcher)
        {
            if (!java.util.Objects.equals(cubeWatcher.getCurrentSwallowedItem(), item))
                cubeWatcher.setSwallowedItem(item == null ? null : item.asOne());
        }
    }

    private void updateWatcherFuse(Player player, int maxFuse)
    {
        if (renderRegistry == null) return;

        if (renderRegistry.getWatcher(player.getUniqueId()) instanceof SulfurCubeWatcher cubeWatcher)
            cubeWatcher.setMaxFuse(maxFuse);
    }

    // endregion Archetype sync

    // region Physics

    /**
     * 原版 SulfurCube#travelInFluid：浸入深度超過 0.2 倍身高時，每 tick 往上加速 min(1, 浸入量) * 0.04。
     */
    private void applyBuoyancy(Player player, ServerPlayer nmsPlayer, CubeState cubeState)
    {
        if (player.isSneaking()) return;

        float vibe = 0.2F * (float) Math.sin(cubeState.ticks * 0.4F);
        var fluid = player.isInWater() ? FluidTags.WATER : FluidTags.LAVA;
        double immersion = nmsPlayer.getFluidHeight(fluid) - nmsPlayer.getBbHeight() * 0.2 + vibe;

        if (immersion <= 0.0) return;

        var velocity = player.getVelocity();
        velocity.setY(velocity.getY() + Math.min(1.0, immersion) * 0.04F);
        player.setVelocity(velocity);
    }

    /**
     * 原版 SulfurCube#applyContactDamage：對碰到的實體造成型態定義的傷害。
     */
    private void applyContactDamage(ServerPlayer nmsPlayer, CubeArchetype archetype)
    {
        if (!(nmsPlayer.level() instanceof ServerLevel level)) return;

        var box = nmsPlayer.getBoundingBox().inflate(0.1, 0.0, 0.1);
        var targets = level.getEntities(nmsPlayer, box, entity -> entity.isAlive() && !entity.isSpectator() && entity.isPickable());
        if (targets.isEmpty()) return;

        for (var target : targets)
        {
            for (var damage : archetype.contactDamages())
            {
                // 原版對非玩家目標不標記來源；這裡由玩家操控，一律標記為玩家，讓領地 / 保護插件能辨識並阻擋
                target.hurtServer(level, new DamageSource(damage.damageType(), nmsPlayer), damage.amount().sample(nmsPlayer.getRandom()));
            }
        }
    }

    // endregion Physics

    // region Explosion

    /**
     * 原版 SulfurCube#primeTime。
     *
     * @param imminent 由爆炸點燃時使用較短的隨機引信
     */
    private boolean prime(Player player, ServerPlayer nmsPlayer, CubeState cubeState, boolean imminent)
    {
        if (cubeState.isPrimed()) return false;

        var explosion = cubeState.archetype.explosion();
        if (explosion.isEmpty()) return false;

        if (!(nmsPlayer.level() instanceof ServerLevel level)) return false;
        if (!level.getGameRules().get(GameRules.TNT_EXPLODES)) return false;

        int fuse = explosion.get().fuse();
        int fuseTime = imminent ? PrimedTnt.getRandomShortFuse(fuse, nmsPlayer.getRandom()) : fuse;

        cubeState.fuse = fuseTime;
        cubeState.primedExplosion = explosion.get();

        level.playSound(null, nmsPlayer.getX(), nmsPlayer.getY(), nmsPlayer.getZ(),
                SoundEvents.TNT_PRIMED, SoundSource.HOSTILE, 1.0F, 1.0F);
        updateWatcherFuse(player, fuseTime);

        return true;
    }

    /**
     * 原版 SulfurCube#tickFuse。引信歸零時爆炸，偽裝者死亡（原版立方體會直接消失）。
     */
    private void tickFuse(Player player, ServerPlayer nmsPlayer, CubeState cubeState)
    {
        if (cubeState.fuse > 0)
        {
            cubeState.fuse--;
            return;
        }

        var explosion = cubeState.primedExplosion;
        resetFuse(player, cubeState);

        if (explosion == null || !(nmsPlayer.level() instanceof ServerLevel level)) return;

        if (level.getGameRules().get(GameRules.TNT_EXPLODES))
        {
            // 原版依 mobGriefing 決定是否破壞方塊；額外保留設定檔的 tnt_breaks_blocks 作為伺服器開關
            boolean breakBlocks = level.getGameRules().get(GameRules.MOB_GRIEFING)
                    && config.getOrDefault(ConfigOptions.SULFUR_CUBE_TNT_BREAKS_BLOCKS);

            level.explode(
                    nmsPlayer,
                    Explosion.getDefaultDamageSource(level, nmsPlayer),
                    null,
                    nmsPlayer.getX(), nmsPlayer.getY(0.0625), nmsPlayer.getZ(),
                    explosion.power(),
                    explosion.causesFire(),
                    breakBlocks ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE
            );
        }

        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR)
            player.setHealth(0);
    }

    private void resetFuse(Player player, CubeState cubeState)
    {
        if (!cubeState.isPrimed()) return;

        cubeState.fuse = -1;
        cubeState.primedExplosion = null;
        updateWatcherFuse(player, -1);
    }

    // endregion Explosion

    // region Events

    @Nullable
    private CubeState getActiveState(Player player)
    {
        var cubeState = states.get(player.getUniqueId());
        if (cubeState == null) return null;

        var disguise = manager.getDisguiseStateFor(player);
        if (disguise == null || disguise.getEntityType() != EntityType.SULFUR_CUBE) return null;

        return cubeState;
    }

    /**
     * 原版 SulfurCube#hurtServer：
     * 已點燃時無敵；爆炸型態被火 / 燃燒的箭 / 爆炸點燃；
     * 裝著方塊時對 {@code #sulfur_cube_with_block_immune_to} 免疫，但仍承受型態擊退。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event)
    {
        if (!(event.getEntity() instanceof Player player)) return;

        var cubeState = getActiveState(player);
        if (cubeState == null) return;

        // 原版點燃後 setPermanentlyInvulnerable(true)
        if (cubeState.isPrimed())
        {
            event.setCancelled(true);
            return;
        }

        if (cubeState.archetype.isEmpty()) return;
        if (!(event.getDamageSource() instanceof CraftDamageSource craftSource)) return;

        var source = craftSource.getHandle();
        var nmsPlayer = ((CraftPlayer) player).getHandle();

        cubeState.lastDamage = (float) Math.max(0, event.getDamage());

        if (cubeState.archetype.explosion().isPresent())
        {
            var direct = source.getDirectEntity();
            if (source.is(DamageTypeTags.IS_FIRE) || (direct instanceof net.minecraft.world.entity.projectile.arrow.AbstractArrow arrow && arrow.isOnFire()))
                prime(player, nmsPlayer, cubeState, false);
            else if (source.is(DamageTypeTags.IS_EXPLOSION))
                prime(player, nmsPlayer, cubeState, true);
        }

        if (!config.getOrDefault(ConfigOptions.SULFUR_CUBE_VANILLA_DAMAGE_IMMUNITY)) return;
        if (!source.is(DamageTypeTags.SULFUR_CUBE_WITH_BLOCK_IMMUNE_TO)) return;

        event.setCancelled(true);

        // 原版：免疫傷害時仍呼叫 dealDefaultKnockback，擊退會在 onKnockback 換成型態擊退
        if (!source.is(DamageTypeTags.NO_KNOCKBACK))
            nmsPlayer.dealDefaultKnockback(source, cubeState.lastDamage, true);
    }

    /**
     * 原版 SulfurCube#knockback：有攻擊來源且裝著方塊時，改用型態定義的擊退並播放受擊音效。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onKnockback(EntityKnockbackByEntityEvent event)
    {
        if (!(event.getEntity() instanceof Player player)) return;

        var cubeState = getActiveState(player);
        if (cubeState == null || cubeState.archetype.isEmpty()) return;

        if (!(event.getHitBy() instanceof CraftEntity craftAttacker)) return;

        var nmsPlayer = ((CraftPlayer) player).getHandle();
        var attacker = craftAttacker.getHandle();

        double xd;
        double zd;
        if (attacker instanceof Projectile projectile)
        {
            var movement = projectile.getDeltaMovement();
            xd = -movement.x;
            zd = -movement.z;
        }
        else
        {
            xd = attacker.getX() - nmsPlayer.getX();
            zd = attacker.getZ() - nmsPlayer.getZ();
        }

        var archetype = cubeState.archetype;
        var knockback = SulfurCubeKnockback.compute(
                nmsPlayer, attacker, archetype.knockbackModifiers(),
                xd, zd, cubeState.lastDamage,
                nmsPlayer.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE)
        );

        event.setKnockback(new org.bukkit.util.Vector(knockback.x, knockback.y, knockback.z));

        if (nmsPlayer.level() instanceof ServerLevel level)
        {
            level.playSound(null, nmsPlayer.getX(), nmsPlayer.getY(), nmsPlayer.getZ(),
                    archetype.soundSettings().hitSound(), SoundSource.HOSTILE, 1.0F, 1.0F);
        }

        cubeState.lastDamage = 1.0F;
    }

    /**
     * 原版 SulfurCube#mobInteract：拿打火石或火焰彈對爆炸型態的立方體右鍵會點燃它。
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event)
    {
        if (!(event.getRightClicked() instanceof Player target)) return;

        var cubeState = getActiveState(target);
        if (cubeState == null || cubeState.isPrimed() || cubeState.archetype.explosion().isEmpty()) return;

        var igniter = event.getPlayer();
        var hand = event.getHand();
        var heldItem = hand == EquipmentSlot.OFF_HAND
                ? igniter.getInventory().getItemInOffHand()
                : igniter.getInventory().getItemInMainHand();

        var type = heldItem.getType();
        if (type != Material.FLINT_AND_STEEL && type != Material.FIRE_CHARGE) return;

        // 點燃會導致被偽裝者死亡：先送出一個測試傷害事件，讓 PvP / 領地保護插件決定是否允許
        if (!canHarm(igniter, target)) return;

        if (!prime(target, ((CraftPlayer) target).getHandle(), cubeState, false)) return;

        event.setCancelled(true);

        var nmsIgniter = ((CraftPlayer) igniter).getHandle();
        var nmsHand = hand == EquipmentSlot.OFF_HAND
                ? net.minecraft.world.InteractionHand.OFF_HAND
                : net.minecraft.world.InteractionHand.MAIN_HAND;
        var nmsStack = nmsIgniter.getItemInHand(nmsHand);

        if (type == Material.FLINT_AND_STEEL)
        {
            nmsIgniter.level().playSound(nmsIgniter, nmsIgniter.getX(), nmsIgniter.getY(), nmsIgniter.getZ(),
                    SoundEvents.FLINTANDSTEEL_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            nmsStack.hurtAndBreak(1, nmsIgniter, nmsHand);
        }
        else
        {
            nmsIgniter.level().playSound(nmsIgniter, nmsIgniter.getX(), nmsIgniter.getY(), nmsIgniter.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (igniter.getGameMode() != GameMode.CREATIVE)
                nmsStack.shrink(1);
        }
    }

    private boolean canHarm(Player attacker, Player victim)
    {
        if (!victim.getWorld().getPVP()) return false;

        var damageSource = org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.PLAYER_ATTACK)
                .withCausingEntity(attacker)
                .withDirectEntity(attacker)
                .build();

        var testEvent = new org.bukkit.event.entity.EntityDamageByEntityEvent(
                attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damageSource, 0.0);

        org.bukkit.Bukkit.getPluginManager().callEvent(testEvent);
        return !testEvent.isCancelled();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event)
    {
        var cubeState = states.get(event.getPlayer().getUniqueId());
        if (cubeState != null)
            resetFuse(event.getPlayer(), cubeState);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        var player = event.getPlayer();
        var cubeState = states.remove(player.getUniqueId());
        if (cubeState != null)
        {
            removeArchetypeModifiers(player, cubeState.archetype);
            setStepModifier(player, false);
        }
    }

    /**
     * 資料包重載後型態與標籤可能改變：清除快取，下一 tick 重新比對。
     */
    @EventHandler
    public void onResourcesReloaded(ServerResourcesReloadedEvent event)
    {
        CubeArchetype.invalidateCache();
        states.values().forEach(s -> s.lastType = Material.STRUCTURE_VOID);
    }

    // endregion Events
}
