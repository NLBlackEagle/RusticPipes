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
import rusticpipes.RusticPipes;
import rusticpipes.block.BlockConduit;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.compat.IC2Compat;
import rusticpipes.handlers.ForgeConfigHandler;
import rusticpipes.tileentity.TileEntityConduit;

import java.util.*;

public class ConduitNetwork {

    private static final Map<BlockPos, ConduitNetwork> NETWORKS = new HashMap<>();

    private final Set<BlockPos> members = new HashSet<>();
    private EnergyStorage sharedBuffer;
    private int lastTier = -1; // -1 forces first-tick notify

    /**
     * Exponential moving average of buffer fill fraction (0.0-1.0).
     * Updated every tick server-side. Exposed to client via getSmoothedFill()
     * which the spark handler uses instead of the raw instantaneous fill.
     * Alpha=0.05 gives ~20 tick smoothing window.
     */
    /**
     * Exponential moving average of FE throughput per tick (0.0-1.0 relative to buffer capacity).
     * Tracks how much FE actually moved through the network each tick — works correctly
     * even when the buffer fills and drains in the same tick (raw fill would always be 0).
     */
    private float smoothedFill = 0f;
    /** Smoothed FE throughput per tick — EMA of FE actually pushed to machines each tick. */
    private float smoothedThroughput = 0f;
    private static final float THROUGHPUT_ALPHA = 0.2f;
    private static final float SMOOTH_ALPHA = 0.15f;

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
        // Snapshot how much was stored BEFORE pulling this tick.
        // Push is capped to this amount so energy that arrives this tick
        // can only leave on the next tick, giving the buffer one full tick
        // to accumulate before anything drains it. The delay is one tick
        // (~50 ms at 20 TPS) — imperceptible to players.
        int storedBefore = sharedBuffer.getEnergyStored();
        int tickPushed = 0;
        int bufferRoom = sharedBuffer.getMaxEnergyStored() - storedBefore;

        // Track positions we pulled from this tick so we never push back into them.
        // Without this, a source that is both canExtract() and canReceive() (e.g. a
        // battery or capacitor) would have energy pulled out and immediately pushed
        // back in the same tick, keeping the conduit buffer permanently empty.
        Set<BlockPos> pulledFrom = new HashSet<>();

