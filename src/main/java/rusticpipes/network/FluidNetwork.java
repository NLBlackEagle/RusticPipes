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

import java.util.*;

/**
 * Single-tier fluid pipe network — Option B buffered transfer.
 *
 * Each pipe has a real fluid buffer. Fluid moves in three phases per tick:
 *   1. SOURCE FILL  — source tanks push into adjacent pipe buffers.
 *   2. PROPAGATION  — pipes above threshold push to adjacent less-full pipes.
 *   3. SINK DRAIN   — destination-adjacent pipes drain into output tanks.
 *
 * This creates a natural staircase fill effect: source-adjacent pipes fill
 * first, then the wave propagates outward hop by hop.
 */
public class FluidNetwork {

    /** How full a pipe must be (0–1) before it pushes to the next pipe. */
    private static final float PUSH_THRESHOLD = 0.2f;
    /** How many propagation passes to run per tick — more = faster wave. */
    private static final int PROPAGATION_PASSES = 3;

    private static final Map<BlockPos, FluidNetwork> NETWORKS = new HashMap<>();

    private final Set<BlockPos> members = new HashSet<>();
    private int rrPointer = 0;

    // -----------------------------------------------------------------------
    // Static accessors
    // -----------------------------------------------------------------------

    public static FluidNetwork getNetwork(BlockPos pos)              { return NETWORKS.get(pos); }
    public static FluidNetwork getNetwork(World world, BlockPos pos) { return NETWORKS.get(pos); }

    // -----------------------------------------------------------------------
    // Network management
    // -----------------------------------------------------------------------

    public static void onPipeAdded(World world, BlockPos pos) {
        Block addedBlock = world.getBlockState(pos).getBlock();
        PipeColor addedColor = (addedBlock instanceof BlockFluidPipe)
                ? ((BlockFluidPipe) addedBlock).pipeColor : null;

        Set<FluidNetwork> neighbours = new HashSet<>();
        for (EnumFacing face : EnumFacing.VALUES) {
            BlockPos neighbourPos = pos.offset(face);
            if (addedColor != null) {
                Block neighbourBlock = world.getBlockState(neighbourPos).getBlock();
                if (!(neighbourBlock instanceof BlockFluidPipe)) continue;
                if (((BlockFluidPipe) neighbourBlock).pipeColor != addedColor) continue;
            }
            FluidNetwork n = NETWORKS.get(neighbourPos);
            if (n != null) neighbours.add(n);
        }

        if (neighbours.isEmpty()) {
            FluidNetwork network = new FluidNetwork();
            network.members.add(pos);
            NETWORKS.put(pos, network);
        } else if (neighbours.size() == 1) {
            FluidNetwork network = neighbours.iterator().next();
            network.members.add(pos);
            NETWORKS.put(pos, network);
        } else {
            FluidNetwork kept = neighbours.iterator().next();
            for (FluidNetwork other : neighbours) {
                if (other == kept) continue;
                for (BlockPos memberPos : other.members) {
                    kept.members.add(memberPos);
                    NETWORKS.put(memberPos, kept);
                }
            }
            kept.members.add(pos);
            NETWORKS.put(pos, kept);
        }
    }

    public static void onPipeRemoved(World world, BlockPos pos) {
        FluidNetwork network = NETWORKS.get(pos);
        if (network == null) return;

        NETWORKS.remove(pos);
        network.members.remove(pos);
        if (network.members.isEmpty()) return;

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        BlockPos seed = network.members.iterator().next();
        queue.add(seed);
        visited.add(seed);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos np = current.offset(face);
                if (network.members.contains(np) && !visited.contains(np)) {
                    visited.add(np);
                    queue.add(np);
                }
            }
        }

        if (visited.size() == network.members.size()) return;

        Set<BlockPos> remaining = new HashSet<>(network.members);
        remaining.removeAll(visited);

        network.members.clear();
        network.members.addAll(visited);
        for (BlockPos memberPos : visited) NETWORKS.put(memberPos, network);

        FluidNetwork newNetwork = new FluidNetwork();
        for (BlockPos memberPos : remaining) {
            newNetwork.members.add(memberPos);
            NETWORKS.put(memberPos, newNetwork);
        }
    }

    // -----------------------------------------------------------------------
    // Transfer — three-phase buffered
    // -----------------------------------------------------------------------

    public void transferFluids(World world) {
        int maxRate = ForgeConfigHandler.fluid.flowRatePerTick;

        // Collect all pipe TEs once
        Map<BlockPos, TileEntityFluidPipe> pipes = new LinkedHashMap<>();
        for (BlockPos pos : members) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityFluidPipe) pipes.put(pos, (TileEntityFluidPipe) te);
        }
        if (pipes.isEmpty()) return;

        // ------------------------------------------------------------------
        // Phase 1: SOURCE FILL — auto-detect sources by checking which
        // adjacent handlers actually have fluid to give (ignores face mode)
        // ------------------------------------------------------------------
        for (Map.Entry<BlockPos, TileEntityFluidPipe> entry : pipes.entrySet()) {
            BlockPos pipePos  = entry.getKey();
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
                FluidStack candidate = source.drain(Math.min(space, maxRate), false);
                if (candidate == null || candidate.amount <= 0) continue;
                if (current != null && !current.isFluidEqual(candidate)) continue;

                FluidStack drained = source.drain(candidate.amount, true);
                if (drained != null && drained.amount > 0) {
                    pipe.addToBuffer(drained);
                }
            }
        }

        // ------------------------------------------------------------------
        // Phase 2: PROPAGATION — BFS from OUTPUT-adjacent pipes (destination
        // side). Distance 0 = nearest to destination. Fluid only flows toward
        // lower distance (toward destination), preventing oscillation.
        // ------------------------------------------------------------------

        // BFS from destination-adjacent pipes (OUTPUT face = dest side, dist 0).
        // Fluid propagates from high dist (source) toward low dist (destination).
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
                if (!nte.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite())) continue;
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

        // Propagate: push from low dist (source) to high dist (destination)
        // Source pipes (dist 0) are fullest — staircase steps down toward destination
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

                    // Only push toward destination (lower dist)
                    int neighborDist = dist.getOrDefault(neighborPos, Integer.MAX_VALUE);
                    if (neighborDist >= myDist) continue;

                    // Don't push into a pipe that is equally or more full — prevents
                    // backpressure reversal when destination tank is saturated
                    float neighborFill = fillSnapshot.getOrDefault(neighborPos, 0f);
                    if (neighborFill >= myFill) continue;

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
        // Phase 3: SINK DRAIN — auto-detect destinations by checking which
        // adjacent handlers have space to accept fluid
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

        // Sync all pipes to client
        for (TileEntityFluidPipe pipe : pipes.values()) {
            pipe.syncToClient();
        }
    }

    public Set<BlockPos> getMembers() { return Collections.unmodifiableSet(members); }
}
