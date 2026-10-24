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
import rusticpipes.RusticPipes;
import rusticpipes.block.BlockFluidTankMultiblock;
import rusticpipes.multiblock.TankMultiblock;

import javax.annotation.Nullable;

public class TileEntityFluidTankMultiblock extends TileEntity implements ITickable {

    @Nullable private BlockPos controllerPos = null;
    private TankMultiblock.Role role = TankMultiblock.Role.SINGLE;
    private int baseSize = 1;
    private int totalCapacity = rusticpipes.handlers.ForgeConfigHandler.fluid.capacityPerTankBlock;

    /**
     * Which row of the multiblock this block occupies.
     * Stored in NBT and synced to the client. Read by
     * BlockFluidTankMultiblock.getExtendedState() to drive texture selection
     * in the custom baked model without using any meta bits.
     */
    private BlockFluidTankMultiblock.ViewportRow viewportRow =
            BlockFluidTankMultiblock.ViewportRow.NONE;
    /** Extra exterior side face for 2x2 corner blocks (null = none). */
    @Nullable private EnumFacing sideFace = null;

    @Nullable private FluidStack fluid = null;
    private float fillFraction = 0f;

    // -----------------------------------------------------------------------
    // Multiblock lifecycle
    // -----------------------------------------------------------------------

