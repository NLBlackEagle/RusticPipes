package rusticpipes.network;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.handlers.ForgeConfigHandler;

import java.util.*;

public class PipeNetwork {

    // -----------------------------------------------------------------------
    // Speed tiers
    // -----------------------------------------------------------------------
    public enum SpeedTier { SLOW, NORMAL, FAST, TURBO }

    // -----------------------------------------------------------------------
    // Static registry
    // -----------------------------------------------------------------------
    private static final Map<BlockPos, PipeNetwork> NETWORKS = new HashMap<>();
    private static int globalTick = 0;

    // -----------------------------------------------------------------------
    // Per-network state
    // -----------------------------------------------------------------------
    private final Set<BlockPos> members = new HashSet<>();
    private int bucket;
    private SpeedTier speedTier = SpeedTier.SLOW;
    private int rrPointer = 0;

    // -----------------------------------------------------------------------
    // Static API
    // -----------------------------------------------------------------------

    public static PipeNetwork getNetwork(World world, BlockPos pos) {
        return NETWORKS.get(pos);
    }

    public static void serverTick() {
        globalTick++;
    }

    public static void onPipeAdded(World world, BlockPos pos) {
        Set<PipeNetwork> neighbours = new HashSet<>();
        for (EnumFacing face : EnumFacing.VALUES) {
            PipeNetwork n = NETWORKS.get(pos.offset(face));
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
    // Tick scheduling
    // -----------------------------------------------------------------------

    public boolean isMyTick() {
        int rate = getEffectiveTickRate(this);
        return (globalTick % rate) == (bucket % rate);
    }

    /**
     * Effective tick rate = base tier rate + (member count * distance penalty).
     * Longer networks are inherently slower.
     */
    private static int getEffectiveTickRate(PipeNetwork network) {
        int base;
        switch (network.speedTier) {
            case TURBO:  base = ForgeConfigHandler.server.pipeTickRateTurbo;  break;
            case FAST:   base = ForgeConfigHandler.server.pipeTickRateFast;   break;
            case NORMAL: base = ForgeConfigHandler.server.pipeTickRateNormal; break;
            default:     base = ForgeConfigHandler.server.pipeTickRateSlow;   break;
        }
        int penalty = network.members.size() * ForgeConfigHandler.server.pipeDistancePenalty;
        return Math.max(1, base + penalty);
    }

    // -----------------------------------------------------------------------
    // Speed tier cycling (called from shift+right-click)
    // -----------------------------------------------------------------------

    public void cycleSpeedTier() {
        switch (speedTier) {
            case SLOW:   speedTier = SpeedTier.NORMAL; break;
            case NORMAL: speedTier = SpeedTier.FAST;   break;
            case FAST:   speedTier = SpeedTier.TURBO;  break;
            default:     speedTier = SpeedTier.SLOW;   break;
        }
    }

    public SpeedTier getSpeedTier() { return speedTier; }

    // -----------------------------------------------------------------------
    // Network-level item transfer
    // -----------------------------------------------------------------------

    /**
     * Called once per network per isMyTick() interval.
     * Scans all member pipes for input/output endpoints and transfers items
     * directly between them — no per-pipe buffering needed.
     */
    public void transferItems(World world) {
        List<IItemHandler> inputs  = new ArrayList<>();
        List<IItemHandler> outputs = new ArrayList<>();

        for (BlockPos memberPos : members) {
            IBlockState state = world.getBlockState(memberPos);
            if (!(state.getBlock() instanceof BlockItemPipe)) continue;

            EnumFacing facing = state.getValue(BlockItemPipe.FACING);

            // Input endpoint — inventory on the opposite side of FACING
            EnumFacing inputFace = facing.getOpposite();
            IItemHandler input = getInventory(world, memberPos.offset(inputFace), inputFace.getOpposite());
            if (input != null) inputs.add(input);

            // Output endpoint — inventory on the FACING side
            BlockPos outputPos = memberPos.offset(facing);
            // Only count as output if it's NOT another pipe
            if (!(world.getBlockState(outputPos).getBlock() instanceof BlockItemPipe)) {
                IItemHandler output = getInventory(world, outputPos, facing.getOpposite());
                if (output != null) outputs.add(output);
            }
        }

        if (inputs.isEmpty() || outputs.isEmpty()) return;

        int maxTransfer = ForgeConfigHandler.server.pipeTransferSize;

        for (IItemHandler source : inputs) {
            for (int slot = 0; slot < source.getSlots(); slot++) {
                ItemStack stack = source.extractItem(slot, maxTransfer, true);
                if (stack.isEmpty()) continue;

                // Round-robin across outputs
                IItemHandler dest = outputs.get(rrPointer % outputs.size());

                for (int outSlot = 0; outSlot < dest.getSlots(); outSlot++) {
                    ItemStack remaining = dest.insertItem(outSlot, stack, true);
                    if (remaining.isEmpty()) {
                        source.extractItem(slot, stack.getCount(), false);
                        dest.insertItem(outSlot, stack, false);
                        rrPointer++;
                        break;
                    }
                }
                break; // one slot per input per tick
            }
        }
    }

    // -----------------------------------------------------------------------
    // Network reversal (shift+right-click)
    // -----------------------------------------------------------------------

    public void reverseNetwork(World world) {
        for (BlockPos memberPos : members) {
            IBlockState state = world.getBlockState(memberPos);
            EnumFacing current = state.getValue(BlockItemPipe.FACING);
            world.setBlockState(memberPos,
                    state.withProperty(BlockItemPipe.FACING, current.getOpposite()));
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    @javax.annotation.Nullable
    private static IItemHandler getInventory(World world, BlockPos pos, EnumFacing face) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return null;
        if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face))
            return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
        // fallback to null-face (vanilla chests)
        if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null))
            return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        return null;
    }

    public Set<BlockPos> getMembers() { return Collections.unmodifiableSet(members); }
}
