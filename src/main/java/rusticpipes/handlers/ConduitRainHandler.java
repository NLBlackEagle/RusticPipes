package rusticpipes.handlers;

import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import rusticpipes.RusticPipes;
import rusticpipes.network.ConduitNetwork;

import java.util.ArrayList;

@Mod.EventBusSubscriber(modid = RusticPipes.MODID)
public class ConduitRainHandler {

    private static int tickCounter = 0;
    private static final int CHECK_INTERVAL = 100;
    private static int lastStrikeTick = -1;
    private static final int STRIKE_COOLDOWN = 200;

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return;

        tickCounter++;
        if (tickCounter % CHECK_INTERVAL != 0) return;

        World world = event.world;
        if (!world.isRaining()) return;
        if (!ForgeConfigHandler.conduit.enableRainDamage) return;

        for (TileEntity te : new ArrayList<>(world.loadedTileEntityList)) {
            if (!(te instanceof rusticpipes.tileentity.TileEntityConduit)) continue;

            BlockPos pos = te.getPos();
            if (!world.canSeeSky(pos.up())) continue;

            ConduitNetwork network = ConduitNetwork.getNetwork(world, pos);
            if (network == null) continue;
            // Only strike powered conduits — use smoothed fill to avoid false positives
            if (network.getSmoothedFill() < 0.01f) continue;

            if (tickCounter - lastStrikeTick < STRIKE_COOLDOWN) continue;
            lastStrikeTick = tickCounter;

            world.setBlockToAir(pos);
            EntityLightningBolt bolt = new EntityLightningBolt(
                    world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, false);
            world.addWeatherEffect(bolt);

            if (RusticPipes.DEBUG) {
                RusticPipes.LOGGER.debug("[RusticPipes] Lightning struck and destroyed conduit at " + pos);
            }
        }
    }
}
