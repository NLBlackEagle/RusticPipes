package rusticpipes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import rusticpipes.RusticPipes;
import rusticpipes.handlers.ForgeConfigHandler;
import rusticpipes.network.ConduitNetwork;
import rusticpipes.tileentity.TileEntityConduit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@SideOnly(Side.CLIENT)
@Mod.EventBusSubscriber(modid = RusticPipes.MODID, value = Side.CLIENT)
public class ConduitSparkHandler {

    private static final Random RAND = new Random();

    // Interval bounds (ticks between scheduled events)
    private static final int INTERVAL_MAX = 80;
    private static final int INTERVAL_MIN = 5;

    // Particle count bounds
    private static final int COUNT_MIN = 1;
    private static final int COUNT_MAX = 2;

    // Rarity bounds — max capped at 40%
    private static final double RARITY_MIN = 0.10;
    private static final double RARITY_MAX = 0.25; // max 25% chance at full

    /** Next spark fire tick per conduit position. */
    private static final Map<BlockPos, Integer> nextFireTick = new HashMap<>();

    /** Conduit positions found in range during the current tick, reused to avoid allocation. */
    private static final Set<BlockPos> seenThisTick = new HashSet<>();

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

        // Iterate loaded conduit tile entities instead of scanning every block in range
        seenThisTick.clear();
        for (TileEntity te : world.loadedTileEntityList) {
            if (!(te instanceof TileEntityConduit) || te.isInvalid()) continue;

            BlockPos pos = te.getPos();
            if (Math.abs(pos.getX() - playerPos.getX()) > range
                    || Math.abs(pos.getY() - playerPos.getY()) > range
                    || Math.abs(pos.getZ() - playerPos.getZ()) > range) continue;

            seenThisTick.add(pos);

            ConduitNetwork network = ConduitNetwork.getNetwork(world, pos);
            if (network == null) {
                nextFireTick.remove(pos);
                continue;
            }

            float fill = network.getSmoothedFill();
            if (fill <= 0.01f) {
                nextFireTick.remove(pos);
                continue;
            }

            int fireTick = nextFireTick.computeIfAbsent(pos, k -> clientTick + nextInterval(fill));
            if (clientTick < fireTick) continue;

            nextFireTick.put(pos, clientTick + nextInterval(fill));

            double rarity = lerp(RARITY_MIN, RARITY_MAX, fill)
                    * ForgeConfigHandler.conduit.sparkRarityMultiplier;
            if (RAND.nextDouble() >= rarity) continue;

            int count = (int) Math.round(lerp(COUNT_MIN, COUNT_MAX, fill));
            for (int i = 0; i < count; i++) {
                double x = pos.getX() + 0.2 + RAND.nextDouble() * 0.6;
                double y = pos.getY() + 0.2 + RAND.nextDouble() * 0.6;
                double z = pos.getZ() + 0.2 + RAND.nextDouble() * 0.6;
                // Randomly mix red and near-white for an electrical spark effect
                if (RAND.nextBoolean()) {
                    world.spawnParticle(EnumParticleTypes.REDSTONE, x, y, z, 1.0, 0.1, 0.1);   // red
                } else {
                    world.spawnParticle(EnumParticleTypes.REDSTONE, x, y, z, 1.0, 0.9, 0.85);  // near-white
                }
            }
        }

        // Drop timers for conduits that are out of range, broken or in another world
        nextFireTick.keySet().retainAll(seenThisTick);
    }

    private static int nextInterval(float fill) {
        int base = (int) Math.round(lerp(INTERVAL_MAX, INTERVAL_MIN, fill));
        int jitter = (int) (base * 0.2f);
        if (jitter < 1) jitter = 1;
        return Math.max(1, base + RAND.nextInt(jitter * 2 + 1) - jitter);
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }
}
