package rusticpipes.handlers;

import net.minecraftforge.common.config.Config;
import net.minecraftforge.common.config.ConfigManager;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import rusticpipes.RusticPipes;
import rusticpipes.network.PipeNetwork;

@Config(modid = RusticPipes.MODID)
public class ForgeConfigHandler {

    // -----------------------------------------------------------------------
    // Parsed tier data — populated on first access or after config reload
    // -----------------------------------------------------------------------

    /** Parsed values from pipe tier strings. Index = SpeedTier.ordinal(). */
    private static int[] parsedTickRate    = null;
    private static int[] parsedTransfer    = null;
    private static int[] parsedFeCost      = null;

    /** Call after config load/reload to re-parse tier strings. */
    public static void parseTiers() {
        PipeNetwork.SpeedTier[] tiers = PipeNetwork.SpeedTier.values();
        parsedTickRate = new int[tiers.length];
        parsedTransfer = new int[tiers.length];
        parsedFeCost   = new int[tiers.length];

        String[] raw = {
                pipes.tierSlow, pipes.tierNormal, pipes.tierFast,
                pipes.tierTurbo, pipes.tierHyper, pipes.tierUltra
        };
        int[][] defaults = {
                {70, 1, 10}, {60, 1, 100}, {40, 2, 500},
                {40, 4, 1000}, {20, 8, 2500}, {20, 16, 5000}
        };

        for (int i = 0; i < tiers.length; i++) {
            try {
                String[] parts = raw[i].split(",");
                parsedTickRate[i] = Math.max(1,  Integer.parseInt(parts[0].trim()));
                parsedTransfer[i] = Math.max(1,  Integer.parseInt(parts[1].trim()));
                parsedFeCost[i]   = Math.max(0,  Integer.parseInt(parts[2].trim()));
            } catch (Exception e) {
                parsedTickRate[i] = defaults[i][0];
                parsedTransfer[i] = defaults[i][1];
                parsedFeCost[i]   = defaults[i][2];
                RusticPipes.LOGGER.warn("[RusticPipes] Failed to parse tier string for "
                        + tiers[i].name() + ", using defaults. Value was: " + raw[i]);
            }
        }
    }

    private static void ensureParsed() {
        if (parsedTickRate == null) parseTiers();
    }

    public static int getTickRate(PipeNetwork.SpeedTier tier) {
        ensureParsed();
        return parsedTickRate[tier.ordinal()];
    }

    public static int getTransferSize(PipeNetwork.SpeedTier tier) {
        ensureParsed();
        return parsedTransfer[tier.ordinal()];
    }

    public static int getFeCost(PipeNetwork.SpeedTier tier) {
        ensureParsed();
        return parsedFeCost[tier.ordinal()];
    }

    // -----------------------------------------------------------------------
    // Config sections
    // -----------------------------------------------------------------------

    @Config.Comment("Pipe Options")
    @Config.Name("Pipe Options")
    public static final PipeConfig pipes = new PipeConfig();

    @Config.Comment("Conduit Options")
    @Config.Name("Conduit Options")
    public static final ConduitConfig conduit = new ConduitConfig();

    @Config.Comment("Motor Options")
    @Config.Name("Motor Options")
    public static final MotorConfig motors = new MotorConfig();

    @Config.Comment("Recipe Options")
    @Config.Name("Recipe Options")
    public static final RecipeConfig recipes = new RecipeConfig();

    @Config.Comment("Client Options")
    @Config.Name("Client Options")
    public static final ClientConfig client = new ClientConfig();

    // -----------------------------------------------------------------------

    public static class PipeConfig {

        @Config.Comment("Pipe tier definitions.\n"
                + "Format: \"<tier>\" = <ticks between transfers>, <items per transfer>, <FE cost per transfer>\n"
                + "SLOW/Basic requires no motor of a higher tier. All other tiers require a motor of that tier or higher.")
        @Config.Name("Slow/Basic")
        public String tierSlow  = "70, 1, 10";

        @Config.Name("Normal/Refined")
        public String tierNormal = "20, 1, 100";

        @Config.Name("Fast/Efficient")
        public String tierFast   = "20, 2, 500";

        @Config.Name("Turbo/Advanced")
        public String tierTurbo  = "20, 4, 1000";

        @Config.Name("Hyper/Reinforced")
        public String tierHyper  = "20, 16, 2500";

        @Config.Name("Ultra/Overclocked")
        public String tierUltra  = "20, 64, 5000";

