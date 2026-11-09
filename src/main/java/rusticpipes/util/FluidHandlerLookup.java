package rusticpipes.util;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import rusticpipes.compat.CauldronCompat;

import javax.annotation.Nullable;

/**
 * Finds a fluid handler on a block: cauldrons first (they need a level-based adapter,
 * see {@link CauldronCompat}), then the Forge fluid capability.
 */
public final class FluidHandlerLookup {

    private FluidHandlerLookup() {}

    /** True if pipes can exchange fluid with the block at {@code pos} through {@code side}. Safe on the client. */
    public static boolean hasFluidHandler(IBlockAccess world, BlockPos pos, @Nullable EnumFacing side) {
        if (CauldronCompat.isCauldron(world, pos)) return true;
        TileEntity te = world.getTileEntity(pos);
        return te != null && te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
    }

    @Nullable
    public static IFluidHandler get(World world, BlockPos pos, @Nullable EnumFacing side) {
        IFluidHandler cauldron = CauldronCompat.getHandler(world, pos);
        if (cauldron != null) return cauldron;
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return null;
        return te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, side);
    }

    /** Cauldrons only move whole levels (a third or quarter bucket), so they bypass the per-tick flow cap. */
    public static boolean isLevelBased(World world, BlockPos pos) {
        return CauldronCompat.isCauldron(world, pos);
    }
}
