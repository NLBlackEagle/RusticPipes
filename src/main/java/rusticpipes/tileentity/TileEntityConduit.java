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
import rusticpipes.network.PipeNetwork;

import javax.annotation.Nullable;

public class TileEntityConduit extends TileEntity implements ITickable {

    /** Internal energy buffer — accepts FE pushed in by external sources. */
    private EnergyStorage energyBuffer;

    /** Cached tier for client-side rendering — synced via NBT. */
    public rusticpipes.network.PipeNetwork.SpeedTier cachedTier =
            rusticpipes.network.PipeNetwork.SpeedTier.SLOW;

    /** Whether this TE has already triggered a tick for its network this tick. */
    private boolean tickedThisTick = false;

    public TileEntityConduit() {
        energyBuffer = new EnergyStorage(500000, 500000, 500000);
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

        // Stable master: smallest BlockPos drives the tick
        BlockPos master = null;
        for (BlockPos p : network.getMembers()) {
            if (master == null || p.toLong() < master.toLong()) master = p;
        }
        if (master != null && master.equals(pos)) {
            network.tick(world);
        }

        // Every TE syncs its own cachedTier so getExtendedState works on every conduit block
        PipeNetwork.SpeedTier networkTier = network.getCurrentTier();
        if (networkTier != cachedTier) {
            cachedTier = networkTier;
            markDirty();
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
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
        compound.setString("tier", cachedTier.name());
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        int stored = compound.getInteger("energy");
        energyBuffer = new EnergyStorage(500000, 500000, 500000);
        energyBuffer.receiveEnergy(stored, false);
        if (compound.hasKey("tier")) {
            try {
                cachedTier = rusticpipes.network.PipeNetwork.SpeedTier
                        .valueOf(compound.getString("tier"));
            } catch (IllegalArgumentException e) {
                cachedTier = rusticpipes.network.PipeNetwork.SpeedTier.SLOW;
            }
        }
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
