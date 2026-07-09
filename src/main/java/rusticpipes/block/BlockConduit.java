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
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.energy.CapabilityEnergy;
import rusticpipes.client.model.ConduitModel;
import rusticpipes.network.ConduitClientState;
import rusticpipes.network.ConduitNetwork;
import rusticpipes.tileentity.TileEntityConduit;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import rusticpipes.handlers.TooltipHandler;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;

public class BlockConduit extends Block implements ITileEntityProvider {

    /** Tracks last tick a player received a conduit message — prevents double sends. */
    private static final java.util.Map<java.util.UUID, Long> lastMessageTick = new java.util.HashMap<>();

    public static final int CON_NONE      = 0;
    public static final int CON_CONDUIT   = 1;
    public static final int CON_FE_SOURCE = 2;

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
    public TileEntity createNewTileEntity(World world, int meta) { return new TileEntityConduit(); }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this,
                new net.minecraft.block.properties.IProperty[]{},
                new net.minecraftforge.common.property.IUnlistedProperty[]{
                        ConduitModel.BLOCK_POS,
                        ConduitModel.CONDUIT_TIER,
                        ConduitModel.CON_NORTH, ConduitModel.CON_SOUTH,
                        ConduitModel.CON_EAST,  ConduitModel.CON_WEST,
                        ConduitModel.CON_UP,    ConduitModel.CON_DOWN,
                        ConduitModel.POWERED
                });
    }

    @Override public int getMetaFromState(IBlockState state) { return 0; }
    @Override public IBlockState getStateFromMeta(int meta)  { return getDefaultState(); }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) return state;
        IExtendedBlockState ext = (IExtendedBlockState) state;

        // Read tier directly from the TE's energy capability — works client-side
        // because the capability is populated via the normal chunk load packet.
        // notifyBlockUpdate(flag=2) triggers a re-render when tier changes.
        // Tier always 0 — conduit has no tier of its own
        int tier = 0;

        ext = ext.withProperty(ConduitModel.BLOCK_POS, pos)
                .withProperty(ConduitModel.CONDUIT_TIER, tier);

        for (EnumFacing face : EnumFacing.VALUES)
            ext = ext.withProperty(ConduitModel.getConProperty(face),
                    computeConnection(world, pos, face));

        // Determine powered state: prefer live network data (integrated server / SSP),
        // fall back to the packet-synced client map (dedicated server).
        // Use getBufferStored() > 0 directly — smoothedFill is an EMA that lags
        // behind reality by ~30 ticks, causing the powered texture to linger
        // after the buffer empties.
        boolean powered;
        ConduitNetwork network = ConduitNetwork.getNetwork(world, pos);
        if (network != null) {
            powered = network.getBufferStored() > 0 || network.getSmoothedThroughput() > 0;
        } else {
            powered = ConduitClientState.isPowered(pos);
        }
        ext = ext.withProperty(ConduitModel.POWERED, powered);

        return ext;
    }

    private int computeConnection(IBlockAccess world, BlockPos pos, EnumFacing face) {
        Block neighbour = world.getBlockState(pos.offset(face)).getBlock();
        if (neighbour instanceof BlockConduit) return CON_CONDUIT;
        // Pipes connect via motors — no direct conduit-to-pipe visual connection
        TileEntity nte = world.getTileEntity(pos.offset(face));
        if (nte != null && nte.hasCapability(CapabilityEnergy.ENERGY, face.getOpposite()))
            return CON_FE_SOURCE;
        return CON_NONE;
    }

    @Override
    public IBlockState getActualState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return state;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block blockIn, BlockPos fromPos) {
        if (!world.isRemote) world.notifyBlockUpdate(pos, state, state, 2);
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        // Return false when holding a block so placement is not cancelled
        if (!player.getHeldItem(hand).isEmpty()) return false;

        // Show info on any click if this conduit has at least one FE source connection
        if (!world.isRemote) {
            boolean hasFESource = false;
            for (net.minecraft.util.EnumFacing f : net.minecraft.util.EnumFacing.VALUES) {
                if (computeConnection(world, pos, f) == CON_FE_SOURCE) { hasFESource = true; break; }
            }
            if (hasFESource) {
                // Cooldown — one message per player per tick to prevent double sends
                long tick = world.getTotalWorldTime();
                Long last = lastMessageTick.get(player.getUniqueID());
                if (last == null || last != tick) {
                    lastMessageTick.put(player.getUniqueID(), tick);
                    ConduitNetwork network = ConduitNetwork.getNetwork(world, pos);
                    if (network != null) {
                        int capacity = network.getBufferCapacity();
                        int displayFe = Math.max(network.getBufferStored(), network.getSmoothedThroughput());
                        double lossRate = rusticpipes.handlers.ForgeConfigHandler.conduit.powerLossPerConduitPerTick;
                        int lossPerCable = (int) Math.round(displayFe * lossRate);
                        int totalLoss = lossPerCable * network.getMemberCount();
                        player.sendMessage(new TextComponentTranslation(
                                "rusticpipes.message.conduit.info",
                                displayFe, capacity, lossPerCable, totalLoss));
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityConduit) ((TileEntityConduit) te).onRemoved();
        super.breakBlock(world, pos, state);
    }

    @Override
    public RayTraceResult collisionRayTrace(IBlockState state, World world, BlockPos pos,
                                            Vec3d start, Vec3d end) {
        RayTraceResult best = rayTrace(pos, start, end,
                new AxisAlignedBB(CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX));
        double bestDist = best != null ? best.hitVec.squareDistanceTo(start) : Double.MAX_VALUE;
        best = armHit(pos, start, end, best, bestDist, EnumFacing.NORTH,
                new AxisAlignedBB(CORE_MIN, CORE_MIN, 0,       CORE_MAX, CORE_MAX, CORE_MIN));
        if (best != null && best.sideHit == EnumFacing.NORTH) bestDist = best.hitVec.squareDistanceTo(start);
        best = armHit(pos, start, end, best, bestDist, EnumFacing.SOUTH,
                new AxisAlignedBB(CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, 1));
        if (best != null && best.sideHit == EnumFacing.SOUTH) bestDist = best.hitVec.squareDistanceTo(start);
        best = armHit(pos, start, end, best, bestDist, EnumFacing.WEST,
                new AxisAlignedBB(0,       CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX));
        if (best != null && best.sideHit == EnumFacing.WEST) bestDist = best.hitVec.squareDistanceTo(start);
        best = armHit(pos, start, end, best, bestDist, EnumFacing.EAST,
                new AxisAlignedBB(CORE_MAX, CORE_MIN, CORE_MIN, 1,       CORE_MAX, CORE_MAX));
        if (best != null && best.sideHit == EnumFacing.EAST) bestDist = best.hitVec.squareDistanceTo(start);
        best = armHit(pos, start, end, best, bestDist, EnumFacing.DOWN,
                new AxisAlignedBB(CORE_MIN, 0,       CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX));
        if (best != null && best.sideHit == EnumFacing.DOWN) bestDist = best.hitVec.squareDistanceTo(start);
        best = armHit(pos, start, end, best, bestDist, EnumFacing.UP,
                new AxisAlignedBB(CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX, 1,       CORE_MAX));
        return best;
    }

    private RayTraceResult armHit(BlockPos pos, Vec3d start, Vec3d end,
                                  RayTraceResult current, double currentDist,
                                  net.minecraft.util.EnumFacing armDir, AxisAlignedBB... boxes) {
        for (AxisAlignedBB box : boxes) {
            RayTraceResult hit = rayTrace(pos, start, end, box);
            if (hit != null && hit.hitVec.squareDistanceTo(start) < currentDist) {
                currentDist = hit.hitVec.squareDistanceTo(start);
                current = new RayTraceResult(hit.hitVec, armDir, pos);
            }
        }
        return current;
    }


    @Override
    public void addCollisionBoxToList(IBlockState state, World world, BlockPos pos,
                                      AxisAlignedBB entityBox, List<AxisAlignedBB> collidingBoxes,
                                      @Nullable Entity entity, boolean isActualState) {
        for (AxisAlignedBB box : buildBoxes())
            addCollisionBoxToList(pos, entityBox, collidingBoxes, box);
    }

    private List<AxisAlignedBB> buildBoxes() {
        List<AxisAlignedBB> boxes = new ArrayList<>();
        boxes.add(new AxisAlignedBB(CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX));
        boxes.add(new AxisAlignedBB(CORE_MIN, CORE_MIN, 0,        CORE_MAX, CORE_MAX, CORE_MIN));
        boxes.add(new AxisAlignedBB(CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, 1       ));
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
    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(net.minecraft.item.ItemStack stack, net.minecraft.world.World world,
                               java.util.List<String> tooltip, net.minecraft.client.util.ITooltipFlag flag) {
        TooltipHandler.addConduitTooltip(stack, tooltip);
    }

}
