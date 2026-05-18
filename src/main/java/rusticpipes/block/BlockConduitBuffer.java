package rusticpipes.block;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import rusticpipes.network.PipeNetwork;
import rusticpipes.tileentity.TileEntityConduitBuffer;

public class BlockConduitBuffer extends Block implements ITileEntityProvider {

    /** Which tier this block represents — determines FE draw amount. */
    public final PipeNetwork.SpeedTier tier;

    public BlockConduitBuffer(PipeNetwork.SpeedTier tier) {
        super(Material.IRON);
        this.tier = tier;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityConduitBuffer(tier);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityConduitBuffer) {
                TileEntityConduitBuffer buf = (TileEntityConduitBuffer) te;
                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                        "Buffer [" + tier.name() + "] "
                        + buf.getStored() + "/" + buf.getCapacity() + " FE"));
            }
        }
        return true;
    }
}
