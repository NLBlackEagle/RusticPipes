package rusticpipes.tileentity;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.items.CapabilityItemHandler;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.network.PipeNetwork;

public class TileEntityItemPipe extends TileEntity implements ITickable {

    public TileEntityItemPipe() {}

    // -----------------------------------------------------------------------
    // Connection rendering — used by BlockItemPipe.getActualState()
    // -----------------------------------------------------------------------

    public boolean isConnected(EnumFacing face) {
        // cheap check — adjacent pipe
        Block neighbourBlock = world.getBlockState(pos.offset(face)).getBlock();
        if (neighbourBlock instanceof BlockItemPipe) return true;
        // expensive check — adjacent inventory
        TileEntity neighbour = world.getTileEntity(pos.offset(face));
        if (neighbour == null) return false;
        return neighbour.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite());
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
    // Tick — delegate transfer to network
    // -----------------------------------------------------------------------

    @Override
    public void update() {
        if (world.isRemote) return;

        PipeNetwork network = PipeNetwork.getNetwork(world, pos);
        if (network == null) return;
        if (!network.isMyTick()) return;

        network.transferItems(world);
    }
}
