package rusticpipes.network;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import rusticpipes.block.BlockConduit;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.handlers.ForgeConfigHandler;
import rusticpipes.tileentity.TileEntityConduit;

import java.util.*;

/**
 * Shared-buffer conduit network.
 *
 * The entire network is one big EnergyStorage. Every conduit TE exposes
 * this same storage object via getCapability(ENERGY, side).
 *
 * Generators push into any conduit → fills the shared buffer.
 * Machines pull from any conduit → drains the shared buffer.
 *
 * Tier is computed on demand from buffer fill % and pushed to adjacent
 * pipe networks each tick. Conduit TEs that border a pipe network also
 * receive a cachedTier update so their flanges render correctly — all
 * other conduit TEs are left alone.
 */
public class ConduitNetwork {

    private static final Map<BlockPos, ConduitNetwork> NETWORKS = new HashMap<>();

    private final Set<BlockPos> members = new HashSet<>();

    /** The single shared energy buffer for this whole network. */
    private EnergyStorage sharedBuffer;

    /** FE delta observed last tick — used for spark effects. */
    private int lastFePerTick = 0;

    /** Snapshot of buffer fill at start of tick — to compute delta. */
    private int bufferAtTickStart = 0;

    public ConduitNetwork() {
        int maxIo = ForgeConfigHandler.conduit.maxFePerTickPerFace;
        sharedBuffer = new EnergyStorage(ForgeConfigHandler.conduit.networkBufferPerConduit, maxIo, maxIo);
    }

    /** Resize the shared buffer to match current member count, preserving stored FE. */
    private void rebuildBuffer() {
        int cap    = ForgeConfigHandler.conduit.networkBufferPerConduit * Math.max(1, members.size());
        int maxIo  = ForgeConfigHandler.conduit.maxFePerTickPerFace;
        int stored = Math.min(sharedBuffer.getEnergyStored(), cap);
        sharedBuffer = new EnergyStorage(cap, maxIo, maxIo);
        sharedBuffer.receiveEnergy(stored, false);
    }

    /** The shared IEnergyStorage every conduit TE in this network exposes. */
    public IEnergyStorage getSharedStorage() {
        return sharedBuffer;
    }

    // -----------------------------------------------------------------------
    // Static network management
    // -----------------------------------------------------------------------

    public static ConduitNetwork getNetwork(BlockPos pos) {
        return NETWORKS.get(pos);
    }

    public static void onConduitAdded(World world, BlockPos pos) {
        Set<ConduitNetwork> neighbours = new HashSet<>();
        for (EnumFacing face : EnumFacing.VALUES) {
            ConduitNetwork n = NETWORKS.get(pos.offset(face));
            if (n != null) neighbours.add(n);
        }

        if (neighbours.isEmpty()) {
            ConduitNetwork network = new ConduitNetwork();
            network.members.add(pos);
            NETWORKS.put(pos, network);
        } else if (neighbours.size() == 1) {
            ConduitNetwork network = neighbours.iterator().next();
            network.members.add(pos);
            NETWORKS.put(pos, network);
            network.rebuildBuffer();
        } else {
            ConduitNetwork kept = neighbours.iterator().next();
            int mergedFe = kept.sharedBuffer.getEnergyStored();
            for (ConduitNetwork other : neighbours) {
                if (other == kept) continue;
                mergedFe += other.sharedBuffer.getEnergyStored();
                for (BlockPos memberPos : other.members) {
                    kept.members.add(memberPos);
                    NETWORKS.put(memberPos, kept);
                }
            }
            kept.members.add(pos);
            NETWORKS.put(pos, kept);
            int cap   = ForgeConfigHandler.conduit.networkBufferPerConduit * kept.members.size();
            int maxIo = ForgeConfigHandler.conduit.maxFePerTickPerFace;
            kept.sharedBuffer = new EnergyStorage(cap, maxIo, maxIo);
            kept.sharedBuffer.receiveEnergy(Math.min(mergedFe, cap), false);
        }
    }

