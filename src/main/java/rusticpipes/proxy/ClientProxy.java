package rusticpipes.proxy;

import rusticpipes.handlers.ClientModRegistry;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        super.preInit();
        ClientModRegistry.preInit();
    }

    @Override
    public void init() {
        super.init();
        ClientModRegistry.init();
    }

    @Override
    public void postInit() {
        super.postInit();
        ClientModRegistry.postInit();
    }
}
