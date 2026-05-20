package rusticpipes.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.block.state.IBlockState;
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

    /**
     * Set to true any tick that FE enters the buffer (either pulled from an
     * adjacent conduit by update(), or pushed in externally via receiveEnergy()
     * by the conduit network capability). Cleared at the start of each tick.
     * Used to decide whether to apply the idle drain.
     */
    private boolean receivedFeThisTick = false;

    public TileEntityConduitBuffer(PipeNetwork.SpeedTier tier) {
        this.tier = tier;
        initBuffer();
    }

    /** No-arg constructor for NBT deserialization — tier resolved from block in onLoad. */
    public TileEntityConduitBuffer() {
        this.tier = PipeNetwork.SpeedTier.SLOW; // placeholder, overwritten in onLoad
        this.buffer = new EnergyStorage(100, 100, 100); // placeholder
    }

    private void initBuffer() { initBuffer(tier); }

    private void initBuffer(PipeNetwork.SpeedTier t) {
        int cap = ForgeConfigHandler.motors.getBuffer(t);
        int stored = buffer != null ? Math.min(buffer.getEnergyStored(), cap) : 0;
        // Subclass tracks whether any FE entered this tick, for idle-drain detection.
        buffer = new EnergyStorage(cap, cap, cap) {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                int accepted = super.receiveEnergy(maxReceive, simulate);
                if (!simulate && accepted > 0) receivedFeThisTick = true;
                return accepted;
            }
        };
        if (stored > 0) buffer.receiveEnergy(stored, false);
    }

    @Override
    public void onLoad() {
        // Resolve actual tier from the placed block and resize buffer accordingly
        if (world != null) {
            net.minecraft.block.Block block = world.getBlockState(pos).getBlock();
            if (block instanceof BlockConduitBuffer) {
                initBuffer(((BlockConduitBuffer) block).tier);
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

        // Reset input flag — will be set to true if any FE enters the buffer this tick,
        // either via our own pull below or via an external receiveEnergy() capability call.
        receivedFeThisTick = false;

        // Only draw from conduit if the local buffer has room
        int room = buffer.getMaxEnergyStored() - buffer.getEnergyStored();
        if (room > 0) {
            IEnergyStorage conduitStorage = findAdjacentConduit();
            if (conduitStorage != null) {
                int available = conduitStorage.getEnergyStored();
                int cost = highestAffordableCost(available);
                if (cost > 0 && cost <= room) {
                    int drawn = conduitStorage.extractEnergy(cost, false);
                    if (drawn > 0) buffer.receiveEnergy(drawn, false);
                }
            }
        }

        // Idle drain — if no FE entered the buffer this tick, bleed off the remainder.
        // This clears residual charge left when the motor is disconnected from its source
        // (e.g. 5000 FE sitting indefinitely after pipes have stopped drawing).
        if (!receivedFeThisTick && buffer.getEnergyStored() > 0) {
            double rate = ForgeConfigHandler.motors.bufferDrainRatePerTick;
            if (rate > 0.0) {
                int drain = (int) Math.ceil(buffer.getEnergyStored() * rate);
                buffer.extractEnergy(drain, false);
            }
        }

        // Sync the block's POWERED meta to the actual buffer state.
        // We compare against the block's STORED state (from meta) rather than
        // a within-tick snapshot, because the buffer can fill via an external
        // receiveEnergy() call (from the conduit network push) BEFORE update()
        // runs — meaning wasPowered would already be true and the transition
        // would never be detected.
        boolean isPowered = buffer.getEnergyStored() > 0;
        IBlockState current = world.getBlockState(pos);
        if (current.getBlock() instanceof BlockConduitBuffer) {
            boolean metaPowered = current.getValue(BlockConduitBuffer.POWERED);
            if (isPowered != metaPowered) {
                world.setBlockState(pos, current.withProperty(BlockConduitBuffer.POWERED, isPowered), 3);
                markDirty();
            }
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
        if (capability == CapabilityEnergy.ENERGY) {
            // Only expose extraction on faces adjacent to a pipe network,
            // so the conduit cannot pull back from the motor buffer
            if (facing != null && world != null) {
                net.minecraft.block.Block neighbour = world.getBlockState(
                        pos.offset(facing)).getBlock();
                if (neighbour instanceof rusticpipes.block.BlockConduit) {
                    // Conduit side — receive only, no extraction
                    return CapabilityEnergy.ENERGY.cast(new net.minecraftforge.energy.EnergyStorage(
                            buffer.getMaxEnergyStored(), buffer.getMaxEnergyStored(), 0) {
                        @Override public int getEnergyStored() { return buffer.getEnergyStored(); }
                        @Override public int receiveEnergy(int maxReceive, boolean simulate) {
                            return buffer.receiveEnergy(maxReceive, simulate);
                        }
                    });
                }
            }
            return CapabilityEnergy.ENERGY.cast(buffer);
        }
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
