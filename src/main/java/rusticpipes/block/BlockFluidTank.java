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
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import rusticpipes.multiblock.TankMultiblock;
import rusticpipes.tileentity.TileEntityFluidTank;

import javax.annotation.Nullable;
import java.util.List;

public class BlockFluidTank extends Block implements ITileEntityProvider {

    /**
     * Blockstate property encoding the visual role of this tank block.
     * Drives texture selection.
     */
    public enum TankRole implements IStringSerializable {
        SINGLE, BOTTOM, TOP, WALL, CORNER, EDGE;

        @Override public String getName() { return name().toLowerCase(); }

        public static TankRole from(TankMultiblock.Role role) {
            switch (role) {
                case SINGLE: return SINGLE;
                case BOTTOM: return BOTTOM;
                case TOP:    return TOP;
                case WALL:   return WALL;
                case CORNER: return CORNER;
                case EDGE:   return EDGE;
                default:     return SINGLE;
            }
        }
    }

    public static final PropertyEnum<TankRole> ROLE =
            PropertyEnum.create("role", TankRole.class);

    public BlockFluidTank() {
        super(Material.IRON);
        setDefaultState(blockState.getBaseState().withProperty(ROLE, TankRole.SINGLE));
        setHardness(2.5f);
        setResistance(10f);
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityFluidTank();
    }

    // -----------------------------------------------------------------------
    // BlockState
    // -----------------------------------------------------------------------

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, ROLE);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(ROLE).ordinal();
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        TankRole[] roles = TankRole.values();
        return getDefaultState().withProperty(ROLE, roles[Math.min(meta, roles.length - 1)]);
    }

    // -----------------------------------------------------------------------
    // Placement / removal — trigger multiblock validation
    // -----------------------------------------------------------------------

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (world.isRemote) return;
        TankMultiblock.Structure structure = TankMultiblock.validate(world, pos);
        if (structure != null) {
            TankMultiblock.apply(world, structure);
            // Update blockstate roles
            for (BlockPos p : structure.allPositions()) {
                TankMultiblock.Role role = structure.roleOf(p);
                IBlockState current = world.getBlockState(p);
                IBlockState desired = current.withProperty(ROLE, TankRole.from(role));
                if (!current.equals(desired)) {
                    world.setBlockState(p, desired, 3);
                }
            }
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TankMultiblock.invalidate(world, pos);
        }
        super.breakBlock(world, pos, state);
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block blockIn, BlockPos fromPos) {
        if (world.isRemote) return;
        // Re-validate when a neighbour changes
        TankMultiblock.Structure structure = TankMultiblock.validate(world, pos);
        if (structure != null) {
            TankMultiblock.apply(world, structure);
            for (BlockPos p : structure.allPositions()) {
                TankRole role = TankRole.from(structure.roleOf(p));
                IBlockState current = world.getBlockState(p);
                IBlockState desired = current.withProperty(ROLE, role);
                if (!current.equals(desired)) world.setBlockState(p, desired, 3);
            }
        } else {
            // No valid structure — reset this block to SINGLE
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityFluidTank) {
                ((TileEntityFluidTank) te).onMultiblockInvalidated();
            }
            world.setBlockState(pos, state.withProperty(ROLE, TankRole.SINGLE), 3);
        }
    }

    // -----------------------------------------------------------------------
    // Right-click — show fluid info
    // -----------------------------------------------------------------------

    @Override
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state,
                                    EntityPlayer player, EnumHand hand,
                                    EnumFacing facing, float hitX, float hitY, float hitZ) {
        if (world.isRemote) return true;
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityFluidTank)) return false;
        TileEntityFluidTank tank = (TileEntityFluidTank) te;

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
