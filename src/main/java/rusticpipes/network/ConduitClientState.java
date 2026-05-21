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

    public static void set(BlockPos pos, boolean powered) {
        POWERED.put(pos, powered);
    }

    public static boolean isPowered(BlockPos pos) {
        Boolean v = POWERED.get(pos);
        return v != null && v;
    }

    public static void remove(BlockPos pos) {
        POWERED.remove(pos);
    }
}
