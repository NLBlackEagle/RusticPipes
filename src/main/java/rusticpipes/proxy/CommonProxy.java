package rusticpipes.proxy;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import rusticpipes.network.ConduitNetwork;
import rusticpipes.network.FluidNetwork;
import rusticpipes.network.PipeNetwork;

public class CommonProxy {

    public void init() {}

    public void postInit() {}

    public void preInit() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return;
        PipeNetwork.serverTick();
    }

    /**
     * Clears all network entries for the unloading dimension.
     * Prevents memory leaks when worlds are unloaded (e.g. on world quit in
     * single-player, or when a dimension unloads on a server).
     */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (event.getWorld().isRemote) return;
        int dimId = event.getWorld().provider.getDimension();
        PipeNetwork.clearDimension(dimId);
        FluidNetwork.clearDimension(dimId);
        ConduitNetwork.clearDimension(dimId);
    }
}
