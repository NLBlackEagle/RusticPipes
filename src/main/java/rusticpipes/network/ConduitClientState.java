package rusticpipes.network;

import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.HashMap;
import java.util.Map;

/**
 * Client-side mirror of conduit power state.
 * Populated by PacketConduitPower so that getExtendedState can pick the
 * correct connector texture on a dedicated server (where ConduitNetwork
 * only exists server-side).
 */
@SideOnly(Side.CLIENT)
public class ConduitClientState {

    private static final Map<BlockPos, Boolean> POWERED = new HashMap<>();
    /** Throughput-based spark level per conduit (0 = no flow, 1–10 = flowing). */
    private static final Map<BlockPos, Integer> SPARK_LEVEL = new HashMap<>();

    public static void set(BlockPos pos, boolean powered) {
        POWERED.put(pos, powered);
    }

    public static boolean isPowered(BlockPos pos) {
        Boolean v = POWERED.get(pos);
        return v != null && v;
    }

    public static void setSparkLevel(BlockPos pos, int level) {
        if (level <= 0) SPARK_LEVEL.remove(pos);
        else SPARK_LEVEL.put(pos, level);
    }

    public static int getSparkLevel(BlockPos pos) {
        Integer v = SPARK_LEVEL.get(pos);
        return v == null ? 0 : v;
    }

    public static void remove(BlockPos pos) {
        POWERED.remove(pos);
        SPARK_LEVEL.remove(pos);
    }

    /**
     * Keys are positions only, so this must be cleared whenever the client world changes
     * (disconnect or dimension change); chunk data re-seeds it via TileEntityConduit's update tag.
     */
    public static void clear() {
        POWERED.clear();
        SPARK_LEVEL.clear();
    }
}