        @Config.Comment("Extra ticks added per pipe in the network. Longer networks are slightly slower.")
        @Config.RangeInt(min = 0, max = 20)
        @Config.Name("Distance Penalty (ticks per pipe)")
        public int distancePenalty = 0;
    }

    public static class ConduitConfig {

        @Config.Comment("Total FE buffer for the entire conduit network.")
        @Config.RangeInt(min = 100, max = 1000000)
        @Config.Name("Network Buffer Capacity (FE)")
        public int networkBufferCapacity = 5000;

        @Config.Comment("Maximum FE/tick pushed or pulled through any single conduit face.")
        @Config.RangeInt(min = 1, max = 100000)
        @Config.Name("Max FE/tick per face")
        public int maxFePerTickPerFace = 5000;

        @Config.Comment("Percentage of stored FE lost per tick, voided. 0.0 = disabled.")
        @Config.Name("Power Loss Per Tick (%)")
        public double powerLossPerConduitPerTick = 0.005;

        @Config.Comment("Conduits spark when powered.")
        @Config.Name("Conduits Spark When Powered")
        public boolean enableSparks = true;

        @Config.Comment("Multiplier for spark particle rarity (0.0-1.0).\n"
                + "1.0 = full rarity, 0.1 = 1/10th as many sparks.\n"
                + "At 0.1 the max chance at full power is ~2.5%.")
        @Config.Name("Spark Rarity Multiplier")
        public double sparkRarityMultiplier = 0.1;

        @Config.Comment("Enable the conduit crafting recipe.")
        @Config.Name("Enable Conduit Recipe")
        public boolean enableConduitRecipe = true;

        @Config.Comment("Shaped recipe for the conduit.\n"
                + "Format: {[slot1],...,[slot9]} * count\n"
                + "Slots: use [modid:itemname], [ore:oreName], or [minecraft:air] for empty.")
        @Config.Name("Conduit Recipe")
        public String conduitRecipe =
                "{[minecraft:wool],[minecraft:wool],[minecraft:wool],"
                + "[minecraft:iron_ingot],[minecraft:iron_ingot],[minecraft:iron_ingot],"
                + "[minecraft:wool],[minecraft:wool],[minecraft:wool]} * 4";

        @Config.Comment("Powered conduits exposed to the sky are struck by lightning during rain.")
        @Config.Name("Enable Rain Lightning Damage")
        public boolean enableRainDamage = true;

        @Config.Comment("IndustrialCraft 2 EU to FE conversion ratio.\n"
                + "1 EU = this many FE. Only used when IC2 is installed.\n"
                + "Community standard is 4 FE per EU.")
        @Config.RangeInt(min = 1, max = 1000)
        @Config.Name("IC2 EU to FE Ratio")
        public int ic2FeRatio = 4;
    }

    public static class MotorConfig {

        @Config.Comment("FE buffer capacity for each motor tier.\n"
                + "This is the local buffer the motor fills from the conduit and pipes drain from.\n"
                + "Larger = more forgiving when the conduit supply dips briefly.")
        @Config.RangeInt(min = 0, max = 1000000)
        @Config.Name("Slow Motor Buffer (FE)")
        public int bufferSlow   = 100;

        @Config.RangeInt(min = 1, max = 1000000)
        @Config.Name("Normal Motor Buffer (FE)")
        public int bufferNormal = 500;

        @Config.RangeInt(min = 1, max = 1000000)
        @Config.Name("Fast Motor Buffer (FE)")
        public int bufferFast   = 1500;

        @Config.RangeInt(min = 1, max = 1000000)
        @Config.Name("Turbo Motor Buffer (FE)")
        public int bufferTurbo  = 4000;

        @Config.RangeInt(min = 1, max = 1000000)
        @Config.Name("Hyper Motor Buffer (FE)")
        public int bufferHyper  = 10000;

        @Config.RangeInt(min = 1, max = 1000000)
        @Config.Name("Ultra Motor Buffer (FE)")
        public int bufferUltra  = 25000;

        @Config.Comment("Percentage of remaining buffer drained per tick when no conduit is connected.\n"
                + "e.g. 0.05 = 5% per tick (drains fully in ~60 ticks / 3 seconds).\n"
                + "Uses ceil so at least 1 FE is always drained per tick.")
        @Config.Name("Buffer Drain Rate (% per tick, no conduit)")
        public double bufferDrainRatePerTick = 0.05;

        // ── Motor crafting recipes ───────────────────────────────────────────
        // Format: {[slot1],...,[slot9]} * count
        // Slots: [modid:itemname], [ore:oreName], or [minecraft:air] for empty.

