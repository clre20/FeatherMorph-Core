package xyz.nifeather.morph.backends.server.renderer.network.datawatcher.values;

import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.values.basetypes.AgeableMobValues;

public class SulfurCubeValues extends AgeableMobValues
{
    // 順序必須與原版一致：AbstractCubeMob.ID_SIZE(18) -> SulfurCube.MAX_FUSE(19) -> SulfurCube.FROM_BUCKET(20)
    public final SingleValue<Integer> SIZE = createSingle("sulfur_cube_size", 2, EntityDataTypes.INT);
    public final SingleValue<Integer> MAX_FUSE = createSingle("sulfur_cube_max_fuse", -1, EntityDataTypes.INT);
    public final SingleValue<Boolean> FROM_BUCKET = createSingle("sulfur_cube_from_bucket", false, EntityDataTypes.BOOLEAN);

    public SulfurCubeValues()
    {
        super();

        registerSingle(SIZE, FROM_BUCKET, MAX_FUSE);
    }
}
