package rusticpipes.network;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
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
import rusticpipes.util.DimPos;

import java.util.*;

public class ConduitNetwork {

    // Keyed by DimPos to prevent cross-dimension network collisions.
    private static final Map<DimPos, ConduitNetwork> NETWORKS = new HashMap<>();

    private final Set<BlockPos> members = new HashSet<>();
    private NetworkBuffer sharedBuffer;
    private int lastTier = -1; // -1 forces first-tick notify
    private int lastSparkLevel = -1; // -1 forces first-tick sync
    // Cached master pos — recomputed only on topology changes, not every tick.
    private BlockPos cachedMasterPos = null;

    private float smoothedFill = 0f;
    private float smoothedThroughput = 0f;
    private static final float THROUGHPUT_ALPHA = 0.2f;
    private static final float SMOOTH_ALPHA = 0.15f;

    // -----------------------------------------------------------------------
    // Constructor / buffer
    // -----------------------------------------------------------------------

    public ConduitNetwork() { rebuildBuffer(0); }

    /**
     * Reconfigures the buffer in place. Setting the energy directly (rather than via
     * receiveEnergy) means the preserved amount isn't capped by maxFePerTickPerFace,
     * and keeping the same object means capability references held by other mods stay live.
     */
    private void rebuildBuffer(int preserveFe) {
        int cap   = ForgeConfigHandler.conduit.networkBufferCapacity;
        int maxIo = ForgeConfigHandler.conduit.maxFePerTickPerFace;
        if (sharedBuffer == null) sharedBuffer = new NetworkBuffer();
        sharedBuffer.configure(cap, maxIo, Math.max(0, Math.min(preserveFe, cap)));
    }

    private static final class NetworkBuffer extends EnergyStorage {
        NetworkBuffer() { super(0); }
        void configure(int cap, int io, int fe) {
            capacity = cap; maxReceive = io; maxExtract = io; energy = fe;
        }
    }

    public IEnergyStorage getSharedStorage() { return sharedBuffer; }

    /**
     * One member's even share of the buffer. Each conduit saves this to NBT so the
     * network's energy survives chunk unloads and restarts (the network itself is not saved).
     */
    public int getShareFor() {
        return members.isEmpty() ? 0 : sharedBuffer.getEnergyStored() / members.size();
    }

    /** Returns FE a conduit saved to NBT back to the network when it loads. */
    public void addStoredEnergy(int fe) {
        if (fe > 0) rebuildBuffer(sharedBuffer.getEnergyStored() + fe);
    }

    /** Takes FE out of the network when a conduit leaves with its saved share. */
    public void removeStoredEnergy(int fe) {
        if (fe > 0) rebuildBuffer(sharedBuffer.getEnergyStored() - fe);
    }

    // -----------------------------------------------------------------------
    // Static network management
    // -----------------------------------------------------------------------

    /** Look up by full World (server-side, always preferred). */
    public static ConduitNetwork getNetwork(World world, BlockPos pos) {
        return NETWORKS.get(DimPos.of(world, pos));
    }

    /**
     * Look up by IBlockAccess — used in block render callbacks where the
     * world is typed as IBlockAccess. Casts to World when possible.
     */
    public static ConduitNetwork getNetwork(IBlockAccess world, BlockPos pos) {
        return NETWORKS.get(DimPos.of(world, pos));
    }

    /**
     * Removes all networks belonging to the given dimension.
     * Called on WorldEvent.Unload to prevent memory leaks across world reloads.
     */
    public static void clearDimension(int dimId) {
        NETWORKS.entrySet().removeIf(e -> e.getKey().dimId == dimId);
    }

    public static void onConduitAdded(World world, BlockPos pos) {
        int dimId = world.provider.getDimension();
        Set<ConduitNetwork> neighbours = new HashSet<>();
        for (EnumFacing face : EnumFacing.VALUES) {
            ConduitNetwork n = NETWORKS.get(new DimPos(dimId, pos.offset(face)));
            if (n != null) neighbours.add(n);
        }

        if (neighbours.isEmpty()) {
            ConduitNetwork net = new ConduitNetwork();
            net.members.add(pos);
            net.cachedMasterPos = pos;
            NETWORKS.put(new DimPos(dimId, pos), net);
        } else if (neighbours.size() == 1) {
            ConduitNetwork net = neighbours.iterator().next();
            net.members.add(pos);
            NETWORKS.put(new DimPos(dimId, pos), net);
            net.rebuildBuffer(net.sharedBuffer.getEnergyStored());
            net.recomputeMaster();
        } else {
            ConduitNetwork kept = neighbours.iterator().next();
            int mergedFe = kept.sharedBuffer.getEnergyStored();
            for (ConduitNetwork other : neighbours) {
                if (other == kept) continue;
                mergedFe += other.sharedBuffer.getEnergyStored();
                for (BlockPos p : other.members) {
                    kept.members.add(p);
                    NETWORKS.put(new DimPos(dimId, p), kept);
                }
            }
            kept.members.add(pos);
            NETWORKS.put(new DimPos(dimId, pos), kept);
            kept.rebuildBuffer(mergedFe);
            kept.recomputeMaster();
        }
    }