        @Config.Comment("Enable crafting recipe for the Basic Pipe Motor.")
        @Config.Name("Enable Basic Motor Recipe")
        public boolean enableMotorSlowRecipe = true;

        @Config.Name("Basic Pipe Motor Recipe")
        public String motorSlowRecipe =
                "{[ore:ingotIron],[rusticpipes:white_pipe],[ore:ingotIron],"
                + "[ore:ingotIron],[minecraft:air],[ore:ingotIron],"
                + "[ore:ingotIron],[rusticpipes:conduit],[ore:ingotIron]} * 1";

        @Config.Comment("Enable crafting recipe for the Refined Pipe Motor.")
        @Config.Name("Enable Refined Motor Recipe")
        public boolean enableMotorNormalRecipe = true;

        @Config.Name("Refined Pipe Motor Recipe")
        public String motorNormalRecipe =
                "{[ore:ingotIron],[minecraft:redstone],[ore:ingotIron],"
                + "[ore:ingotIron],[rusticpipes:conduit_buffer_slow],[ore:ingotIron],"
                + "[ore:ingotIron],[minecraft:dropper],[ore:ingotIron]} * 1";

        @Config.Comment("Enable crafting recipe for the Efficient Pipe Motor.")
        @Config.Name("Enable Efficient Motor Recipe")
        public boolean enableMotorFastRecipe = true;

        @Config.Name("Efficient Pipe Motor Recipe")
        public String motorFastRecipe =
                "{[ore:ingotGold],[rusticpipes:white_pipe],[ore:ingotGold],"
                + "[ore:ingotGold],[rusticpipes:conduit_buffer_normal],[ore:ingotGold],"
                + "[ore:ingotGold],[minecraft:comparator],[ore:ingotGold]} * 1";

        @Config.Comment("Enable crafting recipe for the Advanced Pipe Motor.")
        @Config.Name("Enable Advanced Motor Recipe")
        public boolean enableMotorTurboRecipe = true;

        @Config.Name("Advanced Pipe Motor Recipe")
        public String motorTurboRecipe =
                "{[ore:ingotIron],[minecraft:hopper],[ore:ingotIron],"
                + "[ore:ingotIron],[rusticpipes:conduit_buffer_fast],[ore:ingotIron],"
                + "[ore:ingotIron],[minecraft:hopper],[ore:ingotIron]} * 1";

        @Config.Comment("Enable crafting recipe for the Reinforced Pipe Motor.")
        @Config.Name("Enable Reinforced Motor Recipe")
        public boolean enableMotorHyperRecipe = true;

        @Config.Name("Reinforced Pipe Motor Recipe")
        public String motorHyperRecipe =
                "{[ore:ingotIron],[minecraft:iron_block],[ore:ingotIron],"
                + "[ore:ingotIron],[rusticpipes:conduit_buffer_turbo],[ore:ingotIron],"
                + "[ore:ingotIron],[minecraft:iron_block],[ore:ingotIron]} * 1";

        @Config.Comment("Enable crafting recipe for the Overclocked Pipe Motor.")
        @Config.Name("Enable Overclocked Motor Recipe")
        public boolean enableMotorUltraRecipe = true;

        @Config.Name("Overclocked Pipe Motor Recipe")
        public String motorUltraRecipe =
                "{[ore:ingotIron],[minecraft:gold_block],[ore:ingotIron],"
                + "[ore:ingotIron],[rusticpipes:conduit_buffer_hyper],[ore:ingotIron],"
                + "[ore:ingotIron],[minecraft:gold_block],[ore:ingotIron]} * 1";

        public int getBuffer(rusticpipes.network.PipeNetwork.SpeedTier tier) {
            switch (tier) {
                case NORMAL: return bufferNormal;
                case FAST:   return bufferFast;
                case TURBO:  return bufferTurbo;
                case HYPER:  return bufferHyper;
                case ULTRA:  return bufferUltra;
                default:     return bufferSlow;
            }
        }
    }

    public static class RecipeConfig {

        @Config.Comment("Enable the shaped crafting recipe for the white (base) pipe.")
        @Config.Name("Enable Base Pipe Recipe")
        public boolean enableBasePipeRecipe = true;

