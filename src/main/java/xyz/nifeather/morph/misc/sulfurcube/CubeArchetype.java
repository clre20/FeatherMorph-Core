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
    SLOW_SLIDING;

    public static boolean isSwallowable(@Nullable ItemStack item)
    {
        if (item == null || item.getType() == Material.AIR || !item.getType().isBlock())
            return false;

        var nmsStack = CraftItemStack.asNMSCopy(item);
        return !nmsStack.isEmpty() && nmsStack.is(ItemTags.SULFUR_CUBE_SWALLOWABLE);
    }

    public static CubeArchetype fromItemStack(@Nullable ItemStack item)
    {
        if (!isSwallowable(item))
            return EMPTY;

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
            case REGULAR, BOUNCY, LIGHT, EMPTY -> true;
            case SLOW_FLAT, FAST_FLAT, FAST_SLIDING, SLOW_SLIDING -> false;
        };
    }

    public boolean isSinking()
    {
        return this == SLOW_FLAT;
    }

    public boolean isSliding()
    {
        return this == FAST_SLIDING || this == SLOW_SLIDING;
    }
}
