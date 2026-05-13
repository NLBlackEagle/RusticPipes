package rusticpipes.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import rusticpipes.handlers.ForgeConfigHandler;
import rusticpipes.network.ConduitNetwork;

import javax.annotation.Nullable;

public class TileEntityConduit extends TileEntity implements ITickable {

    /** Internal energy buffer — accepts FE pushed in by external sources. */
    private EnergyStorage energyBuffer;

    /** Whether this TE has already triggered a tick for its network this tick. */
    private boolean tickedThisTick = false;

    public TileEntityConduit() {
        energyBuffer = new EnergyStorage(10000, 10000, 0);
    }

    /** Called by ConduitNetwork to deposit extracted FE into this buffer. */
    public void receiveEnergy(int amount) {
        energyBuffer.receiveEnergy(amount, false);
    }

    public int getEnergyStored()    { return energyBuffer.getEnergyStored(); }
    public int getMaxEnergyStored() { return energyBuffer.getMaxEnergyStored(); }

    // -----------------------------------------------------------------------
    // Tick — only the first TE in the network triggers a network tick
    // -----------------------------------------------------------------------

    @Override
    public void update() {
        if (world.isRemote) return;

        ConduitNetwork network = ConduitNetwork.getNetwork(pos);
        if (network == null) return;

        // Only the TE whose pos is first in the member set drives the network tick
        // to avoid ticking the same network N times per game tick
        BlockPos first = network.getMembers().iterator().next();
        if (!first.equals(pos)) return;

        network.tick(world);

        // Drain the energy buffer proportional to usage
        int drain = network.getLastFePerTick();
        if (drain > 0) {
            energyBuffer.extractEnergy(drain, false);
        }

        // Schedule re-render on conduit tier change
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

    // -----------------------------------------------------------------------
    // Network lifecycle
    // -----------------------------------------------------------------------

    public void onRemoved() {
        ConduitNetwork.onConduitRemoved(world, pos);
    }

    @Override
    public void onLoad() {
        if (!world.isRemote) {
            ConduitNetwork.onConduitAdded(world, pos);
        }
    }

    @Override
    public void invalidate() {
        onRemoved();
        super.invalidate();
    }

    // -----------------------------------------------------------------------
    // Capability — expose energy buffer so FE sources can push power in
    // -----------------------------------------------------------------------

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) return true;
        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            return CapabilityEnergy.ENERGY.cast(energyBuffer);
        }
        return super.getCapability(capability, facing);
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setInteger("energy", energyBuffer.getEnergyStored());
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        int stored = compound.getInteger("energy");
        energyBuffer = new EnergyStorage(10000, 10000, 0);
        energyBuffer.receiveEnergy(stored, false);
    }

    @Override
    public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }

    @Nullable
    @Override
    public net.minecraft.network.play.server.SPacketUpdateTileEntity getUpdatePacket() {
        return new net.minecraft.network.play.server.SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net,
                             net.minecraft.network.play.server.SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
        world.markBlockRangeForRenderUpdate(pos, pos);
    }
}
