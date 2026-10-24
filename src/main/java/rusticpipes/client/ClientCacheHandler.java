package rusticpipes.client;

import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import rusticpipes.RusticPipes;
import rusticpipes.network.ConduitClientState;

/**
 * Clears client-side caches keyed by BlockPos alone when the client world unloads
 * (disconnect or dimension change), so state from one world never shows in another.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = RusticPipes.MODID, value = Side.CLIENT)
public class ClientCacheHandler {

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        if (!event.getWorld().isRemote) return;
        ConduitClientState.clear();
        FluidTankMultiblockRenderer.clearCache();
    }
}
