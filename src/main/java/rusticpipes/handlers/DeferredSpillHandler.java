package rusticpipes.handlers;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import rusticpipes.RusticPipes;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs fluid spills at the end of the world tick instead of inside breakBlock.
 *
 * breakBlock is called from within Chunk.setBlockState after the new (air) state is stored;
 * placing a fluid at the same position from there makes the outer setBlockState return
 * false, so removedByPlayer reports failure and the broken block never drops as an item.
 */
@Mod.EventBusSubscriber(modid = RusticPipes.MODID)
public final class DeferredSpillHandler {

    private DeferredSpillHandler() {}

    private static final class Spill {
        final World world;
        final BlockPos pos;
        final FluidStack fluid;
        final int maxBlocks;

        Spill(World world, BlockPos pos, FluidStack fluid, int maxBlocks) {
            this.world = world;
            this.pos = pos;
            this.fluid = fluid;
            this.maxBlocks = maxBlocks;
        }
    }

    private static final List<Spill> PENDING = new ArrayList<>();

    public static void schedule(World world, BlockPos pos, FluidStack fluid, int maxBlocks) {
        if (world.isRemote || fluid == null || fluid.amount < 1000) return;
        PENDING.add(new Spill(world, pos, fluid.copy(), maxBlocks));
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.world.isRemote || PENDING.isEmpty()) return;
        // Copy first: spilling can break further blocks and schedule more spills
        List<Spill> due = new ArrayList<>();
        PENDING.removeIf(s -> s.world == event.world && due.add(s));
        for (Spill s : due) {
            rusticpipes.block.BlockFluidPipe.spillFluidNow(s.world, s.pos, s.fluid, s.maxBlocks);
        }
    }

    @SubscribeEvent
    public static void onWorldUnload(WorldEvent.Unload event) {
        PENDING.removeIf(s -> s.world == event.getWorld());
    }
}
