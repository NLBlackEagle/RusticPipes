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

    @SubscribeEvent
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        // ── Base pipe recipe ──────────────────────────────────────────────────
        // Shaped: hollow 3x3 ring of iron ingots → N white pipes
        //   I I I
        //   I   I
        //   I I I
        if (ForgeConfigHandler.recipes.enableBasePipeRecipe) {
            ItemStack whitePipeOutput = new ItemStack(
                    getPipe(PipeColor.WHITE), ForgeConfigHandler.recipes.basePipeRecipeOutput);
            ShapedOreRecipe basePipeRecipe = new ShapedOreRecipe(
                    null,
                    whitePipeOutput,
                    "III",
                    "I I",
                    "III",
                    'I', "ingotIron"
            );
            basePipeRecipe.setRegistryName(RusticPipes.MODID, "white_pipe");
            event.getRegistry().register(basePipeRecipe);
        }

        // ── Dye conversion recipes ────────────────────────────────────────────
        // Shapeless: any pipe + matching ore-dict dye → dyed pipe (1 output)
        // Ore dictionary dye names match EnumDyeColor ordinals exactly.
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

                // Register a conversion recipe from every other pipe color to this color
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
    }
}
