package xyz.nifeather.morph.skills.impl;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import xiamomc.pluginbase.Annotations.Resolved;
import xyz.nifeather.morph.abilities.ISkillAbilityOptionHandler;
import xyz.nifeather.morph.api.morphs.skills.SkillNames;
import xyz.nifeather.morph.config.ConfigOptions;
import xyz.nifeather.morph.config.MorphConfigManager;
import xyz.nifeather.morph.messages.MessageUtils;
import xyz.nifeather.morph.messages.strings.SkillStrings;
import xyz.nifeather.morph.misc.DisguiseState;
import xyz.nifeather.morph.misc.ExecutionErrorException;
import xyz.nifeather.morph.misc.sulfurcube.CubeArchetype;
import xyz.nifeather.morph.skills.MorphSkill;
import xyz.nifeather.morph.skills.options.NoOpConfiguration;

public class SulfurCubeMorphSkill extends MorphSkill<NoOpConfiguration>
{
    @Resolved
    private MorphConfigManager config;
    @Override
    public ISkillAbilityOptionHandler<NoOpConfiguration> optionHandler()
    {
        return NoOpConfiguration.OPTION_HANDLER;
    }

    @Override
    public @NotNull NamespacedKey getIdentifier()
    {
        return SkillNames.SULFUR_CUBE;
    }

    @Override
    public int executeSkill(Player player, DisguiseState state, NoOpConfiguration option) throws ExecutionErrorException
    {
        var archetype = CubeArchetype.fromPlayer(player);

        return switch (archetype)
        {
            case EMPTY -> executeEmpty(player);
            case REGULAR -> executeRegular(player);
            case BOUNCY -> executeBouncy(player);
            case SLOW_FLAT -> executeSlowFlat(player);
            case FAST_FLAT -> executeFastFlat(player);
            case LIGHT -> executeLight(player);
            case FAST_SLIDING -> executeFastSliding(player);
            case SLOW_SLIDING -> executeSlowSliding(player);
            default -> executeEmpty(player);
        };
    }

    /**
     * 無型態（空）：無主動技能，提示不可用，僅依靠原版空白鍵跳躍。
     */
    private int executeEmpty(Player player)
    {
        sendDenyMessageToPlayer(player, SkillStrings.skillNotAvaliableString());
        player.playSound(Sound.sound(Key.key("minecraft", "entity.villager.no"),
                Sound.Source.PLAYER, 1f, 1f));
        return 10;
    }

    /**
     * 足球（普通）：石頭類、礦石方塊
     * 主動技能：【踢球抽射突進 (Kick Dash)】
     * 朝準星方向以適度弧度向前突進，撞擊路徑上的生物並造成傷害與擊退。
     */
    private int executeRegular(Player player)
    {
        var dir = player.getLocation().getDirection().normalize().multiply(1.4);
        dir.setY(0.4);
        player.setVelocity(dir);

        var world = player.getWorld();
        var loc = player.getLocation();
        world.playSound(loc, "entity.player.attack.knockback", 1.0f, 1.2f);
        world.playSound(loc, "block.stone.hit", 1.0f, 0.8f);
        world.spawnParticle(Particle.CRIT, loc.clone().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.1);

        applyHitboxDamageAndKnockback(player, 2.5, 4.0, dir.clone().normalize().multiply(0.8).setY(0.35));

        return 50; // 2.5s cooldown
    }

