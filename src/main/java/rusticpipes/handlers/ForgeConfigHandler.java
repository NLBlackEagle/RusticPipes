package rusticpipes.handlers;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import rusticpipes.RusticPipes;

@Config(modid = RusticPipes.MODID)
public class ForgeConfigHandler {

    @Config.Comment("Server-Side Options")
    @Config.Name("Server Options")
    public static final ServerConfig server = new ServerConfig();

    @Config.Comment("Client-Side Options")
    @Config.Name("Client Options")
    public static final ClientConfig client = new ClientConfig();

    @Config.Comment("Recipe Options")
    @Config.Name("Recipe Options")
    public static final RecipeConfig recipes = new RecipeConfig();

    public static class ServerConfig {

        @Config.Comment("Slow tier: ticks between network updates. Higher = slower transfer.")
        @Config.RangeInt(min = 40, max = 200)
        @Config.Name("Pipe Tick Rate - Slow")
        public int pipeTickRateSlow = 100;

        @Config.Comment("Normal tier: ticks between network updates.")
        @Config.RangeInt(min = 40, max = 200)
        @Config.Name("Pipe Tick Rate - Normal")
        public int pipeTickRateNormal = 60;

        @Config.Comment("Fast tier: ticks between network updates.")
        @Config.RangeInt(min = 40, max = 200)
        @Config.Name("Pipe Tick Rate - Fast")
        public int pipeTickRateFast = 40;

        @Config.Comment("Turbo tier: ticks between network updates.")
        @Config.RangeInt(min = 40, max = 200)
        @Config.Name("Pipe Tick Rate - Turbo")
        public int pipeTickRateTurbo = 40;

        @Config.Comment("Hyper tier: ticks between network updates.")
        @Config.RangeInt(min = 40, max = 200)
        @Config.Name("Pipe Tick Rate - Hyper")
        public int pipeTickRateHyper = 40;

        @Config.Comment("Ultra tier: ticks between network updates.")
        @Config.RangeInt(min = 40, max = 200)
        @Config.Name("Pipe Tick Rate - Ultra")
        public int pipeTickRateUltra = 40;

        @Config.Comment("Extra ticks added per pipe in the network. Longer networks are slower.")
        @Config.RangeInt(min = 0, max = 20)
        @Config.Name("Pipe Distance Penalty")
        public int pipeDistancePenalty = 0;

        @Config.Comment("Slow tier: items transferred per network update.")
        @Config.RangeInt(min = 1, max = 64)
        @Config.Name("Pipe Transfer Size - Slow")
        public int pipeTransferSizeSlow = 1;

        @Config.Comment("Normal tier: items transferred per network update.")
        @Config.RangeInt(min = 1, max = 64)
        @Config.Name("Pipe Transfer Size - Normal")
        public int pipeTransferSizeNormal = 1;

        @Config.Comment("Fast tier: items transferred per network update.")
        @Config.RangeInt(min = 1, max = 64)
        @Config.Name("Pipe Transfer Size - Fast")
        public int pipeTransferSizeFast = 2;

        @Config.Comment("Turbo tier: items transferred per network update.")
        @Config.RangeInt(min = 1, max = 64)
        @Config.Name("Pipe Transfer Size - Turbo")
        public int pipeTransferSizeTurbo = 4;

        @Config.Comment("Hyper tier: items transferred per network update.")
        @Config.RangeInt(min = 1, max = 64)
        @Config.Name("Pipe Transfer Size - Hyper")
        public int pipeTransferSizeHyper = 8;

        @Config.Comment("Ultra tier: items transferred per network update.")
        @Config.RangeInt(min = 1, max = 64)
        @Config.Name("Pipe Transfer Size - Ultra")
        public int pipeTransferSizeUltra = 16;
    }

    public static class ClientConfig {

        @Config.Comment("Show pipe network debug info when sneaking.")
        @Config.Name("Show Debug Overlay")
        public boolean showDebugOverlay = false;
    }

