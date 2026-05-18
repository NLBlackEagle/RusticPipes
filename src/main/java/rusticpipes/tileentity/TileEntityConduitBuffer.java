package rusticpipes.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import rusticpipes.handlers.ForgeConfigHandler;
import rusticpipes.network.ConduitNetwork;
import rusticpipes.network.PipeNetwork;

import javax.annotation.Nullable;

/**
 * Sits between a conduit network and a pipe network.
 *
 * Each tick:
 *   1. Draws exactly the tier's FE cost from an adjacent conduit.
 *   2. Stores it in a local buffer.
 *   3. Exposes that buffer via ENERGY capability so pipes drain it normally.
 *
 * If not enough FE is available for the tier, falls back to the next lower tier
 * that can be afforded, or SLOW (free) if nothing is available.
 */
public class TileEntityConduitBuffer extends TileEntity implements ITickable {

    private final PipeNetwork.SpeedTier tier;
    private EnergyStorage buffer;

    public TileEntityConduitBuffer(PipeNetwork.SpeedTier tier) {
        this.tier = tier;
        initBuffer();
    }

    // NBT deserialization calls the no-arg constructor
    public TileEntityConduitBuffer() {
        this.tier = PipeNetwork.SpeedTier.SLOW;
        initBuffer();
    }

    private void initBuffer() {
        // Local buffer sized to hold one tick's worth of the tier's FE cost
        int cap = tierCost(PipeNetwork.SpeedTier.TURBO); // max possible
        buffer = new EnergyStorage(cap, cap, cap);
    }

    // -----------------------------------------------------------------------
    // Tick
    // -----------------------------------------------------------------------

    @Override
    public void update() {
        if (world.isRemote) return;

        // Find adjacent conduit and draw FE for the highest affordable tier
        IEnergyStorage conduitStorage = findAdjacentConduit();
        if (conduitStorage != null) {
            int available = conduitStorage.getEnergyStored();
            int cost = highestAffordableCost(available);
            if (cost > 0) {
                int drawn = conduitStorage.extractEnergy(cost, false);
                if (drawn > 0) buffer.receiveEnergy(drawn, false);
            }
        }
    }

    /**
     * Returns the FE cost of the highest tier this block can afford given
     * the available FE in the conduit. Caps at this block's own tier.
     */
    private int highestAffordableCost(int available) {
        PipeNetwork.SpeedTier[] tiers = {
                PipeNetwork.SpeedTier.TURBO,
                PipeNetwork.SpeedTier.FAST,
                PipeNetwork.SpeedTier.NORMAL
        };
        for (PipeNetwork.SpeedTier t : tiers) {
            if (t.ordinal() > tier.ordinal()) continue; // don't exceed block's own tier
            int cost = tierCost(t);
            if (available >= cost) return cost;
        }
        return 0; // SLOW is free — draw nothing
    }

    private static int tierCost(PipeNetwork.SpeedTier t) {
        ForgeConfigHandler.ConduitConfig cfg = ForgeConfigHandler.conduit;
        switch (t) {
            case TURBO:  return cfg.feThresholdTurbo;
            case FAST:   return cfg.feThresholdFast;
            case NORMAL: return cfg.feThresholdNormal;
            default:     return 0;
        }
    }

    @Nullable
    private IEnergyStorage findAdjacentConduit() {
        for (EnumFacing face : EnumFacing.VALUES) {
            TileEntity nte = world.getTileEntity(pos.offset(face));
            if (!(nte instanceof TileEntityConduit)) continue;
            IEnergyStorage st = nte.getCapability(CapabilityEnergy.ENERGY, face.getOpposite());
            if (st != null && st.getEnergyStored() > 0) return st;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Capability — expose local buffer to pipes
    // -----------------------------------------------------------------------

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) return true;
        return super.hasCapability(capability, facing);
    }

    @Override @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) return CapabilityEnergy.ENERGY.cast(buffer);
        return super.getCapability(capability, facing);
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("stored", buffer.getEnergyStored());
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        initBuffer();
        if (compound.hasKey("stored"))
            buffer.receiveEnergy(compound.getInteger("stored"), false);
    }

    public int getStored()   { return buffer.getEnergyStored(); }
    public int getCapacity() { return buffer.getMaxEnergyStored(); }
}
