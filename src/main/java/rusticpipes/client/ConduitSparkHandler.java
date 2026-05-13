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
import rusticpipes.network.ConduitNetwork;

import java.util.Random;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = RusticPipes.MODID, value = Side.CLIENT)
public class ConduitSparkHandler {

    private static final Random RAND = new Random();

    private static final int FE_MIN   = 10;    // sparks start appearing
    private static final int FE_MAX   = 100000; // maximum spark intensity
    private static final int INT_MAX  = 80;    // ticks between sparks at FE_MIN
    private static final int INT_MIN  = 2;     // ticks between sparks at FE_MAX
    private static final int CNT_MIN  = 1;     // particles per spark at FE_MIN
    private static final int CNT_MAX  = 6;     // particles per spark at FE_MAX

    /** Returns the spark interval in ticks for the given FE/t, or 0 if below threshold. */
    private static int sparkInterval(int fePerTick) {
        if (fePerTick < FE_MIN) return 0;
        float t = Math.min(1f, (float)(fePerTick - FE_MIN) / (FE_MAX - FE_MIN));
        return Math.max(INT_MIN, (int)(INT_MAX - t * (INT_MAX - INT_MIN)));
    }

    /** Returns the number of particles per spark event for the given FE/t. */
    private static int sparkCount(int fePerTick) {
        float t = Math.min(1f, (float)(fePerTick - FE_MIN) / (FE_MAX - FE_MIN));
        return CNT_MIN + (int)(t * (CNT_MAX - CNT_MIN));
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

            int fePerTick = network.getLastFePerTick();
            int interval = sparkInterval(fePerTick);
            if (interval == 0) continue;

            // Probabilistic spark — fires roughly once per interval ticks
            if (RAND.nextInt(interval) != 0) continue;

            // Spawn particles scaled to FE/t
            int count = sparkCount(fePerTick);
            for (int i = 0; i < count; i++) {
                double x = pos.getX() + 0.2 + RAND.nextDouble() * 0.6;
                double y = pos.getY() + 0.2 + RAND.nextDouble() * 0.6;
                double z = pos.getZ() + 0.2 + RAND.nextDouble() * 0.6;
                double vx = (RAND.nextDouble() - 0.5) * 0.2;
                double vy = (RAND.nextDouble() - 0.5) * 0.2;
                double vz = (RAND.nextDouble() - 0.5) * 0.2;
                world.spawnParticle(EnumParticleTypes.CRIT, x, y, z, vx, vy, vz);
            }
        }
    }
}