    public static class RecipeConfig {

        @Config.Comment("Enable the shaped crafting recipe for the white (base) pipe.\n"
                + "Pattern: iron ingots in a hollow 3x3 ring, empty center.\n"
                + "Disable if you want to add your own recipe via CraftTweaker or similar.")
        @Config.Name("Enable Base Pipe Recipe")
        public boolean enableBasePipeRecipe = true;

        @Config.Comment("Number of white pipes produced by the base recipe.")
        @Config.RangeInt(min = 1, max = 64)
        @Config.Name("Base Pipe Recipe Output Count")
        public int basePipeRecipeOutput = 4;

        @Config.Comment("Enable shapeless dye conversion recipes.\n"
                + "Combine any pipe with any Minecraft dye in the crafting grid to recolor it.\n"
                + "Uses ore dictionary dye names (dyeWhite, dyeRed, etc.) so modded dyes work too.")
        @Config.Name("Enable Dye Conversion Recipes")
        public boolean enableDyeRecipes = true;

        @Config.Comment("Enable shift+right-click on a placed pipe with a dye in hand to recolor it in-world.\n"
                + "Consumes one dye from the player's hand.")
        @Config.Name("Enable In-World Dye Conversion")
        public boolean enableInWorldDyeing = true;
    }

    @Config.Comment("Conduit Options")
    @Config.Name("Conduit Options")
    public static final ConduitConfig conduit = new ConduitConfig();

    public static class ConduitConfig {

        @Config.Comment("FE buffer per conduit block in the network.\n"
                + "Total network buffer = this value × number of conduit blocks connected.\n"
                + "e.g. 10 conduits at 1000 FE/block = 10,000 FE total buffer.\n"
                + "Tier is determined by how full the buffer is (see tier thresholds below).")
        @Config.RangeInt(min = 100, max = 1000000)
        @Config.Name("Network Buffer Per Conduit (FE)")
        public int networkBufferPerConduit = 1000;

        @Config.Comment("Maximum FE/tick to push or pull through any single conduit face.\n"
                + "This caps both how fast generators charge the buffer and how fast\n"
                + "machines drain it per face per tick.")
        @Config.RangeInt(min = 1, max = 100000)
        @Config.Name("Max FE/tick per face")
        public int maxFePerTickPerFace = 10000;

        @Config.Comment("Buffer fill % (0.0–1.0) required for NORMAL tier (10%).")
        @Config.Name("Tier threshold - Normal (fill ratio)")
        public float tierNormal = 0.10f;

        @Config.Comment("Buffer fill % required for FAST tier (30%).")
        @Config.Name("Tier threshold - Fast (fill ratio)")
        public float tierFast = 0.30f;

        @Config.Comment("Buffer fill % required for TURBO tier (50%).")
        @Config.Name("Tier threshold - Turbo (fill ratio)")
        public float tierTurbo = 0.50f;

        @Config.Comment("Buffer fill % required for HYPER tier (70%).")
        @Config.Name("Tier threshold - Hyper (fill ratio)")
        public float tierHyper = 0.70f;

        @Config.Comment("Buffer fill % required for ULTRA tier (90%).")
        @Config.Name("Tier threshold - Ultra (fill ratio)")
        public float tierUltra = 0.90f;

        @Config.Comment("Enable the conduit crafting recipe.")
        @Config.Name("Enable Conduit Recipe")
        public boolean enableConduitRecipe = true;

        @Config.Comment("If enabled, powered conduits exposed to the sky are struck by lightning during rain.")
        @Config.Name("Enable Rain Lightning Damage")
        public boolean enableRainDamage = true;
    }

    @Mod.EventBusSubscriber(modid = RusticPipes.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(RusticPipes.MODID)) {
                ConfigManager.sync(RusticPipes.MODID, Config.Type.INSTANCE);
            }
        }
    }
}
