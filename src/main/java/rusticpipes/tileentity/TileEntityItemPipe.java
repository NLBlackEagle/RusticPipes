package rusticpipes.tileentity;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.handlers.ForgeConfigHandler;
import rusticpipes.network.PipeNetwork;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class TileEntityItemPipe extends TileEntity implements ITickable {

    private final Map<EnumFacing, Boolean> faceEnabled = new EnumMap<>(EnumFacing.class);
    private int rrPointer = 0;

    public TileEntityItemPipe() {
        for (EnumFacing face : EnumFacing.VALUES) {
            faceEnabled.put(face, true);
        }
    }

    public boolean isConnected(EnumFacing face) {
        // cheap check first
        Block neighbourBlock = world.getBlockState(pos.offset(face)).getBlock();
        if (neighbourBlock instanceof BlockItemPipe) return true;

        // expensive check - need null guard first
        TileEntity neighbour = world.getTileEntity(pos.offset(face));
        if (neighbour == null) return false;
        if (neighbour.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite())) return true;

        return false;
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
    public void update()  {
        // guard 1 - server side only
        if (world.isRemote) return;

        // guard 2 - get network, if null return
        PipeNetwork network = PipeNetwork.getNetwork(world, pos);
        if (network == null) return;

        // guard 3 - is it our tick? if not return
        if (!network.isMyTick()) return;

        // find all output faces - faces that are:
        // - enabled
        // - not connecting to another pipe (endpoints only)
        // - neighbour can accept items (has IItemHandler)
        // - neighbour's FACING points away from this pipe (it's an output)
        List<EnumFacing> outputFaces = new ArrayList<>();


        for (EnumFacing face : EnumFacing.VALUES) {

            IBlockState neighbourState = world.getBlockState(pos.offset(face));

            // check 1 - is this face enabled?
            if (!faceEnabled.get(face)) continue;

            Block neighbourBlock = neighbourState.getBlock();

            // if it IS a pipe - check facing direction
            if (neighbourBlock instanceof BlockItemPipe) {
                EnumFacing neighbourFacing = neighbourState.getValue(BlockItemPipe.FACING);
                if (face != neighbourFacing) continue;
            }

            // if it's NOT a pipe - check IItemHandler
            if (!(neighbourBlock instanceof BlockItemPipe)) {
                TileEntity neighbour = world.getTileEntity(pos.offset(face));
                if (neighbour == null) continue;
                if (!neighbour.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite())) continue;
            }


            // if all checks pass, add to outputFaces
            outputFaces.add(face);
        }

        // if no output faces, nothing to do - return
        if (outputFaces.isEmpty()) return;

        List<EnumFacing> inputFaces = new ArrayList<>();

        for (EnumFacing face : EnumFacing.VALUES) {
            IBlockState neighbourState = world.getBlockState(pos.offset(face));
            if (!faceEnabled.get(face)) continue;
            Block neighbourBlock = neighbourState.getBlock();
            if (neighbourBlock instanceof BlockItemPipe) {
                EnumFacing neighbourFacing = neighbourState.getValue(BlockItemPipe.FACING);
                if (neighbourFacing != face.getOpposite()) continue;
            }
            if (!(neighbourBlock instanceof BlockItemPipe)) {
                TileEntity neighbour = world.getTileEntity(pos.offset(face));
                if (neighbour == null) continue;
                if (!neighbour.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite())) continue;
            }
            inputFaces.add(face);
        }

        if (inputFaces.isEmpty()) return;

        for (EnumFacing inputFace : inputFaces) {

            TileEntity inputTE = world.getTileEntity(pos.offset(inputFace));
            if (inputTE == null) continue;
            IItemHandler source = inputTE.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, inputFace.getOpposite());
            if (source == null) continue;

            // loop through slots in source
            for (int slot = 0; slot < source.getSlots(); slot++) {

                // simulate extraction - true means "don't actually take it yet"
                ItemStack stack =  source.extractItem(slot, ForgeConfigHandler.server.pipeTransferSize, true);

                // if slot was empty, skip
                if (stack.isEmpty()) continue;

                // pick next output face using round-robin
                EnumFacing outputFace = outputFaces.get(rrPointer % outputFaces.size());
                
                TileEntity outputTE = world.getTileEntity(pos.offset(outputFace));
                if (outputTE == null) continue;
                IItemHandler outputHandler = outputTE
                        .getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, outputFace.getOpposite());
                if (outputHandler == null) continue;

                // try to insert - if successfull, commit the extraction
                ItemStack remaining = outputHandler.insertItem(slot, stack, true);

                if (remaining.isEmpty()) {
                    source.extractItem(slot, stack.getCount(), false); // commit extraction
                    outputHandler.insertItem(slot, stack, false); // commit insertion
                }

                // advance rrPointer
                rrPointer++;
                break;

            }

        }

    }
}