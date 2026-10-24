package rusticpipes.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import rusticpipes.network.ConduitNetwork;

import javax.annotation.Nullable;

public class TileEntityConduit extends TileEntity implements ITickable {

    /** FE loaded from NBT, handed back to the network in onLoad. */
    private int savedShare = 0;

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
        if (world.isRemote) return;
        ConduitNetwork.onConduitAdded(world, pos);
        ConduitNetwork network = ConduitNetwork.getNetwork(world, pos);
        if (network != null) network.addStoredEnergy(savedShare);
        savedShare = 0;
    }

    @Override
    public void invalidate() { if (world != null && !world.isRemote) onRemoved(); super.invalidate(); }

    /**
     * Unloaded conduits leave the network so ticks never touch (and reload) their chunk.
     * The chunk was already saved with this conduit's share, so take that share with it —
     * otherwise it would exist both on disk and in the still-loaded part of the network.
     */
    @Override
    public void onChunkUnload() {
        if (world == null || world.isRemote) return;
        ConduitNetwork network = ConduitNetwork.getNetwork(world, pos);
        if (network != null) network.removeStoredEnergy(network.getShareFor());
        onRemoved();
    }

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
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        int share = savedShare;
        if (world != null && !world.isRemote) {
            ConduitNetwork network = ConduitNetwork.getNetwork(world, pos);
            if (network != null) share = network.getShareFor();
        }
        compound.setInteger("fe", share);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        savedShare = compound.getInteger("fe");
    }

    // -----------------------------------------------------------------------
    // Client sync — seeds ConduitClientState when the chunk reaches a client,
    // so players who join (or change dimension) see the current powered/spark
    // state instead of waiting for the next state change packet.
    // -----------------------------------------------------------------------

    @Override
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound tag = super.getUpdateTag();
        ConduitNetwork network = world != null ? ConduitNetwork.getNetwork(world, pos) : null;
        if (network != null) {
            tag.setBoolean("powered", network.isPoweredSynced());
            tag.setByte("spark", (byte) network.getSparkLevelSynced());
        }
        return tag;
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        if (world != null && world.isRemote) {
            rusticpipes.network.ConduitClientState.set(pos, tag.getBoolean("powered"));
            rusticpipes.network.ConduitClientState.setSparkLevel(pos, tag.getByte("spark"));
        }
    }
}
