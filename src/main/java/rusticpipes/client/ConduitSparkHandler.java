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

/**
 * Spark particle handler.
 *
 * Spark intensity is driven by the buffer fill fraction — a nearly-full buffer
 * produces constant rapid sparks; an empty buffer produces none.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = RusticPipes.MODID, value = Side.CLIENT)
public class ConduitSparkHandler {

    private static final Random RAND = new Random();

    // Spark intensity parameters
    private static final int INT_MAX  = 80; // ticks between sparks at 0% fill
    private static final int INT_MIN  = 2;  // ticks between sparks at 100% fill
    private static final int CNT_MIN  = 1;  // particles per event at 0% fill
    private static final int CNT_MAX  = 6;  // particles per event at 100% fill

    /** Returns spark interval in ticks for fill fraction [0,1], 0 = no sparks. */
    private static int sparkInterval(float fill) {
        if (fill <= 0.01f) return 0;
        return Math.max(INT_MIN, (int)(INT_MAX - fill * (INT_MAX - INT_MIN)));
    }

    private static int sparkCount(float fill) {
        return CNT_MIN + (int)(fill * (CNT_MAX - CNT_MIN));
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null || mc.isGamePaused()) return;

        BlockPos playerPos = mc.player.getPosition();
        int range = 24;

        for (BlockPos pos : BlockPos.getAllInBox(
                playerPos.add(-range, -range, -range),
                playerPos.add( range,  range,  range))) {

            Block block = world.getBlockState(pos).getBlock();
            if (!(block instanceof BlockConduit)) continue;

            ConduitNetwork network = ConduitNetwork.getNetwork(pos);
            if (network == null) continue;

            int stored   = network.getBufferStored();
            int capacity = network.getBufferCapacity();
            if (capacity <= 0 || stored <= 0) continue;

            float fill = Math.min(1f, (float) stored / capacity);
            int interval = sparkInterval(fill);
            if (interval == 0) continue;
            if (RAND.nextInt(interval) != 0) continue;

            int count = sparkCount(fill);
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
