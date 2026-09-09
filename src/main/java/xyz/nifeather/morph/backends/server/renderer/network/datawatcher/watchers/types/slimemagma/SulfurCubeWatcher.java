package xyz.nifeather.morph.backends.server.renderer.network.datawatcher.watchers.types.slimemagma;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public class SulfurCubeWatcher extends AbstractSlimeWatcher
{
    public SulfurCubeWatcher(Player bindingPlayer)
    {
        super(bindingPlayer, EntityType.SULFUR_CUBE);
    }
}
