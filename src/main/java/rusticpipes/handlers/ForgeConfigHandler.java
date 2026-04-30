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


	}

	public static class ClientConfig {

	}

	@Mod.EventBusSubscriber(modid = RusticPipes.MODID)
	private static class EventHandler{

		@SubscribeEvent
		public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
			if(event.getModID().equals(RusticPipes.MODID)) {
				ConfigManager.sync(RusticPipes.MODID, Config.Type.INSTANCE);
			}
		}
	}
}