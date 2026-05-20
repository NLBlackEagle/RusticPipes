package rusticpipes.block;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import rusticpipes.network.PipeNetwork;
import rusticpipes.tileentity.TileEntityConduitBuffer;

public class BlockConduitBuffer extends Block implements ITileEntityProvider {

    /** True when the buffer has any FE stored — drives the on/off texture swap. */
    public static final PropertyBool POWERED = PropertyBool.create("powered");

    /** Which tier this block represents — determines FE draw amount. */
    public final PipeNetwork.SpeedTier tier;

    public BlockConduitBuffer(PipeNetwork.SpeedTier tier) {
        super(Material.IRON);
        this.tier = tier;
        setDefaultState(blockState.getBaseState().withProperty(POWERED, false));
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityConduitBuffer(tier);
    }

    // -----------------------------------------------------------------------
    // BlockState — 1-bit: powered (has FE) vs off (empty)
    // -----------------------------------------------------------------------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, POWERED);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(POWERED) ? 1 : 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(POWERED, meta == 1);
    }

    // -----------------------------------------------------------------------
    // Interaction
    // -----------------------------------------------------------------------

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityConduitBuffer) {
                TileEntityConduitBuffer buf = (TileEntityConduitBuffer) te;
                player.sendMessage(new TextComponentTranslation(
                        "rusticpipes.message.motor.info",
                        tier.name(), buf.getStored(), buf.getCapacity()));
            }
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        // Ensure TE tears down cleanly before the block is removed
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityConduitBuffer)
            world.notifyBlockUpdate(pos, state, state, 3);
        super.breakBlock(world, pos, state);
    }
}
