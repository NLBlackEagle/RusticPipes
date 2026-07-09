package rusticpipes.handlers;

import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import rusticpipes.network.PipeNetwork;

import java.util.List;

/**
 * Shared tooltip logic for all RusticPipes blocks.
 *
 * Pattern:
 *   - data line(s)  — always visible, show live config values
 *   - description   — only shown when the player holds Shift
 *
 * Lang keys follow the convention:
 *   tooltip.<category>.<id>.data
 *   tooltip.<category>.<id>.description
 */
@SideOnly(Side.CLIENT)
public final class TooltipHandler {

    private static final String SHIFT_HINT = TextFormatting.DARK_GRAY + "" + TextFormatting.ITALIC
            + I18n.format("rusticpipes.tooltip.shift_for_info");

    private TooltipHandler() {}

    private static boolean isShiftHeld() {
        return net.minecraft.client.gui.GuiScreen.isShiftKeyDown();
    }

    // -----------------------------------------------------------------------
    // Pipe tooltip
    // -----------------------------------------------------------------------

    /**
     * Appends tooltip lines for an item pipe block.
     * data key    : tooltip.pipe.data
     * desc key    : tooltip.pipe.description.<colorRegistryName>
     */
    public static void addPipeTooltip(ItemStack stack, List<String> tooltip,
                                      rusticpipes.block.PipeColor color) {
        // Data — always visible, uses NONE tier (true unpowered baseline, no motor)
        int transferSize = ForgeConfigHandler.getTransferSize(PipeNetwork.SpeedTier.NONE);
        int tickRate     = ForgeConfigHandler.getTickRate(PipeNetwork.SpeedTier.NONE);
        tooltip.add(TextFormatting.GRAY
                + I18n.format("rusticpipes.tooltip.pipe.data", transferSize, tickRate));

        if (isShiftHeld()) {
            String descKey = "rusticpipes.tooltip.pipe.description." + color.registryName;
            tooltip.add(TextFormatting.DARK_GREEN
                    + I18n.format(descKey));
        } else {
            tooltip.add(SHIFT_HINT);
        }
    }

    // -----------------------------------------------------------------------
    // Conduit tooltip
    // -----------------------------------------------------------------------

    /**
     * Appends tooltip lines for a conduit (power cord) block.
     * data key    : tooltip.conduit.data
     * desc key    : tooltip.conduit.description
     */
    public static void addConduitTooltip(ItemStack stack, List<String> tooltip) {
        int fePerTick = ForgeConfigHandler.conduit.maxFePerTickPerFace;
        tooltip.add(TextFormatting.GRAY
                + I18n.format("rusticpipes.tooltip.conduit.data", fePerTick));

        if (isShiftHeld()) {
            tooltip.add(TextFormatting.DARK_GREEN
                    + I18n.format("rusticpipes.tooltip.conduit.description"));
        } else {
            tooltip.add(SHIFT_HINT);
        }
    }

    // -----------------------------------------------------------------------
    // Motor (ConduitBuffer) tooltip
    // -----------------------------------------------------------------------

    /**
     * Appends tooltip lines for a pipe motor block.
     * data key    : tooltip.pipemotor.<tierId>.data
     * desc key    : tooltip.pipemotor.<tierId>.description
     *
     * tierId maps SpeedTier → "slow","normal","fast","turbo","hyper","ultra"
     */
    public static void addMotorTooltip(ItemStack stack, List<String> tooltip,
                                       PipeNetwork.SpeedTier tier) {
        String tierId = tierToId(tier);
        int transferSize = ForgeConfigHandler.getTransferSize(tier);
        int tickRate     = ForgeConfigHandler.getTickRate(tier);
        int feCost       = ForgeConfigHandler.getFeCost(tier);

        tooltip.add(TextFormatting.GRAY
                + I18n.format("rusticpipes.tooltip.pipemotor." + tierId + ".data",
                        transferSize, tickRate, feCost));

        if (isShiftHeld()) {
            tooltip.add(TextFormatting.DARK_GREEN
                    + I18n.format("rusticpipes.tooltip.pipemotor." + tierId + ".description"));
        } else {
            tooltip.add(SHIFT_HINT);
        }
    }

    // -----------------------------------------------------------------------
    // Fluid pipe tooltip
    // -----------------------------------------------------------------------

    public static void addFluidPipeTooltip(ItemStack stack, List<String> tooltip,
                                           rusticpipes.block.PipeColor color) {
        int flowRate = ForgeConfigHandler.fluid.flowRatePerTick;
        int tickRate = ForgeConfigHandler.fluid.flowTickRate;
        tooltip.add(TextFormatting.GRAY
                + I18n.format("rusticpipes.tooltip.fluidpipe.data", flowRate, tickRate));

        if (isShiftHeld()) {
            String descKey = "rusticpipes.tooltip.fluidpipe.description." + color.registryName;
            tooltip.add(TextFormatting.DARK_GREEN + I18n.format(descKey));
        } else {
            tooltip.add(SHIFT_HINT);
        }
    }

    // -----------------------------------------------------------------------
    // Fluid tank tooltip
    // -----------------------------------------------------------------------

    public static void addFluidTankTooltip(ItemStack stack, List<String> tooltip) {
        int capacity = ForgeConfigHandler.fluid.capacityPerTankBlock;
        tooltip.add(TextFormatting.GRAY
                + I18n.format("rusticpipes.tooltip.fluidtank.data", capacity));

        if (isShiftHeld()) {
            tooltip.add(TextFormatting.DARK_GREEN
                    + I18n.format("rusticpipes.tooltip.fluidtank.description"));
        } else {
            tooltip.add(SHIFT_HINT);
        }
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private static String tierToId(PipeNetwork.SpeedTier tier) {
        switch (tier) {
            case SLOW:   return "slow";
            case NORMAL: return "normal";
            case FAST:   return "fast";
            case TURBO:  return "turbo";
            case HYPER:  return "hyper";
            case ULTRA:  return "ultra";
            default:     return "slow";
        }
    }
}
