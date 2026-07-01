package rusticpipes.util;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * Immutable dimension-aware position key for use in static network maps.
 * Prevents cross-dimension network collisions when pipes exist at identical
 * coordinates in different dimensions (Overworld vs Nether vs modded dims).
 */
public final class DimPos {

    public final int  dimId;
    public final long posLong;

    public DimPos(int dimId, BlockPos pos) {
        this.dimId   = dimId;
        this.posLong = pos.toLong();
    }

    /** Create from a full {@link World} — the typical server-side case. */
    public static DimPos of(World world, BlockPos pos) {
        return new DimPos(world.provider.getDimension(), pos);
    }

    /**
     * Create from an {@link IBlockAccess}.  This is used in block render
     * callbacks where the world parameter is typed as IBlockAccess.
     * Falls back to dimension 0 (Overworld) when the cast isn't possible —
     * which only happens in isolated unit/test environments.
     */
    public static DimPos of(IBlockAccess world, BlockPos pos) {
        int dim = (world instanceof World) ? ((World) world).provider.getDimension() : 0;
        return new DimPos(dim, pos);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DimPos)) return false;
        DimPos that = (DimPos) o;
        return dimId == that.dimId && posLong == that.posLong;
    }

    @Override
    public int hashCode() {
        return 31 * dimId + Long.hashCode(posLong);
    }

    @Override
    public String toString() {
        return "DimPos[dim=" + dimId + ", pos=" + BlockPos.fromLong(posLong) + "]";
    }
}
