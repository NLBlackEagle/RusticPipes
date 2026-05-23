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
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import rusticpipes.block.BlockFluidPipe;
import rusticpipes.block.BlockFluidTank;
import rusticpipes.client.FluidTankRenderer;
import rusticpipes.client.model.FluidPipeModelLoader;
import rusticpipes.tileentity.TileEntityFluidTank;
import rusticpipes.client.model.PipeModelLoader;

@Mod.EventBusSubscriber(modid = RusticPipes.MODID, value = Side.CLIENT)
public class ClientModRegistry {

    public static void preInit() {
        ModelLoaderRegistry.registerLoader(PipeModelLoader.INSTANCE);
        ModelLoaderRegistry.registerLoader(ConduitModelLoader.INSTANCE);
        ModelLoaderRegistry.registerLoader(FluidPipeModelLoader.INSTANCE);
        // Buffer blocks use vanilla full-cube models — no custom loader needed
    }

    public static void init() {}

    public static void postInit() {
        // TESR registration in postInit ensures all other mods have finished
        // their texture/init setup before we bind our renderer.
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                TileEntityFluidTank.class, new FluidTankRenderer());
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

        // Fluid pipe models — reuse the same item_pipe model loader
        final ModelResourceLocation FLUID_MODEL     = new ModelResourceLocation(RusticPipes.MODID + ":fluid_pipe", "normal");
        final ModelResourceLocation FLUID_INVENTORY = new ModelResourceLocation(RusticPipes.MODID + ":fluid_pipe", "inventory");

        for (PipeColor color : PipeColor.values()) {
            BlockFluidPipe fp = ModRegistry.getFluidPipe(color);
            ModelLoader.setCustomStateMapper(fp, new StateMapperBase() {
                @Override
                protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                    return FLUID_MODEL;
                }
            });
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(fp), 0, FLUID_INVENTORY);
        }

        // Fluid tank — vanilla blockstate model, role property drives texture
        for (rusticpipes.block.BlockFluidTank.TankRole role : rusticpipes.block.BlockFluidTank.TankRole.values()) {
            ModelLoader.setCustomModelResourceLocation(
                    Item.getItemFromBlock(ModRegistry.FLUID_TANK), 0,
                    new ModelResourceLocation(RusticPipes.MODID + ":fluid_tank", "inventory"));
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

        // Buffer blocks — full-cube models, on/off texture driven by POWERED blockstate
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
            final String name = bufNames[i];
            ModelLoader.setCustomStateMapper(buffers[i], new StateMapperBase() {
                @Override
                protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                    // Route to the on or off model based on the POWERED property
                    boolean powered = state.getValue(BlockConduitBuffer.POWERED);
                    return new ModelResourceLocation(
                            RusticPipes.MODID + ":" + name,
                            powered ? "powered=true" : "powered=false");
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
