package rusticpipes.handlers;


import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import rusticpipes.RusticPipes;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.block.PipeColor;
import rusticpipes.tileentity.TileEntityItemPipe;


@Mod.EventBusSubscriber(modid = RusticPipes.MODID)
public class ModRegistry {

    /** Creative tab for all RusticPipes items. Uses the white pipe as the tab icon. */
    public static final CreativeTabs CREATIVE_TAB = new CreativeTabs("rusticpipes") {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(PIPES[PipeColor.WHITE.ordinal()]);
        }
    };

    /** One block instance per color, indexed by {@link PipeColor#ordinal()}. */
    public static final BlockItemPipe[] PIPES = new BlockItemPipe[PipeColor.values().length];

    static {
        for (PipeColor color : PipeColor.values()) {
            PIPES[color.ordinal()] = new BlockItemPipe(color);
        }
    }

    /** Convenience accessor. */
    public static BlockItemPipe getPipe(PipeColor color) {
        return PIPES[color.ordinal()];
    }

    public static void init() {
        GameRegistry.registerTileEntity(TileEntityItemPipe.class, RusticPipes.MODID + ":item_pipe");
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        for (PipeColor color : PipeColor.values()) {
            BlockItemPipe pipe = PIPES[color.ordinal()];
            pipe.setRegistryName(RusticPipes.MODID, color.registryName);
            pipe.setTranslationKey(color.registryName);
            pipe.setCreativeTab(CREATIVE_TAB);
            event.getRegistry().register(pipe);
        }
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        for (PipeColor color : PipeColor.values()) {
            BlockItemPipe pipe = PIPES[color.ordinal()];
            ItemBlock ib = new ItemBlock(pipe);
            ib.setRegistryName(pipe.getRegistryName());
            event.getRegistry().register(ib);
        }
    }

}
