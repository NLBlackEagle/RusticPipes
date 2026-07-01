package rusticpipes.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import rusticpipes.network.ConduitNetwork;

import javax.annotation.Nullable;

public class TileEntityConduit extends TileEntity implements ITickable {

    @Override
    public void update() {
        if (world.isRemote) return;
        ConduitNetwork network = ConduitNetwork.getNetwork(world, pos);
        if (network == null) return;
        // Only the cached master TE drives the network — O(1) check, no member scan.
        if (pos.equals(network.getMasterPos())) network.tick(world);
    }

    public void onRemoved() { if (world == null || world.isRemote) return; ConduitNetwork.onConduitRemoved(world, pos); }

    @Override
    public void onLoad() {
        if (!world.isRemote) ConduitNetwork.onConduitAdded(world, pos);
    }

    @Override
    public void invalidate() { if (world != null && !world.isRemote) onRemoved(); super.invalidate(); }

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) return true;
        return super.hasCapability(capability, facing);
    }

    @Override @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityEnergy.ENERGY) {
            ConduitNetwork network = ConduitNetwork.getNetwork(world, pos);
            IEnergyStorage storage = network != null ? network.getSharedStorage() : null;
            if (storage == null) storage = new net.minecraftforge.energy.EnergyStorage(0);
            return CapabilityEnergy.ENERGY.cast(storage);
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) { return super.writeToNBT(compound); }

    @Override
    public void readFromNBT(NBTTagCompound compound) { super.readFromNBT(compound); }
}
