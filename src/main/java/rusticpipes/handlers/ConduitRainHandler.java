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
import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = RusticPipes.MODID)
public class ConduitRainHandler {

    private static final int CHECK_INTERVAL = 100;
    private static final int STRIKE_COOLDOWN = 200;
    /** Last strike time per dimension — WorldTickEvent fires once per loaded world. */
    private static final Map<Integer, Long> lastStrikeTime = new HashMap<>();

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.world.isRemote) return;

        World world = event.world;
        // Per-world clock: a shared static counter advanced once per loaded dimension
        long now = world.getTotalWorldTime();
        if (now % CHECK_INTERVAL != 0) return;

        if (!world.isRaining()) return;
        if (!ForgeConfigHandler.conduit.enableRainDamage) return;

        int dim = world.provider.getDimension();
        for (TileEntity te : new ArrayList<>(world.loadedTileEntityList)) {
            if (!(te instanceof rusticpipes.tileentity.TileEntityConduit)) continue;

            BlockPos pos = te.getPos();
            // isRainingAt also checks sky access, the precipitation height and the biome,
            // so conduits in deserts, savannas and snowy biomes are left alone.
            if (!world.isRainingAt(pos.up())) continue;

            ConduitNetwork network = ConduitNetwork.getNetwork(world, pos);
            if (network == null) continue;
            // Same powered test as the texture: power flowing or stored, not buffer fill
            if (!network.isPowered()) continue;

            Long last = lastStrikeTime.get(dim);
            if (last != null && now - last < STRIKE_COOLDOWN) continue;
            lastStrikeTime.put(dim, now);

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
