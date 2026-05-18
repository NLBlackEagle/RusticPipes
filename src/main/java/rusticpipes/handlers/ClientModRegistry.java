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
import rusticpipes.block.BlockConduit;
import rusticpipes.block.BlockConduitBuffer;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.block.PipeColor;
import rusticpipes.client.color.PipeBlockColor;
import rusticpipes.client.color.PipeItemColor;
import rusticpipes.client.model.ConduitModelLoader;
import rusticpipes.client.model.PipeModelLoader;

@Mod.EventBusSubscriber(modid = RusticPipes.MODID, value = Side.CLIENT)
public class ClientModRegistry {

    public static void preInit() {
        ModelLoaderRegistry.registerLoader(PipeModelLoader.INSTANCE);
        ModelLoaderRegistry.registerLoader(ConduitModelLoader.INSTANCE);
        // Buffer blocks use vanilla full-cube models — no custom loader needed
    }

    @SubscribeEvent
    public static void modelRegisterEvent(ModelRegistryEvent event) {
        final ModelResourceLocation MODEL     = new ModelResourceLocation(RusticPipes.MODID + ":item_pipe", "normal");
        final ModelResourceLocation INVENTORY = new ModelResourceLocation(RusticPipes.MODID + ":item_pipe", "inventory");

        for (PipeColor color : PipeColor.values()) {
            BlockItemPipe pipe = ModRegistry.getPipe(color);
            ModelLoader.setCustomStateMapper(pipe, new StateMapperBase() {
                @Override
                protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                    return MODEL;
                }
            });
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(pipe), 0, INVENTORY);
        }

        // Conduit model
        final ModelResourceLocation CONDUIT_MODEL = new ModelResourceLocation(RusticPipes.MODID + ":conduit", "normal");
        ModelLoader.setCustomStateMapper(ModRegistry.CONDUIT, new StateMapperBase() {
            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return CONDUIT_MODEL;
            }
        });
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(ModRegistry.CONDUIT), 0, CONDUIT_MODEL);

        // Buffer blocks — simple full-cube models, one per tier
        BlockConduitBuffer[] buffers = {
            ModRegistry.BUFFER_SLOW, ModRegistry.BUFFER_NORMAL,
            ModRegistry.BUFFER_FAST, ModRegistry.BUFFER_TURBO,
            ModRegistry.BUFFER_HYPER, ModRegistry.BUFFER_ULTRA
        };
        String[] bufNames = {
            "conduit_buffer_slow", "conduit_buffer_normal",
            "conduit_buffer_fast", "conduit_buffer_turbo",
            "conduit_buffer_hyper", "conduit_buffer_ultra"
        };
        for (int i = 0; i < buffers.length; i++) {
            final ModelResourceLocation loc = new ModelResourceLocation(
                    RusticPipes.MODID + ":" + bufNames[i], "normal");
            ModelLoader.setCustomStateMapper(buffers[i], new StateMapperBase() {
                @Override
                protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                    return loc;
                }
            });
            ModelLoader.setCustomModelResourceLocation(
                    Item.getItemFromBlock(buffers[i]), 0,
                    new ModelResourceLocation(RusticPipes.MODID + ":" + bufNames[i], "inventory"));
        }
    }

    @SubscribeEvent
    public static void registerBlockColors(ColorHandlerEvent.Block event) {
        event.getBlockColors().registerBlockColorHandler(PipeBlockColor.INSTANCE, ModRegistry.PIPES);
    }

    @SubscribeEvent
    public static void registerItemColors(ColorHandlerEvent.Item event) {
        for (BlockItemPipe pipe : ModRegistry.PIPES) {
            event.getItemColors().registerItemColorHandler(
                    PipeItemColor.INSTANCE, Item.getItemFromBlock(pipe));
        }
    }
}
