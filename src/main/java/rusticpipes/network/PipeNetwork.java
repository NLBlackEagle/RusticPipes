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
import rusticpipes.tileentity.FaceMode;
import rusticpipes.tileentity.TileEntityItemPipe;

import java.util.*;

public class PipeNetwork {

    public enum SpeedTier { SLOW, NORMAL, FAST, TURBO }

    private static final Map<BlockPos, PipeNetwork> NETWORKS = new HashMap<>();
    private static int globalTick = 0;

    private final Set<BlockPos> members = new HashSet<>();
    private int bucket;
    private SpeedTier speedTier = SpeedTier.SLOW;
    private int rrPointer = 0;
    private int lastTransferTick = -1;

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

    public boolean isMyTick() {
        if (globalTick == lastTransferTick) return false;
        lastTransferTick = globalTick;
        int rate = getEffectiveTickRate(this);
        return (globalTick % rate) == (bucket % rate);
    }

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


    private static int getEffectiveTransferSize(PipeNetwork network) {
        switch (network.speedTier) {
            case TURBO:  return ForgeConfigHandler.server.pipeTransferSizeTurbo;
            case FAST:   return ForgeConfigHandler.server.pipeTransferSizeFast;
            case NORMAL: return ForgeConfigHandler.server.pipeTransferSizeNormal;
            default:     return ForgeConfigHandler.server.pipeTransferSizeSlow;
        }
    }

    public void cycleSpeedTier() {
        switch (speedTier) {
            case SLOW:   speedTier = SpeedTier.NORMAL; break;
            case NORMAL: speedTier = SpeedTier.FAST;   break;
            case FAST:   speedTier = SpeedTier.TURBO;  break;
            default:     speedTier = SpeedTier.SLOW;   break;
        }
    }

    public SpeedTier getSpeedTier() { return speedTier; }

    public void transferItems(World world) {
        Set<BlockPos> inputPositions  = new LinkedHashSet<>();
        Set<BlockPos> outputPositions = new LinkedHashSet<>();

        for (BlockPos memberPos : members) {
            IBlockState state = world.getBlockState(memberPos);
            if (!(state.getBlock() instanceof BlockItemPipe)) continue;

            TileEntity te = world.getTileEntity(memberPos);
            if (!(te instanceof TileEntityItemPipe)) continue;
            TileEntityItemPipe pipe = (TileEntityItemPipe) te;

            // Check all 6 faces — use face mode to determine input/output
            for (EnumFacing face : EnumFacing.VALUES) {
                BlockPos neighbourPos = memberPos.offset(face);

                // Skip pipe-to-pipe connections — just routing
                if (world.getBlockState(neighbourPos).getBlock() instanceof BlockItemPipe) continue;

                // Only count faces with an actual inventory
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
                            attempt = outputs.size(); // exit attempt loop
                            break;
                        }
                    }
                }
                break; // one slot per input per tick
            }
        }
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

    public Set<BlockPos> getMembers() { return Collections.unmodifiableSet(members); }
}
