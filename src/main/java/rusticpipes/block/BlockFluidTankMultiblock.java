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
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import rusticpipes.multiblock.TankMultiblock;
import rusticpipes.tileentity.TileEntityFluidTankMultiblock;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Multiblock fluid tank block. Fully solid exterior (fluid_tank_solid texture).
 * Interior is rendered by FluidTankMultiblockRenderer TESR.
 *
 * Uses the same item as fluid_tank — player places these in the correct
 * footprint and they auto-validate on placement.
 *
 * Supported configurations: 2x2, 3x3, 4x4 footprint, 1-10 blocks high.
 */
public class BlockFluidTankMultiblock extends Block implements ITileEntityProvider {

    public BlockFluidTankMultiblock() {
        super(Material.IRON);
        setHardness(2.5f);
        setResistance(10f);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityFluidTankMultiblock();
    }

    // -----------------------------------------------------------------------
    // Auto-validate on placement / neighbour change / break
    // -----------------------------------------------------------------------

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote) return;
        tryValidate(world, pos);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block blockIn, BlockPos fromPos) {
        if (world.isRemote) return;
        // Only react when a multiblock tank block changes nearby
        boolean neighbourIsMultiblock =
                world.getBlockState(fromPos).getBlock() instanceof BlockFluidTankMultiblock;
        TileEntity te = world.getTileEntity(pos);
        boolean wasInMultiblock = te instanceof TileEntityFluidTankMultiblock
                && ((TileEntityFluidTankMultiblock) te).isPartOfMultiblock();
        if (!neighbourIsMultiblock && !wasInMultiblock) return;
        tryValidate(world, pos);
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
    }

    private void tryValidate(World world, BlockPos pos) {
        TankMultiblock.Structure st = TankMultiblock.validateMultiblock(world, pos);
        if (st != null) {
            TankMultiblock.applyMultiblock(world, st);
        } else {
            // Not part of a valid structure — reset to standalone
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityFluidTankMultiblock) {
                ((TileEntityFluidTankMultiblock) te).invalidate();
            }
        }
    }

    // -----------------------------------------------------------------------
    // Right-click info
    // -----------------------------------------------------------------------

    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        // SOLID for the cube_all exterior + CUTOUT_MIPPED for viewport texture overlays
        return layer == BlockRenderLayer.SOLID || layer == BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityFluidTankMultiblock)) return false;
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
        return true;
    }

    // -----------------------------------------------------------------------
    // Tooltip
    // -----------------------------------------------------------------------

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(net.minecraft.item.ItemStack stack, World world,
                               List<String> tooltip, net.minecraft.client.util.ITooltipFlag flag) {
        rusticpipes.handlers.TooltipHandler.addFluidTankTooltip(stack, tooltip);
    }
}
