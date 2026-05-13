package rusticpipes.handlers;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import rusticpipes.RusticPipes;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.block.PipeColor;
import rusticpipes.client.color.PipeBlockColor;
import rusticpipes.client.color.PipeItemColor;
import rusticpipes.client.model.PipeModelLoader;

@Mod.EventBusSubscriber(modid = RusticPipes.MODID, value = Side.CLIENT)
public class ClientModRegistry {

    public static void preInit() {
        ModelLoaderRegistry.registerLoader(PipeModelLoader.INSTANCE);
    }

    @SubscribeEvent
    public static void modelRegisterEvent(ModelRegistryEvent event) {
        // All 16 pipe colors share the same single baked model — tinting via IBlockColor
        // handles the visual difference. Mapping every state permutation would produce
        // tens of thousands of bake calls and cause very long load times.
        final ModelResourceLocation MODEL     = new ModelResourceLocation(RusticPipes.MODID + ":item_pipe", "normal");
        final ModelResourceLocation INVENTORY = new ModelResourceLocation(RusticPipes.MODID + ":item_pipe", "inventory");

        for (PipeColor color : PipeColor.values()) {
            BlockItemPipe pipe = ModRegistry.getPipe(color);

            // Every block state variant maps to the same model location
            ModelLoader.setCustomStateMapper(pipe, new StateMapperBase() {
                @Override
                protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                    return MODEL;
                }
            });

            // Item model
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(pipe), 0, INVENTORY);
        }
    }

    /**
     * Register block color handlers AFTER models are baked but BEFORE the first render.
     * This is the correct Forge event for IBlockColor / IItemColor registration.
     */
    @SubscribeEvent
    public static void registerBlockColors(ColorHandlerEvent.Block event) {
        BlockItemPipe[] pipes = ModRegistry.PIPES;
        event.getBlockColors().registerBlockColorHandler(PipeBlockColor.INSTANCE, pipes);
    }

    @SubscribeEvent
    public static void registerItemColors(ColorHandlerEvent.Item event) {
        for (BlockItemPipe pipe : ModRegistry.PIPES) {
            event.getItemColors().registerItemColorHandler(
                    PipeItemColor.INSTANCE,
                    Item.getItemFromBlock(pipe));
        }
    }
}
