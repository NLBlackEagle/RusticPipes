package rusticpipes.compat;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.energy.IEnergyStorage;
import rusticpipes.RusticPipes;
import rusticpipes.handlers.ForgeConfigHandler;

import javax.annotation.Nullable;

/**
 * Soft-dependency bridge for IndustrialCraft 2.
 *
 * All IC2 type references are confined to this class and only reached after
 * {@link #isLoaded()} returns true, so the mod loads cleanly without IC2.
 *
 * Conversion: FE = EU * ratio  (default 4, configurable via
 * {@link rusticpipes.handlers.ForgeConfigHandler.ConduitConfig#ic2FeRatio}).
 *
 * Two adapters are provided:
 *   - {@link #wrapSource} — IC2 IEnergySource (generators) → FE IEnergyStorage
 *     so ConduitNetwork.tick() can pull from IC2 generators normally.
 *   - {@link #wrapSink}   — IC2 IEnergySink (machines) → FE IEnergyStorage
 *     so ConduitNetwork.tick() can push into IC2 machines normally.
 */
public final class IC2Compat {

    private IC2Compat() {}

    private static Boolean loaded = null;

    // -----------------------------------------------------------------------
    // Availability check — cached after first call
    // -----------------------------------------------------------------------

    public static boolean isLoaded() {
        if (loaded == null) {
            try {
                Class.forName("ic2.api.energy.tile.IEnergySource");
                loaded = true;
                RusticPipes.LOGGER.info("[RusticPipes] IndustrialCraft 2 detected — "
                        + "EU interop enabled (1 EU = {} FE).",
                        ForgeConfigHandler.conduit.ic2FeRatio);
            } catch (ClassNotFoundException e) {
                loaded = false;
            }
        }
        return loaded;
    }

    // -----------------------------------------------------------------------
    // Wrappers
    // -----------------------------------------------------------------------

    /**
     * If {@code te} is an IC2 IEnergySource, returns an IEnergyStorage adapter
     * that pulls EU and reports/converts it as FE.
     * Returns null if te is not an IC2 source, or IC2 is absent.
     */
    @Nullable
    public static IEnergyStorage wrapSource(TileEntity te, EnumFacing face) {
        if (!isLoaded() || te == null) return null;
        try {
            if (!(te instanceof ic2.api.energy.tile.IEnergySource)) return null;
            ic2.api.energy.tile.IEnergySource src = (ic2.api.energy.tile.IEnergySource) te;
            if (src.getOfferedEnergy() <= 0) return null;
            return new SourceWrapper(src);
        } catch (Throwable t) {
            RusticPipes.LOGGER.debug("[RusticPipes] IC2 source wrap error: {}", t.getMessage());
            return null;
        }
    }

    /**
     * If {@code te} is an IC2 IEnergySink, returns an IEnergyStorage adapter
     * that accepts FE and injects it as EU.
     * Returns null if te is not an IC2 sink, or IC2 is absent.
     */
    @Nullable
    public static IEnergyStorage wrapSink(TileEntity te, EnumFacing face) {
        if (!isLoaded() || te == null) return null;
        try {
            if (!(te instanceof ic2.api.energy.tile.IEnergySink)) return null;
            ic2.api.energy.tile.IEnergySink sink = (ic2.api.energy.tile.IEnergySink) te;
            if (sink.getDemandedEnergy() <= 0) return null;
            return new SinkWrapper(sink);
        } catch (Throwable t) {
            RusticPipes.LOGGER.debug("[RusticPipes] IC2 sink wrap error: {}", t.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    // Adapter: IC2 IEnergySource → FE IEnergyStorage (extract-only)
    // -----------------------------------------------------------------------

    private static final class SourceWrapper implements IEnergyStorage {

        private final ic2.api.energy.tile.IEnergySource source;

        SourceWrapper(ic2.api.energy.tile.IEnergySource source) {
            this.source = source;
        }

        private int ratio() { return Math.max(1, ForgeConfigHandler.conduit.ic2FeRatio); }

        @Override
        public int extractEnergy(int maxExtractFe, boolean simulate) {
            int ratio = ratio();
            double maxEu = maxExtractFe / (double) ratio;
            double offered = Math.min(source.getOfferedEnergy(), maxEu);
            if (offered <= 0) return 0;
            if (!simulate) source.drawEnergy(offered);
            return (int) (offered * ratio);
        }

        @Override public int receiveEnergy(int maxReceive, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return (int)(source.getOfferedEnergy() * ratio()); }
        // IC2 sources don't expose a meaningful max-stored value; use a large sentinel.
        @Override public int getMaxEnergyStored() { return Integer.MAX_VALUE / 2; }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return false; }
    }

    // -----------------------------------------------------------------------
    // Adapter: IC2 IEnergySink → FE IEnergyStorage (receive-only)
    // -----------------------------------------------------------------------

    private static final class SinkWrapper implements IEnergyStorage {

        private final ic2.api.energy.tile.IEnergySink sink;

        SinkWrapper(ic2.api.energy.tile.IEnergySink sink) {
            this.sink = sink;
        }

        private int ratio() { return Math.max(1, ForgeConfigHandler.conduit.ic2FeRatio); }

        @Override
        public int receiveEnergy(int maxReceiveFe, boolean simulate) {
            int ratio = ratio();
            double maxEu = maxReceiveFe / (double) ratio;
            double demanded = Math.min(sink.getDemandedEnergy(), maxEu);
            if (demanded <= 0) return 0;
            if (!simulate) {
                // injectEnergy returns leftover EU; consumed = demanded - leftover
                double leftover = sink.injectEnergy(null, demanded, sink.getSinkTier());
                double consumed = demanded - leftover;
                return (int) (consumed * ratio);
            }
            return (int) (demanded * ratio);
        }

        @Override public int extractEnergy(int maxExtract, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return 0; }
        @Override public int getMaxEnergyStored() { return (int)(sink.getDemandedEnergy() * ratio()); }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return true; }
    }
}
