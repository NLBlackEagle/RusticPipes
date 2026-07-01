package rusticpipes.network;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import rusticpipes.block.BlockConduit;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.block.PipeColor;
import rusticpipes.handlers.ForgeConfigHandler;
import rusticpipes.tileentity.FaceMode;
import rusticpipes.tileentity.TileEntityConduitBuffer;
import rusticpipes.tileentity.TileEntityItemPipe;
import rusticpipes.util.DimPos;

import java.util.*;

public class PipeNetwork {

    public enum SpeedTier { SLOW, NORMAL, FAST, TURBO, HYPER, ULTRA }

    // Keyed by DimPos to prevent cross-dimension network collisions.
    private static final Map<DimPos, PipeNetwork> NETWORKS = new HashMap<>();
    private static int globalTick = 0;

    private final Set<BlockPos> members = new HashSet<>();
    private int bucket;
    private SpeedTier currentTier     = SpeedTier.SLOW;
    private SpeedTier lastEffectiveTier = SpeedTier.SLOW;
    // Cached master pos — the member with the smallest toLong() value.
    // Recomputed only on topology changes, not every tick.
    private BlockPos cachedMasterPos = null;

    // -----------------------------------------------------------------------
    // Static accessors
    // -----------------------------------------------------------------------

    public static PipeNetwork getNetwork(World world, BlockPos pos) {
        return NETWORKS.get(DimPos.of(world, pos));
    }

    public static void serverTick() {
        globalTick++;
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
        PipeColor addedColor = (addedBlock instanceof BlockItemPipe)
                ? ((BlockItemPipe) addedBlock).pipeColor : null;
        int dimId = world.provider.getDimension();

        Set<PipeNetwork> neighbours = new HashSet<>();
        for (EnumFacing face : EnumFacing.VALUES) {
            BlockPos neighbourPos = pos.offset(face);
            if (addedColor != null) {
                Block neighbourBlock = world.getBlockState(neighbourPos).getBlock();
                if (!(neighbourBlock instanceof BlockItemPipe)) continue;
                if (((BlockItemPipe) neighbourBlock).pipeColor != addedColor) continue;
            }
            PipeNetwork n = NETWORKS.get(new DimPos(dimId, neighbourPos));
            if (n != null) neighbours.add(n);
        }

        if (neighbours.isEmpty()) {
            PipeNetwork network = new PipeNetwork();
            // Spread tick buckets using a stable hash of the seed position
            // so unrelated networks don't all fire on the same tick.
            network.bucket = spreadBucket(pos);
            network.members.add(pos);
            network.cachedMasterPos = pos;
            NETWORKS.put(new DimPos(dimId, pos), network);
        } else if (neighbours.size() == 1) {
            PipeNetwork network = neighbours.iterator().next();
            network.members.add(pos);
            NETWORKS.put(new DimPos(dimId, pos), network);
            network.recomputeMaster();
        } else {
            // Merge all neighbouring networks into the first one.
            PipeNetwork kept = neighbours.iterator().next();
            for (PipeNetwork other : neighbours) {
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
        PipeNetwork network = NETWORKS.get(key);
        if (network == null) return;

        NETWORKS.remove(key);
        network.members.remove(pos);

        if (network.members.isEmpty()) return;

        // Early-exit: if the removed pipe had fewer than 2 in-network neighbours,
        // a split is topologically impossible — skip the BFS entirely.
        int networkNeighbours = 0;
        for (EnumFacing face : EnumFacing.VALUES) {
            if (network.members.contains(pos.offset(face))) {
                networkNeighbours++;
                if (networkNeighbours >= 2) break;
            }
        }
        if (networkNeighbours < 2) {
            network.recomputeMaster();
            return;
        }

        // BFS from an arbitrary seed to find the connected component.
        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        BlockPos seed = network.members.iterator().next();
        queue.add(seed);
        visited.add(seed);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos neighbourPos = current.offset(face);
                if (network.members.contains(neighbourPos) && visited.add(neighbourPos)) {
                    queue.add(neighbourPos);
                }
            }
        }

        if (visited.size() == network.members.size()) {
            // Still fully connected — no split occurred.
            network.recomputeMaster();
            return;
        }

        // Split: keep the visited component in the existing network object,
        // and create a new network for the disconnected remainder.
        Set<BlockPos> remaining = new HashSet<>(network.members);
        remaining.removeAll(visited);

        network.members.clear();
        network.members.addAll(visited);
        network.recomputeMaster();
        for (BlockPos memberPos : visited) NETWORKS.put(new DimPos(dimId, memberPos), network);

        PipeNetwork newNetwork = new PipeNetwork();
        newNetwork.bucket = spreadBucket(remaining.iterator().next());
        for (BlockPos memberPos : remaining) {
            newNetwork.members.add(memberPos);
            NETWORKS.put(new DimPos(dimId, memberPos), newNetwork);
        }
        newNetwork.recomputeMaster();
    }

    // -----------------------------------------------------------------------
    // Master management
    // -----------------------------------------------------------------------

    /** Recomputes and caches the member with the smallest toLong() value. */
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

    /**
     * Returns true if this network should transfer items this game tick.
     * Only the master TE calls this, so there is no per-call state mutation
     * needed to guard against double-firing.
     */
    public boolean isMyTick() {
        if (currentTier != lastEffectiveTier) {
            // Tier changed: fire immediately and re-anchor the bucket to now
            // so the new rate starts from a clean phase boundary.
            lastEffectiveTier = currentTier;
            bucket = globalTick;
        }
        int rate = getEffectiveTickRate(this);
        return (globalTick % rate) == (bucket % rate);
    }

