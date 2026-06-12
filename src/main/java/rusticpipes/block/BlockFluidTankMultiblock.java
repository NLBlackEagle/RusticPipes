package rusticpipes.block;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import rusticpipes.multiblock.TankMultiblock;
import rusticpipes.tileentity.TileEntityFluidTankMultiblock;

import javax.annotation.Nullable;
import java.util.List;

public class BlockFluidTankMultiblock extends Block implements ITileEntityProvider {

    /**
     * Which face of this block shows the viewport.
     * Only 5 values — fits easily in 3 meta bits (max meta = 15).
     */
    public enum ViewportFace implements IStringSerializable {
        NONE, NORTH, SOUTH, EAST, WEST;
        @Override public String getName() { return name().toLowerCase(); }
    }

    /**
     * Which row of the multiblock this block occupies.
     * Stored in the TE and passed to the model via extended block state —
     * never touches block metadata.
     */
    public enum ViewportRow implements IStringSerializable {
        NONE, SINGLE, BOTTOM, CENTER, TOP;
        @Override public String getName() { return name().toLowerCase(); }
    }

    public static final PropertyEnum<ViewportFace> VIEWPORT =
            PropertyEnum.create("viewport", ViewportFace.class);

    /** Unlisted property: row info from TE, read by the custom baked model. */
    public static final IUnlistedProperty<ViewportRow> VIEWPORT_ROW =
            new IUnlistedProperty<ViewportRow>() {
                @Override public String getName()                  { return "viewport_row"; }
                @Override public boolean isValid(ViewportRow v)    { return true; }
                @Override public Class<ViewportRow> getType()      { return ViewportRow.class; }
                @Override public String valueToString(ViewportRow v) { return v.getName(); }
            };

    /**
     * Unlisted property: extra exterior side face for 2x2 corner viewport blocks.
     * Null means no extra side face (3x3+, or non-top blocks).
     */
    public static final IUnlistedProperty<EnumFacing> SIDE_FACE =
            new IUnlistedProperty<EnumFacing>() {
                @Override public String getName()                   { return "side_face"; }
                @Override public boolean isValid(EnumFacing v)      { return true; }
                @Override public Class<EnumFacing> getType()        { return EnumFacing.class; }
                @Override public String valueToString(EnumFacing v) { return v == null ? "none" : v.getName(); }
            };

    public BlockFluidTankMultiblock() {
        super(Material.IRON);
        setHardness(2.5f);
        setResistance(10f);
        setDefaultState(blockState.getBaseState().withProperty(VIEWPORT, ViewportFace.NONE));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new ExtendedBlockState(this,
                new net.minecraft.block.properties.IProperty[]{ VIEWPORT },
                new IUnlistedProperty[]{ VIEWPORT_ROW, SIDE_FACE });
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(VIEWPORT,
                ViewportFace.values()[meta % ViewportFace.values().length]);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(VIEWPORT).ordinal();
    }

    @Override
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        if (!(state instanceof IExtendedBlockState)) return state;
        IExtendedBlockState ext = (IExtendedBlockState) state;
        ViewportRow row = ViewportRow.NONE;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityFluidTankMultiblock) {
            row = ((TileEntityFluidTankMultiblock) te).getViewportRow();
        }
        EnumFacing sideFace = null;
        if (te instanceof TileEntityFluidTankMultiblock) {
            sideFace = ((TileEntityFluidTankMultiblock) te).getSideFace();
        }
        return ext.withProperty(VIEWPORT_ROW, row).withProperty(SIDE_FACE, sideFace);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityFluidTankMultiblock();
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote) return;
        tryValidate(world, pos);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block blockIn, BlockPos fromPos) {
        if (world.isRemote) return;
        if (blockIn instanceof BlockFluidTankMultiblock) {
            // A tank block was placed nearby — try to form a larger structure but
            // don't invalidate the existing one (prevents TESR flicker)
            rusticpipes.multiblock.TankMultiblock.Structure st =
                    rusticpipes.multiblock.TankMultiblock.validateMultiblock(world, pos);
            if (st != null) rusticpipes.multiblock.TankMultiblock.applyMultiblock(world, st);
        } else {
            // Non-tank block placed or removed — full re-validation (can invalidate)
            tryValidate(world, pos);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityFluidTankMultiblock) {
                ((TileEntityFluidTankMultiblock) te).onBreak();
            }
        }
        super.breakBlock(world, pos, state);
        // After the block is removed, check if remaining tank blocks form a valid multiblock
        if (!world.isRemote) {
            for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.VALUES) {
                BlockPos neighborPos = pos.offset(face);
                if (world.getBlockState(neighborPos).getBlock() instanceof BlockFluidTankMultiblock) {
                    rusticpipes.multiblock.TankMultiblock.Structure st =
                            rusticpipes.multiblock.TankMultiblock.validateMultiblock(world, neighborPos);
                    if (st != null) {
                        rusticpipes.multiblock.TankMultiblock.applyMultiblock(world, st);
                        break;
                    }
                }
            }
        }
    }

    private void tryValidate(World world, BlockPos pos) {
        TankMultiblock.Structure st = TankMultiblock.validateMultiblock(world, pos);
        if (st != null) {
            TankMultiblock.applyMultiblock(world, st);
        } else {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityFluidTankMultiblock) {
                ((TileEntityFluidTankMultiblock) te).invalidate();
            }
            IBlockState current = world.getBlockState(pos);
            if (current.getValue(VIEWPORT) != ViewportFace.NONE) {
                world.setBlockState(pos, current.withProperty(VIEWPORT, ViewportFace.NONE), 2);
            }
        }
    }

    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        // Viewport blocks render solid faces in SOLID and the viewport face in CUTOUT_MIPPED
        if (state.getValue(VIEWPORT) != ViewportFace.NONE) {
            return layer == BlockRenderLayer.SOLID || layer == BlockRenderLayer.CUTOUT_MIPPED;
        }
        return layer == BlockRenderLayer.SOLID;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (hand != EnumHand.MAIN_HAND) return true;
        if (world.isRemote) return true;
        if (!player.isSneaking()) return false;
        sendTankInfo(world, pos, player);
        return true;
    }

    private void sendTankInfo(World world, BlockPos pos, EntityPlayer player) {
        if (world.isRemote) return;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityFluidTankMultiblock)) return;
        TileEntityFluidTankMultiblock tank = (TileEntityFluidTankMultiblock) te;
        net.minecraftforge.fluids.FluidStack fluid = tank.getFluid();
        int capacity = tank.getCapacity();
        if (fluid == null || fluid.amount == 0) {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                    "rusticpipes.message.tank.empty", capacity));
        } else {
            player.sendMessage(new net.minecraft.util.text.TextComponentTranslation(
                    "rusticpipes.message.tank.info",
                    fluid.getLocalizedName(), fluid.amount, capacity));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(net.minecraft.item.ItemStack stack, World world,
                               List<String> tooltip, net.minecraft.client.util.ITooltipFlag flag) {
        rusticpipes.handlers.TooltipHandler.addFluidTankTooltip(stack, tooltip);
    }
}
