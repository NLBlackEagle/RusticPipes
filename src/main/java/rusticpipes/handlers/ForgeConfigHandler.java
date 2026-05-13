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

    public static class ServerConfig {

        @Config.Comment("Slow tier: ticks between network updates. Higher = slower transfer.")
        @Config.RangeInt(min = 1, max = 200)
        @Config.Name("Pipe Tick Rate - Slow")
        public int pipeTickRateSlow = 20;

        @Config.Comment("Normal tier: ticks between network updates.")
        @Config.RangeInt(min = 1, max = 200)
        @Config.Name("Pipe Tick Rate - Normal")
        public int pipeTickRateNormal = 10;

        @Config.Comment("Fast tier: ticks between network updates.")
        @Config.RangeInt(min = 1, max = 200)
        @Config.Name("Pipe Tick Rate - Fast")
        public int pipeTickRateFast = 4;

        @Config.Comment("Turbo tier: ticks between network updates.")
        @Config.RangeInt(min = 1, max = 200)
        @Config.Name("Pipe Tick Rate - Turbo")
        public int pipeTickRateTurbo = 1;

        @Config.Comment("Extra ticks added per pipe in the network. Longer networks are slower.")
        @Config.RangeInt(min = 0, max = 20)
        @Config.Name("Pipe Distance Penalty")
        public int pipeDistancePenalty = 1;

        @Config.Comment("Slow tier: items transferred per network update.")
        @Config.RangeInt(min = 1, max = 64)
        @Config.Name("Pipe Transfer Size - Slow")
        public int pipeTransferSizeSlow = 1;

        @Config.Comment("Normal tier: items transferred per network update.")
        @Config.RangeInt(min = 1, max = 64)
        @Config.Name("Pipe Transfer Size - Normal")
        public int pipeTransferSizeNormal = 2;

        @Config.Comment("Fast tier: items transferred per network update.")
        @Config.RangeInt(min = 1, max = 64)
        @Config.Name("Pipe Transfer Size - Fast")
        public int pipeTransferSizeFast = 4;

        @Config.Comment("Turbo tier: items transferred per network update.")
        @Config.RangeInt(min = 1, max = 64)
        @Config.Name("Pipe Transfer Size - Turbo")
        public int pipeTransferSizeTurbo = 8;
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
            }
        }
    }
}
