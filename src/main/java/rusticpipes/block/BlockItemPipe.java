package rusticpipes.block;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import rusticpipes.network.PipeNetwork;
import rusticpipes.tileentity.TileEntityItemPipe;

public class BlockItemPipe extends Block implements ITileEntityProvider {

    public static final PropertyDirection FACING   = PropertyDirection.create("facing");
    public static final PropertyBool      ENDPOINT = PropertyBool.create("endpoint");
    public static final PropertyBool      NORTH    = PropertyBool.create("north");
    public static final PropertyBool      SOUTH    = PropertyBool.create("south");
    public static final PropertyBool      EAST     = PropertyBool.create("east");
    public static final PropertyBool      WEST     = PropertyBool.create("west");
    public static final PropertyBool      UP       = PropertyBool.create("up");
    public static final PropertyBool      DOWN     = PropertyBool.create("down");

    public BlockItemPipe() {
        super(Material.IRON);
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING,   EnumFacing.NORTH)
                .withProperty(ENDPOINT, false)
                .withProperty(NORTH,    false)
                .withProperty(SOUTH,    false)
                .withProperty(EAST,     false)
                .withProperty(WEST,     false)
                .withProperty(UP,       false)
                .withProperty(DOWN,     false));
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state)   { return false; }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityItemPipe();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING, ENDPOINT, NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.byIndex(meta & 7));
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        int pipeNeighbours = 0;
        boolean hasInventory = false;

        for (EnumFacing face : EnumFacing.VALUES) {
            Block neighbour = world.getBlockState(pos.offset(face)).getBlock();
            boolean isPipe = neighbour instanceof BlockItemPipe;

            if (isPipe) {
                pipeNeighbours++;
            } else {
                TileEntity te = world.getTileEntity(pos.offset(face));
                if (te != null && te.hasCapability(
                        CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite())) {
                    hasInventory = true;
                }
            }

            // Set connection bool for this face
            PropertyBool prop = getFaceProperty(face);
            if (prop != null) {
                boolean connected = isPipe || (world.getTileEntity(pos.offset(face)) != null
                        && world.getTileEntity(pos.offset(face)).hasCapability(
                        CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite()));
                state = state.withProperty(prop, connected);
            }
        }

        boolean endpoint = hasInventory || pipeNeighbours >= 3;
        return state.withProperty(ENDPOINT, endpoint);
    }

    private static PropertyBool getFaceProperty(EnumFacing face) {
        switch (face) {
            case NORTH: return NORTH;
            case SOUTH: return SOUTH;
            case EAST:  return EAST;
            case WEST:  return WEST;
            case UP:    return UP;
            case DOWN:  return DOWN;
            default:    return null;
        }
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer, EnumHand hand) {
        return getDefaultState()
                .withProperty(FACING, EnumFacing.getDirectionFromEntityLiving(pos, placer));
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block blockIn, BlockPos fromPos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityItemPipe) {
            ((TileEntityItemPipe) te).refreshConnections();
        }
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) return false;
        if (world.isRemote) return true;
        PipeNetwork network = PipeNetwork.getNetwork(world, pos);
        if (network == null) return false;
        network.reverseNetwork(world);
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityItemPipe) {
            ((TileEntityItemPipe) te).onRemoved();
        }
        super.breakBlock(world, pos, state);
    }
}
