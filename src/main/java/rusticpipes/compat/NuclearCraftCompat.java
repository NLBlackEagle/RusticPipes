package rusticpipes.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.common.Loader;
import rusticpipes.RusticPipes;
import rusticpipes.handlers.ForgeConfigHandler;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Soft-dependency bridge for NuclearCraft 2.x (1.12.2-2.19a).
 *
 * APIs confirmed by inspecting the actual jar bytecode.
 *
 * Fluid / item radiation level:
 *   nc.radiation.RadiationHelper.getRadiationFromFluid(FluidStack, double tickFraction) → double
 *   nc.radiation.RadiationHelper.getRadiationFromStack(ItemStack, double tickFraction)  → double
 *
 * Player radiation application — uses NC's own transfer path so the geiger counter,
 * totalRads accumulation, and potion effects all work correctly:
 *   1. Construct nc.capability.radiation.source.RadiationSource(double radiationLevel)
 *   2. nc.radiation.RadiationHelper.getEntityRadiation(EntityLivingBase)  → IEntityRads
 *   3. nc.radiation.RadiationHelper.transferRadsFromSourceToEntity(
 *          IRadiationSource, IEntityRads, EntityLivingBase, int sourceCount)
 *      — this is the same call NC uses internally; it updates radiationLevel (geiger) AND totalRads.
 */
public final class NuclearCraftCompat {

    private NuclearCraftCompat() {}

    private static Boolean loaded = null;

    // RadiationHelper static methods
    @Nullable private static Method getRadiationFromFluidMethod          = null;
    @Nullable private static Method getRadiationFromStackMethod          = null;
    @Nullable private static Method getEntityRadiationMethod             = null;
    @Nullable private static Method transferRadsFromSourceToEntityMethod = null;

    // RadiationSource instance methods — for explicit field initialisation
    @Nullable private static Method setRadiationLevelMethod  = null;
    @Nullable private static Method setRadiationBufferMethod = null;
    @Nullable private static Method getRadiationLevelMethod  = null;
    @Nullable private static Method getRadiationBufferMethod = null;

    // IEntityRads instance method
    @Nullable private static Method getTotalRadsMethod = null;

    // RadiationSource constructor
    @Nullable private static Constructor<?> radiationSourceCtor = null;

    // -----------------------------------------------------------------------
    // Initialisation
    // -----------------------------------------------------------------------

    public static boolean isLoaded() {
        if (loaded != null) return loaded;

        if (!Loader.isModLoaded("nuclearcraft")) {
            loaded = false;
            return false;
        }

        try {
            Class<?> helper        = Class.forName("nc.radiation.RadiationHelper");
            Class<?> iEntityRads   = Class.forName("nc.capability.radiation.entity.IEntityRads");
            Class<?> iRadSrc       = Class.forName("nc.capability.radiation.source.IRadiationSource");
            Class<?> radSource     = Class.forName("nc.capability.radiation.source.RadiationSource");
            Class<?> entityLiving  = net.minecraft.entity.EntityLivingBase.class;

            getRadiationFromFluidMethod = helper.getMethod(
                    "getRadiationFromFluid", FluidStack.class, double.class);
            getRadiationFromStackMethod = helper.getMethod(
                    "getRadiationFromStack", ItemStack.class, double.class);
            getEntityRadiationMethod = helper.getMethod(
                    "getEntityRadiation", entityLiving);
            transferRadsFromSourceToEntityMethod = helper.getMethod(
                    "transferRadsFromSourceToEntity",
                    iRadSrc, iEntityRads, entityLiving, int.class);

            // RadiationSource(double initialRadiationLevel)
            radiationSourceCtor      = radSource.getDeclaredConstructor(double.class);
            radiationSourceCtor.setAccessible(true);
            setRadiationLevelMethod  = radSource.getMethod("setRadiationLevel",  double.class);
            setRadiationBufferMethod = radSource.getMethod("setRadiationBuffer", double.class);
            getRadiationLevelMethod  = radSource.getMethod("getRadiationLevel");
            getRadiationBufferMethod = radSource.getMethod("getRadiationBuffer");
            getTotalRadsMethod       = iEntityRads.getMethod("getTotalRads");

            loaded = true;
            RusticPipes.LOGGER.info(
                    "[RusticPipes] NuclearCraft 2.x detected — radiation integration enabled.");
        } catch (Exception e) {
            RusticPipes.LOGGER.warn(
                    "[RusticPipes] NuclearCraft detected but radiation API lookup failed: {}",
                    e.getMessage());
            loaded = false;
        }

        return loaded;
    }

    // -----------------------------------------------------------------------
    // Fluid radiation
    // -----------------------------------------------------------------------

