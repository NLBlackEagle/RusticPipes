package rusticpipes.handlers;

import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import rusticpipes.RusticPipes;
import rusticpipes.block.BlockConduit;
import rusticpipes.network.ConduitNetwork;
import rusticpipes.network.PipeNetwork;

import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = RusticPipes.MODID)
public class ConduitRainHandler {

    private static int tickCounter = 0;
    private static final int CHECK_INTERVAL = 100;

    /** Tracks the last world-tick a lightning strike was spawned. One strike per interval globally. */
    private static int lastStrikeTick = -1;
    /** Minimum ticks between any two lightning strikes from this handler. Configurable. */
    private static final int STRIKE_COOLDOWN = 200; // 10 seconds

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return;

        tickCounter++;
        if (tickCounter % CHECK_INTERVAL != 0) return;

        World world = event.world;
        if (!world.isRaining()) return;
        if (!ForgeConfigHandler.conduit.enableRainDamage) return;

        // Iterate all loaded tile entities and find conduits
        for (TileEntity te : new ArrayList<>(world.loadedTileEntityList)) {

            if (!(te instanceof rusticpipes.tileentity.TileEntityConduit)) continue;

            BlockPos pos = te.getPos();

            // Must be exposed to sky
            if (!world.canSeeSky(pos.up())) continue;

            // Must be powered
            ConduitNetwork network = ConduitNetwork.getNetwork(pos);
            if (network == null) continue;
            if (network.getCurrentTier() == PipeNetwork.SpeedTier.SLOW) continue;

            // Enforce cooldown — only one strike per STRIKE_COOLDOWN ticks
            if (tickCounter - lastStrikeTick < STRIKE_COOLDOWN) continue;
            lastStrikeTick = tickCounter;

            // Destroy the conduit block first
            world.setBlockToAir(pos);

            // Then spawn lightning for visual/damage effect
            EntityLightningBolt bolt = new EntityLightningBolt(
                    world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, false);
            world.addWeatherEffect(bolt);

            if (RusticPipes.DEBUG) {
                RusticPipes.LOGGER.info("[RusticPipes] Lightning struck and destroyed conduit at " + pos);
            }
        }
    }
}
