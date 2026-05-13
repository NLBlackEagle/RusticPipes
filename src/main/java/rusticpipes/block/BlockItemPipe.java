package rusticpipes.block;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import rusticpipes.RusticPipes;
import rusticpipes.tileentity.FaceMode;
import rusticpipes.tileentity.TileEntityItemPipe;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BlockItemPipe extends Block implements ITileEntityProvider {

    public static final PropertyDirection FACING = PropertyDirection.create("facing");

    // Per-face state encoded as integer:
    // 0 = not connected
    // 1 = connected to pipe
    // 2 = connected to inventory, OUTPUT mode
    // 3 = connected to inventory, INPUT mode
    public static final PropertyInteger NORTH = PropertyInteger.create("north", 0, 3);
    public static final PropertyInteger SOUTH = PropertyInteger.create("south", 0, 3);
    public static final PropertyInteger EAST  = PropertyInteger.create("east",  0, 3);
    public static final PropertyInteger WEST  = PropertyInteger.create("west",  0, 3);
    public static final PropertyInteger UP    = PropertyInteger.create("up",    0, 3);
    public static final PropertyInteger DOWN  = PropertyInteger.create("down",  0, 3);

    private static final float CORE_MIN = 4f / 16f;
    private static final float CORE_MAX = 12f / 16f;
    private static final float CAP_MIN  = 3f / 16f;
    private static final float CAP_MAX  = 13f / 16f;
    private static final float CAP_W    = 2f / 16f;

    public BlockItemPipe() {
        super(Material.IRON);
        setDefaultState(blockState.getBaseState()
                .withProperty(FACING, EnumFacing.NORTH)
                .withProperty(NORTH,  0)
                .withProperty(SOUTH,  0)
                .withProperty(EAST,   0)
                .withProperty(WEST,   0)
                .withProperty(UP,     0)
                .withProperty(DOWN,   0));
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
        return new BlockStateContainer(this, FACING, NORTH, SOUTH, EAST, WEST, UP, DOWN);
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
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT_MIPPED || layer == BlockRenderLayer.TRANSLUCENT;
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        TileEntityItemPipe pipe = (te instanceof TileEntityItemPipe) ? (TileEntityItemPipe) te : null;

        for (EnumFacing face : EnumFacing.VALUES) {
            PropertyInteger prop = getFaceProp(face);
            if (prop == null) continue;

            Block neighbour = world.getBlockState(pos.offset(face)).getBlock();
            boolean isPipe = neighbour instanceof BlockItemPipe;

            if (isPipe) {
                state = state.withProperty(prop, 1);
            } else {
                TileEntity neighbourTE = world.getTileEntity(pos.offset(face));
                boolean isInventory = neighbourTE != null
                        && neighbourTE.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face.getOpposite());
                if (isInventory) {
                    boolean isInput = pipe != null && pipe.getFaceMode(face) == FaceMode.INPUT;
                    state = state.withProperty(prop, isInput ? 3 : 2);
                } else {
                    state = state.withProperty(prop, 0);
                }
            }
        }
        return state;
    }

    public static PropertyInteger getFaceProp(EnumFacing face) {
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
        if (world.isRemote) return true;

        EnumFacing clickedArm = getClickedArm(hitX, hitY, hitZ, facing);
        if (clickedArm == null) return false;

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityItemPipe)) return false;
        TileEntityItemPipe pipe = (TileEntityItemPipe) te;

        Block neighbour = world.getBlockState(pos.offset(clickedArm)).getBlock();
        if (neighbour instanceof BlockItemPipe) {
            if (RusticPipes.DEBUG) {
                player.sendMessage(new TextComponentString(
                        capitalize(clickedArm.getName()) + " arm: pipe connection"));
            }
            return true;
        }

        TileEntity neighbourTE = world.getTileEntity(pos.offset(clickedArm));
        if (neighbourTE == null || !neighbourTE.hasCapability(
                CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, clickedArm.getOpposite())) {
            if (RusticPipes.DEBUG) {
                player.sendMessage(new TextComponentString(
                        capitalize(clickedArm.getName()) + " arm: no connection"));
            }
            return true;
        }

        FaceMode current = pipe.getFaceMode(clickedArm);
        FaceMode next = (current == FaceMode.INPUT) ? FaceMode.OUTPUT : FaceMode.INPUT;
        pipe.setFaceMode(clickedArm, next);

        // Always show mode change feedback
        player.sendMessage(new TextComponentString(
                capitalize(clickedArm.getName()) + " face: " + next.getDisplayName()));

        return true;
    }

    private EnumFacing getClickedArm(float x, float y, float z, EnumFacing hitFace) {
        float eps = 0.01f;
        float dx = x < CORE_MIN ? CORE_MIN - x : (x > CORE_MAX ? x - CORE_MAX : 0);
        float dy = y < CORE_MIN ? CORE_MIN - y : (y > CORE_MAX ? y - CORE_MAX : 0);
        float dz = z < CORE_MIN ? CORE_MIN - z : (z > CORE_MAX ? z - CORE_MAX : 0);

        float max = Math.max(dx, Math.max(dy, dz));
        if (max <= eps) return null;

        if (dx == max) return x < CORE_MIN ? EnumFacing.WEST  : EnumFacing.EAST;
        if (dz == max) return z < CORE_MIN ? EnumFacing.NORTH : EnumFacing.SOUTH;
        return y < CORE_MIN ? EnumFacing.DOWN : EnumFacing.UP;
    }

    @Override
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos,
                                            Vec3d start, Vec3d end) {
        List<AxisAlignedBB> boxes = new ArrayList<>();
        boxes.add(new AxisAlignedBB(CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX));
        boxes.add(new AxisAlignedBB(CORE_MIN, CORE_MIN, 0,        CORE_MAX, CORE_MAX, CORE_MIN));
        boxes.add(new AxisAlignedBB(CAP_MIN,  CAP_MIN,  0,        CAP_MAX,  CAP_MAX,  CAP_W));
        boxes.add(new AxisAlignedBB(CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, 1));
        boxes.add(new AxisAlignedBB(CAP_MIN,  CAP_MIN,  1-CAP_W,  CAP_MAX,  CAP_MAX,  1));
        boxes.add(new AxisAlignedBB(0,        CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX));
        boxes.add(new AxisAlignedBB(0,        CAP_MIN,  CAP_MIN,  CAP_W,    CAP_MAX,  CAP_MAX));
        boxes.add(new AxisAlignedBB(CORE_MAX, CORE_MIN, CORE_MIN, 1,        CORE_MAX, CORE_MAX));
        boxes.add(new AxisAlignedBB(1-CAP_W,  CAP_MIN,  CAP_MIN,  1,        CAP_MAX,  CAP_MAX));
        boxes.add(new AxisAlignedBB(CORE_MIN, 0,        CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX));
        boxes.add(new AxisAlignedBB(CAP_MIN,  0,        CAP_MIN,  CAP_MAX,  CAP_W,    CAP_MAX));
        boxes.add(new AxisAlignedBB(CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX, 1,        CORE_MAX));
        boxes.add(new AxisAlignedBB(CAP_MIN,  1-CAP_W,  CAP_MIN,  CAP_MAX,  1,        CAP_MAX));

        RayTraceResult closest = null;
        double closestDist = Double.MAX_VALUE;
        for (AxisAlignedBB box : boxes) {
            RayTraceResult hit = rayTrace(pos, start, end, box);
            if (hit != null) {
                double dist = hit.hitVec.squareDistanceTo(start);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = hit;
                }
            }
        }
        return closest;
    }

    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
                                      AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes,
                                      @Nullable Entity entity, boolean isActualState) {
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX));
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(CORE_MIN, CORE_MIN, 0,        CORE_MAX, CORE_MAX, CORE_MIN));
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(CAP_MIN,  CAP_MIN,  0,        CAP_MAX,  CAP_MAX,  CAP_W));
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, 1));
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(CAP_MIN,  CAP_MIN,  1-CAP_W,  CAP_MAX,  CAP_MAX,  1));
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(0,        CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX));
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(0,        CAP_MIN,  CAP_MIN,  CAP_W,    CAP_MAX,  CAP_MAX));
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(CORE_MAX, CORE_MIN, CORE_MIN, 1,        CORE_MAX, CORE_MAX));
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(1-CAP_W,  CAP_MIN,  CAP_MIN,  1,        CAP_MAX,  CAP_MAX));
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(CORE_MIN, 0,        CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX));
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(CAP_MIN,  0,        CAP_MIN,  CAP_MAX,  CAP_W,    CAP_MAX));
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX, 1,        CORE_MAX));
        addCollisionBoxToList(pos, entityBox, collidingBoxes,
                new AxisAlignedBB(CAP_MIN,  1-CAP_W,  CAP_MIN,  CAP_MAX,  1,        CAP_MAX));
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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
