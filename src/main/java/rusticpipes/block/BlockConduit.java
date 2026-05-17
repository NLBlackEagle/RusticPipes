package rusticpipes.block;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import rusticpipes.client.model.ConduitModel;
import rusticpipes.network.ConduitNetwork;
import rusticpipes.network.PipeNetwork;
import rusticpipes.tileentity.TileEntityConduit;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BlockConduit extends Block implements ITileEntityProvider {

    // Connection type constants
    public static final int CON_NONE     = 0; // not connected
    public static final int CON_CONDUIT  = 1; // connected to another conduit
    public static final int CON_FE_SOURCE = 2; // connected to a FE source/sink (reserved for future use)
    public static final int CON_PIPE_NET = 3; // connected to a pipe network

    private static final float CORE_MIN = 6f / 16f;
    private static final float CORE_MAX = 10f / 16f;

    public BlockConduit() {
        super(Material.IRON);
        setDefaultState(blockState.getBaseState());
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state)   { return false; }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityConduit();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this,
                new net.minecraft.block.properties.IProperty[]{},
                new net.minecraftforge.common.property.IUnlistedProperty[]{
                        ConduitModel.BLOCK_POS,
                        ConduitModel.CONDUIT_TIER,
                        ConduitModel.CON_NORTH,
                        ConduitModel.CON_SOUTH,
                        ConduitModel.CON_EAST,
                        ConduitModel.CON_WEST,
                        ConduitModel.CON_UP,
                        ConduitModel.CON_DOWN
                });
    }

    @Override
    public int getMetaFromState(IBlockState state) { return 0; }

    @Override
    public IBlockState getStateFromMeta(int meta) { return getDefaultState(); }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) {
            rusticpipes.RusticPipes.LOGGER.warn("[Conduit] getExtendedState: state is NOT IExtendedBlockState at " + pos);
            return state;
        }
        IExtendedBlockState ext = (IExtendedBlockState) state;

        TileEntity teTier = world.getTileEntity(pos);
        PipeNetwork.SpeedTier tier = (teTier instanceof TileEntityConduit)
                ? ((TileEntityConduit) teTier).cachedTier
                : PipeNetwork.SpeedTier.SLOW;

        ext = ext.withProperty(ConduitModel.BLOCK_POS, pos)
                .withProperty(ConduitModel.CONDUIT_TIER, tier);

        for (EnumFacing face : EnumFacing.VALUES) {
            int con = computeConnection(world, pos, face);
            ext = ext.withProperty(ConduitModel.getConProperty(face), con);
        }

        return ext;
    }

    private int computeConnection(IBlockAccess world, BlockPos pos, EnumFacing face) {
        Block neighbour = world.getBlockState(pos.offset(face)).getBlock();
        if (neighbour instanceof BlockConduit) return CON_CONDUIT;
        if (neighbour instanceof BlockItemPipe) return CON_PIPE_NET;
        // Detect adjacent FE sources/sinks (generators, machines)
        TileEntity neighbourTE = world.getTileEntity(pos.offset(face));
        if (neighbourTE != null
                && neighbourTE.hasCapability(net.minecraftforge.energy.CapabilityEnergy.ENERGY, face.getOpposite())) {
            return CON_FE_SOURCE;
        }
        return CON_NONE;
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block blockIn, BlockPos fromPos) {
        world.notifyBlockUpdate(pos, state, state, 3);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            ConduitNetwork network = ConduitNetwork.getNetwork(pos);
            if (network != null) {
                int stored   = network.getBufferStored();
                int capacity = network.getBufferCapacity();
                player.sendMessage(new net.minecraft.util.text.TextComponentString(
                        "Conduit [" + network.getMemberCount() + " blocks] "
                        + stored + "/" + capacity + " FE"));
            }
        }
        return true;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityConduit) {
            ((TileEntityConduit) te).onRemoved();
        }
        super.breakBlock(world, pos, state);
    }

    // -----------------------------------------------------------------------
    // Collision / bounding boxes
    // -----------------------------------------------------------------------

    @Override
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos,
                                            Vec3d start, Vec3d end) {
        List<AxisAlignedBB> boxes = buildBoxes();
        RayTraceResult closest = null;
        double closestDist = Double.MAX_VALUE;
        for (AxisAlignedBB box : boxes) {
            RayTraceResult hit = rayTrace(pos, start, end, box);
            if (hit != null) {
                double dist = hit.hitVec.squareDistanceTo(start);
                if (dist < closestDist) { closestDist = dist; closest = hit; }
            }
        }
        return closest;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
                                      AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes,
                                      @Nullable Entity entity, boolean isActualState) {
        for (AxisAlignedBB box : buildBoxes()) {
            addCollisionBoxToList(pos, entityBox, collidingBoxes, box);
        }
    }

    private List<AxisAlignedBB> buildBoxes() {
        List<AxisAlignedBB> boxes = new ArrayList<>();
        boxes.add(new AxisAlignedBB(CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX));
        boxes.add(new AxisAlignedBB(CORE_MIN, CORE_MIN, 0,        CORE_MAX, CORE_MAX, CORE_MIN));
        boxes.add(new AxisAlignedBB(CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, 1));
        boxes.add(new AxisAlignedBB(0,        CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX));
        boxes.add(new AxisAlignedBB(CORE_MAX, CORE_MIN, CORE_MIN, 1,        CORE_MAX, CORE_MAX));
        boxes.add(new AxisAlignedBB(CORE_MIN, 0,        CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX));
        boxes.add(new AxisAlignedBB(CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX, 1,        CORE_MAX));
        return boxes;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }
}
