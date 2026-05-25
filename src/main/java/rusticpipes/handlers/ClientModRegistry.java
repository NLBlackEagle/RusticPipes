package rusticpipes.handlers;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ColorHandlerEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
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
import rusticpipes.client.FluidTankMultiblockRenderer;
import rusticpipes.client.model.FluidPipeModelLoader;
import rusticpipes.client.model.FluidTankModelLoader;
import rusticpipes.tileentity.TileEntityFluidTank;
import rusticpipes.tileentity.TileEntityFluidTankMultiblock;
import rusticpipes.client.model.PipeModelLoader;

@Mod.EventBusSubscriber(modid = RusticPipes.MODID, value = Side.CLIENT)
public class ClientModRegistry {

    public static void preInit() {
        ModelLoaderRegistry.registerLoader(PipeModelLoader.INSTANCE);
        ModelLoaderRegistry.registerLoader(ConduitModelLoader.INSTANCE);
        ModelLoaderRegistry.registerLoader(FluidPipeModelLoader.INSTANCE);
        ModelLoaderRegistry.registerLoader(FluidTankModelLoader.INSTANCE);
        // Buffer blocks use vanilla full-cube models — no custom loader needed
    }

    public static void init() {}

    public static void postInit() {
        // TESR registration in postInit ensures all other mods have finished
        // their texture/init setup before we bind our renderer.
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                TileEntityFluidTank.class, new FluidTankRenderer());
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                TileEntityFluidTankMultiblock.class, new FluidTankMultiblockRenderer());
        net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
                rusticpipes.tileentity.TileEntityFluidPipe.class, new rusticpipes.client.FluidPipeRenderer());
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

        // Fluid tank multiblock — state mapper routes viewport enum to correct model
        ModelLoader.setCustomStateMapper(ModRegistry.FLUID_TANK_MULTIBLOCK, new StateMapperBase() {
            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                rusticpipes.block.BlockFluidTankMultiblock.ViewportFace face =
                        state.getValue(rusticpipes.block.BlockFluidTankMultiblock.VIEWPORT);
                return new ModelResourceLocation(RusticPipes.MODID + ":fluid_tank_multiblock",
                        "viewport=" + face.getName());
            }
        });
        // Pre-register all viewport variant models so Forge bakes them
        for (rusticpipes.block.BlockFluidTankMultiblock.ViewportFace face :
                rusticpipes.block.BlockFluidTankMultiblock.ViewportFace.values()) {
            if (face == rusticpipes.block.BlockFluidTankMultiblock.ViewportFace.NONE) continue;
            ModelLoader.registerItemVariants(net.minecraft.item.Item.getItemFromBlock(ModRegistry.FLUID_TANK_MULTIBLOCK),
                    new ModelResourceLocation(RusticPipes.MODID + ":fluid_tank_multiblock", "viewport=" + face.getName()));
        }
        // Item uses the plain solid model
        net.minecraftforge.client.model.ModelLoader.setCustomModelResourceLocation(
                net.minecraft.item.Item.getItemFromBlock(ModRegistry.FLUID_TANK_MULTIBLOCK), 0,
                new ModelResourceLocation(RusticPipes.MODID + ":fluid_tank_multiblock", "viewport=none"));

        // Register single tank item model
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(ModRegistry.FLUID_TANK), 0,
                new ModelResourceLocation(RusticPipes.MODID + ":fluid_tank", "inventory"));

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
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        // Register multiblock tank viewport sprites for atlas stitching
        event.getMap().registerSprite(new net.minecraft.util.ResourceLocation(
                "rusticpipes:blocks/fluid_tank/fluid_tank_viewport_bottom"));
        event.getMap().registerSprite(new net.minecraft.util.ResourceLocation(
                "rusticpipes:blocks/fluid_tank/fluid_tank_viewport_top"));
        event.getMap().registerSprite(new net.minecraft.util.ResourceLocation(
                "rusticpipes:blocks/fluid_tank/fluid_tank_viewport_center"));
        event.getMap().registerSprite(new net.minecraft.util.ResourceLocation(
                "rusticpipes:blocks/fluid_tank/fluid_tank_inner_viewport"));
        event.getMap().registerSprite(new net.minecraft.util.ResourceLocation(
                "rusticpipes:blocks/fluid_tank/fluid_tank_solid"));
    }

    @SubscribeEvent
    public static void registerItemColors(ColorHandlerEvent.Item event) {
        for (BlockItemPipe pipe : ModRegistry.PIPES) {
            event.getItemColors().registerItemColorHandler(
                    PipeItemColor.INSTANCE, Item.getItemFromBlock(pipe));
        }
    }
}
