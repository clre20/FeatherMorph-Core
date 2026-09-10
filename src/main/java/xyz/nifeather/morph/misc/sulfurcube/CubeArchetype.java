package xyz.nifeather.morph.misc.sulfurcube;

import net.minecraft.tags.ItemTags;
import org.bukkit.Material;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public enum CubeArchetype
{
    EMPTY,
    REGULAR,
    BOUNCY,
    SLOW_FLAT,
    FAST_FLAT,
    LIGHT,
    FAST_SLIDING,
    SLOW_SLIDING,
    TNT,
    MAGMA,
    CACTUS,
    BEEHIVE,
    SOUL_SAND;

    public static boolean isSwallowable(@Nullable ItemStack item)
    {
        if (item == null || item.getType() == Material.AIR || !item.getType().isBlock())
            return false;

        var nmsStack = CraftItemStack.asNMSCopy(item);
        return !nmsStack.isEmpty() && nmsStack.is(ItemTags.SULFUR_CUBE_SWALLOWABLE);
    }

    @Nullable
    public static ItemStack getActiveItem(@Nullable org.bukkit.entity.Player player)
    {
        if (player == null) return null;
        var inventory = player.getInventory();
        var offhand = inventory.getItemInOffHand();
        if (isSwallowable(offhand))
            return offhand;

        var mainhand = inventory.getItemInMainHand();
        if (isSwallowable(mainhand))
            return mainhand;

        return null;
    }

    public static CubeArchetype fromPlayer(@Nullable org.bukkit.entity.Player player)
    {
        var item = getActiveItem(player);
        return fromItemStack(item);
    }

    public static CubeArchetype fromItemStack(@Nullable ItemStack item)
    {
        if (!isSwallowable(item))
            return EMPTY;

        var type = item.getType();
        if (type == Material.TNT)
            return TNT;

        if (type == Material.MAGMA_BLOCK)
            return MAGMA;

        if (type == Material.CACTUS)
            return CACTUS;

        if (type == Material.BEEHIVE || type == Material.BEE_NEST || type == Material.HONEY_BLOCK)
            return BEEHIVE;

        if (type == Material.SOUL_SAND || type == Material.SOUL_SOIL)
            return SOUL_SAND;

        var nmsStack = CraftItemStack.asNMSCopy(item);

        if (nmsStack.is(ItemTags.SULFUR_CUBE_ARCHETYPE_BOUNCY))
            return BOUNCY;
        if (nmsStack.is(ItemTags.SULFUR_CUBE_ARCHETYPE_SLOW_FLAT))
            return SLOW_FLAT;
        if (nmsStack.is(ItemTags.SULFUR_CUBE_ARCHETYPE_FAST_FLAT))
            return FAST_FLAT;
        if (nmsStack.is(ItemTags.SULFUR_CUBE_ARCHETYPE_LIGHT))
            return LIGHT;
        if (nmsStack.is(ItemTags.SULFUR_CUBE_ARCHETYPE_FAST_SLIDING))
            return FAST_SLIDING;
        if (nmsStack.is(ItemTags.SULFUR_CUBE_ARCHETYPE_SLOW_SLIDING))
            return SLOW_SLIDING;
        if (nmsStack.is(ItemTags.SULFUR_CUBE_ARCHETYPE_REGULAR))
            return REGULAR;

        return REGULAR;
    }

    public boolean isBuoyant()
    {
        return switch (this)
        {
            case REGULAR, BOUNCY, LIGHT, EMPTY, CACTUS, BEEHIVE -> true;
            case SLOW_FLAT, FAST_FLAT, FAST_SLIDING, SLOW_SLIDING, TNT, MAGMA, SOUL_SAND -> false;
        };
    }

    public boolean isSinking()
    {
        return this == SLOW_FLAT || this == MAGMA || this == SOUL_SAND;
    }

    public boolean isSliding()
    {
        return this == FAST_SLIDING || this == SLOW_SLIDING;
    }

    public boolean isSticky()
    {
        return this == BEEHIVE || this == SOUL_SAND;
    }

    public boolean hasContactDamage()
    {
        return this == MAGMA || this == CACTUS;
    }
}
