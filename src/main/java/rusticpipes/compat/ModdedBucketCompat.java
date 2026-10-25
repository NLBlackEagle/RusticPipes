package rusticpipes.compat;

import net.minecraft.block.Block;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import rusticpipes.RusticPipes;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Fluid-handler support for other mods' buckets that subclass {@link ItemBucket}
 * (e.g. Lycanites Mobs' GenericBucketItem for ooze, acid, moglava...).
 *
 * Forge only gives ItemBucket its FluidBucketWrapper when {@code getClass() == ItemBucket.class},
 * so such subclasses expose no fluid capability at all and FluidUtil can't empty them into
 * any tank. Forge's empty-bucket wrapper likewise only fills with water, lava, milk and
 * universal-bucket fluids, so these fluids can't be taken back out with an empty bucket.
 *
 * Filled side: a capability is attached to those buckets, resolving their fluid from the
 * block they place. Empty side: {@link #getFilledBucket} finds the matching modded bucket,
 * used by the tank's right-click handling.
 */
@Mod.EventBusSubscriber(modid = RusticPipes.MODID)
public final class ModdedBucketCompat {

    private ModdedBucketCompat() {}

    private static final ResourceLocation CAP_KEY = new ResourceLocation(RusticPipes.MODID, "modded_bucket");

    @Nullable private static Field containedBlockField;
    private static boolean fieldLookupFailed = false;

    /** Fluid → filled bucket item, built lazily once all items are registered. */
    @Nullable private static Map<Fluid, Item> bucketsByFluid = null;

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<ItemStack> event) {
        ItemStack stack = event.getObject();
        Item item = stack.getItem();
        if (!(item instanceof ItemBucket) || item.getClass() == ItemBucket.class) return;
        // Leave buckets that already provide their own fluid handler alone
        ICapabilityProvider own = item.initCapabilities(stack, null);
        if (own != null && own.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY, null)) return;
        Fluid fluid = getBucketFluid((ItemBucket) item);
        if (fluid == null) return;
        event.addCapability(CAP_KEY, new FilledBucketHandler(stack, fluid));
    }

    /** The fluid a bucket item places, or null for empty / non-fluid buckets. */
    @Nullable
    public static Fluid getBucketFluid(ItemBucket bucket) {
        Block block = getContainedBlock(bucket);
        if (block == null || block == net.minecraft.init.Blocks.AIR) return null;
        if (block instanceof IFluidBlock) return ((IFluidBlock) block).getFluid();
        return FluidRegistry.lookupFluidForBlock(block);
    }

    /**
     * A filled modded bucket for {@code fluid}, or EMPTY if none exists. Only consulted for
     * fluids vanilla/universal buckets can't hold.
     */
    public static ItemStack getFilledBucket(@Nullable FluidStack fluid) {
        if (fluid == null || fluid.getFluid() == null) return ItemStack.EMPTY;
        if (bucketsByFluid == null) {
            Map<Fluid, Item> map = new HashMap<>();
            for (Item item : ForgeRegistries.ITEMS) {
                if (!(item instanceof ItemBucket) || item.getClass() == ItemBucket.class) continue;
                Fluid f = getBucketFluid((ItemBucket) item);
                if (f != null) map.putIfAbsent(f, item);
            }
            bucketsByFluid = map;
        }
        Item item = bucketsByFluid.get(fluid.getFluid());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Nullable
    private static Block getContainedBlock(ItemBucket bucket) {
        if (containedBlockField == null) {
            if (fieldLookupFailed) return null;
            try {
                containedBlockField = ReflectionHelper.findField(ItemBucket.class, "containedBlock", "field_77876_a");
            } catch (Exception e) {
                fieldLookupFailed = true;
                RusticPipes.LOGGER.warn("Could not access ItemBucket.containedBlock — modded bucket support disabled", e);
                return null;
            }
        }
        try {
            return (Block) containedBlockField.get(bucket);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /** Lets a filled modded bucket be emptied through the standard fluid handler API. */
    private static final class FilledBucketHandler implements IFluidHandlerItem, ICapabilityProvider {

        private ItemStack container;
        private final Fluid fluid;

        FilledBucketHandler(ItemStack container, Fluid fluid) {
            this.container = container;
            this.fluid = fluid;
        }

        private boolean isFilled() {
            return container.getItem() instanceof ItemBucket && container.getItem() != Items.BUCKET;
        }

        @Override public ItemStack getContainer() { return container; }

        @Override
        public IFluidTankProperties[] getTankProperties() {
            FluidStack contents = isFilled() ? new FluidStack(fluid, Fluid.BUCKET_VOLUME) : null;
            return new IFluidTankProperties[]{ new FluidTankProperties(contents, Fluid.BUCKET_VOLUME) };
        }

        @Override public int fill(FluidStack resource, boolean doFill) { return 0; }

        @Override @Nullable
        public FluidStack drain(FluidStack resource, boolean doDrain) {
            if (resource == null || resource.getFluid() != fluid) return null;
            return drain(resource.amount, doDrain);
        }

        @Override @Nullable
        public FluidStack drain(int maxDrain, boolean doDrain) {
            if (container.getCount() != 1 || maxDrain < Fluid.BUCKET_VOLUME || !isFilled()) return null;
            if (doDrain) container = new ItemStack(Items.BUCKET);
            return new FluidStack(fluid, Fluid.BUCKET_VOLUME);
        }

        @Override
        public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
            return capability == CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY;
        }

        @Override @Nullable
        public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
            return capability == CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY
                    ? CapabilityFluidHandler.FLUID_HANDLER_ITEM_CAPABILITY.cast(this) : null;
        }
    }
}
