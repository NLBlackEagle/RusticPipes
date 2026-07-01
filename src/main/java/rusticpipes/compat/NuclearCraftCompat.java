package rusticpipes.compat;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;
import rusticpipes.RusticPipes;
import rusticpipes.handlers.ForgeConfigHandler;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Soft-dependency bridge for NuclearCraft (original and Overhauled variants).
 *
 * All NuclearCraft type references are confined to this class and only reached
 * after {@link #isLoaded()} returns true, so the mod loads cleanly without NC.
 *
 * Item radiation:
 *   Items whose {@link net.minecraft.item.Item} implements nc.api.radiation.IRadiationSource
 *   will emit their declared radiation level to nearby players.
 *
 * Fluid radiation:
 *   Fluids whose registry name is in the NC radioactive fluid list (detected by
 *   checking whether the fluid's class extends nc.fluid.FluidRadioactive, then
 *   reading the radiation level via reflection) will irradiate nearby players.
 *   Falls back to a config-provided radiation level if the field can't be read.
 */
public final class NuclearCraftCompat {

    private NuclearCraftCompat() {}

    // -----------------------------------------------------------------------
    // Reflection handles — populated once on first isLoaded() call
    // -----------------------------------------------------------------------

    private static Boolean loaded = null;

    /** nc.api.radiation.IRadiationSource */
    @Nullable private static Class<?> iRadiationSourceClass   = null;
    /** IRadiationSource#getRadiationLevel(ItemStack) */
    @Nullable private static Method   getItemRadiationMethod  = null;

    /** nc.fluid.FluidRadioactive */
    @Nullable private static Class<?> fluidRadioactiveClass   = null;
    /** FluidRadioactive#radiation (double field) */
    @Nullable private static java.lang.reflect.Field fluidRadiationField = null;

    /**
     * Player radiation application — tried in priority order at runtime because
     * NCCO and the original NC have different APIs.
     *
     * Priority 1 (NCCO): nc.radiation.player.PlayerDataHandler.getPlayerData(EntityPlayer)
     *   then playerData.addRadiation(double, boolean)
     * Priority 2 (original NC): nc.radiation.RadiationHelper.addPlayerRadiation(EntityPlayerMP, double)
     */
    @Nullable private static Method getPlayerDataMethod      = null;
    @Nullable private static Method addRadiationOnDataMethod = null;
    @Nullable private static Method addPlayerRadiationMethod = null; // static fallback

    // -----------------------------------------------------------------------
    // Availability check — cached after first call
    // -----------------------------------------------------------------------

    public static boolean isLoaded() {
        if (loaded != null) return loaded;

        try {
            // Core radiation item interface — present in both NC and NCCO
            iRadiationSourceClass  = Class.forName("nc.api.radiation.IRadiationSource");
            getItemRadiationMethod = iRadiationSourceClass.getMethod("getRadiationLevel", ItemStack.class);
        } catch (ClassNotFoundException e) {
            // NuclearCraft not present at all
            loaded = false;
            return false;
        } catch (NoSuchMethodException e) {
            RusticPipes.LOGGER.warn("[RusticPipes] NuclearCraft found but IRadiationSource API changed: {}", e.getMessage());
        }

        // Radioactive fluid base class (NCCO)
        try {
            fluidRadioactiveClass = Class.forName("nc.fluid.FluidRadioactive");
            fluidRadiationField   = fluidRadioactiveClass.getField("radiation");
        } catch (Exception ignored) {
            // Not fatal — fluid radiation will fall back to config default
        }

        // Player radiation — try NCCO PlayerDataHandler first
        try {
            Class<?> pdh        = Class.forName("nc.radiation.player.PlayerDataHandler");
            getPlayerDataMethod = pdh.getMethod("getPlayerData", EntityPlayer.class);
            // addRadiation signature: (double amount, boolean simulate) in NCCO
            Class<?> pdClass    = Class.forName("nc.radiation.player.PlayerData");
            addRadiationOnDataMethod = pdClass.getMethod("addRadiation", double.class, boolean.class);
        } catch (Exception ignored) {
            // Try legacy static RadiationHelper
            try {
                Class<?> helperClass    = Class.forName("nc.radiation.RadiationHelper");
                addPlayerRadiationMethod = helperClass.getMethod("addPlayerRadiation",
                        net.minecraft.entity.player.EntityPlayerMP.class, double.class);
            } catch (Exception ignored2) {
                RusticPipes.LOGGER.warn("[RusticPipes] NuclearCraft detected but no compatible radiation API found.");
            }
        }

        loaded = true;
        RusticPipes.LOGGER.info("[RusticPipes] NuclearCraft detected — radiation integration enabled.");
        return true;
    }

    // -----------------------------------------------------------------------
    // Item radiation
    // -----------------------------------------------------------------------

    /**
     * Returns the radiation level (Sv/t) for this stack, or 0 if not radioactive
     * or NuclearCraft is not loaded.
     */
    public static double getItemRadiation(ItemStack stack) {
        if (!isLoaded() || stack.isEmpty()) return 0;
        if (iRadiationSourceClass  == null || getItemRadiationMethod == null) return 0;
        if (!iRadiationSourceClass.isInstance(stack.getItem())) return 0;
        try {
            Object result = getItemRadiationMethod.invoke(stack.getItem(), stack);
            return result instanceof Number ? ((Number) result).doubleValue() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** True if this stack has any radiation. */
    public static boolean isItemRadioactive(ItemStack stack) {
        return getItemRadiation(stack) > 0;
    }

    // -----------------------------------------------------------------------
    // Fluid radiation
    // -----------------------------------------------------------------------

    /**
     * Returns the radiation level (Sv/t per 1000 mB) for this fluid,
     * or 0 if not radioactive or NuclearCraft is not loaded.
     */
    public static double getFluidRadiation(FluidStack stack) {
        if (!isLoaded() || stack == null) return 0;
        if (fluidRadioactiveClass != null && fluidRadiationField != null) {
            if (fluidRadioactiveClass.isInstance(stack.getFluid())) {
                try {
                    return ((Number) fluidRadiationField.get(stack.getFluid())).doubleValue();
                } catch (Exception ignored) {}
            }
            // Not a FluidRadioactive subclass — not radioactive
            return 0;
        }
        // Reflection failed — fall back to config default if fluid name contains
        // any known NC radioactive fluid keyword
        if (ForgeConfigHandler.fluid.radiationFallbackLevel > 0) {
            String name = stack.getFluid().getName().toLowerCase();
            for (String keyword : RADIOACTIVE_FLUID_KEYWORDS) {
                if (name.contains(keyword)) return ForgeConfigHandler.fluid.radiationFallbackLevel;
            }
        }
        return 0;
    }

    public static boolean isFluidRadioactive(FluidStack stack) {
        return getFluidRadiation(stack) > 0;
    }

    /** Known substrings that appear in NuclearCraft radioactive fluid registry names. */
    private static final String[] RADIOACTIVE_FLUID_KEYWORDS = {
        "uranium", "plutonium", "thorium", "neptunium", "americium", "curium",
        "berkelium", "californium", "polonium", "radium", "radon",
        "tritium", "deuterium", // some NC variants treat these as radioactive
        "fission", "nuclear_waste", "high_level_waste", "spent_fuel",
        "corium", "reactor_coolant_hot"
    };

    // -----------------------------------------------------------------------
    // Irradiation — applies radiation to nearby players
    // -----------------------------------------------------------------------

    /**
     * Irradiates all players within {@code range} blocks of {@code pos}.
     * {@code radiationPerTick} is the base Sv/t value; it is scaled by
     * {@link ForgeConfigHandler.FluidConfig#radiationMultiplier} and by
     * 1/(distance²) before being applied.
     *
     * Called server-side only.
     */
    public static void irradiateNearbyPlayers(World world, BlockPos pos,
                                              double radiationPerTick, int range) {
        if (!isLoaded()) return;
        if (world.isRemote) return;
        if (radiationPerTick <= 0) return;

        double multiplier = ForgeConfigHandler.fluid.radiationMultiplier;
        if (multiplier <= 0) return;

        AxisAlignedBB aabb = new AxisAlignedBB(pos).grow(range);
        List<EntityPlayer> players = world.getEntitiesWithinAABB(EntityPlayer.class, aabb);

        for (EntityPlayer player : players) {
            double dx = player.posX - (pos.getX() + 0.5);
            double dy = player.posY - (pos.getY() + 0.5);
            double dz = player.posZ - (pos.getZ() + 0.5);
            double dist2 = dx*dx + dy*dy + dz*dz;
            if (dist2 < 0.25) dist2 = 0.25; // clamp to avoid explosion at zero distance

            // Radiation falls off with 1/r² from the source
            double effective = radiationPerTick * multiplier / dist2;
            applyRadiation(player, effective);
        }
    }

    /**
     * Applies {@code radiation} Sv to the given player via whichever NuclearCraft
     * API is available (NCCO PlayerDataHandler or legacy RadiationHelper).
     */
    private static void applyRadiation(EntityPlayer player, double radiation) {
        if (radiation <= 0) return;

        // Try NCCO PlayerDataHandler#getPlayerData → PlayerData#addRadiation
        if (getPlayerDataMethod != null && addRadiationOnDataMethod != null) {
            try {
                Object playerData = getPlayerDataMethod.invoke(null, player);
                if (playerData != null) {
                    addRadiationOnDataMethod.invoke(playerData, radiation, false);
                    return;
                }
            } catch (Exception ignored) {}
        }

        // Fall back to legacy static RadiationHelper#addPlayerRadiation (original NC)
        if (addPlayerRadiationMethod != null
                && player instanceof net.minecraft.entity.player.EntityPlayerMP) {
            try {
                addPlayerRadiationMethod.invoke(null, player, radiation);
            } catch (Exception ignored) {}
        }
    }
}
