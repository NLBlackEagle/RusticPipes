package rusticpipes.network;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.handlers.ForgeConfigHandler;

import java.util.*;

public class PipeNetwork {

    private static final Map<BlockPos, PipeNetwork> NETWORKS = new HashMap<>();
    private final Set<BlockPos> members = new HashSet<>();
    private static int globalTick = 0;
    private int bucket;

    public static PipeNetwork getNetwork(World world, BlockPos pos) {
        return NETWORKS.get(pos);
    }

    public static void serverTick() {
        globalTick++;
    }

    public boolean isMyTick() {
        int rate = ForgeConfigHandler.server.pipeTickRate;
        return (globalTick % rate) == (bucket % rate);
    }

    public static void onPipeAdded(World world, BlockPos pos) {
        Set<PipeNetwork> neighbours = new HashSet<>();

        for (EnumFacing face : EnumFacing.VALUES) {
            BlockPos neighbourPos = pos.offset(face);
            PipeNetwork neighbourNetwork = NETWORKS.get(neighbourPos);
            if (neighbourNetwork != null) {
                neighbours.add(neighbourNetwork);
            }
        }

        // Now decide based on how many neighbours were found
        if (neighbours.isEmpty()) {

            PipeNetwork newNetwork = new PipeNetwork();
            newNetwork.bucket = NETWORKS.size() % Math.max(1, ForgeConfigHandler.server.pipeTickRate);
            newNetwork.members.add(pos);
            NETWORKS.put(pos, newNetwork);

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

        // gets the saved pipe network through location and if not found returns
        PipeNetwork network = NETWORKS.get(pos);
        if (network == null) return;

        // remove from global position lookup so other pipes can't route through here
        NETWORKS.remove(pos);
        // remove from this network's member set so flood fill doesn't count it
        network.members.remove(pos);

        // if no members left, network is gone entirely - nothing to split
        if (network.members.isEmpty()) return;

        // flood fill - starting from one seed position, find every
        // pipe still reachable by stepping through connected neighbours
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

        // no split - done
        if (visited.size() == network.members.size()) return;

        // calculate remaining FIRST - before touching network.members
        Set<BlockPos> remaining = new HashSet<>(network.members);
        remaining.removeAll(visited);

        // THEN update original network to only contain reachable fragment
        network.members.clear();
        network.members.addAll(visited);
        for (BlockPos memberPos : visited) {
            NETWORKS.put(memberPos, network);
        }


        // create new network for disconnected fragment
        PipeNetwork newNetwork = new PipeNetwork();
        for (BlockPos memberPos : remaining) {
            newNetwork.members.add(memberPos);
            NETWORKS.put(memberPos, newNetwork);
        }
    }

    public void reverseNetwork(World world) {

        for (BlockPos memberPos : members) {
            IBlockState state = world.getBlockState(memberPos);
            EnumFacing currentFacing = state.getValue(BlockItemPipe.FACING);
            world.setBlockState(memberPos, state.withProperty(BlockItemPipe.FACING, currentFacing.getOpposite()));
        }
    }
}
