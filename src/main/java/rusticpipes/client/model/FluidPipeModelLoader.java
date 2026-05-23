package rusticpipes.client.model;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import rusticpipes.RusticPipes;

public class FluidPipeModelLoader implements ICustomModelLoader {

    public static final FluidPipeModelLoader INSTANCE = new FluidPipeModelLoader();

    @Override
    public boolean accepts(ResourceLocation modelLocation) {
        return modelLocation.getNamespace().equals(RusticPipes.MODID)
                && modelLocation.getPath().equals("fluid_pipe");
    }

    @Override
    public IModel loadModel(ResourceLocation modelLocation) {
        return FluidPipeModel.INSTANCE;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {}
}
