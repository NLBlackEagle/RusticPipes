package rusticpipes.proxy;

import rusticpipes.handlers.ClientModRegistry;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        super.preInit();
        ClientModRegistry.preInit();
    }
}