    /**
     * 橡膠球（彈性）：木頭類方塊
     * 主動技能：【超級彈跳 (Super Bounce)】
     * 像橡膠球一樣超高速彈射向上衝向高空，落地無摔落傷害並震動周圍。
     */
    private int executeBouncy(Player player)
    {
        var dir = player.getLocation().getDirection().setY(0).normalize().multiply(0.45);
        dir.setY(1.2); // 超高大跳
        player.setVelocity(dir);
        player.setFallDistance(0);

        var world = player.getWorld();
        var loc = player.getLocation();
        world.playSound(loc, "entity.slime.squish", 1.2f, 1.3f);
        world.playSound(loc, "block.slime_block.fall", 1.0f, 1.2f);
        world.spawnParticle(Particle.ITEM_SLIME, loc.clone().add(0, 0.2, 0), 25, 0.4, 0.2, 0.4, 0.1);

        for (var target : world.getNearbyEntities(loc, 3.0, 2.0, 3.0))
        {
            if (target != player && target instanceof LivingEntity living && canTarget(living))
            {
                living.setVelocity(living.getVelocity().add(new Vector(0, 0.45, 0)));
            }
        }

        return 60; // 3.0s cooldown
    }

    /**
     * 藥球（沉重平移）：金屬類方塊（鐵、金、銅、下界合金）
     * 主動技能：【泰山壓頂 / 沉重震地 (Heavy Ground Slam)】
     * 空中快速千斤墜砸地，在地面爆發地裂衝擊波，擊飛並緩速周圍敵人。
     */
    private int executeSlowFlat(Player player)
    {
        if (!player.isOnGround())
        {
            player.setVelocity(new Vector(0, -1.8, 0));
            var tracker = new Object() { int ticks = 0; };
            var task = new Runnable[] { null };
            task[0] = () -> {
                tracker.ticks++;
                if (player.isOnGround() || tracker.ticks >= 15)
                {
                    doGroundSlam(player);
                }
                else
                {
                    addSchedule(task[0], 1);
                }
            };
            addSchedule(task[0], 1);
        }
        else
        {
            doGroundSlam(player);
        }

        return 90; // 4.5s cooldown
    }

