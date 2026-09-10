package xyz.nifeather.morph.backends.server.renderer.network.datawatcher.values;

import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import xyz.nifeather.morph.backends.server.renderer.network.datawatcher.values.basetypes.AgeableMobValues;

public class SulfurCubeValues extends AgeableMobValues
{
    public final SingleValue<Integer> SIZE = createSingle("sulfur_cube_size", 2, EntityDataTypes.INT);
    public final SingleValue<Boolean> FROM_BUCKET = createSingle("sulfur_cube_from_bucket", false, EntityDataTypes.BOOLEAN);
    public final SingleValue<Integer> MAX_FUSE = createSingle("sulfur_cube_max_fuse", -1, EntityDataTypes.INT);

    public SulfurCubeValues()
    {
        super();

        registerSingle(SIZE, FROM_BUCKET, MAX_FUSE);
    }
}
