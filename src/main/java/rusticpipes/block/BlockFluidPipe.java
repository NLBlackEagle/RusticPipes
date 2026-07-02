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
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import rusticpipes.client.model.PipeModel;
import rusticpipes.handlers.TooltipHandler;
import rusticpipes.tileentity.FaceMode;
import rusticpipes.tileentity.TileEntityFluidPipe;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class BlockFluidPipe extends Block implements ITileEntityProvider {

    public static final int CON_NONE       = 0;
    public static final int CON_PIPE       = 1;
    public static final int CON_INV_OUTPUT = 2;
    public static final int CON_INV_INPUT  = 3;

    private static final float CORE_MIN = 4f / 16f;
    private static final float CORE_MAX = 12f / 16f;
    private static final float CAP_MIN  = 3f / 16f;
    private static final float CAP_MAX  = 13f / 16f;
    private static final float CAP_W    = 2f / 16f;

    public final PipeColor pipeColor;

    public BlockFluidPipe(PipeColor color) {
        super(Material.IRON);
        this.pipeColor = color;
        setDefaultState(blockState.getBaseState());
    }

    @Override public boolean isOpaqueCube(IBlockState state) { return false; }
    @Override public boolean isFullCube(IBlockState state)   { return false; }
    @Override public boolean hasTileEntity(IBlockState state) { return true; }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityFluidPipe();
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this,
                new net.minecraft.block.properties.IProperty[]{},
                new net.minecraftforge.common.property.IUnlistedProperty[]{
                        PipeModel.BLOCK_POS,
                        PipeModel.PIPE_COLOR,
                        PipeModel.CON_NORTH,
                        PipeModel.CON_SOUTH,
                        PipeModel.CON_EAST,
                        PipeModel.CON_WEST,
                        PipeModel.CON_UP,
                        PipeModel.CON_DOWN,
                        rusticpipes.client.model.FluidPipeModel.FLUID_COLOR
                });
    }

    @Override public int getMetaFromState(IBlockState state) { return 0; }
    @Override public IBlockState getStateFromMeta(int meta)  { return getDefaultState(); }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) return state;
        IExtendedBlockState ext = (IExtendedBlockState) state;

        TileEntity te = world.getTileEntity(pos);
        TileEntityFluidPipe pipe = (te instanceof TileEntityFluidPipe) ? (TileEntityFluidPipe) te : null;

        int fluidColor = 0;
        if (pipe != null) fluidColor = pipe.getFluidColor();

        ext = ext.withProperty(PipeModel.BLOCK_POS, pos)
                .withProperty(PipeModel.PIPE_COLOR, this.pipeColor)
                .withProperty(rusticpipes.client.model.FluidPipeModel.FLUID_COLOR, fluidColor);

        for (EnumFacing face : EnumFacing.VALUES) {
            int con = computeConnection(world, pos, face, pipe);
            ext = ext.withProperty(PipeModel.getConProperty(face), con);
        }
        return ext;
    }

    private int computeConnection(IBlockAccess world, BlockPos pos, EnumFacing face,
                                   @Nullable TileEntityFluidPipe pipe) {
        Block neighbour = world.getBlockState(pos.offset(face)).getBlock();
        if (neighbour instanceof BlockFluidPipe
                && ((BlockFluidPipe) neighbour).pipeColor == this.pipeColor) {
            return CON_PIPE;
        }
        TileEntity neighbourTE = world.getTileEntity(pos.offset(face));
        if (neighbourTE != null
                && neighbourTE.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, face.getOpposite())) {
            boolean isInput = pipe != null && pipe.getFaceMode(face) == FaceMode.INPUT;
            return isInput ? CON_INV_INPUT : CON_INV_OUTPUT;
        }
        return CON_NONE;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block blockIn, BlockPos fromPos) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityFluidPipe) ((TileEntityFluidPipe) te).refreshConnections();
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        net.minecraft.item.ItemStack held = player.getHeldItem(hand);
        if (!held.isEmpty() && held.getItem() instanceof net.minecraft.item.ItemBlock) {
            Block heldBlock = ((net.minecraft.item.ItemBlock) held.getItem()).getBlock();
            if (heldBlock instanceof BlockFluidPipe) return false;
        }

        if (world.isRemote) return true;

        // Debug info — only when RusticPipes.DEBUG is true
        if (rusticpipes.RusticPipes.DEBUG) {
            int texBase = Math.abs((pos.getX() * 73856093) ^ (pos.getY() * 19349663) ^ (pos.getZ() * 83492791));
            boolean vpN = ((texBase + 3)  % 20) % 10 < 3;
            boolean vpS = ((texBase + 7)  % 20) % 10 < 3;
            boolean vpE = ((texBase + 11) % 20) % 10 < 3;
            boolean vpW = ((texBase + 13) % 20) % 10 < 3;
            rusticpipes.network.FluidNetwork network = rusticpipes.network.FluidNetwork.getNetwork(world, pos);
            TileEntity dbgTe = world.getTileEntity(pos);
            rusticpipes.tileentity.TileEntityFluidPipe dbgPipe = dbgTe instanceof rusticpipes.tileentity.TileEntityFluidPipe
                    ? (rusticpipes.tileentity.TileEntityFluidPipe) dbgTe : null;
            player.sendMessage(new net.minecraft.util.text.TextComponentString(
                    "[FluidPipe] pos=" + pos + " color=" + pipeColor.registryName
                    + " vp=N:" + vpN + " S:" + vpS + " E:" + vpE + " W:" + vpW
                    + " network=" + (network != null ? network.getMembers().size() + " members" : "none")
                    + " buffer=" + (dbgPipe != null && dbgPipe.getBuffer() != null ? dbgPipe.getBuffer().amount + "mB " + dbgPipe.getBuffer().getFluid().getName() : "empty")
            ));
        }

        EnumFacing clickedArm = getClickedArm(hitX, hitY, hitZ);
        if (clickedArm == null) return false;

        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityFluidPipe)) return false;
        TileEntityFluidPipe pipe = (TileEntityFluidPipe) te;

        Block neighbour = world.getBlockState(pos.offset(clickedArm)).getBlock();
        if (neighbour instanceof BlockFluidPipe) return true;

        TileEntity neighbourTE = world.getTileEntity(pos.offset(clickedArm));
        if (neighbourTE == null || !neighbourTE.hasCapability(
                CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, clickedArm.getOpposite())) {
            return true;
        }

        FaceMode current = pipe.getFaceMode(clickedArm);
        FaceMode next = (current == FaceMode.INPUT) ? FaceMode.OUTPUT : FaceMode.INPUT;
        pipe.setFaceMode(clickedArm, next);
        player.sendMessage(new TextComponentString(
                capitalize(clickedArm.getName()) + " face: " + next.getDisplayName()));
        return true;
    }

    private EnumFacing getClickedArm(float x, float y, float z) {
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
        return boxes;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return FULL_BLOCK_AABB;
    }

    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.SOLID || layer == BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityFluidPipe) {
            TileEntityFluidPipe pipe = (TileEntityFluidPipe) te;
            // Drop filled buckets for any fluid remaining in the in-transit buffer
            if (rusticpipes.handlers.ForgeConfigHandler.fluid.dropBucketsOnBreak) {
                spillFluid(world, pos, pipe.getBuffer());
            }
            pipe.onRemoved();
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(net.minecraft.item.ItemStack stack, World world,
                               List<String> tooltip, net.minecraft.client.util.ITooltipFlag flag) {
        TooltipHandler.addFluidPipeTooltip(stack, tooltip, pipeColor);
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * Spills fluid when a pipe or tank is broken:
     *  - If the fluid has an associated world block (water, lava, corium, etc.),
     *    places a source block at the break position so it flows naturally.
     *  - Otherwise falls back to dropping filled bucket items (one per 1000 mB).
     *
     * Only acts when fluid.amount >= 1000 mB.
     */
    static void spillFluidFromSurface(World world,
                                      net.minecraftforge.fluids.FluidStack fluid,
                                      BlockPos min, BlockPos max) {
        if (world.isRemote || fluid == null || fluid.amount < 1000) return;
        net.minecraft.block.Block fluidBlock = fluid.getFluid().getBlock();
        if (fluidBlock == null) return;

        int totalBuckets = Math.min(fluid.amount / 1000, 64);
        net.minecraft.block.state.IBlockState sourceState = fluidBlock.getDefaultState();

        // All positions one block outside each face of the bounding box
        java.util.List<BlockPos> surface = new java.util.ArrayList<>();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                surface.add(new BlockPos(x, min.getY() - 1, z));
                surface.add(new BlockPos(x, max.getY() + 1, z));
            }
            for (int y = min.getY(); y <= max.getY(); y++) {
                surface.add(new BlockPos(x, y, min.getZ() - 1));
                surface.add(new BlockPos(x, y, max.getZ() + 1));
            }
        }
        for (int z = min.getZ(); z <= max.getZ(); z++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                surface.add(new BlockPos(min.getX() - 1, y, z));
                surface.add(new BlockPos(max.getX() + 1, y, z));
            }
        }

        java.util.Collections.shuffle(surface);

        int placed = 0;
        for (BlockPos p : surface) {
            if (placed >= totalBuckets) break;
            net.minecraft.block.state.IBlockState existing = world.getBlockState(p);
            if (existing.getMaterial() == net.minecraft.block.material.Material.AIR
                    || existing.getBlock().isReplaceable(world, p)
                    || existing.getMaterial().isLiquid()) {
                world.setBlockState(p, sourceState);
                placed++;
            }
        }
    }

    static void spillFluid(World world, BlockPos pos,
                           @Nullable net.minecraftforge.fluids.FluidStack fluid) {
        if (world.isRemote || fluid == null || fluid.amount < 1000) return;

        net.minecraft.block.Block fluidBlock = fluid.getFluid().getBlock();
        if (fluidBlock == null) return; // no placeable block — void silently

        // Place one source block per 1000 mB, spreading outward from the break
        // position via BFS so larger tanks create a wider initial spill.
        // Cap at 64 blocks to avoid excessive lag on enormous tanks.
        int toPlace = Math.min(fluid.amount / 1000, 16);
        net.minecraft.block.state.IBlockState sourceState = fluidBlock.getDefaultState();

        java.util.Queue<net.minecraft.util.math.BlockPos> queue = new java.util.ArrayDeque<>();
        java.util.Set<net.minecraft.util.math.BlockPos>   visited = new java.util.HashSet<>();
        queue.add(pos);
        visited.add(pos);

        while (!queue.isEmpty() && toPlace > 0) {
            net.minecraft.util.math.BlockPos current = queue.poll();
            net.minecraft.block.state.IBlockState existing = world.getBlockState(current);

            // Only place in air or fluid-replaceable blocks
            if (existing.getBlock().isReplaceable(world, current)
                    || existing.getMaterial() == net.minecraft.block.material.Material.AIR
                    || existing.getMaterial().isLiquid()) {
                world.setBlockState(current, sourceState);
                toPlace--;
            }

            // Expand horizontally first (same Y), then drop down one level
            for (net.minecraft.util.EnumFacing face : new net.minecraft.util.EnumFacing[]{
                    net.minecraft.util.EnumFacing.NORTH, net.minecraft.util.EnumFacing.SOUTH,
                    net.minecraft.util.EnumFacing.EAST,  net.minecraft.util.EnumFacing.WEST,
                    net.minecraft.util.EnumFacing.DOWN}) {
                net.minecraft.util.math.BlockPos neighbour = current.offset(face);
                if (visited.add(neighbour)) queue.add(neighbour);
            }
        }
    }
}