    private void doGroundSlam(Player player)
    {
        var world = player.getWorld();
        var loc = player.getLocation();

        world.playSound(loc, "block.anvil.land", 0.8f, 0.7f);
        world.playSound(loc, "entity.iron_golem.attack", 1.0f, 0.6f);
        world.spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.2, 0), 45, 1.5, 0.3, 1.5, 0.1,
                Material.IRON_BLOCK.createBlockData());

        for (var entity : world.getNearbyEntities(loc, 4.5, 3.0, 4.5))
        {
            if (entity != player && entity instanceof LivingEntity living && canTarget(living))
            {
                living.damage(6.0, player);
                var knockbackDir = living.getLocation().toVector().subtract(loc.toVector()).setY(0.4).normalize().multiply(0.95);
                living.setVelocity(knockbackDir);
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
            }
        }
    }

    /**
     * 高爾夫球（高速平移）：草方塊、泥土、樹葉等植物
     * 主動技能：【破風疾馳 (Golf Bullet Dash)】
     * 像一號木桿開球般貼地破風疾衝，直線穿透突進並對沿途敵人造成衝撞傷害。
     */
    private int executeFastFlat(Player player)
    {
        var dir = player.getLocation().getDirection().setY(0).normalize().multiply(2.2);
        dir.setY(0.08); // 水平極速直線衝刺
        player.setVelocity(dir);

        var world = player.getWorld();
        var loc = player.getLocation();
        world.playSound(loc, "entity.arrow.shoot", 1.2f, 1.6f);
        world.playSound(loc, "entity.breeze.wind_burst", 1.0f, 1.4f);
        world.spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 0.8, 0), 4, 0.3, 0.2, 0.3, 0);

        applyHitboxDamageAndKnockback(player, 2.8, 5.0, dir.clone().normalize().multiply(0.6).setY(0.25));

        return 60; // 3.0s cooldown
    }

    /**
     * 沙灘球（輕盈）：各色羊毛方塊
     * 主動技能：【輕盈托球浮空 (Beach Ball Float & Glide)】
     * 向上輕柔拍起浮空，獲得緩降與跳躍增益，在空中長時間輕飄飄隨風滑翔。
     */
    private int executeLight(Player player)
    {
        var vel = player.getVelocity();
        vel.setY(0.75);
        player.setVelocity(vel);

        var world = player.getWorld();
        var loc = player.getLocation();
        world.playSound(loc, "entity.breeze.inhale", 1.0f, 1.2f);
        world.playSound(loc, "block.wool.step", 1.2f, 1.5f);
        world.spawnParticle(Particle.POOF, loc.clone().add(0, 0.3, 0), 20, 0.5, 0.3, 0.5, 0.05);

        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 1));

        return 80; // 4.0s cooldown
    }

    /**
     * 保齡球（快速滑行）：冰類方塊、陶瓦、混凝土
     * 主動技能：【全倒衝撞滑行 (Bowling Strike Slide)】
     * 貼地極低摩擦急速滑行，將沿途碰到的生物像保齡球瓶一樣全部往兩側撞飛擊退！
     */
    private int executeFastSliding(Player player)
    {
        var dir = player.getLocation().getDirection().setY(0).normalize().multiply(1.85);
        player.setVelocity(dir);

        var world = player.getWorld();
        var loc = player.getLocation();
        world.playSound(loc, "block.glass.break", 0.7f, 1.4f);
        world.playSound(loc, "block.snow.step", 1.2f, 0.8f);
        world.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 0.2, 0), 25, 0.5, 0.2, 0.5, 0.1);

        var tracker = new Object() { int ticks = 0; };
        var task = new Runnable[] { null };
        task[0] = () -> {
            tracker.ticks++;
            applyBowlingHitbox(player, dir);
            if (tracker.ticks < 4)
            {
                addSchedule(task[0], 2);
            }
        };
        addSchedule(task[0], 1);

        return 60; // 3.0s cooldown
    }

    private void applyBowlingHitbox(Player player, Vector dir)
    {
        var world = player.getWorld();
        var loc = player.getLocation();

        for (var entity : world.getNearbyEntities(loc, 2.5, 2.0, 2.5))
        {
            if (entity != player && entity instanceof LivingEntity living && canTarget(living))
            {
                living.damage(5.0, player);
                var sideVector = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize().multiply(0.75);
                if (Math.random() > 0.5) sideVector.multiply(-1);
                sideVector.setY(0.4);
                living.setVelocity(sideVector.add(dir.clone().multiply(0.4)));
                world.playSound(living.getLocation(), "entity.player.attack.knockback", 1.0f, 0.9f);
            }
        }
    }

    /**
     * 泥球 / 鉛球（慢速滑行）：泥巴、黏土、靈魂沙等方塊
     * 主動技能：【泥沼噴濺陷阱 (Mud Quagmire Splash)】
     * 向四周迸發大片泥漿，使周遭敵人深陷泥沼減速並附加短暫失明。
     */
    private int executeSlowSliding(Player player)
    {
        var world = player.getWorld();
        var loc = player.getLocation();

        world.playSound(loc, "block.mud.place", 1.2f, 0.8f);
        world.playSound(loc, "block.mud.step", 1.2f, 0.6f);
        world.spawnParticle(Particle.BLOCK, loc.clone().add(0, 0.3, 0), 40, 1.3, 0.4, 1.3, 0.05,
                Material.MUD.createBlockData());

        for (var entity : world.getNearbyEntities(loc, 4.2, 2.5, 4.2))
        {
            if (entity != player && entity instanceof LivingEntity living && canTarget(living))
            {
                living.damage(3.5, player);
                living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 70, 2));
                living.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0));
            }
        }

        return 70; // 3.5s cooldown
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

    private void applyHitboxDamageAndKnockback(Player player, double radius, double damage, Vector knockback)
    {
        var world = player.getWorld();
        var loc = player.getLocation();
        for (var entity : world.getNearbyEntities(loc, radius, radius, radius))
        {
            if (entity != player && entity instanceof LivingEntity living && canTarget(living))
            {
                living.damage(damage, player);
                living.setVelocity(living.getVelocity().add(knockback));
            }
        }
    }
}