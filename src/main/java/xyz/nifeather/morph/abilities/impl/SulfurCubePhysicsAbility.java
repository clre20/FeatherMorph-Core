package xyz.nifeather.morph.abilities.impl;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;
import xiamomc.pluginbase.Annotations.Resolved;
import xyz.nifeather.morph.MorphManager;
import xyz.nifeather.morph.api.morphs.abilities.AbilityNames;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.types.slimemagma.SulfurCubeWatcher;
import xyz.nifeather.morph.backends.server.renderer.network.registries.RenderRegistry;
import xyz.nifeather.morph.misc.DisguiseState;
import xyz.nifeather.morph.misc.sulfurcube.CubeArchetype;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SulfurCubePhysicsAbility extends NoOpOptionAbility
{
    @Resolved
    private MorphManager manager;

    @Resolved
    private RenderRegistry renderRegistry;

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

    private void updateWatcher(Player player)
    {
        if (renderRegistry == null) return;
        var watcher = renderRegistry.getWatcher(player.getUniqueId());
        if (watcher instanceof SulfurCubeWatcher cubeWatcher)
        {
            cubeWatcher.updateSwallowedItem();
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

        var item = player.getInventory().getItemInMainHand();
        var archetype = CubeArchetype.fromItemStack(item);

        playerArchetypes.put(player.getUniqueId(), archetype);
        applyArchetypeModifiers(player, archetype);
        updateWatcher(player);

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
        var item = player.getInventory().getItemInMainHand();
        var archetype = CubeArchetype.fromItemStack(item);

        var prevArchetype = playerArchetypes.get(player.getUniqueId());
        if (prevArchetype != archetype)
        {
            playerArchetypes.put(player.getUniqueId(), archetype);
            applyArchetypeModifiers(player, archetype);
            updateWatcher(player);
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

        var newItem = player.getInventory().getItem(event.getNewSlot());
        var newArchetype = CubeArchetype.fromItemStack(newItem);

        playerArchetypes.put(player.getUniqueId(), newArchetype);
        applyArchetypeModifiers(player, newArchetype);
        updateWatcher(player);
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
            case EMPTY -> {
                // 空（基礎）: 基礎狀態、有浮力
            }
        }
    }
}
