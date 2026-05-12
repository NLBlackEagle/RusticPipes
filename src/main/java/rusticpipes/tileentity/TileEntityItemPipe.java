package rusticpipes.tileentity;

import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.items.CapabilityItemHandler;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.network.PipeNetwork;

import java.util.EnumMap;
import java.util.Map;

public class TileEntityItemPipe extends TileEntity implements ITickable {

    // Per-face mode — defaults to OUTPUT on all faces
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
            world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
        }
    }

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

    public boolean isConnected(EnumFacing face) {
        Block neighbourBlock = world.getBlockState(pos.offset(face)).getBlock();
        if (neighbourBlock instanceof BlockItemPipe) return true;
        TileEntity neighbour = world.getTileEntity(pos.offset(face));
        if (neighbour == null) return false;
        return neighbour.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite());
    }

    public void refreshConnections() {
        if (world == null || world.isRemote) return;
        world.notifyBlockUpdate(pos, world.getBlockState(pos), world.getBlockState(pos), 3);
    }

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

    @Override
    public void update() {
        if (world.isRemote) return;
        PipeNetwork network = PipeNetwork.getNetwork(world, pos);
        if (network == null) return;
        if (!network.isMyTick()) return;
        network.transferItems(world);
    }
}
