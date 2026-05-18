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

import java.util.*;

public class PipeNetwork {

    public enum SpeedTier { SLOW, NORMAL, FAST, TURBO, HYPER, ULTRA }


    private static final Map<BlockPos, PipeNetwork> NETWORKS = new HashMap<>();
    private static int globalTick = 0;

    private final Set<BlockPos> members = new HashSet<>();
    private int bucket;
    private SpeedTier currentTier = SpeedTier.SLOW;
    private int rrPointer = 0;
    private int lastTransferTick = -1;
    private SpeedTier lastEffectiveTier = SpeedTier.SLOW;

    public static PipeNetwork getNetwork(World world, BlockPos pos) { return NETWORKS.get(pos); }
    public static PipeNetwork getNetwork(BlockPos pos)              { return NETWORKS.get(pos); }

    public static void serverTick() {
        globalTick++;
    }

    // -----------------------------------------------------------------------
    // Network management
    // -----------------------------------------------------------------------

    public static void onPipeAdded(World world, BlockPos pos) {
        Block addedBlock = world.getBlockState(pos).getBlock();
        PipeColor addedColor = (addedBlock instanceof BlockItemPipe)
                ? ((BlockItemPipe) addedBlock).pipeColor : null;

        Set<PipeNetwork> neighbours = new HashSet<>();
        for (EnumFacing face : EnumFacing.VALUES) {
            BlockPos neighbourPos = pos.offset(face);
            if (addedColor != null) {
                Block neighbourBlock = world.getBlockState(neighbourPos).getBlock();
                if (!(neighbourBlock instanceof BlockItemPipe)) continue;
                if (((BlockItemPipe) neighbourBlock).pipeColor != addedColor) continue;
            }
            PipeNetwork n = NETWORKS.get(neighbourPos);
            if (n != null) neighbours.add(n);
        }

        if (neighbours.isEmpty()) {
            PipeNetwork network = new PipeNetwork();
            network.bucket = NETWORKS.size() % Math.max(1, getEffectiveTickRate(network));
            network.members.add(pos);
            NETWORKS.put(pos, network);
        } else if (neighbours.size() == 1) {
            PipeNetwork network = neighbours.iterator().next();
            network.members.add(pos);
            NETWORKS.put(pos, network);
        } else {
            PipeNetwork kept = neighbours.iterator().next();
            for (PipeNetwork other : neighbours) {
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
        PipeNetwork network = NETWORKS.get(pos);
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

        network.members.clear();
        network.members.addAll(visited);
        for (BlockPos memberPos : visited) NETWORKS.put(memberPos, network);

        PipeNetwork newNetwork = new PipeNetwork();
        for (BlockPos memberPos : remaining) {
            newNetwork.members.add(memberPos);
            NETWORKS.put(memberPos, newNetwork);
        }
    }

    // -----------------------------------------------------------------------
    // Tick
    // -----------------------------------------------------------------------

    public boolean isMyTick() {
        if (globalTick == lastTransferTick) return false;
        if (currentTier != lastEffectiveTier) {
            lastEffectiveTier = currentTier;
            bucket = globalTick;
            lastTransferTick = globalTick;
            return true;
        }
        lastTransferTick = globalTick;
        int rate = getEffectiveTickRate(this);
        return (globalTick % rate) == (bucket % rate);
    }

    /**
     * Called each tick by the master pipe TE.
     * Scans all member faces for a directly adjacent conduit and draws up to
     * 1000 FE from it. The amount actually extracted determines the tier for
     * this tick. Stops at the first conduit face that yields any FE.
     */
    /**
     * Scans all adjacent motor blocks (TileEntityConduitBuffer), sorted highest tier first.
     * Tries each in order — if a motor has enough FE for its tier cost, drains it and sets
     * the pipe tier. Falls back to the next motor down if the current one is insufficient.
     * If no motor can supply, runs SLOW (free).
     */
    public void drainFromConduit(World world) {
        // Collect all adjacent motors with their tiers
        java.util.List<MotorEntry> motors = new java.util.ArrayList<>();
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

        // Sort highest tier first
        motors.sort((a, b) -> b.tier.ordinal() - a.tier.ordinal());

        // Try each motor in order, fall back down the list
        for (MotorEntry entry : motors) {
            int cost = ForgeConfigHandler.getFeCost(entry.tier);
            if (cost == 0) {
                // SLOW motor — always succeeds, no drain
                currentTier = SpeedTier.SLOW;
                return;
            }
            if (entry.storage.getEnergyStored() >= cost) {
                entry.storage.extractEnergy(cost, false);
                currentTier = entry.tier;
                return;
            }
        }

        // All motors insufficient
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

            for (int slot = 0; slot < source.getSlots(); slot++) {
                ItemStack stack = source.extractItem(slot, maxTransfer, true);
                if (stack.isEmpty()) continue;

                for (int attempt = 0; attempt < outputs.size(); attempt++) {
                    BlockPos destPos = outputs.get(rrPointer % outputs.size());
                    rrPointer++;

                    IItemHandler dest = getInventoryAtPos(world, destPos);
                    if (dest == null) continue;

                    for (int outSlot = 0; outSlot < dest.getSlots(); outSlot++) {
                        ItemStack remaining = dest.insertItem(outSlot, stack, true);
                        if (remaining.isEmpty()) {
                            source.extractItem(slot, stack.getCount(), false);
                            dest.insertItem(outSlot, stack, false);
                            attempt = outputs.size();
                            break;
                        }
                    }
                }
                break;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static int getEffectiveTickRate(PipeNetwork network) {
        int base = ForgeConfigHandler.getTickRate(network.currentTier);
        int penalty = network.members.size() * ForgeConfigHandler.pipes.distancePenalty;
        return Math.max(1, base + penalty);
    }

    private static int getEffectiveTransferSize(PipeNetwork network) {
        return ForgeConfigHandler.getTransferSize(network.currentTier);
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

    public SpeedTier getCurrentTier()              { return currentTier; }
    public Set<BlockPos> getMembers()              { return Collections.unmodifiableSet(members); }
}
