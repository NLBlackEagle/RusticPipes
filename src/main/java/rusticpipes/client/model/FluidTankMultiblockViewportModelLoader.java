package rusticpipes.client.model;

import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import rusticpipes.RusticPipes;

import java.util.*;
import java.util.function.Function;

/**
 * Handles the single model location "fluid_tank_multiblock_viewport".
 * All 4 viewport directions (N/S/E/W) share this one baked model;
 * the direction is read at render time from the block's VIEWPORT property,
 * and the row from the extended state VIEWPORT_ROW property.
 */
public class FluidTankMultiblockViewportModelLoader implements ICustomModelLoader {

    public static final FluidTankMultiblockViewportModelLoader INSTANCE =
            new FluidTankMultiblockViewportModelLoader();

    private static final ResourceLocation MODEL_LOCATION =
            new ResourceLocation(RusticPipes.MODID, "fluid_tank_multiblock_viewport");

    private static final ResourceLocation TEX_SINGLE =
            new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_viewport");
    private static final ResourceLocation TEX_BOTTOM =
            new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_viewport_bottom");
    private static final ResourceLocation TEX_CENTER =
            new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_viewport_center");
    private static final ResourceLocation TEX_TOP =
            new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_viewport_top");
    private static final ResourceLocation TEX_SOLID =
            new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_solid");

    @Override
    public boolean accepts(ResourceLocation modelLocation) {
        return modelLocation.getNamespace().equals(RusticPipes.MODID)
                && modelLocation.getPath().equals("fluid_tank_multiblock_viewport");
    }

    @Override
    public IModel loadModel(ResourceLocation modelLocation) {
        return new ViewportIModel();
    }

    @Override
    public void onResourceManagerReload(IResourceManager resourceManager) {}

    private static final class ViewportIModel implements IModel {

        @Override
        public Collection<ResourceLocation> getTextures() {
            List<ResourceLocation> textures = new ArrayList<>(Arrays.asList(TEX_SINGLE, TEX_BOTTOM, TEX_CENTER, TEX_TOP, TEX_SOLID));
            
            System.out.println("[FluidTankMultiblockViewportModelLoader] getTextures() called!");
            
            // Register inner side textures (2x1-4x10)
            for (int base = 2; base <= 4; base++) {
                for (int height = 1; height <= 10; height++) {
                    ResourceLocation loc = new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_inner_side_" + base + "x" + height);
                    textures.add(loc);
                }
            }
            
            // Register inner top/bottom textures (2x2, 3x3, 4x4)
            for (int base = 2; base <= 4; base++) {
                textures.add(new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_inner_top_" + base + "x" + base));
                textures.add(new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_inner_bottom_" + base + "x" + base));
            }
            
            System.out.println("[FluidTankMultiblockViewportModelLoader] Total textures: " + textures.size());
            return textures;
        }

        @Override
        public Collection<ResourceLocation> getDependencies() {
            return Collections.emptyList();
        }

        @Override
        public IBakedModel bake(IModelState state, VertexFormat format,
                                Function<ResourceLocation, TextureAtlasSprite> getter) {
            // Load viewport textures
            getter.apply(TEX_SINGLE);
            getter.apply(TEX_BOTTOM);
            getter.apply(TEX_CENTER);
            getter.apply(TEX_TOP);
            getter.apply(TEX_SOLID);
            
            // Load inner side textures (2x1-4x10) to ensure they're stitched into atlas
            for (int base = 2; base <= 4; base++) {
                for (int height = 1; height <= 10; height++) {
                    getter.apply(new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_inner_side_" + base + "x" + height));
                }
            }
            
            // Load inner top/bottom textures (2x2, 3x3, 4x4)
            for (int base = 2; base <= 4; base++) {
                getter.apply(new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_inner_top_" + base + "x" + base));
                getter.apply(new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_inner_bottom_" + base + "x" + base));
            }
            
            return new FluidTankMultiblockViewportModel(
                    getter.apply(TEX_SINGLE),
                    getter.apply(TEX_BOTTOM),
                    getter.apply(TEX_CENTER),
                    getter.apply(TEX_TOP),
                    getter.apply(TEX_SOLID));
        }

        @Override
        public IModelState getDefaultState() {
            return TRSRTransformation.identity();
        }
    }
}
