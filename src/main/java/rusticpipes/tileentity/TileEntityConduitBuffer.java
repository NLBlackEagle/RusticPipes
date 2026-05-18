package rusticpipes.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import rusticpipes.block.BlockConduitBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import rusticpipes.handlers.ForgeConfigHandler;
import rusticpipes.network.PipeNetwork;

import javax.annotation.Nullable;

/**
 * Sits between a conduit network and a pipe network.
 *
 * Each tick draws FE from an adjacent conduit equal to the highest tier
 * this motor can supply that the conduit can afford. Stores it locally.
 * Pipes drain from this local buffer via ENERGY capability.
 */
public class TileEntityConduitBuffer extends TileEntity implements ITickable {

    private final PipeNetwork.SpeedTier tier;
    private EnergyStorage buffer;

    public TileEntityConduitBuffer(PipeNetwork.SpeedTier tier) {
        this.tier = tier;
        initBuffer();
    }

    /** No-arg constructor for NBT deserialization — tier resolved from block in onLoad. */
    public TileEntityConduitBuffer() {
        this.tier = PipeNetwork.SpeedTier.SLOW; // placeholder, overwritten in onLoad
        this.buffer = new EnergyStorage(100, 100, 100); // placeholder
    }

    private void initBuffer() {
        int cap = ForgeConfigHandler.motors.getBuffer(tier);
        int stored = buffer != null ? Math.min(buffer.getEnergyStored(), cap) : 0;
        buffer = new EnergyStorage(cap, cap, cap);
        if (stored > 0) buffer.receiveEnergy(stored, false);
    }

    @Override
    public void onLoad() {
        // Resolve actual tier from the placed block and resize buffer accordingly
        if (world != null) {
            net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
            if (block instanceof BlockConduitBuffer) {
                // Use reflection-free field trick: re-assign via a local shadow
                // Since tier is final we rebuild the buffer using the block's tier
                PipeNetwork.SpeedTier blockTier = ((BlockConduitBuffer) block).tier;
                int cap = ForgeConfigHandler.motors.getBuffer(blockTier);
                int stored = Math.min(buffer.getEnergyStored(), cap);
                buffer = new EnergyStorage(cap, cap, cap);
                if (stored > 0) buffer.receiveEnergy(stored, false);
            }
        }
    }

    public PipeNetwork.SpeedTier getTier() {
        if (world != null) {
            net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
            if (block instanceof BlockConduitBuffer) return ((BlockConduitBuffer) block).tier;
        }
        return tier;
    }

    // -----------------------------------------------------------------------
    // Tick — draw from adjacent conduit into local buffer
    // -----------------------------------------------------------------------

    @Override
    public void update() {
        if (world.isRemote) return;

        // Only draw from conduit if the local buffer has room
        int room = buffer.getMaxEnergyStored() - buffer.getEnergyStored();
        if (room <= 0) return;

        IEnergyStorage conduitStorage = findAdjacentConduit();
        if (conduitStorage == null) return;

        int available = conduitStorage.getEnergyStored();
        int cost = highestAffordableCost(available);
        if (cost > 0 && cost <= room) {
            int drawn = conduitStorage.extractEnergy(cost, false);
            if (drawn > 0) buffer.receiveEnergy(drawn, false);
        }
    }

    /**
     * Returns the FE cost of the highest tier this motor can supply
     * that the conduit can currently afford. Caps at this motor's own tier.
     */
    private int highestAffordableCost(int available) {
        PipeNetwork.SpeedTier[] descending = {
                PipeNetwork.SpeedTier.ULTRA,
                PipeNetwork.SpeedTier.HYPER,
                PipeNetwork.SpeedTier.TURBO,
                PipeNetwork.SpeedTier.FAST,
                PipeNetwork.SpeedTier.NORMAL
        };
        for (PipeNetwork.SpeedTier t : descending) {
            if (t.ordinal() > tier.ordinal()) continue; // don't exceed motor's own tier
            int cost = ForgeConfigHandler.getFeCost(t);
            if (cost == 0) return 0; // SLOW is free
            if (available >= cost) return cost;
        }
        return 0; // SLOW — draw nothing
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
    // Capability
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
            buffer.receiveEnergy(Math.min(compound.getInteger("stored"),
                    buffer.getMaxEnergyStored()), false);
    }

    public int getStored()   { return buffer.getEnergyStored(); }
    public int getCapacity() { return buffer.getMaxEnergyStored(); }
}
