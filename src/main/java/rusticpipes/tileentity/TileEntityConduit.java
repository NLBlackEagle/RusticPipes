package rusticpipes.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;
import rusticpipes.network.ConduitNetwork;
import rusticpipes.network.PipeNetwork;

import javax.annotation.Nullable;

/**
 * Conduit tile entity.
 *
 * Exposes the network's shared EnergyStorage via the ENERGY capability so that
 * any generator/machine adjacent to any conduit block reads/writes the same buffer.
 *
 * The master TE (smallest BlockPos in the network) drives the network tick each
 * game tick to push buffered FE into machines and update the tier.
 */
public class TileEntityConduit extends TileEntity implements ITickable {

    /** Cached tier for client-side rendering — synced via getUpdatePacket. */
    public PipeNetwork.SpeedTier cachedTier = PipeNetwork.SpeedTier.SLOW;

    // -----------------------------------------------------------------------
    // Tick — master TE drives the network tick
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
        // cachedTier is updated by ConduitNetwork.tick() only for TEs that border a pipe network
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
    // Capability — expose shared network buffer
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
            ConduitNetwork network = ConduitNetwork.getNetwork(pos);
            IEnergyStorage storage = (network != null)
                    ? network.getSharedStorage()
                    : null;
            // Fallback: return a zero-capacity dummy so callers don't NPE
            if (storage == null) storage = new net.minecraftforge.energy.EnergyStorage(0);
            return CapabilityEnergy.ENERGY.cast(storage);
        }
        return super.getCapability(capability, facing);
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setString("tier", cachedTier.name());
        // Note: buffer FE is NOT saved per-TE — it lives on ConduitNetwork.
        // On world reload, ConduitNetwork is rebuilt from onLoad() calls.
        // If you want buffer persistence across reloads, save it on the
        // master TE only (check pos == master before writing).
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("tier")) {
            try {
                cachedTier = PipeNetwork.SpeedTier.valueOf(compound.getString("tier"));
            } catch (IllegalArgumentException e) {
                cachedTier = PipeNetwork.SpeedTier.SLOW;
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
