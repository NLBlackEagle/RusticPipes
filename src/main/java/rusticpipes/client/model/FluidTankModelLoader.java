package rusticpipes.client.model;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import rusticpipes.RusticPipes;

public class FluidTankModelLoader implements ICustomModelLoader {

    public static final FluidTankModelLoader INSTANCE = new FluidTankModelLoader();

    @Override
    public boolean accepts(ResourceLocation modelLocation) {
        if (!modelLocation.getNamespace().equals(RusticPipes.MODID)) return false;
        String path = modelLocation.getPath();
        // Multiblock uses plain cube_all — skip it
        if (path.equals("fluid_tank_multiblock")) return false;
        return path.startsWith("fluid_tank") || path.equals("item/fluid_tank");
    }

    @Override
    public IModel loadModel(ResourceLocation modelLocation) {
        return FluidTankModel.INSTANCE;
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {}
}
