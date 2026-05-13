package rusticpipes.client;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import rusticpipes.RusticPipes;
import rusticpipes.block.BlockConduit;
import rusticpipes.client.model.ConduitModel;
import rusticpipes.network.ConduitNetwork;
import rusticpipes.network.PipeNetwork;

import java.util.Random;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = RusticPipes.MODID, value = Side.CLIENT)
public class ConduitSparkHandler {

    private static final Random RAND = new Random();

    /** Ticks between spark attempts per conduit block, per tier. */
    private static int sparkIntervalForTier(PipeNetwork.SpeedTier tier) {
        switch (tier) {
            case TURBO:  return 5;
            case FAST:   return 15;
            case NORMAL: return 40;
            default:     return 0; // no sparks when SLOW (no power)
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null || mc.isGamePaused()) return;

        // Iterate loaded conduit blocks near the player
        BlockPos playerPos = mc.player.getPosition();
        int range = 24;

        for (BlockPos pos : BlockPos.getAllInBox(
                playerPos.add(-range, -range, -range),
                playerPos.add( range,  range,  range))) {

            Block block = world.getBlockState(pos).getBlock();
            if (!(block instanceof BlockConduit)) continue;

            ConduitNetwork network = ConduitNetwork.getNetwork(pos);
            if (network == null) continue;

            PipeNetwork.SpeedTier tier = network.getCurrentTier();
            int interval = sparkIntervalForTier(tier);
            if (interval == 0) continue;

            // Probabilistic spark — fires roughly once per interval ticks
            if (RAND.nextInt(interval) != 0) continue;

            // Spawn 1-3 CRIT_MAGIC particles at a random point on the conduit block
            int count = 1 + RAND.nextInt(2);
            for (int i = 0; i < count; i++) {
                double x = pos.getX() + 0.2 + RAND.nextDouble() * 0.6;
                double y = pos.getY() + 0.2 + RAND.nextDouble() * 0.6;
                double z = pos.getZ() + 0.2 + RAND.nextDouble() * 0.6;
                double vx = (RAND.nextDouble() - 0.5) * 0.2;
                double vy = (RAND.nextDouble() - 0.5) * 0.2;
                double vz = (RAND.nextDouble() - 0.5) * 0.2;
                world.spawnParticle(EnumParticleTypes.CRIT_MAGIC, x, y, z, vx, vy, vz);
            }
        }
    }
}