    /**
     * Returns the rads/t for this fluid stack, as reported by NC's own
     * RadiationHelper.getRadiationFromFluid. The fluid amount in the stack
     * is used directly — no separate fill-fraction scaling needed.
     */
    public static double getFluidRadiation(FluidStack stack) {
        if (!isLoaded() || stack == null || getRadiationFromFluidMethod == null) {
            RusticPipes.LOGGER.info("[RadDebug] getFluidRadiation early-exit: isLoaded={} stack={} method={}",
                    isLoaded(), stack != null ? stack.getFluid().getName() : "null", getRadiationFromFluidMethod != null);
            return 0;
        }
        try {
            Object r = getRadiationFromFluidMethod.invoke(null, stack, 1.0D);
            double val = r instanceof Number ? ((Number) r).doubleValue() : 0;
            RusticPipes.LOGGER.info("[RadDebug] getRadiationFromFluid({}, 1.0) = {}", stack.getFluid().getName(), val);
            return val;
        } catch (Exception e) {
            RusticPipes.LOGGER.info("[RadDebug] getRadiationFromFluid threw: {}", e.getMessage());
            return 0;
        }
    }

    public static boolean isFluidRadioactive(FluidStack stack) {
        return getFluidRadiation(stack) > 0;
    }

    // -----------------------------------------------------------------------
    // Item radiation
    // -----------------------------------------------------------------------

    public static double getItemRadiation(ItemStack stack) {
        if (!isLoaded() || stack.isEmpty() || getRadiationFromStackMethod == null) return 0;
        try {
            Object r = getRadiationFromStackMethod.invoke(null, stack, 1.0D);
            return r instanceof Number ? ((Number) r).doubleValue() : 0;
        } catch (Exception e) { return 0; }
    }

    public static boolean isItemRadioactive(ItemStack stack) {
        return getItemRadiation(stack) > 0;
    }

    // -----------------------------------------------------------------------
    // Irradiation
    // -----------------------------------------------------------------------

    public static void irradiateNearbyPlayers(World world, BlockPos pos,
                                              double radiationPerTick, int range) {
        if (!isLoaded() || world.isRemote || radiationPerTick <= 0) return;

        double multiplier = ForgeConfigHandler.fluid.radiationMultiplier;
        if (multiplier <= 0) return;

        AxisAlignedBB aabb = new AxisAlignedBB(pos).grow(range);
        List<EntityPlayer> players = world.getEntitiesWithinAABB(EntityPlayer.class, aabb);
        RusticPipes.LOGGER.info("[RadDebug] irradiateNearbyPlayers: rads/t={} range={} players found={}",
                radiationPerTick, range, players.size());
        for (EntityPlayer player : players) {
            double dx = player.posX - (pos.getX() + 0.5);
            double dy = player.posY - (pos.getY() + 0.5);
            double dz = player.posZ - (pos.getZ() + 0.5);
            double dist2 = Math.max(0.25, dx*dx + dy*dy + dz*dz);
            double effective = radiationPerTick * multiplier / dist2;
            RusticPipes.LOGGER.info("[RadDebug] Applying {} rads to {} (dist2={})", effective, player.getName(), dist2);
            applyRadiation(player, effective);
        }
    }

    private static void applyRadiation(EntityPlayer player, double rads) {
        if (rads <= 0) return;
        if (radiationSourceCtor == null
                || getEntityRadiationMethod == null
                || transferRadsFromSourceToEntityMethod == null) {
            RusticPipes.LOGGER.info("[RadDebug] applyRadiation blocked: ctor={} getEntity={} transfer={}",
                    radiationSourceCtor != null, getEntityRadiationMethod != null,
                    transferRadsFromSourceToEntityMethod != null);
            return;
        }
        try {
            Object tempSource  = radiationSourceCtor.newInstance(rads);

            // RadiationSource(double) initialises radiationLevel; transferRadsFromSourceToEntity
            // reads radiationBuffer. Explicitly set both so one of them is definitely non-zero.
            if (setRadiationLevelMethod != null) setRadiationLevelMethod.invoke(tempSource, rads);
            if (setRadiationBufferMethod != null) setRadiationBufferMethod.invoke(tempSource, rads);
            RusticPipes.LOGGER.info("[RadDebug] tempSource level={} buffer={}",
                    getRadiationLevelMethod != null ? getRadiationLevelMethod.invoke(tempSource) : "?",
                    getRadiationBufferMethod != null ? getRadiationBufferMethod.invoke(tempSource) : "?");

            Object entityRads = getEntityRadiationMethod.invoke(null, player);
            if (entityRads == null) { RusticPipes.LOGGER.info("[RadDebug] entityRads is null!"); return; }

            double before = ((Number) getTotalRadsMethod.invoke(entityRads)).doubleValue();
            transferRadsFromSourceToEntityMethod.invoke(null, tempSource, entityRads, player, 1);
            double after  = ((Number) getTotalRadsMethod.invoke(entityRads)).doubleValue();
            RusticPipes.LOGGER.info("[RadDebug] totalRads before={} after={} delta={}", before, after, after - before);
        } catch (Exception e) {
            RusticPipes.LOGGER.info("[RadDebug] applyRadiation exception: {} — {}", e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
