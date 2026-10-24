package rusticpipes.block;

import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.property.ExtendedBlockState;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import rusticpipes.multiblock.TankMultiblock;
import rusticpipes.tileentity.TileEntityFluidTankMultiblock;

import java.util.List;

public class BlockFluidTankMultiblock extends Block implements ITileEntityProvider {

    /**
     * Which face of this block shows the viewport.
     * Only 5 values — fits easily in 3 meta bits (max meta = 15).
     */
    public enum ViewportFace implements IStringSerializable {
        NONE, SINGLE, NORTH, SOUTH, EAST, WEST;
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

    /**
     * Unlisted property: whether a solid neighbour currently blocks the viewport face,
     * so the model should show a solid texture there instead of the cutout window.
     * Kept separate from VIEWPORT_ROW so blocking the window doesn't also suppress
     * row-gated geometry (e.g. the TOP row's ceiling quad).
     */
    public static final IUnlistedProperty<Boolean> VIEWPORT_BLOCKED =
            new IUnlistedProperty<Boolean>() {
                @Override public String getName()                { return "viewport_blocked"; }
                @Override public boolean isValid(Boolean v)       { return true; }
                @Override public Class<Boolean> getType()         { return Boolean.class; }
                @Override public String valueToString(Boolean v)  { return v.toString(); }
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
                new IUnlistedProperty[]{ VIEWPORT_ROW, SIDE_FACE, VIEWPORT_BLOCKED });
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

        ViewportFace viewport = state.getValue(VIEWPORT);

        ViewportRow row = ViewportRow.NONE;
        EnumFacing sideFace = null;
        TileEntity te = world.getTileEntity(pos);
        if (te instanceof TileEntityFluidTankMultiblock) {
            row = ((TileEntityFluidTankMultiblock) te).getViewportRow();
            sideFace = ((TileEntityFluidTankMultiblock) te).getSideFace();
        }

        // For SINGLE blocks: if any horizontal neighbor is also a tank, this structure is
        // invalid — show solid immediately without waiting for a server sync packet.
        // Row is kept as-is (not wiped) so row-gated geometry like the ceiling still renders.
        if (viewport == ViewportFace.SINGLE) {
            for (EnumFacing face : new EnumFacing[]{EnumFacing.NORTH, EnumFacing.SOUTH,
                                                    EnumFacing.EAST,  EnumFacing.WEST}) {
                if (world.getBlockState(pos.offset(face)).getBlock() instanceof BlockFluidTankMultiblock) {
                    return ext.withProperty(VIEWPORT_ROW, row).withProperty(SIDE_FACE, null)
                              .withProperty(VIEWPORT_BLOCKED, true);
                }
            }
        }

        // If the block directly in front of the viewport face is opaque, use the solid
        // texture on that face instead of the transparent viewport texture.
        // Without this the CUTOUT quad bleeds through the adjacent block (x-ray effect).
        // Row is kept as-is (not wiped) so row-gated geometry like the ceiling still renders.
        if (viewport != ViewportFace.NONE) {
            EnumFacing vpDir = viewportToFacing(viewport);
            if (vpDir != null && world.isSideSolid(pos.offset(vpDir), vpDir.getOpposite(), false)) {
                return ext.withProperty(VIEWPORT_ROW, row).withProperty(SIDE_FACE, sideFace)
                          .withProperty(VIEWPORT_BLOCKED, true);
            }
        }

        return ext.withProperty(VIEWPORT_ROW, row).withProperty(SIDE_FACE, sideFace)
                  .withProperty(VIEWPORT_BLOCKED, false);
    }

    /** Maps the listed ViewportFace property to the corresponding EnumFacing. */
    private static EnumFacing viewportToFacing(ViewportFace vp) {
        switch (vp) {
            case NORTH: return EnumFacing.NORTH;
            case SOUTH: return EnumFacing.SOUTH;
            case EAST:  return EnumFacing.EAST;
            case WEST:  return EnumFacing.WEST;
            default:    return null;
        }
    }

