package rusticpipes.client.model;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import rusticpipes.RusticPipes;

public class PipeModelLoader implements ICustomModelLoader {

    public static final PipeModelLoader INSTANCE = new PipeModelLoader();

    @Override
    public boolean accepts(ResourceLocation modelLocation) {
        return modelLocation.getNamespace().equals(RusticPipes.MODID)
                && modelLocation.getPath().equals("item_pipe");
    }

    @Override
    public IModel loadModel(ResourceLocation modelLocation) {
        return PipeModel.INSTANCE;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {}
}
