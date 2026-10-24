package rusticpipes.tileentity;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import rusticpipes.block.BlockFluidPipe;
import rusticpipes.compat.NuclearCraftCompat;
import rusticpipes.network.FluidNetwork;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public class TileEntityFluidPipe extends TileEntity implements ITickable {

    private final Map<EnumFacing, FaceMode> faceModes = new EnumMap<>(EnumFacing.class);

    /** Small in-transit fluid buffer — used for viewport color rendering. */
    private static final int BUFFER_CAPACITY = 4000;
    @Nullable private FluidStack buffer = null;
    /** Fluid color synced to client for viewport rendering. 0 = empty. */
    private int fluidColor = 0;
    /** Smoothed fill fraction for rendering — lerps toward actual fill to prevent visual jumping. */
    private float visualFillFraction = 0f;
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
        int oldColor = fluidColor;
        Map<EnumFacing, FaceMode> oldModes = new EnumMap<>(faceModes);
        readFromNBT(pkt.getNbtCompound());
        // Fill level is drawn by the TESR every frame; only colour and face modes are baked
        // into the chunk model, so skip the (expensive) chunk rebuild for fill-only updates.
        if (fluidColor != oldColor || !faceModes.equals(oldModes)) {
            world.markBlockRangeForRenderUpdate(pos, pos);
        }
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
        compound.setFloat("visualFill", visualFillFraction);
        if (buffer != null) compound.setTag("buffer", buffer.writeToNBT(new NBTTagCompound()));
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        fluidColor = compound.hasKey("fluidColor") ? compound.getInteger("fluidColor") : 0;
        visualFillFraction = compound.hasKey("visualFill") ? compound.getFloat("visualFill") : 0f;
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

    public float getFillFraction() {
        return buffer != null ? (float) buffer.amount / BUFFER_CAPACITY : 0f;
    }

    /** Smoothed fill fraction for rendering — prevents visual jumping. */
    public float getVisualFillFraction() { return visualFillFraction; }

    public int getBufferSpace() {
        return buffer != null ? BUFFER_CAPACITY - buffer.amount : BUFFER_CAPACITY;
    }

    /** Add fluid to this pipe's buffer. */
    public void addToBuffer(FluidStack fluid) {
        if (fluid == null || fluid.amount <= 0) return;
        if (buffer == null) {
            buffer = fluid.copy();
            buffer.amount = Math.min(buffer.amount, BUFFER_CAPACITY);
        } else {
            buffer.amount = Math.min(buffer.amount + fluid.amount, BUFFER_CAPACITY);
        }
        int rawColor = fluid.getFluid().getColor(fluid);
        // Force full alpha so the color renders correctly
        int newColor = (rawColor & 0xFF000000) == 0 ? (rawColor | 0xFF000000) : rawColor;
        fluidColor = newColor;
        ticksSinceFlow = 0;
    }

    /** Remove up to amount mB from this pipe's buffer. */
    public void drainBuffer(int amount) {
        if (buffer == null || amount <= 0) return;
        buffer.amount -= amount;
        if (buffer.amount <= 0) buffer = null;
        ticksSinceFlow = 0;
    }

    /** Called by FluidNetwork after all transfers — syncs to client when something visible changed. */
    public void syncToClient() {
        // Always keep color in sync with current buffer content
        if (buffer != null && buffer.getFluid() != null) {
            int rawColor = buffer.getFluid().getColor(buffer);
            fluidColor = (rawColor & 0xFF000000) == 0 ? (rawColor | 0xFF000000) : rawColor;
        } else {
            fluidColor = 0;
        }
        // Smooth visual fill fraction to prevent jumping from rapid buffer changes
        float target = getFillFraction();
        visualFillFraction = visualFillFraction + (target - visualFillFraction) * 0.2f;
        // Reset drain timer — network is actively processing this pipe
        ticksSinceFlow = 0;

        int amount = buffer != null ? buffer.amount : 0;
        if (amount != lastSavedAmount) {
            lastSavedAmount = amount;
            markDirty(); // buffer holds real fluid — make sure the chunk saves it
        }

        // Sending every network tick flooded clients with packets and chunk re-renders,
        // even for idle pipes. Only send when the colour changed or the rendered fill
        // moved noticeably (or hit/left empty); the smoothing above converges, so this stops.
        boolean colorChanged = fluidColor != lastSentColor;
        boolean fillChanged  = Math.abs(visualFillFraction - lastSentFill) >= 0.02f
                || (visualFillFraction <= 0.001f) != (lastSentFill <= 0.001f);
        if ((colorChanged || fillChanged) && world != null && !world.isRemote) {
            lastSentColor = fluidColor;
            lastSentFill  = visualFillFraction;
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    // Last state sent to clients / marked for saving (server-side only, not persisted)
    private int lastSentColor = 0;
    private float lastSentFill = 0f;
    private int lastSavedAmount = 0;

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
        if (world != null && !world.isRemote) onRemoved();
        super.invalidate();
    }

    /** Unloaded pipes leave the network so transfers never touch (and reload) their chunk. */
    @Override
    public void onChunkUnload() {
        if (world != null && !world.isRemote) onRemoved();
    }

    // -----------------------------------------------------------------------
    // Tick — master pipe drives transfer
    // -----------------------------------------------------------------------

    @Override
    public void update() {
        if (world.isRemote) return;
        FluidNetwork network = FluidNetwork.getNetwork(world, pos);
        if (network == null) return;

        // NuclearCraft radiation — irradiate nearby players if buffer holds a radioactive fluid
        if (rusticpipes.handlers.ForgeConfigHandler.fluid.enableRadiation
                && buffer != null
                && world.getTotalWorldTime() % rusticpipes.handlers.ForgeConfigHandler.fluid.radiationTickInterval == 0) {
            double rad = NuclearCraftCompat.getFluidRadiation(buffer);
            if (rad > 0) {
                // Scale by how full this pipe is so an empty pipe doesn't irradiate
                double scaled = rad * ((double) buffer.amount / BUFFER_CAPACITY);
                NuclearCraftCompat.irradiateNearbyPlayers(world, pos, scaled,
                        rusticpipes.handlers.ForgeConfigHandler.fluid.radiationRange);
            }
        }

        // Clear visual color if no fluid has passed through recently.
        // Do NOT clear the actual buffer — that holds real fluid that should not be voided.
        ticksSinceFlow++;
        if (ticksSinceFlow > DRAIN_AFTER_TICKS && fluidColor != 0) {
            fluidColor = 0;
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }

        // Only master pipe drives transfer
        if (!pos.equals(network.getMasterPos())) return;

        int tickRate = rusticpipes.handlers.ForgeConfigHandler.fluid.flowTickRate;
        if (world.getTotalWorldTime() % tickRate == 0) {
            network.transferFluids(world);
        }
    }
}
