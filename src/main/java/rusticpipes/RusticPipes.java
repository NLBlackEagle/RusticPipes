package rusticpipes;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;
import rusticpipes.handlers.ForgeConfigHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import rusticpipes.handlers.ModRegistry;
import rusticpipes.network.PacketConduitPower;
import rusticpipes.proxy.CommonProxy;

@Mod(modid = RusticPipes.MODID, version = RusticPipes.VERSION, name = RusticPipes.NAME)
public class RusticPipes {
    public static final String MODID = "rusticpipes";
    public static final String VERSION = "1.0.0";
    public static final String NAME = "RusticPipes";
    public static final Logger LOGGER = LogManager.getLogger();
    public static boolean completedLoading = false;

    /** Set to true to enable debug chat messages in-game. */
    public static final boolean DEBUG = false;

    public static SimpleNetworkWrapper NET;

    @SidedProxy(clientSide = "rusticpipes.proxy.ClientProxy", serverSide = "rusticpipes.proxy.CommonProxy")
    public static CommonProxy PROXY;

    @Instance(MODID)
    public static RusticPipes instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        NET = NetworkRegistry.INSTANCE.newSimpleChannel(MODID);
        NET.registerMessage(PacketConduitPower.Handler.class,
                PacketConduitPower.class, 0, Side.CLIENT);

        ModRegistry.init();
        RusticPipes.PROXY.preInit();
        ForgeConfigHandler.parseTiers();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        completedLoading = true;
    }
}
