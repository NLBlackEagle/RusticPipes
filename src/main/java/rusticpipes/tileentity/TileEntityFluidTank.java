package rusticpipes.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import rusticpipes.block.BlockFluidTank;
import rusticpipes.multiblock.TankMultiblock;

import javax.annotation.Nullable;

/**
 * Tile entity for the fluid tank block.
 *
 * In a valid multiblock, all member TEs point to the controller (min-corner TE).
 * The controller is the sole holder of the FluidStack — all other members
 * delegate to it via getController().
 *
 * When not part of a valid multiblock, each tank acts as a 1×1×1 pipe-buffer
 * with capacity = capacityPerTankBlock from config.
 */
public class TileEntityFluidTank extends TileEntity implements ITickable {

    // -----------------------------------------------------------------------
    // Multiblock state
    // -----------------------------------------------------------------------

    @Nullable private BlockPos controllerPos = null;
    private TankMultiblock.Role role = TankMultiblock.Role.SINGLE;
    private int totalCapacity = rusticpipes.handlers.ForgeConfigHandler.fluid.capacityPerTankBlock;

    // -----------------------------------------------------------------------
    // Fluid storage — only used by the controller
    // -----------------------------------------------------------------------

    @Nullable private FluidStack fluid = null;

    // -----------------------------------------------------------------------
    // Fill fraction for rendering (0.0 – 1.0) — kept on all members
    // -----------------------------------------------------------------------

    private float fillFraction = 0f;

    // -----------------------------------------------------------------------
    // Multiblock lifecycle
    // -----------------------------------------------------------------------

    public void onMultiblockFormed(BlockPos controller, TankMultiblock.Role role, int capacity) {
        this.controllerPos = controller;
        this.role = role;
        this.totalCapacity = capacity;
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    public void onMultiblockInvalidated() {
        controllerPos = null;
        role = TankMultiblock.Role.SINGLE;
        totalCapacity = rusticpipes.handlers.ForgeConfigHandler.fluid.capacityPerTankBlock;
        fillFraction = 0f;
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    @Nullable
    public BlockPos getControllerPos() { return controllerPos; }

    public TankMultiblock.Role getRole() { return role; }

    public boolean isController() {
        return controllerPos != null && controllerPos.equals(pos);
    }

    public boolean isPartOfMultiblock() {
        return controllerPos != null;
    }

    /** Returns the controller TE, or this if we are the controller (or single). */
    @Nullable
    private TileEntityFluidTank getController() {
        if (controllerPos == null || controllerPos.equals(pos)) return this;
        if (world == null) return null;
        TileEntity te = world.getTileEntity(controllerPos);
        return te instanceof TileEntityFluidTank ? (TileEntityFluidTank) te : null;
    }

    // -----------------------------------------------------------------------
    // Fluid access — delegates to controller
    // -----------------------------------------------------------------------

    public int getCapacity() { return totalCapacity; }

    @Nullable
    public FluidStack getFluid() {
        TileEntityFluidTank ctrl = getController();
        return ctrl == null ? null : ctrl.fluid;
    }

    public float getFillFraction() { return fillFraction; }

    // -----------------------------------------------------------------------
    // Tick — sync fill fraction to non-controller members every 10 ticks
    // -----------------------------------------------------------------------

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (world.getTotalWorldTime() % 10 != 0) return;

        TileEntityFluidTank ctrl = getController();
        if (ctrl == null) return;

        float newFill = ctrl.totalCapacity > 0
                ? (ctrl.fluid != null ? (float) ctrl.fluid.amount / ctrl.totalCapacity : 0f)
                : 0f;

        if (Math.abs(newFill - fillFraction) > 0.005f) {
            fillFraction = newFill;
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    // -----------------------------------------------------------------------
    // Fluid capability
    // -----------------------------------------------------------------------

    private final IFluidHandler fluidHandler = new IFluidHandler() {

        @Override
        public IFluidTankProperties[] getTankProperties() {
            TileEntityFluidTank ctrl = getController();
            if (ctrl == null) return new IFluidTankProperties[0];
            return new IFluidTankProperties[]{
                    new FluidTankProperties(ctrl.fluid, ctrl.totalCapacity)
            };
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null || resource.amount <= 0) return 0;
            TileEntityFluidTank ctrl = getController();
            if (ctrl == null) return 0;

            // Only accept input through TOP face of TOP blocks
            // (enforced by caller — we just store here)
            if (ctrl.fluid == null) {
                int toFill = Math.min(resource.amount, ctrl.totalCapacity);
                if (doFill) {
                    ctrl.fluid = resource.copy();
                    ctrl.fluid.amount = toFill;
                    ctrl.markDirty();
                }
                return toFill;
            }
            if (!ctrl.fluid.isFluidEqual(resource)) return 0;
            int space = ctrl.totalCapacity - ctrl.fluid.amount;
            int toFill = Math.min(resource.amount, space);
            if (doFill && toFill > 0) {
                ctrl.fluid.amount += toFill;
                ctrl.markDirty();
            }
            return toFill;
        }

        @Override
        @Nullable
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null) return null;
            TileEntityFluidTank ctrl = getController();
            if (ctrl == null || ctrl.fluid == null) return null;
            if (!ctrl.fluid.isFluidEqual(resource)) return null;
            return drain(resource.amount, doDrain);
        }

        @Override
        @Nullable
        public FluidStack drain(int maxDrain, boolean doDrain) {
            TileEntityFluidTank ctrl = getController();
            if (ctrl == null || ctrl.fluid == null || ctrl.fluid.amount <= 0) return null;
            int toDrain = Math.min(maxDrain, ctrl.fluid.amount);
            FluidStack drained = ctrl.fluid.copy();
            drained.amount = toDrain;
            if (doDrain) {
                ctrl.fluid.amount -= toDrain;
                if (ctrl.fluid.amount <= 0) ctrl.fluid = null;
                ctrl.markDirty();
            }
            return drained;
        }
    };

