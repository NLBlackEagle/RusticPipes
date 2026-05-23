package rusticpipes.tileentity;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import rusticpipes.block.BlockFluidPipe;
import rusticpipes.network.FluidNetwork;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public class TileEntityFluidPipe extends TileEntity implements ITickable {

    private final Map<EnumFacing, FaceMode> faceModes = new EnumMap<>(EnumFacing.class);

    /** Small in-transit fluid buffer — used for viewport color rendering. */
    private static final int BUFFER_CAPACITY = 100;
    @Nullable private FluidStack buffer = null;
    /** Fluid color synced to client for viewport rendering. 0 = empty. */
    private int fluidColor = 0;
    /** Ticks since last fluid passed through — used to drain the visual buffer. */
    private int ticksSinceFlow = 0;
    private static final int DRAIN_AFTER_TICKS = 40;

    public TileEntityFluidPipe() {
        for (EnumFacing face : EnumFacing.VALUES) {
            faceModes.put(face, FaceMode.OUTPUT);
        }
    }

    public FaceMode getFaceMode(EnumFacing face) {
        return faceModes.getOrDefault(face, FaceMode.OUTPUT);
    }

    public void setFaceMode(EnumFacing face, FaceMode mode) {
        faceModes.put(face, mode);
        markDirty();
        if (world != null && !world.isRemote) {
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
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
        world.markBlockRangeForRenderUpdate(pos, pos);
    }

    // -----------------------------------------------------------------------
    // NBT
    // -----------------------------------------------------------------------

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        NBTTagCompound modes = new NBTTagCompound();
        for (EnumFacing face : EnumFacing.VALUES) {
            modes.setString(face.getName(), faceModes.get(face).name());
        }
        compound.setTag("faceModes", modes);
        compound.setInteger("fluidColor", fluidColor);
        if (buffer != null) compound.setTag("buffer", buffer.writeToNBT(new NBTTagCompound()));
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        fluidColor = compound.hasKey("fluidColor") ? compound.getInteger("fluidColor") : 0;
        buffer = compound.hasKey("buffer")
                ? FluidStack.loadFluidStackFromNBT(compound.getCompoundTag("buffer")) : null;
        if (compound.hasKey("faceModes")) {
            NBTTagCompound modes = compound.getCompoundTag("faceModes");
            for (EnumFacing face : EnumFacing.VALUES) {
                if (modes.hasKey(face.getName())) {
                    try {
                        faceModes.put(face, FaceMode.valueOf(modes.getString(face.getName())));
                    } catch (IllegalArgumentException e) {
                        faceModes.put(face, FaceMode.OUTPUT);
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Buffer access
    // -----------------------------------------------------------------------

    public int getBufferCapacity() { return BUFFER_CAPACITY; }

    @Nullable
    public FluidStack getBuffer() { return buffer; }

    public int getFluidColor() { return fluidColor; }

    /**
     * Called by FluidNetwork during transfer to push fluid through this pipe's buffer.
     * Updates color and resets the drain timer.
     */
    public void onFluidPassed(FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) return;
        buffer = fluid.copy();
        buffer.amount = Math.min(buffer.amount, BUFFER_CAPACITY);
        int newColor = fluid.getFluid().getColor(fluid);
        if (newColor == 0xFFFFFFFF || newColor == 0) newColor = 0xFF4444FF; // default blue for colorless fluids
        if (newColor != fluidColor) {
            fluidColor = newColor;
            if (world != null && !world.isRemote) {
                world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
            }
        }
        ticksSinceFlow = 0;
    }

    // -----------------------------------------------------------------------
    // Connection rendering
    // -----------------------------------------------------------------------

    public boolean isConnected(EnumFacing face) {
        Block neighbourBlock = world.getBlockState(pos.offset(face)).getBlock();
        if (neighbourBlock instanceof BlockFluidPipe) return true;
        TileEntity neighbour = world.getTileEntity(pos.offset(face));
        if (neighbour == null) return false;
        return neighbour.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite());
    }

    public void refreshConnections() {
        if (world == null || world.isRemote) return;
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

    // -----------------------------------------------------------------------
    // Network lifecycle
    // -----------------------------------------------------------------------

    public void onRemoved() {
        FluidNetwork.onPipeRemoved(world, pos);
    }

    @Override
    public void onLoad() {
        if (!world.isRemote) FluidNetwork.onPipeAdded(world, pos);
    }

    @Override
    public void invalidate() {
        onRemoved();
        super.invalidate();
    }

    // -----------------------------------------------------------------------
    // Tick — master pipe drives transfer
    // -----------------------------------------------------------------------

    @Override
    public void update() {
        if (world.isRemote) return;
        FluidNetwork network = FluidNetwork.getNetwork(world, pos);
        if (network == null) return;

        // Master = smallest pos in network
        BlockPos master = null;
        for (BlockPos p : network.getMembers()) {
            if (master == null || p.toLong() < master.toLong()) master = p;
        }
        if (!pos.equals(master)) return;

        int tickRate = rusticpipes.handlers.ForgeConfigHandler.fluid.flowTickRate;
        if (world.getTotalWorldTime() % tickRate == 0) {
            network.transferFluids(world);
        }

        // Drain visual buffer if no fluid has passed through recently
        ticksSinceFlow++;
        if (ticksSinceFlow > DRAIN_AFTER_TICKS && fluidColor != 0) {
            buffer = null;
            fluidColor = 0;
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }
}