        // Pull from adjacent energy sources (e.g. batteries/capacitors that don't push)
        if (bufferRoom > 0) {
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
                    // IC2 soft-dependency fallback: wrap EU sources as FE
                    if (st == null) st = IC2Compat.wrapSource(nte, face.getOpposite());
                    if (st == null || !st.canExtract()) continue;
                    int toTake = Math.min(ForgeConfigHandler.conduit.maxFePerTickPerFace, bufferRoom);
                    if (toTake <= 0) continue;
                    int extracted = st.extractEnergy(toTake, false);
                    if (extracted > 0) {
                        sharedBuffer.receiveEnergy(extracted, false);
                        bufferRoom -= extracted;
                        pulledFrom.add(np); // mark so we don't push back this tick
                    }
                }
            }
        }

        // Push to adjacent machines (e.g. machines that don't pull themselves).
        // Skip any position we just pulled from — those are sources, not consumers.
        // Also skip pure-source tiles (canExtract but not canReceive) to avoid
        // accidentally back-feeding generators that incorrectly accept energy.
        // Cap push to storedBefore — energy pulled in this tick stays until next tick.
        int availableToPush = Math.min(sharedBuffer.getEnergyStored(), storedBefore);
        if (availableToPush > 0) {
            for (BlockPos memberPos : members) {
                if (!(world.getTileEntity(memberPos) instanceof TileEntityConduit)) continue;
                for (EnumFacing face : EnumFacing.VALUES) {
                    BlockPos np = memberPos.offset(face);
                    if (pulledFrom.contains(np)) continue; // never push back into a source
                    Block nb = world.getBlockState(np).getBlock();
                    if (nb instanceof BlockConduit || nb instanceof BlockItemPipe) continue;
                    TileEntity nte = world.getTileEntity(np);
                    if (nte == null) continue;
                    IEnergyStorage st = nte.getCapability(CapabilityEnergy.ENERGY, face.getOpposite());
                    if (st == null) st = nte.getCapability(CapabilityEnergy.ENERGY, null);
                    // IC2 soft-dependency fallback: wrap EU sinks as FE
                    if (st == null) st = IC2Compat.wrapSink(nte, face.getOpposite());
                    if (st == null || !st.canReceive()) continue;
                    int toSend = Math.min(ForgeConfigHandler.conduit.maxFePerTickPerFace,
                            availableToPush);
                    if (toSend <= 0) continue;
                    int accepted = st.receiveEnergy(toSend, false);
                    if (accepted > 0) {
                        sharedBuffer.extractEnergy(accepted, false);
                        availableToPush -= accepted;
                        tickPushed += accepted;
                    }
                }
            }
        }

        // Percentage loss
        double loss = ForgeConfigHandler.conduit.powerLossPerConduitPerTick;
        if (loss > 0.0 && sharedBuffer.getEnergyStored() > 0) {
            int lossFe = (int) Math.ceil(sharedBuffer.getEnergyStored() * loss);
            if (lossFe > 0) sharedBuffer.extractEnergy(lossFe, false);
        }

        // Update smoothed throughput EMA
        float throughputFraction = sharedBuffer.getMaxEnergyStored() > 0
                ? Math.min(1f, (float) tickPushed / sharedBuffer.getMaxEnergyStored())
                : 0f;
        smoothedThroughput = THROUGHPUT_ALPHA * throughputFraction + (1f - THROUGHPUT_ALPHA) * smoothedThroughput;

        // Update smoothed fill EMA — use ACTUAL stored/capacity ratio, not throughput.
        // Previously this tracked tickThroughput (FE moved by tick() only), which meant
        // energy pushed in externally by generators (via receiveEnergy capability calls
        // outside of tick()) was invisible: smoothedFill stayed 0 even when the buffer
        // was full, causing the display to always read "0/5000 FE (0%)".
        float currentFill = sharedBuffer.getMaxEnergyStored() > 0
                ? Math.min(1f, (float) sharedBuffer.getEnergyStored() / sharedBuffer.getMaxEnergyStored())
                : 0f;
        smoothedFill = SMOOTH_ALPHA * currentFill + (1f - SMOOTH_ALPHA) * smoothedFill;
        // Notify client re-render only when tier bracket changes
        int tier = tierFromStored(sharedBuffer.getEnergyStored());
        if (tier != lastTier) {
            lastTier = tier;
            boolean powered = sharedBuffer.getEnergyStored() > 0;
            for (BlockPos memberPos : members) {
                IBlockState bs = world.getBlockState(memberPos);
                // Flag 2 = send update to clients only, no neighbour cascade, no chunk save
                world.notifyBlockUpdate(memberPos, bs, bs, 2);
                // Sync power state to clients so getExtendedState can pick the
                // correct connector texture on a dedicated server.
                RusticPipes.NET.sendToAllAround(
                        new PacketConduitPower(memberPos, powered),
                        new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(
                                world.provider.getDimension(),
                                memberPos.getX(), memberPos.getY(), memberPos.getZ(), 64));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    public static int tierFromStored(int stored) {
        // Use motor FE costs as the tier display thresholds on conduits
        if (stored >= ForgeConfigHandler.getFeCost(rusticpipes.network.PipeNetwork.SpeedTier.TURBO)) return 3;
        if (stored >= ForgeConfigHandler.getFeCost(rusticpipes.network.PipeNetwork.SpeedTier.FAST))  return 2;
        if (stored >= ForgeConfigHandler.getFeCost(rusticpipes.network.PipeNetwork.SpeedTier.NORMAL))return 1;
        return 0;
    }

    /** Smoothed fill fraction 0.0-1.0 — use this for spark effects. */
    public float getSmoothedFill() { return smoothedFill; }
    public int getSmoothedThroughput() {
        return (int)(smoothedThroughput * sharedBuffer.getMaxEnergyStored());
    }
    public int getBufferStored()   { return sharedBuffer.getEnergyStored(); }
    public int getBufferCapacity() { return ForgeConfigHandler.conduit.networkBufferCapacity; }
    public Set<BlockPos> getMembers() { return Collections.unmodifiableSet(members); }
    public int getMemberCount()    { return members.size(); }
}
