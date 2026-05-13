package rusticpipes.client.model;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import rusticpipes.RusticPipes;

public class ConduitModelLoader implements ICustomModelLoader {

    public static final ConduitModelLoader INSTANCE = new ConduitModelLoader();

    @Override
    public boolean accepts(ResourceLocation modelLocation) {
        return modelLocation.getNamespace().equals(RusticPipes.MODID)
                && modelLocation.getPath().equals("conduit");
    }

    @Override
    public IModel loadModel(ResourceLocation modelLocation) {
        return ConduitModel.INSTANCE;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {}
}