        @Config.Comment("Shaped recipe for the white (base) pipe.\n"
                + "Format: {[slot1],...,[slot9]} * count\n"
                + "Slots: use [modid:itemname], [ore:oreName], or [minecraft:air] for empty.\n"
                + "Slots are read left-to-right, top-to-bottom (3x3 grid).")
        @Config.Name("Base Pipe Recipe")
        public String basePipeRecipe =
                "{[minecraft:iron_ingot],[minecraft:iron_ingot],[minecraft:iron_ingot],"
                + "[minecraft:iron_ingot],[minecraft:air],[minecraft:iron_ingot],"
                + "[minecraft:iron_ingot],[minecraft:iron_ingot],[minecraft:iron_ingot]} * 4";

        @Config.Comment("Enable shapeless dye conversion recipes.")
        @Config.Name("Enable Dye Conversion Recipes")
        public boolean enableDyeRecipes = true;

        @Config.Comment("Enable shift+right-click on a placed pipe with a dye in hand to recolor it.")
        @Config.Name("Enable In-World Dye Conversion")
        public boolean enableInWorldDyeing = true;
    }

    @Config.Comment("Fluid Pipe and Tank Options")
    @Config.Name("Fluid Options")
    public static final FluidConfig fluid = new FluidConfig();

    public static class FluidConfig {

        @Config.Comment("Fluid transfer rate per tick in millibuckets (mB).")
        @Config.RangeInt(min = 1, max = 10000)
        @Config.Name("Flow Rate Per Tick (mB)")
        public int flowRatePerTick = 100;

        @Config.Comment("How often fluid pipes transfer, in ticks.")
        @Config.RangeInt(min = 1, max = 100)
        @Config.Name("Flow Tick Rate")
        public int flowTickRate = 4;

        @Config.Comment("Fluid capacity per tank block in millibuckets (mB).\n"
                + "Total capacity = blockCount x this value.")
        @Config.RangeInt(min = 1, max = 1000000)
        @Config.Name("Capacity Per Tank Block (mB)")
        public int capacityPerTankBlock = 8000;

        // ---- NuclearCraft radiation ----------------------------------------

        @Config.Comment("Enable NuclearCraft radiation from fluid pipes and tanks.\n"
                + "Requires NuclearCraft (original or Overhauled) to be installed.\n"
                + "Pipes and tanks containing radioactive fluids will irradiate nearby players.")
        @Config.Name("Enable NC Radiation")
        public boolean enableRadiation = true;

        @Config.Comment("How often radiation is applied to players, in ticks.\n"
                + "Lower values apply radiation more frequently. Minimum 1.")
        @Config.RangeInt(min = 1, max = 200)
        @Config.Name("Radiation Check Interval (ticks)")
        public int radiationTickInterval = 20;

        @Config.Comment("Radius in blocks around each pipe/tank block from which\n"
                + "players will receive radiation.")
        @Config.RangeInt(min = 1, max = 16)
        @Config.Name("Radiation Range (blocks)")
        public int radiationRange = 4;

        @Config.Comment("Multiplier applied to all radiation values emitted by\n"
                + "RusticPipes fluid blocks. 0.0 = no radiation, 1.0 = natural NC levels.\n"
                + "Use this to tune radiation strength without editing individual fluid values.")
        @Config.Name("Radiation Strength Multiplier")
        public double radiationMultiplier = 1.0;

        @Config.Comment("Fallback radiation level (Sv/t) used when NuclearCraft\'s reflection\n"
                + "API cannot read the exact value from the fluid class.\n"
                + "Applied to any fluid whose name contains a known radioactive keyword.\n"
                + "Set to 0.0 to disable the fallback entirely.")
        @Config.Name("Radiation Fallback Level (Sv/t)")
        public double radiationFallbackLevel = 0.001;

        // ---- Bucket drop on destruction ------------------------------------

        @Config.Comment("When a fluid pipe or tank is destroyed, drop a filled bucket\n"
                + "for every 1000 mB of fluid it contains. The remainder is voided.\n"
                + "Requires the fluid to have a registered bucket item.")
        @Config.Name("Drop Buckets On Break")
        public boolean dropBucketsOnBreak = true;
    }

    public static class ClientConfig {

        @Config.Comment("Show pipe network debug info when sneaking.")
        @Config.Name("Show Debug Overlay")
        public boolean showDebugOverlay = false;
    }

    @Mod.EventBusSubscriber(modid = RusticPipes.MODID)
    private static class EventHandler {
        @SubscribeEvent
        public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (event.getModID().equals(RusticPipes.MODID)) {
                ConfigManager.sync(RusticPipes.MODID, Config.Type.INSTANCE);
                parsedTickRate = null; // force re-parse on next access
            }
        }
    }
}
