package rusticpipes.handlers;


import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.oredict.OreDictionary;
import net.minecraftforge.oredict.ShapedOreRecipe;
import net.minecraftforge.oredict.ShapelessOreRecipe;
import rusticpipes.RusticPipes;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.block.PipeColor;
import rusticpipes.block.BlockConduit;
import rusticpipes.block.BlockConduitBuffer;
import rusticpipes.network.PipeNetwork;
import rusticpipes.tileentity.TileEntityConduit;
import rusticpipes.tileentity.TileEntityConduitBuffer;
import rusticpipes.block.BlockFluidPipe;
import rusticpipes.block.BlockFluidTank;
import rusticpipes.block.BlockFluidTankMultiblock;
import rusticpipes.tileentity.TileEntityFluidPipe;
import rusticpipes.tileentity.TileEntityFluidTank;
import rusticpipes.tileentity.TileEntityFluidTankMultiblock;
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
    public static final BlockItemPipe[]  PIPES       = new BlockItemPipe[PipeColor.values().length];
    public static final BlockFluidPipe[] FLUID_PIPES = new BlockFluidPipe[PipeColor.values().length];
    public static final BlockFluidTank          FLUID_TANK           = new BlockFluidTank();
    public static final BlockFluidTankMultiblock FLUID_TANK_MULTIBLOCK = new BlockFluidTankMultiblock();

    static {
        for (PipeColor color : PipeColor.values()) {
            PIPES[color.ordinal()]       = new BlockItemPipe(color);
            FLUID_PIPES[color.ordinal()] = new BlockFluidPipe(color);
        }
    }

    /** Convenience accessor. */
    public static BlockItemPipe getPipe(PipeColor color) {
        return PIPES[color.ordinal()];
    }

    public static BlockFluidPipe getFluidPipe(PipeColor color) {
        return FLUID_PIPES[color.ordinal()];
    }

    public static final BlockConduit CONDUIT = new BlockConduit();

    public static final BlockConduitBuffer BUFFER_SLOW   = new BlockConduitBuffer(PipeNetwork.SpeedTier.SLOW);
    public static final BlockConduitBuffer BUFFER_NORMAL = new BlockConduitBuffer(PipeNetwork.SpeedTier.NORMAL);
    public static final BlockConduitBuffer BUFFER_FAST   = new BlockConduitBuffer(PipeNetwork.SpeedTier.FAST);
    public static final BlockConduitBuffer BUFFER_TURBO  = new BlockConduitBuffer(PipeNetwork.SpeedTier.TURBO);
    public static final BlockConduitBuffer BUFFER_HYPER  = new BlockConduitBuffer(PipeNetwork.SpeedTier.HYPER);
    public static final BlockConduitBuffer BUFFER_ULTRA  = new BlockConduitBuffer(PipeNetwork.SpeedTier.ULTRA);

    public static void init() {
        GameRegistry.registerTileEntity(TileEntityItemPipe.class,  RusticPipes.MODID + ":item_pipe");
        GameRegistry.registerTileEntity(TileEntityFluidPipe.class, RusticPipes.MODID + ":fluid_pipe");
        GameRegistry.registerTileEntity(TileEntityFluidTank.class, RusticPipes.MODID + ":fluid_tank");
        GameRegistry.registerTileEntity(TileEntityFluidTankMultiblock.class, RusticPipes.MODID + ":fluid_tank_multiblock");
        GameRegistry.registerTileEntity(TileEntityConduit.class,       RusticPipes.MODID + ":conduit");
        GameRegistry.registerTileEntity(TileEntityConduitBuffer.class, RusticPipes.MODID + ":conduit_buffer");
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
        // Fluid pipes
        for (PipeColor color : PipeColor.values()) {
            BlockFluidPipe fp = FLUID_PIPES[color.ordinal()];
            fp.setRegistryName(RusticPipes.MODID, "fluid_" + color.registryName);
            fp.setTranslationKey("fluid_" + color.registryName);
            fp.setCreativeTab(CREATIVE_TAB);
            event.getRegistry().register(fp);
        }

        // Fluid tank
        FLUID_TANK.setRegistryName(RusticPipes.MODID, "fluid_tank");
        FLUID_TANK.setTranslationKey("fluid_tank");
        FLUID_TANK.setCreativeTab(CREATIVE_TAB);
        event.getRegistry().register(FLUID_TANK);

        FLUID_TANK_MULTIBLOCK.setRegistryName(RusticPipes.MODID, "fluid_tank_multiblock");
        FLUID_TANK_MULTIBLOCK.setTranslationKey("fluid_tank_multiblock");
        FLUID_TANK_MULTIBLOCK.setCreativeTab(CREATIVE_TAB);
        event.getRegistry().register(FLUID_TANK_MULTIBLOCK);

        CONDUIT.setRegistryName(RusticPipes.MODID, "conduit");
        CONDUIT.setTranslationKey("conduit");
        CONDUIT.setCreativeTab(CREATIVE_TAB);
        event.getRegistry().register(CONDUIT);

        BlockConduitBuffer[] buffers = {BUFFER_SLOW, BUFFER_NORMAL, BUFFER_FAST, BUFFER_TURBO, BUFFER_HYPER, BUFFER_ULTRA};
        String[] bufNames = {"conduit_buffer_slow","conduit_buffer_normal","conduit_buffer_fast","conduit_buffer_turbo","conduit_buffer_hyper","conduit_buffer_ultra"};
        for (int i = 0; i < buffers.length; i++) {
            buffers[i].setRegistryName(RusticPipes.MODID, bufNames[i]);
            buffers[i].setTranslationKey(bufNames[i]);
            buffers[i].setCreativeTab(CREATIVE_TAB);
            event.getRegistry().register(buffers[i]);
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
        // Fluid pipe items
        for (PipeColor color : PipeColor.values()) {
            BlockFluidPipe fp = FLUID_PIPES[color.ordinal()];
            ItemBlock ib = new ItemBlock(fp);
            ib.setRegistryName(fp.getRegistryName());
            event.getRegistry().register(ib);
        }

        // Fluid tank item
        ItemBlock tankItem = new ItemBlock(FLUID_TANK);
        tankItem.setRegistryName(FLUID_TANK.getRegistryName());
        net.minecraft.item.ItemBlock tankMultiblockItem = new net.minecraft.item.ItemBlock(FLUID_TANK_MULTIBLOCK);
        tankMultiblockItem.setRegistryName(FLUID_TANK_MULTIBLOCK.getRegistryName());
        event.getRegistry().register(tankItem);
            event.getRegistry().register(tankMultiblockItem);

        net.minecraft.item.ItemBlock conduitItem = new net.minecraft.item.ItemBlock(CONDUIT);
        conduitItem.setRegistryName(CONDUIT.getRegistryName());
        event.getRegistry().register(conduitItem);
   
        BlockConduitBuffer[] buffers = {BUFFER_SLOW, BUFFER_NORMAL, BUFFER_FAST, BUFFER_TURBO, BUFFER_HYPER, BUFFER_ULTRA};
        for (BlockConduitBuffer buf : buffers) {
            net.minecraft.item.ItemBlock ib = new net.minecraft.item.ItemBlock(buf);
            ib.setRegistryName(buf.getRegistryName());
            event.getRegistry().register(ib);
        }
    }

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        // ── Base pipe recipe ──────────────────────────────────────────────────
        if (ForgeConfigHandler.recipes.enableBasePipeRecipe) {
            net.minecraft.item.crafting.IRecipe basePipeRecipe =
                    rusticpipes.util.RecipeParser.parse(
                            ForgeConfigHandler.recipes.basePipeRecipe,
                            new net.minecraft.util.ResourceLocation(RusticPipes.MODID, "white_pipe"),
                            new ItemStack(getPipe(PipeColor.WHITE), 1));
            if (basePipeRecipe != null) event.getRegistry().register(basePipeRecipe);
        }

        // ── Dye conversion recipes ────────────────────────────────────────────
        if (ForgeConfigHandler.recipes.enableDyeRecipes) {
            String[] oreNames = {
                    "dyeWhite", "dyeOrange", "dyeMagenta", "dyeLightBlue",
                    "dyeYellow", "dyeLime", "dyePink", "dyeGray",
                    "dyeLightGray", "dyeCyan", "dyePurple", "dyeBlue",
                    "dyeBrown", "dyeGreen", "dyeRed", "dyeBlack"
            };

            PipeColor[] colors = PipeColor.values();
            for (int i = 0; i < colors.length; i++) {
                PipeColor targetColor = colors[i];
                String dyeOreName = oreNames[i];
                ItemStack output = new ItemStack(getPipe(targetColor), 1);

                for (PipeColor sourceColor : colors) {
                    if (sourceColor == targetColor) continue;
                    ShapelessOreRecipe dyeRecipe = new ShapelessOreRecipe(
                            null,
                            output,
                            new ItemStack(getPipe(sourceColor)),
                            dyeOreName
                    );
                    dyeRecipe.setRegistryName(RusticPipes.MODID,
                            sourceColor.registryName + "_to_" + targetColor.registryName);
                    event.getRegistry().register(dyeRecipe);
                }
            }
        }

        // ── Conduit recipe ────────────────────────────────────────────────────
        if (ForgeConfigHandler.conduit.enableConduitRecipe) {
            net.minecraft.item.crafting.IRecipe conduitRecipe =
                    rusticpipes.util.RecipeParser.parse(
                            ForgeConfigHandler.conduit.conduitRecipe,
                            new net.minecraft.util.ResourceLocation(RusticPipes.MODID, "conduit"),
                            new ItemStack(CONDUIT, 1));
            if (conduitRecipe != null) event.getRegistry().register(conduitRecipe);
        }

        // ── Motor recipes ─────────────────────────────────────────────────────
        BlockConduitBuffer[] buffers   = { BUFFER_SLOW, BUFFER_NORMAL, BUFFER_FAST, BUFFER_TURBO, BUFFER_HYPER, BUFFER_ULTRA };
        String[]             recipeIds = { "motor_slow", "motor_normal", "motor_fast", "motor_turbo", "motor_hyper", "motor_ultra" };
        boolean[]            enabled   = {
                ForgeConfigHandler.motors.enableMotorSlowRecipe,
                ForgeConfigHandler.motors.enableMotorNormalRecipe,
                ForgeConfigHandler.motors.enableMotorFastRecipe,
                ForgeConfigHandler.motors.enableMotorTurboRecipe,
                ForgeConfigHandler.motors.enableMotorHyperRecipe,
                ForgeConfigHandler.motors.enableMotorUltraRecipe
        };
        String[] recipeStrings = {
                ForgeConfigHandler.motors.motorSlowRecipe,
                ForgeConfigHandler.motors.motorNormalRecipe,
                ForgeConfigHandler.motors.motorFastRecipe,
                ForgeConfigHandler.motors.motorTurboRecipe,
                ForgeConfigHandler.motors.motorHyperRecipe,
                ForgeConfigHandler.motors.motorUltraRecipe
        };

        for (int i = 0; i < buffers.length; i++) {
            if (!enabled[i]) continue;
            net.minecraft.item.crafting.IRecipe motorRecipe =
                    rusticpipes.util.RecipeParser.parse(
                            recipeStrings[i],
                            new net.minecraft.util.ResourceLocation(RusticPipes.MODID, recipeIds[i]),
                            new ItemStack(buffers[i], 1));
            if (motorRecipe != null) event.getRegistry().register(motorRecipe);
        }
    }
}