    public static void onConduitRemoved(World world, BlockPos pos) {
        int dimId = world.provider.getDimension();
        DimPos key = new DimPos(dimId, pos);
        ConduitNetwork net = NETWORKS.get(key);
        if (net == null) return;
        NETWORKS.remove(key);
        net.members.remove(pos);
        if (net.members.isEmpty()) return;

        // Early-exit: fewer than 2 in-network neighbours means no split is possible.
        int networkNeighbours = 0;
        for (EnumFacing face : EnumFacing.VALUES) {
            if (net.members.contains(pos.offset(face))) {
                if (++networkNeighbours >= 2) break;
            }
        }
        if (networkNeighbours < 2) {
            net.recomputeMaster();
            return;
        }

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
        if (visited.size() == net.members.size()) {
            net.recomputeMaster();
            return;
        }

        Set<BlockPos> remaining = new HashSet<>(net.members);
        remaining.removeAll(visited);
        int totalFe = net.sharedBuffer.getEnergyStored();
        int total   = net.members.size();

        net.members.clear();
        net.members.addAll(visited);
        net.rebuildBuffer(total > 0 ? totalFe * visited.size() / total : 0);
        net.recomputeMaster();
        for (BlockPos p : visited) NETWORKS.put(new DimPos(dimId, p), net);

        ConduitNetwork split = new ConduitNetwork();
        split.rebuildBuffer(total > 0 ? totalFe * remaining.size() / total : 0);
        for (BlockPos p : remaining) {
            split.members.add(p);
            NETWORKS.put(new DimPos(dimId, p), split);
        }
        split.recomputeMaster();
    }

    // -----------------------------------------------------------------------
    // Master management
    // -----------------------------------------------------------------------

    private void recomputeMaster() {
        cachedMasterPos = null;
        for (BlockPos p : members) {
            if (cachedMasterPos == null || p.toLong() < cachedMasterPos.toLong()) {
                cachedMasterPos = p;
            }
        }
    }

    /** Returns the cached master position — O(1), safe to call every tick. */
    public BlockPos getMasterPos() {
        return cachedMasterPos;
    }

    // -----------------------------------------------------------------------
    // Tick
    // -----------------------------------------------------------------------