    @Override
    public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            // Input only on top face of TOP/SINGLE blocks
            // Output only on side faces of BOTTOM/SINGLE blocks
            if (facing == EnumFacing.UP) {
                return role == TankMultiblock.Role.TOP || role == TankMultiblock.Role.SINGLE;
            }
            if (facing == EnumFacing.DOWN) return false;
            // Side faces
            return role == TankMultiblock.Role.BOTTOM || role == TankMultiblock.Role.SINGLE;
        }
        return super.hasCapability(capability, facing);
    }

    @Override
    @Nullable
    public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY
                && hasCapability(capability, facing)) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(fluidHandler);
        }
        return super.getCapability(capability, facing);
    }

    // -----------------------------------------------------------------------
    // Placement / removal — trigger multiblock validation
    // -----------------------------------------------------------------------

    @Override
    public void onLoad() {
        if (world != null && !world.isRemote) {
            TankMultiblock.Structure structure = TankMultiblock.validate(world, pos);
            if (structure != null) TankMultiblock.apply(world, structure);
        }
    }

    // -----------------------------------------------------------------------
    // Client sync
    // -----------------------------------------------------------------------

    @Override
    public NBTTagCompound getUpdateTag() { return writeToNBT(new NBTTagCompound()); }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
        if (world != null) world.markBlockRangeForRenderUpdate(pos, pos);
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        if (fluid != null) compound.setTag("fluid", fluid.writeToNBT(new NBTTagCompound()));
        if (controllerPos != null) {
            compound.setLong("controllerPos", controllerPos.toLong());
        }
        compound.setString("role", role.name());
        compound.setInteger("capacity", totalCapacity);
        compound.setFloat("fillFraction", fillFraction);
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        fluid = compound.hasKey("fluid")
                ? FluidStack.loadFluidStackFromNBT(compound.getCompoundTag("fluid")) : null;
        controllerPos = compound.hasKey("controllerPos")
                ? BlockPos.fromLong(compound.getLong("controllerPos")) : null;
        if (compound.hasKey("role")) {
            try { role = TankMultiblock.Role.valueOf(compound.getString("role")); }
            catch (IllegalArgumentException e) { role = TankMultiblock.Role.SINGLE; }
        }
        totalCapacity = compound.hasKey("capacity")
                ? compound.getInteger("capacity")
                : rusticpipes.handlers.ForgeConfigHandler.fluid.capacityPerTankBlock;
        fillFraction = compound.hasKey("fillFraction") ? compound.getFloat("fillFraction") : 0f;
    }
}