    @Override public boolean hasTileEntity(IBlockState state) { return true; }

    @Override
    public TileEntity createNewTileEntity(World world, int meta) {
        return new TileEntityFluidTankMultiblock();
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            world.scheduleUpdate(pos, this, 1); // validate on next tick
        }
    }

    @Override
    public void updateTick(World world, BlockPos pos, IBlockState state, java.util.Random rand) {
        if (!world.isRemote) {
            tryValidate(world, pos);
        }
    }

    /**
     * The player currently placing a tank block. getStateForPlacement runs just before
     * the block is set, and neighborChanged (where tryPlaceLayer runs) fires inside that
     * setBlockState — before onBlockPlacedBy — so this is the only way to know who
     * placed the block that triggered a layer fill. Weak so a failed placement can't leak.
     */
    private static java.lang.ref.WeakReference<EntityPlayer> pendingPlacer = null;
    @javax.annotation.Nullable private static BlockPos pendingPlacerPos = null;
    private static EnumHand pendingPlacerHand = EnumHand.MAIN_HAND;

    /** True while tryPlaceLayer is setting blocks — suppresses re-entrant layer fills. */
    private static boolean placingLayer = false;

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing facing,
                                            float hitX, float hitY, float hitZ, int meta,
                                            net.minecraft.entity.EntityLivingBase placer, EnumHand hand) {
        if (!world.isRemote && placer instanceof EntityPlayer) {
            pendingPlacer = new java.lang.ref.WeakReference<>((EntityPlayer) placer);
            pendingPlacerPos = pos;
            pendingPlacerHand = hand;
        }
        return super.getStateForPlacement(world, pos, facing, hitX, hitY, hitZ, meta, placer, hand);
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state,
                                net.minecraft.entity.EntityLivingBase placer, net.minecraft.item.ItemStack stack) {
        // Multiblock forming is server-only (see neighborChanged); the server syncs the
        // VIEWPORT state and TE data to clients.
        if (!world.isRemote) {
            pendingPlacer = null;
            pendingPlacerPos = null;
        }
    }

    /**
     * When a player places a tank block on top of an existing valid multiblock,
     * automatically fill the rest of the layer and consume blocks from that player's inventory.
     * @return true if a layer was placed (suppresses normal single-block validation)
     */
    private boolean tryPlaceLayer(World world, BlockPos pos) {
        // Only the player who placed this exact block pays for (and triggers) a layer fill.
        // Non-player placement (deployers, other mods' setBlockState) never auto-fills.
        EntityPlayer player = pendingPlacer != null ? pendingPlacer.get() : null;
        if (player == null || player.world != world || !pos.equals(pendingPlacerPos)) return false;

        BlockPos below = pos.down();
        TileEntity tBelow = world.getTileEntity(below);
        if (!(tBelow instanceof TileEntityFluidTankMultiblock)) return false;

        TileEntityFluidTankMultiblock memberBelow = (TileEntityFluidTankMultiblock) tBelow;
        if (!memberBelow.isPartOfMultiblock()) return false;

        BlockPos ctrlPos = memberBelow.getControllerPos();
        if (ctrlPos == null) return false;

        TileEntity ctrlTe = world.getTileEntity(ctrlPos);
        if (!(ctrlTe instanceof TileEntityFluidTankMultiblock)) return false;
        TileEntityFluidTankMultiblock ctrl = (TileEntityFluidTankMultiblock) ctrlTe;

        int baseSize = ctrl.getBaseSize();
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
        if (currentHeight >= 10) return false;
        if (pos.getY() != topY + 1) return false;

        // The placed block must sit inside the footprint, otherwise it's just an adjacent block
        if (pos.getX() < minX || pos.getX() > maxX || pos.getZ() < minZ || pos.getZ() > maxZ) return false;

        // Collect the positions that still need a block. Every one must be placeable —
        // a partial layer can never validate, so abort rather than charge for a broken tank.
        List<BlockPos> toPlace = new java.util.ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos layerPos = new BlockPos(x, pos.getY(), z);
                if (layerPos.equals(pos)) continue;
                if (world.getBlockState(layerPos).getBlock() instanceof BlockFluidTankMultiblock) continue;
                if (!world.mayPlace(this, layerPos, false, EnumFacing.UP, player)) return false;
                if (!world.isBlockModifiable(player, layerPos)) return false;
                toPlace.add(layerPos);
            }
        }
        if (toPlace.isEmpty()) return false;

        // The block being placed is still in the held stack here (ItemBlock shrinks it after
        // placement), so the player needs one more than the layer remainder.
        boolean creative = player.capabilities.isCreativeMode;
        if (!creative && countTankBlocksInInventory(player) < toPlace.size() + 1) return false;

        placingLayer = true;
        try {
            for (BlockPos layerPos : toPlace) {
                world.setBlockState(layerPos, getDefaultState(), 3);
            }
        } finally {
            placingLayer = false;
        }

        if (!creative) {
            removeTankBlocksFromInventory(player, toPlace.size());
        }

        // Neighbour updates were suppressed while the layer went down — validate once now.
        tryValidate(world, pos);
        return true;
    }

    private int countTankBlocksInInventory(EntityPlayer player) {
        int count = 0;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            net.minecraft.item.ItemStack stack = player.inventory.getStackInSlot(i);
            if (isTankBlockStack(stack)) count += stack.getCount();
        }
        return count;
    }

    /**
     * Takes {@code count} tank blocks, emptying other stacks before the held one, and
     * always leaving one in the held stack — ItemBlock shrinks that stack by one after
     * placement, and if it were already empty the placed block would be free.
     */
    private void removeTankBlocksFromInventory(EntityPlayer player, int count) {
        net.minecraft.item.ItemStack held = player.getHeldItem(pendingPlacerHand);
        int remaining = count;
        for (int i = 0; i < player.inventory.getSizeInventory() && remaining > 0; i++) {
            net.minecraft.item.ItemStack stack = player.inventory.getStackInSlot(i);
            if (stack == held || !isTankBlockStack(stack)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
            if (stack.isEmpty()) player.inventory.setInventorySlotContents(i, net.minecraft.item.ItemStack.EMPTY);
        }
        if (remaining > 0 && isTankBlockStack(held)) {
            held.shrink(Math.min(remaining, held.getCount() - 1));
        }
        player.inventoryContainer.detectAndSendChanges();
    }

    private static boolean isTankBlockStack(net.minecraft.item.ItemStack stack) {
        return !stack.isEmpty()
                && stack.getItem() instanceof net.minecraft.item.ItemBlock
                && ((net.minecraft.item.ItemBlock) stack.getItem()).getBlock() instanceof BlockFluidTankMultiblock;
    }

    @Override
    public void neighborChanged(IBlockState state, World world, BlockPos pos,
                                Block blockIn, BlockPos fromPos) {
        // Server-only. Clients used to validate here too for an instant visual update, but
        // block changes reach the client one chunk section / packet at a time, so the client
        // could validate a half-updated tank and wipe TE data the server had already synced —
        // leaving stale textures (or a tank that looked unformed) until another neighbour update.
        if (world.isRemote) return;
        // tryPlaceLayer validates the whole structure once its layer is down
        if (placingLayer) return;
        // Note: blockIn is the OLD block before the change, so we check the current block at fromPos
        boolean tankPlaced = world.getBlockState(fromPos).getBlock() instanceof BlockFluidTankMultiblock;
        if (tankPlaced) {
            rusticpipes.multiblock.TankMultiblock.Structure st =
                    rusticpipes.multiblock.TankMultiblock.validateMultiblock(world, pos);
            if (st != null) {
                rusticpipes.multiblock.TankMultiblock.applyMultiblock(world, st);
            } else if (tryPlaceLayer(world, fromPos)) {
                // Layer placement (places blocks and consumes inventory)
            } else {
                tryValidate(world, pos);
            }
        } else {
            tryValidate(world, pos);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileEntityFluidTankMultiblock) {
                TileEntityFluidTankMultiblock teTank = (TileEntityFluidTankMultiblock) te;

                // Save fluid from the controller BEFORE anything invalidates it,
                // and immediately zero it out so it can't be duplicated if the
                // controller block survives the break and later joins a reformed tank.
                net.minecraftforge.fluids.FluidStack savedFluid = null;
                BlockPos ctrlPos = teTank.getControllerPos();
                if (ctrlPos != null) {
                    TileEntity ctrlTe = world.getTileEntity(ctrlPos);
                    if (ctrlTe instanceof TileEntityFluidTankMultiblock) {
                        TileEntityFluidTankMultiblock ctrl = (TileEntityFluidTankMultiblock) ctrlTe;
                        net.minecraftforge.fluids.FluidStack raw = ctrl.getRawFluid();
                        if (raw != null && raw.amount > 0) {
                            savedFluid = raw.copy();
                            ctrl.setRawFluid(null);
                            ctrlTe.markDirty();
                        }
                    }
                }

                teTank.onBreak();

                // After block removal and revalidation, push saved fluid into new controller
                super.breakBlock(world, pos, state);

                net.minecraftforge.fluids.FluidStack overflow = savedFluid; // fluid not restored
                for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.VALUES) {
                    BlockPos neighborPos = pos.offset(face);
                    if (world.getBlockState(neighborPos).getBlock() instanceof BlockFluidTankMultiblock) {
                        rusticpipes.multiblock.TankMultiblock.Structure st =
                                rusticpipes.multiblock.TankMultiblock.validateMultiblock(world, neighborPos);
                        if (st != null) {
                            rusticpipes.multiblock.TankMultiblock.applyMultiblock(world, st);
                            // Restore fluid to the new controller, trimmed to new capacity
                            if (savedFluid != null) {
                                TileEntity newCtrl = world.getTileEntity(st.controller);
                                if (newCtrl instanceof TileEntityFluidTankMultiblock) {
                                    int newCap = st.blockCount *
                                            rusticpipes.handlers.ForgeConfigHandler.fluid.capacityPerTankBlock;
                                    net.minecraftforge.fluids.FluidStack toRestore = savedFluid.copy();
                                    if (toRestore.amount > newCap) toRestore.amount = newCap;
                                    ((TileEntityFluidTankMultiblock) newCtrl).setRawFluid(toRestore);
                                    newCtrl.markDirty();
                                    // Only the excess that didn't fit is truly lost
                                    if (savedFluid.amount > newCap) {
                                        overflow = savedFluid.copy();
                                        overflow.amount = savedFluid.amount - newCap;
                                    } else {
                                        overflow = null;
                                    }
                                }
                            }
                            break;
                        }
                    }
                }

                // Spill overflow fluid — scatter source blocks across random positions
                // on the outer surface of the old multiblock for a realistic burst effect.
                if (rusticpipes.handlers.ForgeConfigHandler.fluid.dropBucketsOnBreak
                        && overflow != null) {
                    rusticpipes.block.BlockFluidPipe.spillFluid(world, pos, overflow);
                }
                return;
            }
        }
        super.breakBlock(world, pos, state);
    }

    private void tryValidate(World world, BlockPos pos) {
        TankMultiblock.Structure st = TankMultiblock.validateMultiblock(world, pos);
        if (st != null) {
            TankMultiblock.applyMultiblock(world, st);
        } else {
            TankMultiblock.invalidateMultiblock(world, pos);
        }
    }

    // Only viewport blocks are non-opaque — they need CUTOUT_MIPPED rendering for the
    // glass face. Solid non-viewport blocks must stay opaque so adjacent tank blocks
    // correctly cull each other's shared faces and don't render internal seams.
    @Override public boolean isOpaqueCube(IBlockState state) {
        return state.getValue(VIEWPORT) == ViewportFace.NONE;
    }
    @Override public boolean isFullCube(IBlockState state) {
        return state.getValue(VIEWPORT) == ViewportFace.NONE;
    }

    /**
     * Suppress any solid tank-block face that points toward a viewport block.
     *
     * Minecraft's chunk renderer calls shouldSideBeRendered (not isSideSolid) to
     * decide whether to emit a face quad. The default implementation renders the
     * face when the adjacent block has isOpaqueCube = false — which is true for
     * our viewport blocks. For 3×3 and 4×4 tanks this makes the solid blocks
     * flanking the narrow viewport (and the interior block directly behind it)
     * render their faces into the viewport area, creating visible solid-texture
     * patches. Returning false here suppresses those faces; the TESR handles all
     * interior visuals.
     */
    @Override
    public boolean shouldSideBeRendered(IBlockState state,
                                        net.minecraft.world.IBlockAccess world,
                                        BlockPos pos,
                                        net.minecraft.util.EnumFacing side) {
        net.minecraft.block.state.IBlockState adj =
                world.getBlockState(pos.offset(side));
        if (adj.getBlock() instanceof BlockFluidTankMultiblock
                && adj.getValue(VIEWPORT) != ViewportFace.NONE) {
            // Never render a solid face pointing into a viewport block —
            // the TESR provides all interior rendering.
            return false;
        }
        return super.shouldSideBeRendered(state, world, pos, side);
    }

    /**
     * Viewport blocks report all faces as solid EXCEPT the actual glass face.
     * This prevents the solid tank blocks that border a viewport block from
     * rendering their face toward it on the non-glass sides.
     */
    @Override
    public boolean isSideSolid(IBlockState state, net.minecraft.world.IBlockAccess world,
                                BlockPos pos, net.minecraft.util.EnumFacing side) {
        ViewportFace viewport = state.getValue(VIEWPORT);
        if (viewport == ViewportFace.NONE) return true;
        net.minecraft.util.EnumFacing vpFace = viewportToFacing(viewport);
        return vpFace == null || side != vpFace;
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

        // Bucket interaction — fill or empty the tank with a bucket.
        // Routed through the controller so clicking ANY member block works,
        // not just the ones whose own TE happens to hold valid multiblock data.
        net.minecraft.item.ItemStack held = player.getHeldItem(hand);
        if (!held.isEmpty()) {
            TileEntityFluidTankMultiblock ctrl = resolveController(world, pos);
            if (ctrl != null) {
                net.minecraftforge.fluids.capability.IFluidHandler handler =
                        ctrl.getCapability(net.minecraftforge.fluids.capability.CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, facing);
                if (handler != null) {
                    boolean success = net.minecraftforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, handler);
                    if (success) return true;
                }
            }
        }

        // Sneak right-click: show tank info
        if (!player.isSneaking()) return false;
        sendTankInfo(world, pos, player);
        return true;
    }

    /**
     * Finds the controller TE for the multiblock containing {@code pos}.
     * If the clicked member has lost its controller link, the structure is
     * revalidated (which also repairs every member) before giving up.
     */
    @javax.annotation.Nullable
    private TileEntityFluidTankMultiblock resolveController(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityFluidTankMultiblock)) return null;
        BlockPos ctrlPos = ((TileEntityFluidTankMultiblock) te).getControllerPos();
        if (ctrlPos == null) {
            TankMultiblock.Structure st = TankMultiblock.validateMultiblock(world, pos);
            if (st == null) return null;
            TankMultiblock.applyMultiblock(world, st);
            ctrlPos = st.controller;
        }
        TileEntity ctrlTe = world.getTileEntity(ctrlPos);
        if (!(ctrlTe instanceof TileEntityFluidTankMultiblock)) return null;
        TileEntityFluidTankMultiblock ctrl = (TileEntityFluidTankMultiblock) ctrlTe;
        return ctrl.isPartOfMultiblock() ? ctrl : null;
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