    public static void onConduitRemoved(World world, BlockPos pos) {
        ConduitNetwork network = NETWORKS.get(pos);
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
                BlockPos neighbourPos = current.offset(face);
                if (network.members.contains(neighbourPos) && !visited.contains(neighbourPos)) {
                    visited.add(neighbourPos);
                    queue.add(neighbourPos);
                }
            }
        }

        if (visited.size() == network.members.size()) return;

        Set<BlockPos> remaining = new HashSet<>(network.members);
        remaining.removeAll(visited);

        int totalFe    = network.sharedBuffer.getEnergyStored();
        int totalNodes = network.members.size();
        int feA = (totalNodes > 0) ? (totalFe * visited.size()   / totalNodes) : 0;
        int feB = (totalNodes > 0) ? (totalFe * remaining.size() / totalNodes) : 0;

        int maxIo    = ForgeConfigHandler.conduit.maxFePerTickPerFace;
        int perBlock = ForgeConfigHandler.conduit.networkBufferPerConduit;

        network.members.clear();
        network.members.addAll(visited);
        int capA = perBlock * network.members.size();
        network.sharedBuffer = new EnergyStorage(capA, maxIo, maxIo);
        network.sharedBuffer.receiveEnergy(Math.min(feA, capA), false);
        for (BlockPos memberPos : visited) NETWORKS.put(memberPos, network);

        ConduitNetwork newNetwork = new ConduitNetwork();
        int capB = perBlock * remaining.size();
        newNetwork.sharedBuffer = new EnergyStorage(capB, maxIo, maxIo);
        newNetwork.sharedBuffer.receiveEnergy(Math.min(feB, capB), false);
        for (BlockPos memberPos : remaining) {
            newNetwork.members.add(memberPos);
            NETWORKS.put(memberPos, newNetwork);
        }
    }

    // -----------------------------------------------------------------------
    // Tick — called from TileEntityConduit (master = smallest BlockPos)
    // -----------------------------------------------------------------------

    public void tick(World world) {
        int stored = sharedBuffer.getEnergyStored();
        lastFePerTick = stored - bufferAtTickStart;
        bufferAtTickStart = stored;

        // Push FE into adjacent machines that cannot pull on their own
        if (stored > 0) {
            for (BlockPos memberPos : members) {
                TileEntity mte = world.getTileEntity(memberPos);
                if (!(mte instanceof TileEntityConduit)) continue;

                for (EnumFacing face : EnumFacing.VALUES) {
                    BlockPos np = memberPos.offset(face);
                    Block nb = world.getBlockState(np).getBlock();
                    if (nb instanceof BlockConduit)  continue;
                    if (nb instanceof BlockItemPipe) continue;

                    TileEntity nte = world.getTileEntity(np);
                    if (nte == null) continue;

                    IEnergyStorage st = nte.getCapability(CapabilityEnergy.ENERGY, face.getOpposite());
                    if (st == null) st = nte.getCapability(CapabilityEnergy.ENERGY, null);
                    if (st == null || !st.canReceive()) continue;

                    int toSend = Math.min(ForgeConfigHandler.conduit.maxFePerTickPerFace,
                                         sharedBuffer.getEnergyStored());
                    if (toSend <= 0) break;

                    int accepted = st.receiveEnergy(toSend, false);
                    if (accepted > 0) sharedBuffer.extractEnergy(accepted, false);
                }
            }
        }

        // Compute tier from buffer fill and push to pipe networks + border conduit TEs
        PipeNetwork.SpeedTier tier = tierFromBuffer(sharedBuffer.getEnergyStored());
        pushTierToPipeNetworks(world, tier);
    }

    /**
     * Computes the current tier from buffer fill % and:
     * 1. Pushes it to all adjacent pipe networks.
     * 2. Updates cachedTier ONLY on conduit TEs that border a pipe network,
     *    so their flanges render correctly. Interior conduits are untouched.
     */
    private void pushTierToPipeNetworks(World world, PipeNetwork.SpeedTier tier) {
        Set<PipeNetwork> adjacentPipeNets = new HashSet<>();

        for (BlockPos memberPos : members) {
            boolean bordersPipe = false;

            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos neighbourPos = memberPos.offset(face);
                Block neighbourBlock = world.getBlockState(neighbourPos).getBlock();
                if (!(neighbourBlock instanceof BlockItemPipe)) continue;

                bordersPipe = true;
                PipeNetwork pipeNet = PipeNetwork.getNetwork(neighbourPos);
                if (pipeNet != null) adjacentPipeNets.add(pipeNet);
            }

            // Only sync cachedTier (and trigger re-render) for conduits touching a pipe
            if (bordersPipe) {
                TileEntity te = world.getTileEntity(memberPos);
                if (te instanceof TileEntityConduit) {
                    TileEntityConduit cte = (TileEntityConduit) te;
                    if (cte.cachedTier != tier) {
                        cte.cachedTier = tier;
                        te.markDirty();
                        IBlockState bs = world.getBlockState(memberPos);
                        world.notifyBlockUpdate(memberPos, bs, bs, 3);
                    }
                }
            }
        }

        for (PipeNetwork pipeNet : adjacentPipeNets) {
            pipeNet.setConduitTier(tier);
        }
    }

    private PipeNetwork.SpeedTier tierFromBuffer(int stored) {
        int cap = ForgeConfigHandler.conduit.networkBufferPerConduit * Math.max(1, members.size());
        if (cap <= 0 || stored <= 0) return PipeNetwork.SpeedTier.SLOW;
        float fill = (float) stored / cap;
        if (fill >= ForgeConfigHandler.conduit.tierUltra)  return PipeNetwork.SpeedTier.ULTRA;
        if (fill >= ForgeConfigHandler.conduit.tierHyper)  return PipeNetwork.SpeedTier.HYPER;
        if (fill >= ForgeConfigHandler.conduit.tierTurbo)  return PipeNetwork.SpeedTier.TURBO;
        if (fill >= ForgeConfigHandler.conduit.tierFast)   return PipeNetwork.SpeedTier.FAST;
        if (fill >= ForgeConfigHandler.conduit.tierNormal) return PipeNetwork.SpeedTier.NORMAL;
        return PipeNetwork.SpeedTier.SLOW;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    /** Positive = net gain last tick, negative = net drain. Used for spark effects. */
    public int getLastFePerTick()  { return lastFePerTick; }
    public int getBufferStored()   { return sharedBuffer.getEnergyStored(); }
    public int getBufferCapacity() { return ForgeConfigHandler.conduit.networkBufferPerConduit * Math.max(1, members.size()); }
    public Set<BlockPos> getMembers() { return Collections.unmodifiableSet(members); }
    public int getMemberCount()    { return members.size(); }
}
