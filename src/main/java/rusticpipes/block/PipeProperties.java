package rusticpipes.block;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.property.IUnlistedProperty;

/**
 * Unlisted properties for item and fluid pipes.
 * Lives in common code so the blocks can build their state container on a
 * dedicated server — the client model classes implement IBakedModel and must
 * never be loaded there.
 */
public final class PipeProperties {

    private PipeProperties() {}

    public static final IUnlistedProperty<BlockPos> BLOCK_POS = new IUnlistedProperty<BlockPos>() {
        @Override public String getName() { return "block_pos"; }
        @Override public Class<BlockPos> getType() { return BlockPos.class; }
        @Override public boolean isValid(BlockPos value) { return true; }
        @Override public String valueToString(BlockPos value) { return value.toString(); }
    };

    public static final IUnlistedProperty<PipeColor> PIPE_COLOR = new IUnlistedProperty<PipeColor>() {
        @Override public String getName() { return "pipe_color"; }
        @Override public Class<PipeColor> getType() { return PipeColor.class; }
        @Override public boolean isValid(PipeColor value) { return true; }
        @Override public String valueToString(PipeColor value) { return value.name(); }
    };

    // One unlisted Integer property per face — replaces the listed PropertyInteger fields
    // that were causing the combinatorial explosion of blockstate permutations.
    public static final IUnlistedProperty<Integer> CON_NORTH = conProp("con_north");
    public static final IUnlistedProperty<Integer> CON_SOUTH = conProp("con_south");
    public static final IUnlistedProperty<Integer> CON_EAST  = conProp("con_east");
    public static final IUnlistedProperty<Integer> CON_WEST  = conProp("con_west");
    public static final IUnlistedProperty<Integer> CON_UP    = conProp("con_up");
    public static final IUnlistedProperty<Integer> CON_DOWN  = conProp("con_down");

    /** Extra unlisted property, only on fluid pipes. */
    public static final IUnlistedProperty<Integer> FLUID_COLOR = new IUnlistedProperty<Integer>() {
        @Override public String getName()                   { return "fluid_color"; }
        @Override public Class<Integer> getType()           { return Integer.class; }
        @Override public boolean isValid(Integer value)     { return true; }
        @Override public String valueToString(Integer value){ return value.toString(); }
    };

    private static IUnlistedProperty<Integer> conProp(String name) {
        return new IUnlistedProperty<Integer>() {
            @Override public String getName() { return name; }
            @Override public Class<Integer> getType() { return Integer.class; }
            @Override public boolean isValid(Integer value) { return value >= 0 && value <= 5; }
            @Override public String valueToString(Integer value) { return value.toString(); }
        };
    }

    /** Returns the connection unlisted property for the given face. */
    public static IUnlistedProperty<Integer> getConProperty(EnumFacing face) {
        switch (face) {
            case NORTH: return CON_NORTH;
            case SOUTH: return CON_SOUTH;
            case EAST:  return CON_EAST;
            case WEST:  return CON_WEST;
            case UP:    return CON_UP;
            case DOWN:  return CON_DOWN;
            default:    return CON_NORTH;
        }
    }
}
