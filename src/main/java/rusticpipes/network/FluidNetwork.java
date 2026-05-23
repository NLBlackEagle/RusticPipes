package rusticpipes.network;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import rusticpipes.block.BlockFluidPipe;
import rusticpipes.block.PipeColor;
import rusticpipes.handlers.ForgeConfigHandler;
import rusticpipes.tileentity.FaceMode;
import rusticpipes.tileentity.TileEntityFluidPipe;

import java.util.*;

/**
 * Single-tier fluid pipe network.
 * One network per connected set of same-color fluid pipes.
 * No motor required — fluid just flows at the configured rate.
 */
public class FluidNetwork {

    private static final Map<BlockPos, FluidNetwork> NETWORKS = new HashMap<>();

    private final Set<BlockPos> members = new HashSet<>();
    private int rrPointer = 0;

    // -----------------------------------------------------------------------
    // Static accessors
    // -----------------------------------------------------------------------

    public static FluidNetwork getNetwork(BlockPos pos) { return NETWORKS.get(pos); }
    public static FluidNetwork getNetwork(World world, BlockPos pos) { return NETWORKS.get(pos); }

    // -----------------------------------------------------------------------
    // Network management — mirrors PipeNetwork logic
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

        // Flood-fill to detect splits
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
    // Transfer
    // -----------------------------------------------------------------------

    public void transferFluids(World world) {
        List<BlockPos> inputs  = new ArrayList<>();
        List<BlockPos> outputs = new ArrayList<>();

        for (BlockPos memberPos : members) {
            TileEntity te = world.getTileEntity(memberPos);
            if (!(te instanceof TileEntityFluidPipe)) continue;
            TileEntityFluidPipe pipe = (TileEntityFluidPipe) te;

            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos neighbourPos = memberPos.offset(face);
                if (world.getBlockState(neighbourPos).getBlock() instanceof BlockFluidPipe) continue;
                TileEntity nte = world.getTileEntity(neighbourPos);
                if (nte == null) continue;
                if (!nte.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite())) continue;

                FaceMode mode = pipe.getFaceMode(face);
                if (mode == FaceMode.INPUT) inputs.add(neighbourPos);
                else outputs.add(neighbourPos);
            }
        }

        if (inputs.isEmpty() || outputs.isEmpty()) return;

        int maxTransfer = ForgeConfigHandler.fluid.flowRatePerTick;

        for (BlockPos inputPos : inputs) {
            TileEntity sourceTe = world.getTileEntity(inputPos);
            if (sourceTe == null) continue;

            // Find which face this input is connected from
            IFluidHandler source = null;
            EnumFacing sourceFace = null;
            for (BlockPos memberPos : members) {
                for (EnumFacing face : EnumFacing.VALUES) {
                    if (memberPos.offset(face).equals(inputPos)) {
                        IFluidHandler fh = sourceTe.getCapability(
                                CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite());
                        if (fh != null) { source = fh; sourceFace = face; break; }
                    }
                }
                if (source != null) break;
            }
            if (source == null) continue;

            // Simulate drain
            FluidStack drained = source.drain(maxTransfer, false);
            if (drained == null || drained.amount <= 0) continue;

            int remaining = drained.amount;

            for (int attempt = 0; attempt < outputs.size() && remaining > 0; attempt++) {
                BlockPos destPos = outputs.get(rrPointer % outputs.size());
                rrPointer++;

                TileEntity destTe = world.getTileEntity(destPos);
                if (destTe == null) continue;

                IFluidHandler dest = null;
                for (BlockPos memberPos : members) {
                    for (EnumFacing face : EnumFacing.VALUES) {
                        if (memberPos.offset(face).equals(destPos)) {
                            IFluidHandler fh = destTe.getCapability(
                                    CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite());
                            if (fh != null) { dest = fh; break; }
                        }
                    }
                    if (dest != null) break;
                }
                if (dest == null) continue;

                FluidStack toInsert = drained.copy();
                toInsert.amount = remaining;

                int accepted = dest.fill(toInsert, false);
                if (accepted <= 0) continue;

                FluidStack actuallyDrained = source.drain(accepted, true);
                if (actuallyDrained == null || actuallyDrained.amount <= 0) continue;

                FluidStack actualInsert = actuallyDrained.copy();
                dest.fill(actualInsert, true);
                remaining -= actuallyDrained.amount;

                // Notify all pipe TEs in the network that fluid passed through
                for (BlockPos memberPos : members) {
                    net.minecraft.tileentity.TileEntity mte = world.getTileEntity(memberPos);
                    if (mte instanceof TileEntityFluidPipe) {
                        ((TileEntityFluidPipe) mte).onFluidPassed(actuallyDrained);
                    }
                }
            }
        }
    }

    public Set<BlockPos> getMembers() { return Collections.unmodifiableSet(members); }
}
