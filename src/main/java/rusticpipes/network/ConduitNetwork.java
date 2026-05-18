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

public class ConduitNetwork {

    private static final Map<BlockPos, ConduitNetwork> NETWORKS = new HashMap<>();

    private final Set<BlockPos> members = new HashSet<>();
    private EnergyStorage sharedBuffer;
    private int lastTier = -1; // -1 forces first-tick notify

    // -----------------------------------------------------------------------
    // Constructor / buffer
    // -----------------------------------------------------------------------

    public ConduitNetwork() { rebuildBuffer(0); }

    private void rebuildBuffer(int preserveFe) {
        int cap   = ForgeConfigHandler.conduit.networkBufferCapacity;
        int maxIo = ForgeConfigHandler.conduit.maxFePerTickPerFace;
        sharedBuffer = new EnergyStorage(cap, maxIo, maxIo);
        if (preserveFe > 0) sharedBuffer.receiveEnergy(Math.min(preserveFe, cap), false);
    }

    public IEnergyStorage getSharedStorage() { return sharedBuffer; }

    // -----------------------------------------------------------------------
    // Static network management
    // -----------------------------------------------------------------------

    public static ConduitNetwork getNetwork(BlockPos pos) { return NETWORKS.get(pos); }

    public static void onConduitAdded(World world, BlockPos pos) {
        Set<ConduitNetwork> neighbours = new HashSet<>();
        for (EnumFacing face : EnumFacing.VALUES) {
            ConduitNetwork n = NETWORKS.get(pos.offset(face));
            if (n != null) neighbours.add(n);
        }

        if (neighbours.isEmpty()) {
            ConduitNetwork net = new ConduitNetwork();
            net.members.add(pos);
            NETWORKS.put(pos, net);
        } else if (neighbours.size() == 1) {
            ConduitNetwork net = neighbours.iterator().next();
            net.members.add(pos);
            NETWORKS.put(pos, net);
            net.rebuildBuffer(net.sharedBuffer.getEnergyStored());
        } else {
            ConduitNetwork kept = neighbours.iterator().next();
            int mergedFe = kept.sharedBuffer.getEnergyStored();
            for (ConduitNetwork other : neighbours) {
                if (other == kept) continue;
                mergedFe += other.sharedBuffer.getEnergyStored();
                for (BlockPos p : other.members) { kept.members.add(p); NETWORKS.put(p, kept); }
            }
            kept.members.add(pos);
            NETWORKS.put(pos, kept);
            kept.rebuildBuffer(mergedFe);
        }
    }

    public static void onConduitRemoved(World world, BlockPos pos) {
        ConduitNetwork net = NETWORKS.get(pos);
        if (net == null) return;
        NETWORKS.remove(pos);
        net.members.remove(pos);
        if (net.members.isEmpty()) return;

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(net.members.iterator().next());
        visited.add(queue.peek());
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos np = cur.offset(face);
                if (net.members.contains(np) && visited.add(np)) queue.add(np);
            }
        }
        if (visited.size() == net.members.size()) return;

        Set<BlockPos> remaining = new HashSet<>(net.members);
        remaining.removeAll(visited);
        int totalFe = net.sharedBuffer.getEnergyStored();
        int total   = net.members.size();

        net.members.clear();
        net.members.addAll(visited);
        net.rebuildBuffer(total > 0 ? totalFe * visited.size() / total : 0);
        for (BlockPos p : visited) NETWORKS.put(p, net);

        ConduitNetwork split = new ConduitNetwork();
        split.rebuildBuffer(total > 0 ? totalFe * remaining.size() / total : 0);
        for (BlockPos p : remaining) { split.members.add(p); NETWORKS.put(p, split); }
    }

    // -----------------------------------------------------------------------
    // Tick
    // -----------------------------------------------------------------------

    public void tick(World world) {
        int stored = sharedBuffer.getEnergyStored();

        // Push to adjacent machines
        if (stored > 0) {
            for (BlockPos memberPos : members) {
                if (!(world.getTileEntity(memberPos) instanceof TileEntityConduit)) continue;
                for (EnumFacing face : EnumFacing.VALUES) {
                    BlockPos np = memberPos.offset(face);
                    Block nb = world.getBlockState(np).getBlock();
                    if (nb instanceof BlockConduit || nb instanceof BlockItemPipe) continue;
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

        // Percentage loss
        double loss = ForgeConfigHandler.conduit.powerLossPerConduitPerTick;
        if (loss > 0.0 && sharedBuffer.getEnergyStored() > 0) {
            int lossFe = (int) Math.ceil(sharedBuffer.getEnergyStored() * loss);
            if (lossFe > 0) sharedBuffer.extractEnergy(lossFe, false);
        }

        // Notify client re-render only when tier bracket changes
        int tier = tierFromStored(sharedBuffer.getEnergyStored());
        if (tier != lastTier) {
            lastTier = tier;
            for (BlockPos memberPos : members) {
                IBlockState bs = world.getBlockState(memberPos);
                // Flag 2 = send update to clients only, no neighbour cascade, no chunk save
                world.notifyBlockUpdate(memberPos, bs, bs, 2);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    public static int tierFromStored(int stored) {
        ForgeConfigHandler.ConduitConfig cfg = ForgeConfigHandler.conduit;
        if (stored >= cfg.feThresholdTurbo)  return 3;
        if (stored >= cfg.feThresholdFast)   return 2;
        if (stored >= cfg.feThresholdNormal) return 1;
        return 0;
    }

    public int getBufferStored()   { return sharedBuffer.getEnergyStored(); }
    public int getBufferCapacity() { return ForgeConfigHandler.conduit.networkBufferCapacity; }
    public Set<BlockPos> getMembers() { return Collections.unmodifiableSet(members); }
    public int getMemberCount()    { return members.size(); }
}
