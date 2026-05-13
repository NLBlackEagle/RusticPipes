package rusticpipes.tileentity;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.items.CapabilityItemHandler;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.network.PipeNetwork;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

public class TileEntityItemPipe extends TileEntity implements ITickable {

    private final Map<EnumFacing, FaceMode> faceModes = new EnumMap<>(EnumFacing.class);

    public TileEntityItemPipe() {
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
            // Sync to client via tile entity update packet
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

    // -----------------------------------------------------------------------
    // Client sync — these methods send TE data to the client when the
    // block state changes so the renderer gets the correct face modes
    // -----------------------------------------------------------------------

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromNBT(pkt.getNbtCompound());
        // Force re-render on client
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
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
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
    // Connection rendering
    // -----------------------------------------------------------------------

    public boolean isConnected(EnumFacing face) {
        Block neighbourBlock = world.getBlockState(pos.offset(face)).getBlock();
        if (neighbourBlock instanceof BlockItemPipe) return true;
        if (neighbourBlock instanceof rusticpipes.block.BlockConduit) return true;
        TileEntity neighbour = world.getTileEntity(pos.offset(face));
        if (neighbour == null) return false;
        if (neighbour.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite())) return true;
        if (neighbour.hasCapability(CapabilityEnergy.ENERGY, face.getOpposite())) return true;
        return false;
    }

    public void refreshConnections() {
        if (world == null || world.isRemote) return;
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

    // -----------------------------------------------------------------------
    // Network lifecycle
    // -----------------------------------------------------------------------

    public void onRemoved() {
        PipeNetwork.onPipeRemoved(world, pos);
    }

    @Override
    public void onLoad() {
        PipeNetwork.onPipeAdded(world, pos);
    }

    @Override
    public void invalidate() {
        onRemoved();
        super.invalidate();
    }

    // -----------------------------------------------------------------------
    // Tick
    // -----------------------------------------------------------------------

    @Override
    public void update() {
        if (world.isRemote) return;
        PipeNetwork network = PipeNetwork.getNetwork(world, pos);
        if (network == null) return;
        // collectFe guards itself with lastFeTick so it only runs once per global tick
        network.collectFe(world);
        if (!network.isMyTick()) return;
        network.transferItems(world);
    }
}
