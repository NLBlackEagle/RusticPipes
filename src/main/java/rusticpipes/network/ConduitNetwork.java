package rusticpipes.network;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import rusticpipes.block.BlockConduit;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.handlers.ForgeConfigHandler;
import rusticpipes.tileentity.TileEntityConduit;

import java.util.*;

public class ConduitNetwork {

    private static final Map<BlockPos, ConduitNetwork> NETWORKS = new HashMap<>();

    private final Set<BlockPos> members = new HashSet<>();

    /** FE/tick consumed last tick — used for GUI meter and tier calculation. */
    private int lastFePerTick = 0;

    /** Current speed tier pushed to adjacent pipe networks. */
    private PipeNetwork.SpeedTier currentTier = PipeNetwork.SpeedTier.SLOW;

    // -----------------------------------------------------------------------
    // Static network management — mirrors PipeNetwork
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
        } else {
            ConduitNetwork kept = neighbours.iterator().next();
            for (ConduitNetwork other : neighbours) {
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

    public static void onConduitRemoved(World world, BlockPos pos) {
        ConduitNetwork network = NETWORKS.get(pos);
        if (network == null) return;

        NETWORKS.remove(pos);
        network.members.remove(pos);

        if (network.members.isEmpty()) return;

        // BFS to detect splits
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

        network.members.clear();
        network.members.addAll(visited);
        for (BlockPos memberPos : visited) NETWORKS.put(memberPos, network);

        ConduitNetwork newNetwork = new ConduitNetwork();
        for (BlockPos memberPos : remaining) {
            newNetwork.members.add(memberPos);
            NETWORKS.put(memberPos, newNetwork);
        }
    }

    // -----------------------------------------------------------------------
    // Tick — called from TileEntityConduit (one TE per network is enough)
    // -----------------------------------------------------------------------

    public void tick(World world) {
        // Collect FE from all adjacent FE sources on all conduit members
        int totalFe = 0;
        for (BlockPos memberPos : members) {
            TileEntity te = world.getTileEntity(memberPos);
            if (!(te instanceof TileEntityConduit)) continue;

            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos neighbourPos = memberPos.offset(face);
                Block neighbourBlock = world.getBlockState(neighbourPos).getBlock();

                // Skip other conduits and pipe blocks
                if (neighbourBlock instanceof BlockConduit) continue;
                if (neighbourBlock instanceof BlockItemPipe) continue;

                TileEntity neighbourTe = world.getTileEntity(neighbourPos);
                if (neighbourTe == null) continue;

                IEnergyStorage storage = neighbourTe.getCapability(
                        CapabilityEnergy.ENERGY, face.getOpposite());
                if (storage == null || !storage.canExtract()) continue;

                // Extract up to the configured max per face per tick
                int maxExtract = ForgeConfigHandler.conduit.maxFePerTickPerFace;
                int extracted = storage.extractEnergy(maxExtract, false);
                totalFe += extracted;

                // Store in the conduit's own buffer
                TileEntityConduit conduitTe = (TileEntityConduit) te;
                conduitTe.receiveEnergy(extracted);
            }
        }

        lastFePerTick = totalFe;
        PipeNetwork.SpeedTier newTier = tierFromFe(totalFe);

        // If tier changed, sync TE to client so getExtendedState gets the new tier
        if (newTier != currentTier) {
            currentTier = newTier;
            for (BlockPos memberPos : members) {
                net.minecraft.tileentity.TileEntity te = world.getTileEntity(memberPos);
                if (te instanceof TileEntityConduit) {
                    te.markDirty();
                    net.minecraft.block.state.IBlockState bs = world.getBlockState(memberPos);
                    world.notifyBlockUpdate(memberPos, bs, bs, 3);
                }
            }
        } else {
            currentTier = newTier;
        }

        // Push tier to all adjacent pipe networks
        pushTierToPipeNetworks(world);
    }

    private void pushTierToPipeNetworks(World world) {
        Set<PipeNetwork> adjacentNetworks = new HashSet<>();
        for (BlockPos memberPos : members) {
            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos neighbourPos = memberPos.offset(face);
                Block neighbourBlock = world.getBlockState(neighbourPos).getBlock();
                if (!(neighbourBlock instanceof BlockItemPipe)) continue;
                PipeNetwork pipeNet = PipeNetwork.getNetwork(world, neighbourPos);
                if (pipeNet != null) adjacentNetworks.add(pipeNet);
            }
        }
        for (PipeNetwork pipeNet : adjacentNetworks) {
            pipeNet.setConduitTier(currentTier);
        }
    }

    private static PipeNetwork.SpeedTier tierFromFe(int fePerTick) {
        if (fePerTick >= ForgeConfigHandler.conduit.fePerTickUltra)  return PipeNetwork.SpeedTier.ULTRA;
        if (fePerTick >= ForgeConfigHandler.conduit.fePerTickHyper)  return PipeNetwork.SpeedTier.HYPER;
        if (fePerTick >= ForgeConfigHandler.conduit.fePerTickTurbo)  return PipeNetwork.SpeedTier.TURBO;
        if (fePerTick >= ForgeConfigHandler.conduit.fePerTickFast)   return PipeNetwork.SpeedTier.FAST;
        if (fePerTick >= ForgeConfigHandler.conduit.fePerTickNormal) return PipeNetwork.SpeedTier.NORMAL;
        return PipeNetwork.SpeedTier.SLOW;
    }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public int getLastFePerTick()              { return lastFePerTick; }
    public PipeNetwork.SpeedTier getCurrentTier() { return currentTier; }
    public Set<BlockPos> getMembers()          { return Collections.unmodifiableSet(members); }
    public int getMemberCount()                { return members.size(); }
}
