package rusticpipes.handlers;


import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import rusticpipes.RusticPipes;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.tileentity.TileEntityItemPipe;


@Mod.EventBusSubscriber(modid = RusticPipes.MODID)
public class ModRegistry {

    public static final BlockItemPipe ITEM_PIPE = new BlockItemPipe();

    public static void init() {
        GameRegistry.registerTileEntity(TileEntityItemPipe.class, RusticPipes.MODID + ":item_pipe");
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        ITEM_PIPE.setRegistryName(RusticPipes.MODID, "item_pipe");
        ITEM_PIPE.setTranslationKey("item_pipe");
        event.getRegistry().register(ITEM_PIPE);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        ItemBlock ib = new ItemBlock(ITEM_PIPE);
        ib.setRegistryName(ITEM_PIPE.getRegistryName());
        event.getRegistry().register(ib);
    }

}