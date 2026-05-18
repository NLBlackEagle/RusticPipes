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
import rusticpipes.handlers.ForgeConfigHandler;
import rusticpipes.network.ConduitNetwork;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Spark particle handler.
 *
 * Spark behaviour scales continuously with buffer fill fraction (0–1):
 *
 *   Interval: lerp(MAX_INTERVAL, MIN_INTERVAL, fill)
 *             At 1 FE (fill ≈ 0): fires rarely  (~80 ticks apart)
 *             At full (fill = 1): fires often    (~5 ticks apart)
 *
 *   Count:    lerp(MIN_COUNT, MAX_COUNT, fill)
 *             At empty: 1 particle per event
 *             At full:  6 particles per event
 *
 *   Rarity:   lerp(MIN_RARITY, MAX_RARITY, fill)
 *             At empty: 10% chance the event actually fires
 *             At full:  100% chance
 *
 * Each conduit has its own independent next-fire tick so they
 * don't all spark in unison.
 */
@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = RusticPipes.MODID, value = Side.CLIENT)
public class ConduitSparkHandler {

    private static final Random RAND = new Random();

    // Interval bounds (ticks between scheduled events)
    private static final int INTERVAL_MAX = 80;  // near-empty
    private static final int INTERVAL_MIN = 5;   // full

    // Particle count bounds
    private static final int COUNT_MIN = 1;
    private static final int COUNT_MAX = 2;

    // Rarity (chance event actually fires) bounds
    private static final double RARITY_MIN = 0.10;
    private static final double RARITY_MAX = 1.00;

    /** Next fire tick per conduit position — each conduit independent. */
    private static final Map<BlockPos, Integer> nextFireTick = new HashMap<>();

    private static int clientTick = 0;

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        World world = mc.world;
        if (world == null || mc.isGamePaused()) return;
        if (!ForgeConfigHandler.conduit.enableSparks) return;

        clientTick++;

        int range = 24;
        BlockPos playerPos = mc.player.getPosition();

        for (BlockPos pos : BlockPos.getAllInBox(
                playerPos.add(-range, -range, -range),
                playerPos.add( range,  range,  range))) {

            Block block = world.getBlockState(pos).getBlock();
            if (!(block instanceof BlockConduit)) continue;

            ConduitNetwork network = ConduitNetwork.getNetwork(pos);
            if (network == null || network.getBufferStored() <= 0) {
                nextFireTick.remove(pos);
                continue;
            }

            // fill: 0.0 = 1 FE stored, 1.0 = buffer completely full
            float fill = Math.min(1f, (float) network.getBufferStored() / network.getBufferCapacity());

            // Schedule initial fire tick lazily
            int fireTick = nextFireTick.computeIfAbsent(pos, k -> clientTick + nextInterval(fill));

            if (clientTick < fireTick) continue;

            // Always reschedule next event based on current fill
            nextFireTick.put(pos, clientTick + nextInterval(fill));

            // Rarity check — scales from 10% at empty to 100% at full
            double rarity = lerp(RARITY_MIN, RARITY_MAX, fill);
            if (RAND.nextDouble() >= rarity) continue;

            // Particle count — scales from 1 at empty to 6 at full
            int count = (int) Math.round(lerp(COUNT_MIN, COUNT_MAX, fill));

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

    /** Random interval in ticks, lerped between MAX and MIN based on fill. */
    private static int nextInterval(float fill) {
        int base = (int) Math.round(lerp(INTERVAL_MAX, INTERVAL_MIN, fill));
        // ±20% jitter so conduits drift out of sync naturally
        int jitter = (int) (base * 0.2f);
        if (jitter < 1) jitter = 1;
        return Math.max(1, base + RAND.nextInt(jitter * 2 + 1) - jitter);
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }
}
