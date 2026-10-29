package rusticpipes.block;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.property.IUnlistedProperty;

/**
 * Unlisted properties for conduits. Common-side for the same reason as
 * {@link PipeProperties}: ConduitModel is client-only.
 */
public final class ConduitProperties {

    private ConduitProperties() {}

    public static final IUnlistedProperty<BlockPos> BLOCK_POS    = unlisted("conduit_block_pos", BlockPos.class);
    public static final IUnlistedProperty<Integer>  CONDUIT_TIER = unlisted("conduit_tier",      Integer.class);
    public static final IUnlistedProperty<Integer>  CON_NORTH    = unlisted("conduit_con_north", Integer.class);
    public static final IUnlistedProperty<Integer>  CON_SOUTH    = unlisted("conduit_con_south", Integer.class);
    public static final IUnlistedProperty<Integer>  CON_EAST     = unlisted("conduit_con_east",  Integer.class);
    public static final IUnlistedProperty<Integer>  CON_WEST     = unlisted("conduit_con_west",  Integer.class);
    public static final IUnlistedProperty<Integer>  CON_UP       = unlisted("conduit_con_up",    Integer.class);
    public static final IUnlistedProperty<Integer>  CON_DOWN     = unlisted("conduit_con_down",  Integer.class);
    public static final IUnlistedProperty<Boolean>  POWERED      = unlisted("conduit_powered",   Boolean.class);

    private static <T> IUnlistedProperty<T> unlisted(String name, Class<T> type) {
        return new IUnlistedProperty<T>() {
            @Override public String getName() { return name; }
            @Override public Class<T> getType() { return type; }
            @Override public boolean isValid(T value) { return true; }
            @Override public String valueToString(T value) { return value == null ? "null" : value.toString(); }
        };
    }

    public static IUnlistedProperty<Integer> getConProperty(EnumFacing face) {
        switch (face) {
            case NORTH: return CON_NORTH; case SOUTH: return CON_SOUTH;
            case EAST:  return CON_EAST;  case WEST:  return CON_WEST;
            case UP:    return CON_UP;    default:    return CON_DOWN;
        }
    }
}
