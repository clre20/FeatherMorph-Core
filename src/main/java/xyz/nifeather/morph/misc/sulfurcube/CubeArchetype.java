package xyz.nifeather.morph.misc.sulfurcube;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.SulfurCubeArchetype;
import org.bukkit.Material;
import org.bukkit.craftbukkit.util.CraftMagicNumbers;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 硫磺立方體「身體方塊」對應的原版型態集合。
 * <p>
 * 所有資料都直接取自伺服器的 {@code minecraft:sulfur_cube_archetype} registry
 * 與 {@code #minecraft:sulfur_cube_archetype/*}、{@code #minecraft:sulfur_cube_swallowable} 物品標籤，
 * 因此會與原版（以及伺服器上的資料包）完全一致，不寫死任何方塊清單或數值。
 * <p>
 * 原版的物品標籤以「物品種類」為單位判斷，所以結果依 {@link Material} 快取；
 * 資料包重載時呼叫 {@link #invalidateCache()} 清除。快取可在 PacketEvents 的網路執行緒上讀取，因此使用 ConcurrentHashMap。
 */
public final class CubeArchetype
{
    /** 沒有吞下方塊（或手上的方塊不可被吞下）。 */
    public static final CubeArchetype EMPTY = new CubeArchetype(List.of(), null);

    private static final Map<Material, CubeArchetype> archetypeCache = new ConcurrentHashMap<>();
    private static final Map<Material, Boolean> swallowableCache = new ConcurrentHashMap<>();

    private final List<SulfurCubeArchetype> archetypes;

    @Nullable
    private final ResourceKey<SulfurCubeArchetype> primaryKey;

    // 以下欄位的合併方式與原版 SulfurCube#collectEquipmentChanges 相同
    private final boolean buoyant;
    private final Optional<SulfurCubeArchetype.ExplosionData> explosion;
    private final List<SulfurCubeArchetype.ContactDamage> contactDamages;
    private final SulfurCubeArchetype.KnockbackModifiers knockbackModifiers;
    private final SulfurCubeArchetype.SoundSettings soundSettings;

    private CubeArchetype(List<SulfurCubeArchetype> archetypes, @Nullable ResourceKey<SulfurCubeArchetype> primaryKey)
    {
        this.archetypes = List.copyOf(archetypes);
        this.primaryKey = primaryKey;

        boolean buoyant = false;
        Optional<SulfurCubeArchetype.ExplosionData> explosion = Optional.empty();
        var contactDamages = new ArrayList<SulfurCubeArchetype.ContactDamage>();
        var knockback = SulfurCubeArchetype.DEFAULT_KNOCKBACK_MODIFIERS;
        var sound = SulfurCubeArchetype.DEFAULT_SOUND_SETTINGS;

        for (var archetype : archetypes)
        {
            if (archetype.buoyant()) buoyant = true;
            if (archetype.explosion().isPresent()) explosion = archetype.explosion();
            archetype.contactDamage().ifPresent(contactDamages::add);

            // 原版：後面符合的型態覆蓋前面的擊退與音效設定
            knockback = archetype.knockbackModifiers();
            sound = archetype.soundSettings();
        }

        this.buoyant = buoyant;
        this.explosion = explosion;
        this.contactDamages = List.copyOf(contactDamages);
        this.knockbackModifiers = knockback;
        this.soundSettings = sound;
    }

    // region Resolve

    public static void invalidateCache()
    {
        archetypeCache.clear();
        swallowableCache.clear();
    }

    /**
     * 原版判斷：物品屬於 {@code #minecraft:sulfur_cube_swallowable}。
     */
    public static boolean isSwallowable(@Nullable ItemStack item)
    {
        if (item == null || item.isEmpty()) return false;

        var type = item.getType();
        if (!type.isItem()) return false;

        return swallowableCache.computeIfAbsent(type, CubeArchetype::computeSwallowable);
    }

    private static boolean computeSwallowable(Material type)
    {
        var nmsItem = CraftMagicNumbers.getItem(type);
        if (nmsItem == null) return false;

        return nmsItem.builtInRegistryHolder().is(ItemTags.SULFUR_CUBE_SWALLOWABLE);
    }

    /**
     * 玩家目前代表「身體方塊」的物品：副手優先，其次主手；都不可吞下時回傳 null。
     */
    @Nullable
    public static ItemStack getActiveItem(@Nullable Player player)
    {
        if (player == null) return null;

        var inventory = player.getInventory();

        var offhand = inventory.getItemInOffHand();
        if (isSwallowable(offhand)) return offhand;

        var mainhand = inventory.getItemInMainHand();
        if (isSwallowable(mainhand)) return mainhand;

        return null;
    }

    @NotNull
    public static CubeArchetype fromPlayer(@Nullable Player player)
    {
        return fromItemStack(getActiveItem(player));
    }

    @NotNull
    public static CubeArchetype fromItemStack(@Nullable ItemStack item)
    {
        if (!isSwallowable(item)) return EMPTY;

        //noinspection DataFlowIssue isSwallowable() 已確保非 null
        return fromMaterial(item.getType());
    }

    @NotNull
    public static CubeArchetype fromMaterial(@Nullable Material type)
    {
        if (type == null) return EMPTY;

        return archetypeCache.computeIfAbsent(type, CubeArchetype::computeArchetype);
    }

    /**
     * 等同原版 {@code SulfurCube#matchingArchetypes}：依 registry 順序收集所有包含此物品的型態。
     */
    private static CubeArchetype computeArchetype(Material type)
    {
        var nmsItem = CraftMagicNumbers.getItem(type);
        if (nmsItem == null) return EMPTY;

        var holder = nmsItem.builtInRegistryHolder();
        if (!holder.is(ItemTags.SULFUR_CUBE_SWALLOWABLE)) return EMPTY;

        var registry = MinecraftServer.getServer()
                .registryAccess()
                .lookupOrThrow(Registries.SULFUR_CUBE_ARCHETYPE);

        var matched = new ArrayList<SulfurCubeArchetype>();
        ResourceKey<SulfurCubeArchetype> primary = null;

        for (var entry : registry.listElements().toList())
        {
            var archetype = entry.value();
            if (archetype.items().contains(holder))
            {
                matched.add(archetype);
                primary = entry.key();
            }
        }

        return matched.isEmpty() ? EMPTY : new CubeArchetype(matched, primary);
    }

    // endregion Resolve

    public boolean isEmpty()
    {
        return archetypes.isEmpty();
    }

    /**
     * 所有符合的原版型態（依 registry 順序）。
     */
    public List<SulfurCubeArchetype> archetypes()
    {
        return archetypes;
    }

    /**
     * 最後一個符合的型態 ID 路徑（例如 {@code bouncy}），沒有則為空字串。
     * 原版以最後符合者決定擊退與音效，技能也以此為準。
     */
    @NotNull
    public String primaryId()
    {
        return primaryKey == null ? "" : primaryKey.identifier().getPath();
    }

    public boolean isBuoyant()
    {
        return buoyant;
    }

    public Optional<SulfurCubeArchetype.ExplosionData> explosion()
    {
        return explosion;
    }

    public List<SulfurCubeArchetype.ContactDamage> contactDamages()
    {
        return contactDamages;
    }

    public SulfurCubeArchetype.KnockbackModifiers knockbackModifiers()
    {
        return knockbackModifiers;
    }

    public SulfurCubeArchetype.SoundSettings soundSettings()
    {
        return soundSettings;
    }
}
