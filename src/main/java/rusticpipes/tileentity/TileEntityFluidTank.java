package rusticpipes.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;

import javax.annotation.Nullable;

/**
 * Standalone single-block fluid tank tile entity.
 * No multiblock awareness — that is handled by TileEntityFluidTankMultiblock.
 */
public class TileEntityFluidTank extends TileEntity implements ITickable {

    private int capacity = rusticpipes.handlers.ForgeConfigHandler.fluid.capacityPerTankBlock;
    @Nullable private FluidStack fluid = null;
    private float fillFraction = 0f;

    public int getCapacity() { return capacity; }

    @Nullable
    public FluidStack getFluid() { return fluid; }

    public float getFillFraction() { return fillFraction; }

    // -----------------------------------------------------------------------
    // Tick — sync fill fraction for rendering
    // -----------------------------------------------------------------------

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (world.getTotalWorldTime() % 10 != 0) return;

        float newFill = capacity > 0
                ? (fluid != null ? (float) fluid.amount / capacity : 0f)
                : 0f;

        if (Math.abs(newFill - fillFraction) > 0.005f) {
            fillFraction = newFill;
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    // -----------------------------------------------------------------------
    // Fluid capability — input on top, output on sides
    // -----------------------------------------------------------------------

    private final IFluidHandler fluidHandler = new IFluidHandler() {

        @Override
        public IFluidTankProperties[] getTankProperties() {
            return new IFluidTankProperties[]{new FluidTankProperties(fluid, capacity)};
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null || resource.amount <= 0) return 0;
            if (fluid == null) {
                int toFill = Math.min(resource.amount, capacity);
                if (doFill) { fluid = resource.copy(); fluid.amount = toFill; markDirty(); }
                return toFill;
            }
            if (!fluid.isFluidEqual(resource)) return 0;
            int space = capacity - fluid.amount;
            int toFill = Math.min(resource.amount, space);
            if (doFill && toFill > 0) { fluid.amount += toFill; markDirty(); }
            return toFill;
        }

        @Override @Nullable
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || fluid == null || !fluid.isFluidEqual(resource)) return null;
            return drain(resource.amount, doDrain);
        }

        @Override @Nullable
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (fluid == null || fluid.amount <= 0) return null;
            int toDrain = Math.min(maxDrain, fluid.amount);
            FluidStack drained = fluid.copy();
            drained.amount = toDrain;
            if (doDrain) {
                fluid.amount -= toDrain;
                if (fluid.amount <= 0) fluid = null;
                markDirty();
            }
            return drained;
        }
    };

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            if (facing == EnumFacing.UP)   return true; // input on top
            if (facing == EnumFacing.DOWN) return false;
            return true; // output on sides
        }
        return super.hasCapability(capability, facing);
    }

    @Override @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
                && hasCapability(capability, facing))
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidHandler);
        return super.getCapability(capability, facing);
    }

    // -----------------------------------------------------------------------
    // Client sync / NBT
    // -----------------------------------------------------------------------

    @Override public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }

    @Nullable @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (fluid != null) compound.setTag("fluid", fluid.writeToNBT(new NBTTagCompound()));
        compound.setInteger("capacity", capacity);
        compound.setFloat("fillFraction", fillFraction);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        fluid = compound.hasKey("fluid")
                ? FluidStack.loadFluidStackFromNBT(compound.getCompoundTag("fluid")) : null;
        capacity = compound.hasKey("capacity")
                ? compound.getInteger("capacity")
                : rusticpipes.handlers.ForgeConfigHandler.fluid.capacityPerTankBlock;
        fillFraction = compound.hasKey("fillFraction") ? compound.getFloat("fillFraction") : 0f;
    }
}
