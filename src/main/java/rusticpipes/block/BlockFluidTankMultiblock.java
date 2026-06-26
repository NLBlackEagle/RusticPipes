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

    /**
     * When a tank block is placed on top of an existing valid multiblock,
     * automatically fill the entire layer and consume blocks from the placer's inventory.
     * @return true if a layer was placed (suppresses normal single-block validation)
     */
    private boolean tryPlaceLayer(World world, BlockPos pos) {
        BlockPos below = pos.down();
        TileEntity tBelow = world.getTileEntity(below);
        System.out.println("DEBUG tryPlaceLayer pos=" + pos + " below TE=" + tBelow);
        if (!(tBelow instanceof TileEntityFluidTankMultiblock)) return false;

        TileEntityFluidTankMultiblock memberBelow = (TileEntityFluidTankMultiblock) tBelow;
        System.out.println("DEBUG isPartOfMultiblock=" + memberBelow.isPartOfMultiblock());
        if (!memberBelow.isPartOfMultiblock()) return false;

        BlockPos ctrlPos = memberBelow.getControllerPos();
        System.out.println("DEBUG ctrlPos=" + ctrlPos);
        if (ctrlPos == null) return false;

        TileEntity ctrlTe = world.getTileEntity(ctrlPos);
        if (!(ctrlTe instanceof TileEntityFluidTankMultiblock)) return false;
        TileEntityFluidTankMultiblock ctrl = (TileEntityFluidTankMultiblock) ctrlTe;

        int baseSize = ctrl.getBaseSize();
        System.out.println("DEBUG baseSize=" + baseSize);
        if (baseSize < 2) return false;

        int minX = ctrlPos.getX();
        int minZ = ctrlPos.getZ();
        int maxX = minX + baseSize - 1;
        int maxZ = minZ + baseSize - 1;

        int topY = ctrlPos.getY();
        while (topY + 1 < pos.getY() &&
               world.getBlockState(new BlockPos(minX, topY + 1, minZ)).getBlock() instanceof BlockFluidTankMultiblock) {
            topY++;
        }

        int currentHeight = topY - ctrlPos.getY() + 1;
        System.out.println("DEBUG topY=" + topY + " pos.getY()=" + pos.getY() + " currentHeight=" + currentHeight);
        if (currentHeight >= 10) return false;
        if (pos.getY() != topY + 1) return false;

        int blocksNeeded = baseSize * baseSize;

        EntityPlayer player = findNearestPlayer(world, pos);
        System.out.println("DEBUG player=" + player);
        if (player == null) return false;

        int inInventory = countTankBlocksInInventory(player);
        System.out.println("DEBUG inInventory=" + inInventory + " blocksNeeded=" + blocksNeeded);
        if (inInventory < blocksNeeded - 1) return false;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos layerPos = new BlockPos(x, pos.getY(), z);
                if (layerPos.equals(pos)) continue;
                if (world.isAirBlock(layerPos)) {
                    world.setBlockState(layerPos, getDefaultState(), 3);
                }
            }
        }

        if (!player.isCreative()) {
            removeTankBlocksFromInventory(player, blocksNeeded - 1);
        }

        System.out.println("DEBUG layer placed successfully!");
        return true;
    }

    private EntityPlayer findNearestPlayer(World world, BlockPos pos) {
        return world.getClosestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8.0, false);
    }

    private int countTankBlocksInInventory(EntityPlayer player) {
        int count = 0;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            net.minecraft.item.ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty()
                    && stack.getItem() instanceof net.minecraft.item.ItemBlock
                    && ((net.minecraft.item.ItemBlock) stack.getItem()).getBlock() instanceof BlockFluidTankMultiblock) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void removeTankBlocksFromInventory(EntityPlayer player, int count) {
        int remaining = count;
        for (int i = 0; i < player.inventory.getSizeInventory() && remaining > 0; i++) {
            net.minecraft.item.ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty()
                    && stack.getItem() instanceof net.minecraft.item.ItemBlock
                    && ((net.minecraft.item.ItemBlock) stack.getItem()).getBlock() instanceof BlockFluidTankMultiblock) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
                if (stack.isEmpty()) player.inventory.setInventorySlotContents(i, net.minecraft.item.ItemStack.EMPTY);
            }
        }
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block blockIn, BlockPos fromPos) {
        if (world.isRemote) return;
        // Note: blockIn is the OLD block before the change, so we check the current block at fromPos
        boolean tankPlaced = world.getBlockState(fromPos).getBlock() instanceof BlockFluidTankMultiblock;
        if (tankPlaced) {
            // A tank block was placed nearby — try layer placement first, then form larger structure
            rusticpipes.multiblock.TankMultiblock.Structure st =
                    rusticpipes.multiblock.TankMultiblock.validateMultiblock(world, pos);
            if (st != null) {
                rusticpipes.multiblock.TankMultiblock.applyMultiblock(world, st);
            } else if (tryPlaceLayer(world, fromPos)) {
                // Layer placed successfully — don't invalidate
            } else {
                // New block breaks the structure and not enough blocks for a layer — invalidate
                tryValidate(world, pos);
            }
        } else {
            // Non-tank block placed or tank block removed — full re-validation (can invalidate)
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
            // Invalidate the entire multiblock structure (resets VIEWPORT on all blocks)
            TankMultiblock.invalidateMultiblock(world, pos);
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