    /**
     * Scans all adjacent motor blocks (TileEntityConduitBuffer), sorted highest tier first.
     * Tries each in order — if a motor has enough FE for its tier cost, drains it and sets
     * the pipe tier. Falls back to the next motor down if the current one is insufficient.
     * If no motor can supply, runs SLOW (free).
     */
    public void drainFromConduit(World world) {
        List<MotorEntry> motors = new ArrayList<>();
        for (BlockPos memberPos : members) {
            for (EnumFacing face : EnumFacing.VALUES) {
                TileEntity nte = world.getTileEntity(memberPos.offset(face));
                if (!(nte instanceof TileEntityConduitBuffer)) continue;
                TileEntityConduitBuffer motor = (TileEntityConduitBuffer) nte;
                IEnergyStorage st = nte.getCapability(CapabilityEnergy.ENERGY, face.getOpposite());
                if (st == null || !st.canExtract()) continue;
                motors.add(new MotorEntry(motor.getTier(), st));
            }
        }

        if (motors.isEmpty()) {
            currentTier = SpeedTier.SLOW;
            return;
        }

        // Sort highest tier first — fall back down the list if a motor lacks FE.
        motors.sort((a, b) -> b.tier.ordinal() - a.tier.ordinal());

        for (MotorEntry entry : motors) {
            int cost = ForgeConfigHandler.getFeCost(entry.tier);
            if (cost == 0) {
                currentTier = SpeedTier.SLOW;
                return;
            }
            if (entry.storage.getEnergyStored() >= cost) {
                entry.storage.extractEnergy(cost, false);
                currentTier = entry.tier;
                return;
            }
        }

        currentTier = SpeedTier.SLOW;
    }

    private static class MotorEntry {
        final SpeedTier tier;
        final IEnergyStorage storage;
        MotorEntry(SpeedTier tier, IEnergyStorage storage) {
            this.tier = tier; this.storage = storage;
        }
    }

    // -----------------------------------------------------------------------
    // Transfer
    // -----------------------------------------------------------------------

    public void transferItems(World world) {
        Set<BlockPos> inputPositions  = new LinkedHashSet<>();
        Set<BlockPos> outputPositions = new LinkedHashSet<>();

        for (BlockPos memberPos : members) {
            IBlockState state = world.getBlockState(memberPos);
            if (!(state.getBlock() instanceof BlockItemPipe)) continue;

            TileEntity te = world.getTileEntity(memberPos);
            if (!(te instanceof TileEntityItemPipe)) continue;
            TileEntityItemPipe pipe = (TileEntityItemPipe) te;

            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos neighbourPos = memberPos.offset(face);
                if (world.getBlockState(neighbourPos).getBlock() instanceof BlockItemPipe) continue;
                if (getInventoryAtPos(world, neighbourPos) == null) continue;

                FaceMode mode = pipe.getFaceMode(face);
                if (mode == FaceMode.INPUT) {
                    inputPositions.add(neighbourPos);
                } else {
                    outputPositions.add(neighbourPos);
                }
            }
        }

        if (inputPositions.isEmpty() || outputPositions.isEmpty()) return;

        List<BlockPos> inputs  = new ArrayList<>(inputPositions);
        List<BlockPos> outputs = new ArrayList<>(outputPositions);
        int maxTransfer = getEffectiveTransferSize(this);

        for (BlockPos inputPos : inputs) {
            IItemHandler source = getInventoryAtPos(world, inputPos);
            if (source == null) continue;

            boolean movedAnything = false;
            for (int slot = 0; slot < source.getSlots() && !movedAnything; slot++) {
                ItemStack stack = source.extractItem(slot, maxTransfer, true);
                if (stack.isEmpty()) continue;

                int toMove = stack.getCount();

                for (int attempt = 0; attempt < outputs.size() && toMove > 0; attempt++) {
                    BlockPos destPos = outputs.get(rrPointer % outputs.size());
                    rrPointer++;

                    IItemHandler dest = getInventoryAtPos(world, destPos);
                    if (dest == null) continue;

                    for (int outSlot = 0; outSlot < dest.getSlots() && toMove > 0; outSlot++) {
                        ItemStack toInsert = stack.copy();
                        toInsert.setCount(toMove);
                        ItemStack remaining = dest.insertItem(outSlot, toInsert, true);
                        int accepted = toMove - remaining.getCount();
                        if (accepted <= 0) continue;

                        ItemStack actuallyExtracted = source.extractItem(slot, accepted, false);
                        if (actuallyExtracted.isEmpty()) continue;
                        dest.insertItem(outSlot, actuallyExtracted, false);
                        toMove -= actuallyExtracted.getCount();
                        movedAnything = true;
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static int getEffectiveTickRate(PipeNetwork network) {
        int base    = ForgeConfigHandler.getTickRate(network.currentTier);
        int penalty = network.members.size() * ForgeConfigHandler.pipes.distancePenalty;
        return Math.max(1, base + penalty);
    }

    private static int getEffectiveTransferSize(PipeNetwork network) {
        return ForgeConfigHandler.getTransferSize(network.currentTier);
    }

    /**
     * Produces a stable, deterministic bucket offset from a seed position.
     * Spreads networks across tick phases without depending on insertion order.
     */
    private static int spreadBucket(BlockPos pos) {
        long l = pos.toLong();
        return (int)(l ^ (l >>> 32));
    }

    @javax.annotation.Nullable
    private static IItemHandler getInventoryAtPos(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return null;
        for (EnumFacing face : EnumFacing.VALUES) {
            if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face))
                return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
        }
        if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null))
            return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        return null;
    }

    public SpeedTier getCurrentTier()    { return currentTier; }
    public Set<BlockPos> getMembers()    { return Collections.unmodifiableSet(members); }

    private int rrPointer = 0;
}