    public void tick(World world) {
        int storedBefore = sharedBuffer.getEnergyStored();
        int tickPushed = 0;
        int bufferRoom = sharedBuffer.getMaxEnergyStored() - storedBefore;

        Set<BlockPos> pulledFrom = new HashSet<>();

        if (bufferRoom > 0) {
            for (BlockPos memberPos : members) {
                if (!(world.getTileEntity(memberPos) instanceof TileEntityConduit)) continue;
                for (EnumFacing face : EnumFacing.VALUES) {
                    BlockPos np = memberPos.offset(face);
                    if (!world.isBlockLoaded(np)) continue; // never load chunks from a tick
                    Block nb = world.getBlockState(np).getBlock();
                    if (nb instanceof BlockConduit || nb instanceof BlockItemPipe) continue;
                    TileEntity nte = world.getTileEntity(np);
                    if (nte == null) continue;
                    IEnergyStorage st = nte.getCapability(CapabilityEnergy.ENERGY, face.getOpposite());
                    if (st == null) st = nte.getCapability(CapabilityEnergy.ENERGY, null);
                    if (st == null) st = IC2Compat.wrapSource(nte, face.getOpposite());
                    if (st == null || !st.canExtract()) continue;
                    int toTake = Math.min(ForgeConfigHandler.conduit.maxFePerTickPerFace, bufferRoom);
                    if (toTake <= 0) continue;
                    int extracted = st.extractEnergy(toTake, false);
                    if (extracted > 0) {
                        sharedBuffer.receiveEnergy(extracted, false);
                        bufferRoom -= extracted;
                        pulledFrom.add(np);
                    }
                }
            }
        }

        int availableToPush = Math.min(sharedBuffer.getEnergyStored(), storedBefore);
        if (availableToPush > 0) {
            for (BlockPos memberPos : members) {
                if (!(world.getTileEntity(memberPos) instanceof TileEntityConduit)) continue;
                for (EnumFacing face : EnumFacing.VALUES) {
                    BlockPos np = memberPos.offset(face);
                    if (pulledFrom.contains(np)) continue;
                    if (!world.isBlockLoaded(np)) continue;
                    Block nb = world.getBlockState(np).getBlock();
                    if (nb instanceof BlockConduit || nb instanceof BlockItemPipe) continue;
                    TileEntity nte = world.getTileEntity(np);
                    if (nte == null) continue;
                    IEnergyStorage st = nte.getCapability(CapabilityEnergy.ENERGY, face.getOpposite());
                    if (st == null) st = nte.getCapability(CapabilityEnergy.ENERGY, null);
                    if (st == null) st = IC2Compat.wrapSink(nte, face.getOpposite());
                    if (st == null || !st.canReceive()) continue;
                    int toSend = Math.min(ForgeConfigHandler.conduit.maxFePerTickPerFace, availableToPush);
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

        double loss = ForgeConfigHandler.conduit.powerLossPerConduitPerTick;
        if (loss > 0.0 && sharedBuffer.getEnergyStored() > 0) {
            int lossFe = (int) Math.ceil(sharedBuffer.getEnergyStored() * loss);
            if (lossFe > 0) sharedBuffer.extractEnergy(lossFe, false);
        }

        float throughputFraction = sharedBuffer.getMaxEnergyStored() > 0
                ? Math.min(1f, (float) tickPushed / sharedBuffer.getMaxEnergyStored()) : 0f;
        smoothedThroughput = THROUGHPUT_ALPHA * throughputFraction + (1f - THROUGHPUT_ALPHA) * smoothedThroughput;

        float currentFill = sharedBuffer.getMaxEnergyStored() > 0
                ? Math.min(1f, (float) sharedBuffer.getEnergyStored() / sharedBuffer.getMaxEnergyStored()) : 0f;
        smoothedFill = SMOOTH_ALPHA * currentFill + (1f - SMOOTH_ALPHA) * smoothedFill;

        boolean powered = smoothedThroughput > 0.001f || sharedBuffer.getEnergyStored() > 0;
        int poweredInt = powered ? 1 : 0;
        boolean tierChanged = poweredInt != lastTier;

        // Sparks follow actual throughput, not buffer fill. Hysteresis of 2 levels
        // (except to/from 0) keeps a fluctuating flow from spamming packets.
        int sparkLevel = sparkLevelFor(getSmoothedThroughput());
        boolean sparkChanged = sparkLevel != lastSparkLevel
                && (sparkLevel == 0 || lastSparkLevel <= 0 || Math.abs(sparkLevel - lastSparkLevel) >= 2);

        if (tierChanged || sparkChanged) {
            lastTier = poweredInt;
            if (sparkChanged) lastSparkLevel = sparkLevel;
            for (BlockPos memberPos : members) {
                if (tierChanged) {
                    IBlockState bs = world.getBlockState(memberPos);
                    world.notifyBlockUpdate(memberPos, bs, bs, 2);
                }
                RusticPipes.NET.sendToAllAround(
                        new PacketConduitPower(memberPos, powered, lastSparkLevel),
                        new net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint(
                                world.provider.getDimension(),
                                memberPos.getX(), memberPos.getY(), memberPos.getZ(), 64));
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Maps FE/tick delivered to a spark level: 0 = no flow, 1–10 = flowing.
     * Log scale so a small generator (~20 FE/t) still sparks visibly while
     * a full-rate face (maxFePerTickPerFace) reaches 10.
     */
    private static int sparkLevelFor(int fePerTick) {
        if (fePerTick <= 0) return 0;
        int max = Math.max(1, ForgeConfigHandler.conduit.maxFePerTickPerFace);
        double t = Math.min(1.0, Math.log1p(fePerTick) / Math.log1p(max));
        return 1 + (int) Math.round(t * 9);
    }

    public static int tierFromStored(int stored) {
        if (stored >= ForgeConfigHandler.getFeCost(rusticpipes.network.PipeNetwork.SpeedTier.TURBO)) return 3;
        if (stored >= ForgeConfigHandler.getFeCost(rusticpipes.network.PipeNetwork.SpeedTier.FAST))  return 2;
        if (stored >= ForgeConfigHandler.getFeCost(rusticpipes.network.PipeNetwork.SpeedTier.NORMAL))return 1;
        return 0;
    }

    /** Powered state as last sent to clients — used to seed clients on chunk load. */
    public boolean isPoweredSynced()       { return lastTier == 1; }
    /** Spark level as last sent to clients (0 = no flow). */
    public int getSparkLevelSynced()       { return Math.max(0, lastSparkLevel); }
    public boolean isPowered()             { return sharedBuffer.getEnergyStored() > 0 || getSmoothedThroughput() > 0; }
    public float getSmoothedFill()         { return smoothedFill; }
    public int getSmoothedThroughput()     { return (int)(smoothedThroughput * sharedBuffer.getMaxEnergyStored()); }
    public int getBufferStored()           { return sharedBuffer.getEnergyStored(); }
    public int getBufferCapacity()         { return ForgeConfigHandler.conduit.networkBufferCapacity; }
    public Set<BlockPos> getMembers()      { return Collections.unmodifiableSet(members); }
    public int getMemberCount()            { return members.size(); }
}