    public void onMultiblockFormed(BlockPos controller, TankMultiblock.Role role,
                                   int capacity, int baseSize,
                                   BlockFluidTankMultiblock.ViewportRow viewportRow,
                                   @Nullable EnumFacing sideFace) {
        this.controllerPos = controller;
        this.role          = role;
        this.totalCapacity = capacity;
        this.baseSize      = baseSize;
        this.viewportRow   = viewportRow;
        this.sideFace      = sideFace;
        // Snap fill fraction immediately on structural change — no lerp
        if (controller.equals(pos) && capacity > 0) {
            fillFraction = fluid != null ? Math.min(1f, (float) fluid.amount / capacity) : 0f;
        }
        markDirty();
        if (world != null && !world.isRemote)
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

    /**
     * Detaches this block from its multiblock. Deliberately NOT named invalidate():
     * overriding TileEntity.invalidate() without calling super left removed TEs
     * looking valid to other mods' pipes and caches.
     */
    public void resetMultiblock() {
        controllerPos = null;
        role          = TankMultiblock.Role.SINGLE;
        baseSize      = 1;
        totalCapacity = rusticpipes.handlers.ForgeConfigHandler.fluid.capacityPerTankBlock;
        viewportRow   = BlockFluidTankMultiblock.ViewportRow.NONE;
        sideFace      = null;
        fillFraction  = 0f;
        markDirty();
        if (world != null && !world.isRemote)
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

    public void onBreak() {
        if (world == null || world.isRemote) return;
        TankMultiblock.invalidateMultiblock(world, pos);
    }

    /**
     * Keep this TE when only the VIEWPORT property changes. Forge's default for modded
     * TEs replaces the TE on ANY state change, which wiped controllerPos/role (and the
     * controller's fluid on 2x2 tanks) every time applyMultiblock() set a viewport face.
     */
    @Override
    public boolean shouldRefresh(net.minecraft.world.World world, BlockPos pos,
                                 net.minecraft.block.state.IBlockState oldState,
                                 net.minecraft.block.state.IBlockState newState) {
        return oldState.getBlock() != newState.getBlock();
    }

    public boolean isController()          { return controllerPos != null && controllerPos.equals(pos); }
    public boolean isPartOfMultiblock()    { return controllerPos != null; }

    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        // Only the controller renders, spanning the whole multiblock. Its box covers the
        // footprint up to max height (10) so it is culled only when the tank is fully
        // off-screen; members draw nothing and keep their own block box so they cull normally.
        if (isController()) {
            return new net.minecraft.util.math.AxisAlignedBB(pos, pos.add(baseSize, 10, baseSize));
        }
        return new net.minecraft.util.math.AxisAlignedBB(pos, pos.add(1, 1, 1));
    }
    public BlockPos getControllerPos()     { return controllerPos; }
    public TankMultiblock.Role getRole()   { return role; }
    public int getBaseSize()               { return baseSize; }
    public float getFillFraction()         { return fillFraction; }
    public int getCapacity()               { return totalCapacity; }
    /** Direct fluid access — bypasses controller resolution, used during revalidation. */
    public FluidStack getRawFluid()         { return fluid; }
    public void setRawFluid(FluidStack f)   { fluid = f; markDirty(); }
    public BlockFluidTankMultiblock.ViewportRow getViewportRow() { return viewportRow; }
    @Nullable public EnumFacing getSideFace() { return sideFace; }

    @Nullable
    private TileEntityFluidTankMultiblock getController() {
        if (controllerPos == null || controllerPos.equals(pos)) return this;
        if (world == null) return null;
        TileEntity te = world.getTileEntity(controllerPos);
        return te instanceof TileEntityFluidTankMultiblock ? (TileEntityFluidTankMultiblock) te : null;
    }

    @Nullable
    public FluidStack getFluid() {
        TileEntityFluidTankMultiblock ctrl = getController();
        return ctrl == null ? null : ctrl.fluid;
    }

    // -----------------------------------------------------------------------
    // Tick
    // -----------------------------------------------------------------------

    @Override
    public void update() {
        if (world == null || world.isRemote) return;
        if (world.getTotalWorldTime() % 10 != 0) return;
        // Only the controller's fill is rendered (and only it emits radiation), so members
        // don't sync — a 4x4x10 tank would otherwise send 160 updates per change and push
        // the chunk past the 64-change limit into full section resends.
        if (!isController()) return;
        TileEntityFluidTankMultiblock ctrl = this;
        float newFill = ctrl.totalCapacity > 0
                ? (ctrl.fluid != null ? (float) ctrl.fluid.amount / ctrl.totalCapacity : 0f)
                : 0f;
        if (Math.abs(newFill - fillFraction) > 0.001f) {
            fillFraction = newFill;
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }

        // NuclearCraft radiation — only the controller emits radiation (once per check interval)
        // to avoid N-times-over-application for each member block ticking independently.
        if (isController()
                && rusticpipes.handlers.ForgeConfigHandler.fluid.enableRadiation
                && ctrl.fluid != null) {
            int interval = rusticpipes.handlers.ForgeConfigHandler.fluid.radiationTickInterval;
            if (world.getTotalWorldTime() % interval == 0) {
                if (RusticPipes.DEBUG) {
                    rusticpipes.RusticPipes.LOGGER.debug("[RadDebug] Radiation tick fired. fluid={} amount={} pos={}",
                            ctrl.fluid.getFluid().getName(), ctrl.fluid.amount, pos);
                }
                double rad = rusticpipes.compat.NuclearCraftCompat.getFluidRadiation(ctrl.fluid);
                if (RusticPipes.DEBUG) {
                    rusticpipes.RusticPipes.LOGGER.debug("[RadDebug] getFluidRadiation returned {}", rad);
                }
                if (rad > 0) {
                    rusticpipes.compat.NuclearCraftCompat.irradiateNearbyPlayers(world, pos, rad,
                            rusticpipes.handlers.ForgeConfigHandler.fluid.radiationRange);
                }
            }
        } else if (world.getTotalWorldTime() % 100 == 0) {
            if (RusticPipes.DEBUG) {
                rusticpipes.RusticPipes.LOGGER.debug("[RadDebug] Radiation skipped — isController={} enableRadiation={} fluid={}",
                        isController(),
                        rusticpipes.handlers.ForgeConfigHandler.fluid.enableRadiation,
                        ctrl.fluid != null ? ctrl.fluid.getFluid().getName() : "null");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Fluid capability
    // -----------------------------------------------------------------------

    private final IFluidHandler fluidHandler = new IFluidHandler() {
        @Override
        public IFluidTankProperties[] getTankProperties() {
            TileEntityFluidTankMultiblock ctrl = getController();
            if (ctrl == null) return new IFluidTankProperties[0];
            return new IFluidTankProperties[]{ new FluidTankProperties(ctrl.fluid, ctrl.totalCapacity) };
        }

        @Override
        public int fill(FluidStack resource, boolean doFill) {
            if (resource == null || resource.amount <= 0) return 0;
            TileEntityFluidTankMultiblock ctrl = getController();
            if (ctrl == null) return 0;
            if (ctrl.fluid == null) {
                int toFill = Math.min(resource.amount, ctrl.totalCapacity);
                if (doFill) { ctrl.fluid = resource.copy(); ctrl.fluid.amount = toFill; ctrl.markDirty(); }
                return toFill;
            }
            if (!ctrl.fluid.isFluidEqual(resource)) return 0;
            int space = ctrl.totalCapacity - ctrl.fluid.amount;
            int toFill = Math.min(resource.amount, space);
            if (doFill && toFill > 0) { ctrl.fluid.amount += toFill; ctrl.markDirty(); }
            return toFill;
        }

        @Override @Nullable
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null) return null;
            TileEntityFluidTankMultiblock ctrl = getController();
            if (ctrl == null || ctrl.fluid == null || !ctrl.fluid.isFluidEqual(resource)) return null;
            return drain(resource.amount, doDrain);
        }

        @Override @Nullable
        public FluidStack drain(int maxDrain, boolean doDrain) {
            TileEntityFluidTankMultiblock ctrl = getController();
            if (ctrl == null || ctrl.fluid == null || ctrl.fluid.amount <= 0) return null;
            int toDrain = Math.min(maxDrain, ctrl.fluid.amount);
            FluidStack drained = ctrl.fluid.copy(); drained.amount = toDrain;
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
            // Expose fluid capability on all sides for any block that is part of the multiblock
            return isPartOfMultiblock();
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
    // NBT / sync
    // -----------------------------------------------------------------------

@Override
    public void onLoad() {
        if (world != null && !world.isRemote) {
            world.scheduleUpdate(pos, world.getBlockState(pos).getBlock(), 1);
        }
    }

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
        if (controllerPos != null) compound.setLong("controllerPos", controllerPos.toLong());
        compound.setString("role", role.name());
        compound.setInteger("capacity", totalCapacity);
        compound.setInteger("baseSize", baseSize);
        compound.setFloat("fillFraction", fillFraction);
        compound.setString("viewportRow", viewportRow.name());
        if (sideFace != null) compound.setString("sideFace", sideFace.getName());
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
        baseSize = compound.hasKey("baseSize") ? compound.getInteger("baseSize") : 1;
        fillFraction = compound.hasKey("fillFraction") ? compound.getFloat("fillFraction") : 0f;
        if (compound.hasKey("viewportRow")) {
            try { viewportRow = BlockFluidTankMultiblock.ViewportRow.valueOf(compound.getString("viewportRow")); }
            catch (IllegalArgumentException e) { viewportRow = BlockFluidTankMultiblock.ViewportRow.NONE; }
        }
        sideFace = compound.hasKey("sideFace")
                ? EnumFacing.byName(compound.getString("sideFace")) : null;
    }
}
