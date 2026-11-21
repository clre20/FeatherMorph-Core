package xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.types;

import com.destroystokyo.paper.ClientOption;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.pose.EntityPose;
import com.github.retrooper.packetevents.protocol.player.GameMode;
import com.github.retrooper.packetevents.protocol.player.HumanoidArm;
import com.github.retrooper.packetevents.util.Vector3i;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoRemove;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.mojang.authlib.GameProfile;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.MainHand;
import xyz.nifeather.morph.backends.server.renderer.network.DisguiseNameplate;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.values.SingleValue;
import xyz.nifeather.morph.backends.server.renderer.network.registries.CustomEntries;
import xyz.nifeather.morph.backends.server.renderer.network.registries.CustomEntry;
import xyz.nifeather.morph.backends.server.renderer.network.registries.ValueIndex;
import xyz.nifeather.morph.misc.AnimationNames;
import xyz.nifeather.morph.misc.BuildFailedException;
import xyz.nifeather.morph.misc.disguiseProperty.DisguiseProperties;
import xyz.nifeather.morph.misc.disguiseProperty.SingleProperty;
import xyz.nifeather.morph.misc.disguiseProperty.values.PlayerPropertyCollection;
import xyz.nifeather.morph.utilities.GameProfileUtils;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class PlayerWatcher extends LivingEntityWatcher
{
    @Override
    protected void initRegistry()
    {
        super.initRegistry();

        register(ValueIndex.PLAYER);
    }

    private final PlayerPropertyCollection playerDisguiseProperties;

    public PlayerWatcher(Player bindingPlayer)
    {
        super(bindingPlayer, EntityType.PLAYER);

        this.playerDisguiseProperties = DisguiseProperties.INSTANCE.getCollectionOrThrow(PlayerPropertyCollection.class);
    }

    @Override
    protected void doSync()
    {
        super.doSync();

        var bindingPlayer = getBindingPlayer();
        this.writeTemp(ValueIndex.PLAYER.SKIN_FLAGS, (byte)bindingPlayer.getClientOption(ClientOption.SKIN_PARTS).getRaw());

        if (!this.isValuePresent(ValueIndex.PLAYER.MAINHAND))
            this.writeTemp(ValueIndex.PLAYER.MAINHAND, bindingPlayer.getMainHand() == MainHand.LEFT ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
    }

    @Override
    protected <X> void onPropertyWrite(SingleProperty<X> property, X value)
    {
        if (property.equals(playerDisguiseProperties.MAIN_HAND))
        {
            var handStatus = (PlayerPropertyCollection.MainHandStatus) value;
            if (handStatus == PlayerPropertyCollection.MainHandStatus.NOTSET)
                return;

            var hand = handStatus.bindingHand;
            assert hand != null;

            this.writePersistent(ValueIndex.PLAYER.MAINHAND, hand == MainHand.LEFT ? HumanoidArm.LEFT : HumanoidArm.RIGHT);
        }
        else if (property.equals(playerDisguiseProperties.SKIN))
        {
            var skin = (GameProfile) value;
            this.writeEntry(CustomEntries.PROFILE, skin);
        }

        super.onPropertyWrite(property, value);
    }

    @Override
    protected <X> void onEntryWrite(CustomEntry<X> entry, X oldVal, X newVal)
    {
        super.onEntryWrite(entry, oldVal, newVal);

        if (entry.equals(CustomEntries.PROFILE) && isPlayerOnline() && !isSilent())
        {
            var player = getBindingPlayer();
            var affected = getAffectedPlayers(player);

            if (affected.isEmpty())
                return;

            List<PacketWrapper<?>> spawnPackets;

            try
            {
                spawnPackets = this.buildSpawnPackets();
            }
            catch (BuildFailedException e)
            {
                logger.error("Build spawn packet FAILED for player skin update! not continuing", e);
                return;
            }

            var protocol = PacketEvents.getAPI().getPlayerManager();

            affected.forEach(p ->
                    spawnPackets.forEach(packet -> protocol.sendPacket(p, packet)));
        }

        if (entry.equals(CustomEntries.ANIMATION))
        {
            var animId = newVal + "";

            switch (animId)
            {
                case AnimationNames.LAY ->
                {
                    this.remove(ValueIndex.PLAYER.POSE);
                    this.writePersistent(ValueIndex.PLAYER.POSE, EntityPose.SLEEPING);

                    var playerPos = getBindingPlayer().getLocation();
                    var vec3i = new Vector3i(playerPos.getBlockX(), playerPos.getBlockY(), playerPos.getBlockZ());
                    this.writePersistent(ValueIndex.PLAYER.BED_POS, Optional.of(vec3i));
                }
                case AnimationNames.CRAWL ->
                {
                    resetValues();
                    this.writePersistent(ValueIndex.PLAYER.POSE, EntityPose.SWIMMING);
                }
                case AnimationNames.STANDUP, AnimationNames.RESET ->
                {
                    this.writePersistent(ValueIndex.PLAYER.POSE, SpigotConversionUtil.fromBukkitPose(getBindingPlayer().getPose()));
                    resetValues();
                }
            }
        }
    }

    public List<PacketWrapper<?>> buildPlayerInfoPackets()
    {
        var virtualEntityUUID = this.readEntryOrThrow(CustomEntries.SPAWN_UUID);
        var infoRemove = new WrapperPlayServerPlayerInfoRemove(List.of(virtualEntityUUID));

        var packetProfile = GameProfileUtils.toPacketEventsUserProfile(this.readEntryOrThrow(CustomEntries.PROFILE));
        packetProfile.setUUID(virtualEntityUUID);
        var infoUpdate = new WrapperPlayServerPlayerInfoUpdate(
                EnumSet.of(
                        WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER,
                        WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_LISTED
                ),
                new WrapperPlayServerPlayerInfoUpdate.PlayerInfo(
                        packetProfile,
                        this.readEntryOrDefault(CustomEntries.PROFILE_LISTED, false), 114514, GameMode.defaultGameMode(), null,
                        null, 0, true
                )
        );

        return List.of(infoRemove, infoUpdate);
    }

    public List<PacketWrapper<?>> buildSpawnPackets(boolean includePlayerInfo) throws BuildFailedException
    {
        var list = new ObjectArrayList<PacketWrapper<?>>();

        var gameProfile = this.readEntryOrThrow(CustomEntries.PROFILE);

        if (gameProfile.name().isBlank())
            throw new IllegalArgumentException("GameProfile name is empty!");

        if (includePlayerInfo)
            list.addAll(this.buildPlayerInfoPackets());

        var basePackets = super.buildSpawnPackets();
        list.addAll(basePackets);

        // 偽裝實體沒有生成（例如隱身中）時，不生成名牌
        if (!basePackets.isEmpty())
            list.addAll(this.buildNameplatePackets(gameProfile.name()));

        return list;
    }

    //region 「偽裝」名牌標記

    private int nameplateEntityId = -1;
    private final UUID nameplateUUID = UUID.randomUUID();

    /**
     * 目前在 {@link DisguiseNameplate} 中登記的玩家實體 ID，-1 表示沒有登記
     */
    private volatile int trackedPlayerEntityId = -1;

    /**
     * 上次送出的潛行/隱形狀態，用來避免重複送出相同的封包
     */
    private volatile int lastNameplateState = -1;

    private boolean nameplateActive()
    {
        return this.readEntryOrDefault(CustomEntries.NAMEPLATE_MARKER, false) && DisguiseNameplate.isEnabled();
    }

    private synchronized void trackNameplate(int playerEntityId)
    {
        if (trackedPlayerEntityId == playerEntityId) return;

        untrackNameplate();
        DisguiseNameplate.track(playerEntityId, nameplateEntityId);
        trackedPlayerEntityId = playerEntityId;
    }

    private synchronized void untrackNameplate()
    {
        if (trackedPlayerEntityId == -1) return;

        DisguiseNameplate.untrack(trackedPlayerEntityId, nameplateEntityId);
        trackedPlayerEntityId = -1;
    }

    private static int nameplateState(byte generalFlags)
    {
        // bit 0：潛行（0x02），bit 1：隱形（0x20）
        return ((generalFlags & 0x02) != 0 ? 1 : 0) | ((generalFlags & 0x20) != 0 ? 2 : 0);
    }

    private List<PacketWrapper<?>> buildNameplatePackets(String name)
    {
        if (!nameplateActive())
        {
            untrackNameplate();

            // 功能被關閉後重新生成時，順便移除之前留下的名牌
            return nameplateEntityId == -1
                    ? List.of()
                    : List.of(new WrapperPlayServerDestroyEntities(nameplateEntityId));
        }

        var player = getBindingPlayer();

        if (nameplateEntityId == -1)
            nameplateEntityId = Bukkit.getUnsafe().nextEntityId(player.getWorld());

        int playerEntityId = this.readEntryOrThrow(CustomEntries.SPAWN_ID);
        trackNameplate(playerEntityId);

        int state = nameplateState(this.read(ValueIndex.BASE_ENTITY.GENERAL));
        lastNameplateState = state;

        var list = new ObjectArrayList<PacketWrapper<?>>(4);

        list.add(new WrapperPlayServerSpawnEntity(
                nameplateEntityId, Optional.of(nameplateUUID), EntityTypes.TEXT_DISPLAY,
                new Vector3d(player.getX(), player.getY(), player.getZ()), 0f, 0f, 0f, 0, Optional.empty()));

        list.add(new WrapperPlayServerEntityMetadata(nameplateEntityId,
                DisguiseNameplate.buildMetadata(name, (state & 1) != 0, (state & 2) != 0)));

        // 讓名牌騎在玩家身上（同時保留玩家原本的乘客）
        var realPassengers = this.lastPassengerIds;
        var passengers = Arrays.copyOf(realPassengers, realPassengers.length + 1);
        passengers[realPassengers.length] = nameplateEntityId;
        list.add(new WrapperPlayServerSetPassengers(playerEntityId, passengers));

        return list;
    }

    @Override
    protected <X> void onTrackerWrite(SingleValue<X> single, @org.jetbrains.annotations.Nullable X oldVal, @org.jetbrains.annotations.Nullable X newVal)
    {
        super.onTrackerWrite(single, oldVal, newVal);

        if (trackedPlayerEntityId == -1 || nameplateEntityId == -1) return;
        if (!(newVal instanceof Byte flags) || !single.equals(ValueIndex.BASE_ENTITY.GENERAL)) return;

        int state = nameplateState(flags);
        if (state == lastNameplateState) return;
        lastNameplateState = state;

        if (isSilent() || !isAlive() || !nameplateActive()) return;

        var packet = new WrapperPlayServerEntityMetadata(nameplateEntityId,
                DisguiseNameplate.buildMetadata(this.readEntryOrThrow(CustomEntries.PROFILE).name(), (state & 1) != 0, (state & 2) != 0));

        sendPacketToAffectedPlayers(packet);
    }

    @Override
    protected void onDispose()
    {
        super.onDispose();
        untrackNameplate();
    }

    //endregion

    @Override
    public List<PacketWrapper<?>> buildVirtualEntityDisposalPackets() throws BuildFailedException
    {
        var list = new ObjectArrayList<>(super.buildVirtualEntityDisposalPackets());

        if (nameplateEntityId != -1)
            list.add(new WrapperPlayServerDestroyEntities(nameplateEntityId));

        var playerInfoRmPacket = new WrapperPlayServerPlayerInfoRemove(this.readEntryOrThrow(CustomEntries.SPAWN_UUID));
        list.add(playerInfoRmPacket);

        return list;
    }

    @Override
    public List<PacketWrapper<?>> buildSpawnPackets() throws BuildFailedException
    {
        return buildSpawnPackets(true);
    }

    private void resetValues()
    {
        this.remove(ValueIndex.PLAYER.POSE);
        this.writePersistent(ValueIndex.PLAYER.BED_POS, Optional.empty());
        this.remove(ValueIndex.PLAYER.BED_POS);
    }
}
