package rusticpipes.network;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import rusticpipes.block.BlockFluidPipe;
import rusticpipes.block.PipeColor;
import rusticpipes.handlers.ForgeConfigHandler;
import rusticpipes.tileentity.FaceMode;
import rusticpipes.tileentity.TileEntityFluidPipe;
import rusticpipes.util.DimPos;

import java.util.*;

/**
 * Single-tier fluid pipe network — buffered three-phase transfer.
 *
 * Each pipe has a real fluid buffer. Fluid moves in three phases per tick:
 *   1. SOURCE FILL  — source tanks push into adjacent pipe buffers.
 *   2. PROPAGATION  — pipes above threshold push to adjacent less-full pipes.
 *   3. SINK DRAIN   — destination-adjacent pipes drain into output tanks.
 */
public class FluidNetwork {

    /** How full a pipe must be (0–1) before it pushes to the next pipe. */
    private static final float PUSH_THRESHOLD = 0.05f;
    /** How many propagation passes to run per tick — more = faster wave. */
    private static final int PROPAGATION_PASSES = 3;

    // Keyed by DimPos to prevent cross-dimension network collisions.
    private static final Map<DimPos, FluidNetwork> NETWORKS = new HashMap<>();

    private final Set<BlockPos> members = new HashSet<>();
    private int rrPointer = 0;
    // Cached master pos — the member with the smallest toLong() value.
    private BlockPos cachedMasterPos = null;

    // -----------------------------------------------------------------------
    // Static accessors
    // -----------------------------------------------------------------------

    public static FluidNetwork getNetwork(World world, BlockPos pos) {
        return NETWORKS.get(DimPos.of(world, pos));
    }

    /**
     * Removes all networks belonging to the given dimension.
     * Called on WorldEvent.Unload to prevent memory leaks across world reloads.
     */
    public static void clearDimension(int dimId) {
        NETWORKS.entrySet().removeIf(e -> e.getKey().dimId == dimId);
    }

    // -----------------------------------------------------------------------
    // Network management
    // -----------------------------------------------------------------------

    public static void onPipeAdded(World world, BlockPos pos) {
        Block addedBlock = world.getBlockState(pos).getBlock();
        PipeColor addedColor = (addedBlock instanceof BlockFluidPipe)
                ? ((BlockFluidPipe) addedBlock).pipeColor : null;
        int dimId = world.provider.getDimension();

        Set<FluidNetwork> neighbours = new HashSet<>();
        for (EnumFacing face : EnumFacing.VALUES) {
            BlockPos neighbourPos = pos.offset(face);
            if (addedColor != null) {
                Block neighbourBlock = world.getBlockState(neighbourPos).getBlock();
                if (!(neighbourBlock instanceof BlockFluidPipe)) continue;
                if (((BlockFluidPipe) neighbourBlock).pipeColor != addedColor) continue;
            }
            FluidNetwork n = NETWORKS.get(new DimPos(dimId, neighbourPos));
            if (n != null) neighbours.add(n);
        }

        if (neighbours.isEmpty()) {
            FluidNetwork network = new FluidNetwork();
            network.members.add(pos);
            network.recomputeMaster();
            NETWORKS.put(new DimPos(dimId, pos), network);
        } else if (neighbours.size() == 1) {
            FluidNetwork network = neighbours.iterator().next();
            network.members.add(pos);
            NETWORKS.put(new DimPos(dimId, pos), network);
            network.recomputeMaster();
        } else {
            FluidNetwork kept = neighbours.iterator().next();
            for (FluidNetwork other : neighbours) {
                if (other == kept) continue;
                for (BlockPos memberPos : other.members) {
                    kept.members.add(memberPos);
                    NETWORKS.put(new DimPos(dimId, memberPos), kept);
                }
            }
            kept.members.add(pos);
            NETWORKS.put(new DimPos(dimId, pos), kept);
            kept.recomputeMaster();
        }
    }

