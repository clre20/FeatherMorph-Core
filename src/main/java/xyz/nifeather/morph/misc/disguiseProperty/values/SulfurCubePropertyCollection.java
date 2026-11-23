package xyz.nifeather.morph.misc.disguiseProperty.values;

import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.nifeather.morph.misc.disguiseProperty.*;

import java.util.Optional;

public class SulfurCubePropertyCollection extends BaseLivingEntityPropertyCollection<Entity>
{
    public final SingleProperty<Integer> SIZE = SingleProperty.builder(PropertyNames.SULFUR_CUBE_SIZE, 2)
            .withInputHandle(this::readSize)
            .withOutputHandle(OutputHandles::writeInteger)
            .build();

    private Optional<Integer> readSize(String propertyName, String string) throws ParseErrorException
    {
        var val = InputHandles.readInteger(propertyName, string)
                .orElseThrow(() -> new ParseErrorException(propertyName, "readSize: Unable to parse sulfur cube size"));

        InputHandles.throwIfOutOfBounds(propertyName, val, 1, 2);

        return Optional.of(val);
    }

    public SulfurCubePropertyCollection()
    {
        registerSingle(SIZE);
    }

    @Override
    protected @Nullable Entity tryCastEntity(@Nullable Entity targetEntity)
    {
        return targetEntity;
    }

    @Override
    protected void setupPropertiesFromEntity(PropertyHandler propertyHandler, @NotNull Entity targetEntity)
    {
        super.setupPropertiesFromEntity(propertyHandler, targetEntity);

        propertyHandler.set(SIZE, 2);
    }

    @Override
    protected void setupDefaultProperties(PropertyHandler propertyHandler)
    {
        propertyHandler.set(SIZE, 2);
    }
}
