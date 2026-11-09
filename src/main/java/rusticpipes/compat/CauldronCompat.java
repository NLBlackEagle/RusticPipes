package rusticpipes.compat;

import net.minecraft.block.Block;
import net.minecraft.block.BlockCauldron;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Loader;
import rusticpipes.RusticPipes;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Lets fluid pipes fill and drain cauldrons.
 *
 * Vanilla cauldrons have no tile entity, and Inspirations' enhanced cauldron exposes a fluid
 * handler that only moves whole buckets (it rejects anything under 1000 mB and only drains
 * a full cauldron), so pipes moving 100 mB at a time never interacted with either.
 *
 * This handler works in cauldron levels instead: a bucket is split across the cauldron's
 * levels (3, or 4 with Inspirations' bigger cauldron) and every transfer moves whole levels,
 * with amounts chosen so that filling then draining always nets exactly 1000 mB per bucket.
 */
public final class CauldronCompat {

    private CauldronCompat() {}

    private static final int BUCKET = 1000;

    // Inspirations reflection, resolved once. All null if Inspirations is absent or changed.
    private static boolean inspInit = false;
    private static Class<?> inspTileClass;
    private static Method inspGetLevel, inspSetLevel, inspGetState, inspSetState;
    private static Method inspStateFluid, inspStateGetFluid, inspGetMax;
    private static Field inspFluidsEnabled;

    /** True if the block is a cauldron pipes can connect to. Safe on the client. */
    public static boolean isCauldron(IBlockAccess world, BlockPos pos) {
        return world.getBlockState(pos).getBlock() instanceof BlockCauldron;
    }

    /** Returns a level-based fluid handler for the cauldron at {@code pos}, or null if it is not one. */
    @Nullable
    public static IFluidHandler getHandler(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (!(state.getBlock() instanceof BlockCauldron)) return null;
        initInspirations();
        TileEntity te = world.getTileEntity(pos);
        if (inspTileClass != null && inspTileClass.isInstance(te)) return new InspirationsCauldron(te);
        if (state.getPropertyKeys().contains(BlockCauldron.LEVEL)) return new VanillaCauldron(world, pos);
        return null;
    }

    // -----------------------------------------------------------------------
    // Shared level logic
    // -----------------------------------------------------------------------

    private abstract static class LevelHandler implements IFluidHandler {
        abstract int maxLevel();
        abstract int level();
        /** Fluid currently in the cauldron, or null if empty or holding a non-fluid (dye, potion). */
        @Nullable abstract Fluid contents();
        abstract boolean accepts(Fluid fluid);
        abstract void apply(@Nullable Fluid fluid, int newLevel);

        /** mB stored at a given level; consecutive differences sum to exactly one bucket. */
        int amountAt(int level) {
            return level * BUCKET / maxLevel();
        }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            Fluid f = contents();
            FluidStack stack = (f != null && level() > 0) ? new FluidStack(f, amountAt(level())) : null;
            return new IFluidTankProperties[]{ new FluidTankProperties(stack, BUCKET) };
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null || resource.amount <= 0 || resource.tag != null) return 0;
            Fluid fluid = resource.getFluid();
            if (fluid == null || !accepts(fluid)) return 0;
            int cur = level();
            int max = maxLevel();
            if (cur >= max) return 0;
            if (cur > 0 && contents() != fluid) return 0;

            int next = cur;
            while (next < max && amountAt(next + 1) - amountAt(cur) <= resource.amount) next++;
            if (next == cur) return 0;

            if (doFill) apply(fluid, next);
            return amountAt(next) - amountAt(cur);
        }

        @Nullable
        @Override
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.tag != null || resource.getFluid() != contents()) return null;
            return drain(resource.amount, doDrain);
        }

        @Nullable
        @Override
        public FluidStack drain(int maxDrain, boolean doDrain) {
            Fluid fluid = contents();
            int cur = level();
            if (fluid == null || cur <= 0 || maxDrain <= 0) return null;

            int next = cur;
            while (next > 0 && amountAt(cur) - amountAt(next - 1) <= maxDrain) next--;
            if (next == cur) return null;

            if (doDrain) apply(fluid, next);
            return new FluidStack(fluid, amountAt(cur) - amountAt(next));
        }
    }

    // -----------------------------------------------------------------------
    // Vanilla cauldron — water only, LEVEL 0..3, no tile entity
    // -----------------------------------------------------------------------

    private static final class VanillaCauldron extends LevelHandler {
        private final World world;
        private final BlockPos pos;

        VanillaCauldron(World world, BlockPos pos) {
            this.world = world;
            this.pos = pos;
        }

        @Override int maxLevel() { return 3; }

        @Override
        int level() {
            IBlockState state = world.getBlockState(pos);
            return state.getPropertyKeys().contains(BlockCauldron.LEVEL) ? state.getValue(BlockCauldron.LEVEL) : 0;
        }

        @Nullable @Override Fluid contents() { return level() > 0 ? FluidRegistry.WATER : null; }

        @Override boolean accepts(Fluid fluid) { return fluid == FluidRegistry.WATER; }

        @Override
        void apply(@Nullable Fluid fluid, int newLevel) {
            IBlockState state = world.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof BlockCauldron) ((BlockCauldron) block).setWaterLevel(world, pos, state, newLevel);
        }
    }

    // -----------------------------------------------------------------------
    // Inspirations enhanced cauldron — any fluid (if enabled), 3 or 4 levels
    // -----------------------------------------------------------------------

    private static final class InspirationsCauldron extends LevelHandler {
        private final TileEntity te;

        InspirationsCauldron(TileEntity te) { this.te = te; }

        @Override
        int maxLevel() {
            Object max = invoke(inspGetMax, null);
            return max instanceof Integer && (Integer) max > 0 ? (Integer) max : 3;
        }

        @Override
        int level() {
            Object lvl = invoke(inspGetLevel, te);
            return lvl instanceof Integer ? (Integer) lvl : 0;
        }

        @Nullable
        @Override
        Fluid contents() {
            if (level() <= 0) return null;
            Object state = invoke(inspGetState, te);
            Object fluid = state == null ? null : invoke(inspStateGetFluid, state);
            return fluid instanceof Fluid ? (Fluid) fluid : null;
        }

        @Override
        boolean accepts(Fluid fluid) {
            if (fluid == FluidRegistry.WATER) return true;
            try {
                return inspFluidsEnabled == null || inspFluidsEnabled.getBoolean(null);
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        void apply(@Nullable Fluid fluid, int newLevel) {
            // Set the contents first: setFluidLevel(0) resets the state to water by itself.
            if (newLevel > 0 && fluid != null) {
                Object state = invoke(inspStateFluid, null, fluid);
                if (state != null) invoke(inspSetState, te, state, true);
            }
            invoke(inspSetLevel, te, newLevel);
        }
    }

    // -----------------------------------------------------------------------
    // Reflection helpers
    // -----------------------------------------------------------------------

    private static synchronized void initInspirations() {
        if (inspInit) return;
        inspInit = true;
        if (!Loader.isModLoaded("inspirations")) return;
        try {
            Class<?> tile  = Class.forName("knightminer.inspirations.recipes.tileentity.TileCauldron");
            Class<?> state = Class.forName("knightminer.inspirations.library.recipe.cauldron.ICauldronRecipe$CauldronState");
            Class<?> reg   = Class.forName("knightminer.inspirations.library.InspirationsRegistry");
            inspGetLevel      = tile.getMethod("getFluidLevel");
            inspSetLevel      = tile.getMethod("setFluidLevel", int.class);
            inspGetState      = tile.getMethod("getState");
            inspSetState      = tile.getMethod("setState", state, boolean.class);
            inspStateFluid    = state.getMethod("fluid", Fluid.class);
            inspStateGetFluid = state.getMethod("getFluid");
            inspGetMax        = reg.getMethod("getCauldronMax");
            try {
                inspFluidsEnabled = Class.forName("knightminer.inspirations.common.Config").getField("enableCauldronFluids");
            } catch (Exception ignored) {
                inspFluidsEnabled = null; // older/newer build without the toggle: allow all fluids
            }
            inspTileClass = tile;
        } catch (Exception e) {
            inspTileClass = null;
            RusticPipes.LOGGER.warn("Inspirations cauldron support disabled, API changed: {}", e.toString());
        }
    }

    @Nullable
    private static Object invoke(Method m, @Nullable Object target, Object... args) {
        try {
            return m.invoke(target, args);
        } catch (Exception e) {
            return null;
        }
    }
}