    public static void onPipeRemoved(World world, BlockPos pos) {
        int dimId = world.provider.getDimension();
        DimPos key = new DimPos(dimId, pos);
        FluidNetwork network = NETWORKS.get(key);
        if (network == null) return;

        NETWORKS.remove(key);
        network.members.remove(pos);
        if (network.members.isEmpty()) return;

        // Early-exit: fewer than 2 in-network neighbours means no split is possible.
        int networkNeighbours = 0;
        for (EnumFacing face : EnumFacing.VALUES) {
            if (network.members.contains(pos.offset(face))) {
                if (++networkNeighbours >= 2) break;
            }
        }
        if (networkNeighbours < 2) {
            network.recomputeMaster();
            return;
        }

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        BlockPos seed = network.members.iterator().next();
        queue.add(seed);
        visited.add(seed);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos np = current.offset(face);
                if (network.members.contains(np) && visited.add(np)) queue.add(np);
            }
        }

        if (visited.size() == network.members.size()) {
            network.recomputeMaster();
            return;
        }

        Set<BlockPos> remaining = new HashSet<>(network.members);
        remaining.removeAll(visited);

        network.members.clear();
        network.members.addAll(visited);
        network.recomputeMaster();
        for (BlockPos memberPos : visited) NETWORKS.put(new DimPos(dimId, memberPos), network);

        FluidNetwork newNetwork = new FluidNetwork();
        for (BlockPos memberPos : remaining) {
            newNetwork.members.add(memberPos);
            NETWORKS.put(new DimPos(dimId, memberPos), newNetwork);
        }
        newNetwork.recomputeMaster();
    }

    // -----------------------------------------------------------------------
    // Transfer — three-phase buffered
    // -----------------------------------------------------------------------

    public void transferFluids(World world) {
        int maxRate = ForgeConfigHandler.fluid.flowRatePerTick;

        Map<BlockPos, TileEntityFluidPipe> pipes = new LinkedHashMap<>();
        for (BlockPos pos : members) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityFluidPipe) pipes.put(pos, (TileEntityFluidPipe) te);
        }
        if (pipes.isEmpty()) return;

        // ------------------------------------------------------------------
        // Phase 1: SOURCE FILL
        // ------------------------------------------------------------------
        for (Map.Entry<BlockPos, TileEntityFluidPipe> entry : pipes.entrySet()) {
            BlockPos pipePos = entry.getKey();
            TileEntityFluidPipe pipe = entry.getValue();

            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos neighborPos = pipePos.offset(face);
                if (members.contains(neighborPos)) continue;
                if (pipe.getFaceMode(face) != FaceMode.INPUT) continue;

                TileEntity nte = world.getTileEntity(neighborPos);
                if (nte == null) continue;
                IFluidHandler source = nte.getCapability(
                        CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite());
                if (source == null) continue;

                int space = pipe.getBufferSpace();
                if (space <= 0) continue;

                FluidStack current = pipe.getBuffer();
                FluidStack candidate = source.drain(space, false);
                if (candidate == null || candidate.amount <= 0) continue;
                if (current != null && !current.isFluidEqual(candidate)) continue;

                FluidStack drained = source.drain(candidate.amount, true);
                if (drained != null && drained.amount > 0) pipe.addToBuffer(drained);
            }
        }

        // ------------------------------------------------------------------
        // Phase 2: PROPAGATION — BFS from OUTPUT-adjacent pipes (destination side)
        // ------------------------------------------------------------------
        Map<BlockPos, Integer> dist = new HashMap<>();
        Queue<BlockPos> bfsQ = new ArrayDeque<>();
        for (Map.Entry<BlockPos, TileEntityFluidPipe> e : pipes.entrySet()) {
            BlockPos pipePos = e.getKey();
            TileEntityFluidPipe pipe = e.getValue();
            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos nbPos = pipePos.offset(face);
                if (members.contains(nbPos)) continue;
                if (pipe.getFaceMode(face) != FaceMode.OUTPUT) continue;
                TileEntity nte = world.getTileEntity(nbPos);
                if (nte == null) continue;
                boolean isFluidHandler = nte.hasCapability(
                        CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite());
                boolean isUnformedTank = nte instanceof rusticpipes.tileentity.TileEntityFluidTankMultiblock;
                if (!isFluidHandler && !isUnformedTank) continue;
                if (!dist.containsKey(pipePos)) {
                    dist.put(pipePos, 0);
                    bfsQ.add(pipePos);
                }
            }
        }
        while (!bfsQ.isEmpty()) {
            BlockPos cur = bfsQ.poll();
            int d = dist.get(cur);
            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos nb = cur.offset(face);
                if (members.contains(nb) && !dist.containsKey(nb)) {
                    dist.put(nb, d + 1);
                    bfsQ.add(nb);
                }
            }
        }

        boolean hasDestination = !dist.isEmpty();

        for (int pass = 0; pass < PROPAGATION_PASSES; pass++) {
            Map<BlockPos, Float> fillSnapshot = new HashMap<>();
            for (Map.Entry<BlockPos, TileEntityFluidPipe> entry : pipes.entrySet()) {
                fillSnapshot.put(entry.getKey(), entry.getValue().getFillFraction());
            }

            for (Map.Entry<BlockPos, TileEntityFluidPipe> entry : pipes.entrySet()) {
                BlockPos pipePos = entry.getKey();
                TileEntityFluidPipe pipe = entry.getValue();

                float myFill = fillSnapshot.get(pipePos);
                if (myFill < PUSH_THRESHOLD) continue;
                if (pipe.getBuffer() == null || pipe.getBuffer().amount <= 0) continue;

                int myDist = dist.getOrDefault(pipePos, Integer.MAX_VALUE);

                for (EnumFacing face : EnumFacing.VALUES) {
                    BlockPos neighborPos = pipePos.offset(face);
                    if (!members.contains(neighborPos)) continue;

                    TileEntityFluidPipe neighbor = pipes.get(neighborPos);
                    if (neighbor == null) continue;

                    if (pipe.getBuffer() == null || pipe.getBuffer().amount <= 0) break;

                    int neighborDist = dist.getOrDefault(neighborPos, Integer.MAX_VALUE);
                    float neighborFill = fillSnapshot.getOrDefault(neighborPos, 0f);

                    if (hasDestination) {
                        if (neighborDist == myDist) continue;
                        if (neighborFill >= myFill) continue;
                    } else {
                        if (neighborFill >= myFill) continue;
                    }

                    FluidStack neighborBuf = neighbor.getBuffer();
                    if (neighborBuf != null && !neighborBuf.isFluidEqual(pipe.getBuffer())) continue;

                    int space  = neighbor.getBufferSpace();
                    int toPush = Math.min(maxRate, Math.min(pipe.getBuffer().amount, space));
                    if (toPush <= 0) continue;

                    FluidStack pushing = pipe.getBuffer().copy();
                    pushing.amount = toPush;
                    pipe.drainBuffer(toPush);
                    neighbor.addToBuffer(pushing);
                }
            }
        }

        // ------------------------------------------------------------------
        // Phase 3: SINK DRAIN
        // ------------------------------------------------------------------
        for (Map.Entry<BlockPos, TileEntityFluidPipe> entry : pipes.entrySet()) {
            BlockPos pipePos = entry.getKey();
            TileEntityFluidPipe pipe = entry.getValue();
            if (pipe.getBuffer() == null || pipe.getBuffer().amount <= 0) continue;

            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos neighborPos = pipePos.offset(face);
                if (members.contains(neighborPos)) continue;
                if (pipe.getFaceMode(face) != FaceMode.OUTPUT) continue;

                TileEntity nte = world.getTileEntity(neighborPos);
                if (nte == null) continue;
                IFluidHandler dest = nte.getCapability(
                        CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite());
                if (dest == null) continue;

                FluidStack toInsert = pipe.getBuffer().copy();
                toInsert.amount = Math.min(toInsert.amount, maxRate);

                int accepted = dest.fill(toInsert, false);
                if (accepted <= 0) continue;

                toInsert.amount = accepted;
                dest.fill(toInsert.copy(), true);
                pipe.drainBuffer(accepted);
            }
        }

        for (TileEntityFluidPipe pipe : pipes.values()) {
            pipe.syncToClient();
        }
    }

    public Set<BlockPos> getMembers() { return Collections.unmodifiableSet(members); }

    private void recomputeMaster() {
        cachedMasterPos = null;
        for (BlockPos p : members) {
            if (cachedMasterPos == null || p.toLong() < cachedMasterPos.toLong())
                cachedMasterPos = p;
        }
    }

    public BlockPos getMasterPos() { return cachedMasterPos; }
}
