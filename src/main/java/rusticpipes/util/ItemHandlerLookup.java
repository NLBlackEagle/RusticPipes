package rusticpipes.util;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Finds an item handler on a block. Prefers the Forge item capability, but falls back to
 * wrapping a plain {@link IInventory} for mods that never expose the capability
 * (e.g. Varied Commodities crates, barrels, pedestals and weapon racks).
 */
public final class ItemHandlerLookup {

    private ItemHandlerLookup() {}

    /** True if the tile entity at {@code pos} can hold items through {@code side}. Safe on the client. */
    public static boolean hasItemHandler(IBlockAccess world, BlockPos pos, @Nullable EnumFacing side) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return false;
        if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side)) return true;
        return te instanceof IInventory;
    }

    /**
     * Returns the block's item handler, trying every side before the null side, and only then
     * an {@link IInventory} wrapper.
     */
    @Nullable
    public static IItemHandler getAnySide(World world, BlockPos pos) {
        TileEntity te = world.getTileEntity(pos);
        if (te == null) return null;
        for (EnumFacing face : EnumFacing.VALUES) {
            if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face))
                return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, face);
        }
        if (te.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null))
            return te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);
        if (te instanceof ISidedInventory) return new SyncingSidedWrapper(te, (ISidedInventory) te, EnumFacing.UP);
        if (te instanceof IInventory) return new SyncingWrapper(te, (IInventory) te);
        return null;
    }

    /**
     * Raw IInventory tiles usually only sync to the client through their own GUI code, so a
     * pipe moving items would leave displays (pedestals, weapon racks) stale. Send a block
     * update after every real change.
     */
    private static void sync(TileEntity te) {
        World w = te.getWorld();
        if (w == null || w.isRemote) return;
        w.notifyBlockUpdate(te.getPos(), w.getBlockState(te.getPos()), w.getBlockState(te.getPos()), 3);
    }

    private static final class SyncingWrapper extends InvWrapper {
        private final TileEntity te;

        SyncingWrapper(TileEntity te, IInventory inv) {
            super(inv);
            this.te = te;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            ItemStack rest = super.insertItem(slot, stack, simulate);
            if (!simulate && rest.getCount() != stack.getCount()) sync(te);
            return rest;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack out = super.extractItem(slot, amount, simulate);
            if (!simulate && !out.isEmpty()) sync(te);
            return out;
        }
    }

    private static final class SyncingSidedWrapper extends SidedInvWrapper {
        private final TileEntity te;

        SyncingSidedWrapper(TileEntity te, ISidedInventory inv, EnumFacing side) {
            super(inv, side);
            this.te = te;
        }

        @Nonnull
        @Override
        public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
            ItemStack rest = super.insertItem(slot, stack, simulate);
            if (!simulate && rest.getCount() != stack.getCount()) sync(te);
            return rest;
        }

        @Nonnull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack out = super.extractItem(slot, amount, simulate);
            if (!simulate && !out.isEmpty()) sync(te);
            return out;
        }
    }
}
