package rusticpipes.handlers;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import rusticpipes.RusticPipes;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.client.model.PipeModelLoader;

@Mod.EventBusSubscriber(modid = RusticPipes.MODID, value = Side.CLIENT)
public class ClientModRegistry {

    public static void preInit() {
        ModelLoaderRegistry.registerLoader(PipeModelLoader.INSTANCE);
    }

    @SubscribeEvent
    public static void modelRegisterEvent(ModelRegistryEvent event) {
        ModelLoader.setCustomStateMapper(ModRegistry.ITEM_PIPE, new StateMapperBase() {
            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                // Encode full state into variant string so getQuads receives correct booleans
                String variant = "facing="   + state.getValue(BlockItemPipe.FACING).getName()
                        + ",endpoint=" + state.getValue(BlockItemPipe.ENDPOINT)
                        + ",north="    + state.getValue(BlockItemPipe.NORTH)
                        + ",south="    + state.getValue(BlockItemPipe.SOUTH)
                        + ",east="     + state.getValue(BlockItemPipe.EAST)
                        + ",west="     + state.getValue(BlockItemPipe.WEST)
                        + ",up="       + state.getValue(BlockItemPipe.UP)
                        + ",down="     + state.getValue(BlockItemPipe.DOWN);
                return new ModelResourceLocation(RusticPipes.MODID + ":item_pipe", variant);
            }
        });

        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(ModRegistry.ITEM_PIPE),
                0,
                new ModelResourceLocation(RusticPipes.MODID + ":item_pipe", "inventory")
        );
    }
}
