package xyz.nifeather.morph.abilities.impl;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import xiamomc.pluginbase.Annotations.Resolved;
import xyz.nifeather.morph.MorphManager;
import xyz.nifeather.morph.api.morphs.abilities.AbilityNames;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.types.slimemagma.SulfurCubeWatcher;
import xyz.nifeather.morph.backends.server.renderer.network.registries.RenderRegistry;
import xyz.nifeather.morph.config.ConfigOptions;
import xyz.nifeather.morph.config.MorphConfigManager;
import xyz.nifeather.morph.misc.DisguiseState;
import xyz.nifeather.morph.misc.sulfurcube.CubeArchetype;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class SulfurCubePhysicsAbility extends NoOpOptionAbility
{
    @Resolved
    private MorphManager manager;

    @Resolved
    private RenderRegistry renderRegistry;

    @Resolved
    private MorphConfigManager config;

    @NotNull
    public static final NamespacedKey MODIFIER_KEY = Objects.requireNonNull(
            NamespacedKey.fromString("feathermorph:sulfur_cube_archetype"),
            "Bad server implementation"
    );

    private static final Attribute[] MANAGED_ATTRIBUTES = new Attribute[] {
            Attribute.MOVEMENT_SPEED,
            Attribute.JUMP_STRENGTH,
            Attribute.KNOCKBACK_RESISTANCE,
            Attribute.EXPLOSION_KNOCKBACK_RESISTANCE,
            Attribute.GRAVITY,
            Attribute.SAFE_FALL_DISTANCE,
            Attribute.FALL_DAMAGE_MULTIPLIER,
            Attribute.BOUNCINESS,
            Attribute.FRICTION_MODIFIER,
            Attribute.AIR_DRAG_MODIFIER
    };

    private final Map<UUID, CubeArchetype> playerArchetypes = new Object2ObjectOpenHashMap<>();

    private void updateWatcher(Player player, ItemStack item)
    {
        if (renderRegistry == null) return;
        var watcher = renderRegistry.getWatcher(player.getUniqueId());
        if (watcher instanceof SulfurCubeWatcher cubeWatcher)
        {
            cubeWatcher.setSwallowedItem(item);
        }
    }

    @Override
    public @NotNull NamespacedKey getIdentifier()
    {
        return AbilityNames.SULFUR_CUBE_PHYSICS;
    }

    @Override
    public boolean applyToPlayer(Player player, DisguiseState state)
    {
        if (!super.applyToPlayer(player, state))
            return false;

        var item = CubeArchetype.getActiveItem(player);
        var archetype = CubeArchetype.fromItemStack(item);

        playerArchetypes.put(player.getUniqueId(), archetype);
        applyArchetypeModifiers(player, archetype);
        updateWatcher(player, item);

        return true;
    }

    @Override
    public boolean revokeFromPlayer(Player player, DisguiseState state)
    {
        if (!super.revokeFromPlayer(player, state))
            return false;

        clearModifiers(player);
        playerArchetypes.remove(player.getUniqueId());

        return true;
    }

    @Override
    public boolean handle(Player player, DisguiseState state)
    {
        var item = CubeArchetype.getActiveItem(player);
        var archetype = CubeArchetype.fromItemStack(item);

        var prevArchetype = playerArchetypes.get(player.getUniqueId());
        boolean archetypeChanged = prevArchetype != archetype;
        if (archetypeChanged)
        {
            playerArchetypes.put(player.getUniqueId(), archetype);
            applyArchetypeModifiers(player, archetype);
        }

        if (renderRegistry != null)
        {
            var watcher = renderRegistry.getWatcher(player.getUniqueId());
            if (watcher instanceof SulfurCubeWatcher cubeWatcher)
            {
                if (archetypeChanged || !Objects.equals(cubeWatcher.getCurrentSwallowedItem(), item))
                {
                    cubeWatcher.setSwallowedItem(item);
                }
            }
        }

        // Liquid buoyancy (Water / Lava)
        boolean inLiquid = player.isInWater() || player.isInLava();
        if (inLiquid)
        {
            var vel = player.getVelocity();
            if (archetype.isBuoyant())
            {
                if (!player.isSneaking() && vel.getY() < 0.08)
                {
                    vel.setY(Math.min(vel.getY() + 0.04, 0.1));
                    player.setVelocity(vel);
                }
            }
            else if (archetype.isSinking())
            {
                if (vel.getY() > -0.25)
                {
                    vel.setY(vel.getY() - 0.04);
                    player.setVelocity(vel);
                }
            }
        }

        // Sliding on ground (Ice hockey / Curling)
        if (archetype.isSliding() && player.isOnGround())
        {
            var vel = player.getVelocity();
            double hSpeedSq = vel.getX() * vel.getX() + vel.getZ() * vel.getZ();
            if (hSpeedSq > 0.0005)
            {
                double multiplier = (archetype == CubeArchetype.FAST_SLIDING) ? 1.035 : 1.01;
                vel.setX(vel.getX() * multiplier);
                vel.setZ(vel.getZ() * multiplier);
                player.setVelocity(vel);
            }
        }

        // --- 特殊方塊互動與特性 ---

        // 1. 岩漿塊：常駐防火
        if (archetype == CubeArchetype.MAGMA)
        {
            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 40, 0, false, false, false));
        }

        // 2. 接觸傷害（仙人掌 / 岩漿塊）
        if (archetype.hasContactDamage() && player.getTicksLived() % 10 == 0)
        {
            var world = player.getWorld();
            var loc = player.getLocation();
            for (var entity : world.getNearbyEntities(loc, 1.3, 1.5, 1.3))
            {
                if (entity != player && entity instanceof LivingEntity living && canTarget(living))
                {
                    if (archetype == CubeArchetype.CACTUS)
                    {
                        living.damage(2.0, player);
                        world.playSound(loc, "block.cactus.hit", 0.8f, 1.2f);
                    }
                    else if (archetype == CubeArchetype.MAGMA)
                    {
                        living.setFireTicks(60);
                        living.damage(2.0, player);
                        world.playSound(loc, "entity.generic.burn", 0.8f, 1.0f);
                    }
                }
            }
        }

        // 3. 蜂窩方塊：牆面附著與緩降黏滯
        if (archetype == CubeArchetype.BEEHIVE && !player.isOnGround() && !player.isSneaking() && isTouchingWall(player))
        {
            var vel = player.getVelocity();
            if (vel.getY() < -0.12)
            {
                vel.setY(-0.12);
                player.setVelocity(vel);
                player.setFallDistance(0);
                player.getWorld().spawnParticle(Particle.ITEM, player.getLocation().add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0.02, new ItemStack(Material.HONEY_BLOCK));
            }
        }

        // 4. 靈魂沙：踩在靈魂沙/靈魂土上觸發靈魂疾走加速
        if (archetype == CubeArchetype.SOUL_SAND && player.isOnGround())
        {
            var blockBelow = player.getLocation().getBlock().getRelative(org.bukkit.block.BlockFace.DOWN);
            if (blockBelow.getType() == Material.SOUL_SAND || blockBelow.getType() == Material.SOUL_SOIL)
            {
                var vel = player.getVelocity();
                vel.setX(vel.getX() * 1.15);
                vel.setZ(vel.getZ() * 1.15);
                player.setVelocity(vel);
                player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 0.1, 0), 1, 0.2, 0.1, 0.2, 0.02);
            }
        }

        // 5. TNT：遇火或紅石引爆
        if (archetype == CubeArchetype.TNT)
        {
            var fuse = tntFuseMap.get(player.getUniqueId());
            if (fuse != null)
            {
                if (fuse <= 0)
                {
                    tntFuseMap.remove(player.getUniqueId());
                    boolean breakBlocks = config.getOrDefault(ConfigOptions.SULFUR_CUBE_TNT_BREAKS_BLOCKS);
                    explodeTNT(player, breakBlocks);
                    return true;
                }
                else
                {
                    tntFuseMap.put(player.getUniqueId(), fuse - 1);
                    player.getWorld().spawnParticle(Particle.SMOKE, player.getLocation().add(0, 0.8, 0), 3, 0.1, 0.1, 0.1, 0.02);
                }
            }
            else
            {
                if (isFireTriggered(player) || isRedstoneTriggered(player))
                {
                    primeTNT(player, 20);
                }
            }
        }
        else
        {
            tntFuseMap.remove(player.getUniqueId());
        }

        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onItemHeld(PlayerItemHeldEvent event)
    {
        var player = event.getPlayer();
        if (!isPlayerApplied(player)) return;

        var state = manager.getDisguiseStateFor(player);
        if (state == null || state.getEntityType() != EntityType.SULFUR_CUBE)
            return;

        var offhand = player.getInventory().getItemInOffHand();
        ItemStack activeItem;
        if (CubeArchetype.isSwallowable(offhand))
        {
            activeItem = offhand;
        }
        else
        {
            activeItem = player.getInventory().getItem(event.getNewSlot());
            if (!CubeArchetype.isSwallowable(activeItem))
                activeItem = null;
        }

        var newArchetype = CubeArchetype.fromItemStack(activeItem);

        playerArchetypes.put(player.getUniqueId(), newArchetype);
        applyArchetypeModifiers(player, newArchetype);
        updateWatcher(player, activeItem);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event)
    {
        var player = event.getPlayer();
        if (!isPlayerApplied(player)) return;

        var state = manager.getDisguiseStateFor(player);
        if (state == null || state.getEntityType() != EntityType.SULFUR_CUBE)
            return;

        var newOffhand = event.getOffHandItem();
        var newMainhand = event.getMainHandItem();

        ItemStack activeItem = CubeArchetype.isSwallowable(newOffhand)
                ? newOffhand
                : (CubeArchetype.isSwallowable(newMainhand) ? newMainhand : null);

        var newArchetype = CubeArchetype.fromItemStack(activeItem);

        playerArchetypes.put(player.getUniqueId(), newArchetype);
        applyArchetypeModifiers(player, newArchetype);
        updateWatcher(player, activeItem);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event)
    {
        var player = event.getPlayer();
        if (isPlayerApplied(player))
        {
            clearModifiers(player);
            playerArchetypes.remove(player.getUniqueId());
        }
    }

    private void applyModifier(Player player, Attribute attribute, double amount, AttributeModifier.Operation operation)
    {
        var instance = player.getAttribute(attribute);
        if (instance == null) return;

        instance.removeModifier(MODIFIER_KEY);
        if (Math.abs(amount) > 1e-6)
        {
            instance.addTransientModifier(new AttributeModifier(MODIFIER_KEY, amount, operation));
        }
    }

    private void clearModifiers(Player player)
    {
        for (var attr : MANAGED_ATTRIBUTES)
        {
            var instance = player.getAttribute(attr);
            if (instance != null)
                instance.removeModifier(MODIFIER_KEY);
        }
    }

    private void applyArchetypeModifiers(Player player, CubeArchetype archetype)
    {
        clearModifiers(player);

        switch (archetype)
        {
            case BOUNCY -> {
                // 橡膠球（彈性）: 速度快、彈跳力極高、中等摩擦力、低空氣阻力、有浮力
                applyModifier(player, Attribute.MOVEMENT_SPEED, 0.025, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.JUMP_STRENGTH, 0.25, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.BOUNCINESS, 0.9, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.SAFE_FALL_DISTANCE, 10.0, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.FALL_DAMAGE_MULTIPLIER, -0.8, AttributeModifier.Operation.ADD_SCALAR);
                applyModifier(player, Attribute.FRICTION_MODIFIER, -0.7, AttributeModifier.Operation.ADD_SCALAR);
                applyModifier(player, Attribute.AIR_DRAG_MODIFIER, -0.99, AttributeModifier.Operation.ADD_SCALAR);
            }
            case SLOW_FLAT -> {
                // 藥球（沉重平移）: 移動緩慢、彈性極低、中等摩擦力、中等空氣阻力（沉重抗推）、無浮力（沉底）
                applyModifier(player, Attribute.MOVEMENT_SPEED, -0.025, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.JUMP_STRENGTH, -0.22, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.KNOCKBACK_RESISTANCE, 1.0, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 1.0, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.BOUNCINESS, 0.0, AttributeModifier.Operation.ADD_NUMBER);
            }
            case FAST_FLAT -> {
                // 高爾夫球（高速平移）: 移動速度快、彈跳力低、中等摩擦力、空氣阻力極小、無浮力
                applyModifier(player, Attribute.MOVEMENT_SPEED, 0.025, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.JUMP_STRENGTH, -0.10, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.BOUNCINESS, 0.0, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.AIR_DRAG_MODIFIER, -0.99, AttributeModifier.Operation.ADD_SCALAR);
            }
            case LIGHT -> {
                // 沙灘球（輕盈）: 移動速度慢、彈跳力高、中等摩擦力、空氣阻力極大（空中滯空感強）、有浮力
                applyModifier(player, Attribute.MOVEMENT_SPEED, -0.025, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.JUMP_STRENGTH, 0.20, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.GRAVITY, -0.035, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.SAFE_FALL_DISTANCE, 10.0, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.FALL_DAMAGE_MULTIPLIER, -0.8, AttributeModifier.Operation.ADD_SCALAR);
                applyModifier(player, Attribute.BOUNCINESS, 1.0, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.AIR_DRAG_MODIFIER, 0.8, AttributeModifier.Operation.ADD_SCALAR);
            }
            case FAST_SLIDING -> {
                // 冰球（極速滑行）: 移動極快、無彈跳、地面摩擦力極小、空氣阻力極低（長距離滑行）、無浮力
                applyModifier(player, Attribute.MOVEMENT_SPEED, 0.05, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.JUMP_STRENGTH, -0.22, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.BOUNCINESS, 0.0, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.FRICTION_MODIFIER, -0.95, AttributeModifier.Operation.ADD_SCALAR);
                applyModifier(player, Attribute.AIR_DRAG_MODIFIER, -0.99, AttributeModifier.Operation.ADD_SCALAR);
            }
            case SLOW_SLIDING -> {
                // 冰壺（緩慢滑行）: 移動較慢、無彈跳、地面摩擦力極小、空氣阻力低、無浮力
                applyModifier(player, Attribute.MOVEMENT_SPEED, -0.015, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.JUMP_STRENGTH, -0.22, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.BOUNCINESS, 0.0, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.FRICTION_MODIFIER, -0.95, AttributeModifier.Operation.ADD_SCALAR);
                applyModifier(player, Attribute.AIR_DRAG_MODIFIER, -0.99, AttributeModifier.Operation.ADD_SCALAR);
            }
            case REGULAR -> {
                // 足球（普通）: 中等速度、中等彈跳力、中等摩擦力、低空氣阻力、有浮力
                applyModifier(player, Attribute.BOUNCINESS, 0.5, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.FRICTION_MODIFIER, -0.7, AttributeModifier.Operation.ADD_SCALAR);
                applyModifier(player, Attribute.AIR_DRAG_MODIFIER, -0.9, AttributeModifier.Operation.ADD_SCALAR);
            }
            case BEEHIVE -> {
                // 蜂窩方塊: 大幅改變附著與地面阻尼特性，增加黏滯力
                applyModifier(player, Attribute.MOVEMENT_SPEED, -0.035, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.FRICTION_MODIFIER, 0.85, AttributeModifier.Operation.ADD_SCALAR);
                applyModifier(player, Attribute.BOUNCINESS, -1.0, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.JUMP_STRENGTH, -0.15, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.SAFE_FALL_DISTANCE, 15.0, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.FALL_DAMAGE_MULTIPLIER, -0.9, AttributeModifier.Operation.ADD_SCALAR);
            }
            case SOUL_SAND -> {
                // 靈魂沙: 沉重幽冥阻尼、增加黏滯力
                applyModifier(player, Attribute.MOVEMENT_SPEED, -0.04, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.FRICTION_MODIFIER, 0.90, AttributeModifier.Operation.ADD_SCALAR);
                applyModifier(player, Attribute.KNOCKBACK_RESISTANCE, 0.6, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.BOUNCINESS, 0.0, AttributeModifier.Operation.ADD_NUMBER);
            }
            case MAGMA -> {
                // 岩漿塊: 熾熱熔岩核心、抗擊退、完全防火
                applyModifier(player, Attribute.MOVEMENT_SPEED, -0.015, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.KNOCKBACK_RESISTANCE, 0.5, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.BOUNCINESS, 0.0, AttributeModifier.Operation.ADD_NUMBER);
            }
            case CACTUS -> {
                // 仙人掌: 荊棘刺球、反傷碰撞
                applyModifier(player, Attribute.KNOCKBACK_RESISTANCE, 0.3, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.BOUNCINESS, 0.2, AttributeModifier.Operation.ADD_NUMBER);
            }
            case TNT -> {
                // TNT: 危險爆裂物、高抗爆炸
                applyModifier(player, Attribute.EXPLOSION_KNOCKBACK_RESISTANCE, 0.8, AttributeModifier.Operation.ADD_NUMBER);
                applyModifier(player, Attribute.BOUNCINESS, 0.1, AttributeModifier.Operation.ADD_NUMBER);
            }
            case EMPTY -> {
                // 空（基礎）: 基礎狀態、有浮力
            }
        }
    }

    private final Map<UUID, Integer> tntFuseMap = new Object2ObjectOpenHashMap<>();

    public void primeTNT(Player player, int fuseTicks)
    {
        if (tntFuseMap.containsKey(player.getUniqueId())) return;
        tntFuseMap.put(player.getUniqueId(), fuseTicks);
        player.getWorld().playSound(player.getLocation(), "entity.tnt.primed", 1.2f, 1.0f);
    }

    public static void explodeTNT(Player player, boolean breakBlocks)
    {
        var world = player.getWorld();
        var loc = player.getLocation();

        world.createExplosion(player, loc, 4.0f, false, breakBlocks);

        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR)
        {
            player.setHealth(0);
        }
    }

    private boolean isRedstoneTriggered(Player player)
    {
        var loc = player.getLocation();
        var block = loc.getBlock();
        var blockBelow = block.getRelative(org.bukkit.block.BlockFace.DOWN);

        if (block.isBlockPowered() || block.isBlockIndirectlyPowered()
                || blockBelow.isBlockPowered() || blockBelow.isBlockIndirectlyPowered())
        {
            return true;
        }

        if (block.getType() == Material.REDSTONE_BLOCK || blockBelow.getType() == Material.REDSTONE_BLOCK)
            return true;

        if (block.getBlockData() instanceof org.bukkit.block.data.type.RedstoneWire wire && wire.getPower() > 0)
            return true;

        if (block.getBlockData() instanceof org.bukkit.block.data.Powerable powerable && powerable.isPowered())
            return true;

        return false;
    }

    private boolean isFireTriggered(Player player)
    {
        if (player.getFireTicks() > 0 || player.isInLava())
            return true;

        var block = player.getLocation().getBlock();
        var type = block.getType();
        return type == Material.FIRE || type == Material.SOUL_FIRE || type == Material.LAVA;
    }

    private boolean isTouchingWall(Player player)
    {
        var loc = player.getLocation();
        var world = player.getWorld();
        int px = loc.getBlockX();
        int py = loc.getBlockY();
        int pz = loc.getBlockZ();

        return world.getBlockAt(px + 1, py, pz).getType().isSolid()
                || world.getBlockAt(px - 1, py, pz).getType().isSolid()
                || world.getBlockAt(px, py, pz + 1).getType().isSolid()
                || world.getBlockAt(px, py, pz - 1).getType().isSolid();
    }

    private boolean canTarget(LivingEntity entity)
    {
        if (entity instanceof Player targetPlayer)
        {
            return targetPlayer.getGameMode() != GameMode.CREATIVE
                    && targetPlayer.getGameMode() != GameMode.SPECTATOR;
        }
        return true;
    }

    private void spawnDefenderBee(Player player, LivingEntity target)
    {
        player.getWorld().spawn(player.getLocation().add(0, 1, 0), org.bukkit.entity.Bee.class, b -> {
            b.setTarget(target);
            b.setAnger(600);
            b.setCannotEnterHiveTicks(600);
        });
        player.getWorld().playSound(player.getLocation(), "entity.bee.loop_aggressive", 1.0f, 1.0f);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onEntityDamagePlayer(EntityDamageByEntityEvent event)
    {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isPlayerApplied(player)) return;

        var state = manager.getDisguiseStateFor(player);
        if (state == null || state.getEntityType() != EntityType.SULFUR_CUBE) return;

        var archetype = playerArchetypes.get(player.getUniqueId());
        if (archetype == null) return;

        var damager = event.getDamager();
        if (damager instanceof LivingEntity livingDamager)
        {
            if (archetype == CubeArchetype.CACTUS)
            {
                livingDamager.damage(3.0, player);
                player.getWorld().playSound(player.getLocation(), "block.cactus.hit", 1.0f, 1.0f);
            }
            else if (archetype == CubeArchetype.MAGMA)
            {
                livingDamager.setFireTicks(80);
                livingDamager.damage(2.5, player);
                player.getWorld().playSound(player.getLocation(), "entity.generic.burn", 1.0f, 1.0f);
            }
            else if (archetype == CubeArchetype.BEEHIVE)
            {
                if (ThreadLocalRandom.current().nextDouble() < 0.4)
                {
                    spawnDefenderBee(player, livingDamager);
                }
            }
            else if (archetype == CubeArchetype.TNT)
            {
                primeTNT(player, 10);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTakeDamage(EntityDamageEvent event)
    {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isPlayerApplied(player)) return;

        var state = manager.getDisguiseStateFor(player);
        if (state == null || state.getEntityType() != EntityType.SULFUR_CUBE) return;

        var archetype = playerArchetypes.get(player.getUniqueId());
        if (archetype == CubeArchetype.TNT)
        {
            var cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.FIRE
                    || cause == EntityDamageEvent.DamageCause.FIRE_TICK
                    || cause == EntityDamageEvent.DamageCause.LAVA
                    || cause == EntityDamageEvent.DamageCause.HOT_FLOOR
                    || cause == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                    || cause == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION)
            {
                primeTNT(player, 10);
            }
        }
    }
}
