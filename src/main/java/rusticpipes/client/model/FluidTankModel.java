package rusticpipes.client.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

/**
 * Custom model for the fluid tank block.
 * Splits rendering: SOLID pass renders opaque pixels, CUTOUT_MIPPED renders transparent ones.
 * This is required for the viewport texture to show through properly.
 */
public class FluidTankModel implements IModel {

    public static final FluidTankModel INSTANCE = new FluidTankModel();

    private static final ResourceLocation TEXTURE =
            new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_viewport");

    @Override
    public Collection<ResourceLocation> getTextures() {
        return Collections.singletonList(TEXTURE);
    }

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> getter) {
        return new BakedTankModel(getter.apply(TEXTURE));
    }

    @Override public IModelState getDefaultState() { return TRSRTransformation.identity(); }
    @Override public Collection<ResourceLocation> getDependencies() { return Collections.emptyList(); }

    public static final class BakedTankModel implements IBakedModel {

        private final TextureAtlasSprite sprite;
        private static final int NO_TINT = -1;

        BakedTankModel(TextureAtlasSprite sprite) {
            this.sprite = sprite;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            if (side != null) return Collections.emptyList();

            BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
            // In-world: only render in CUTOUT_MIPPED so transparent pixels show through.
            // Inventory (state==null, layer==null): always render.
            if (state != null && layer != BlockRenderLayer.CUTOUT_MIPPED) {
                return Collections.emptyList();
            }

            List<BakedQuad> quads = new ArrayList<>();
            float u0 = sprite.getMinU(), v0 = sprite.getMinV();
            float u1 = sprite.getMaxU(), v1 = sprite.getMaxV();

            addQuad(quads, EnumFacing.DOWN,  0,0,0, 1,0,0, 1,0,1, 0,0,1, u0,v0,u1,v1);
            addQuad(quads, EnumFacing.UP,    0,1,1, 1,1,1, 1,1,0, 0,1,0, u0,v0,u1,v1);
            addQuad(quads, EnumFacing.NORTH, 1,0,0, 0,0,0, 0,1,0, 1,1,0, u0,v0,u1,v1);
            addQuad(quads, EnumFacing.SOUTH, 0,0,1, 1,0,1, 1,1,1, 0,1,1, u0,v0,u1,v1);
            addQuad(quads, EnumFacing.WEST,  0,0,0, 0,0,1, 0,1,1, 0,1,0, u0,v0,u1,v1);
            addQuad(quads, EnumFacing.EAST,  1,0,1, 1,0,0, 1,1,0, 1,1,1, u0,v0,u1,v1);

            return quads;
        }

        private void addQuad(List<BakedQuad> quads, EnumFacing face,
                float x1, float y1, float z1, float x2, float y2, float z2,
                float x3, float y3, float z3, float x4, float y4, float z4,
                float u0, float v0, float u1, float v1) {
            int[] data = new int[28];
            putVertex(data, 0,  x1,y1,z1, u0,v1, face);
            putVertex(data, 7,  x2,y2,z2, u1,v1, face);
            putVertex(data, 14, x3,y3,z3, u1,v0, face);
            putVertex(data, 21, x4,y4,z4, u0,v0, face);
            quads.add(new BakedQuad(data, NO_TINT, face, sprite, true, DefaultVertexFormats.ITEM));
        }

        private void putVertex(int[] data, int i, float x, float y, float z,
                               float u, float v, EnumFacing face) {
            data[i]   = Float.floatToRawIntBits(x);
            data[i+1] = Float.floatToRawIntBits(y);
            data[i+2] = Float.floatToRawIntBits(z);
            data[i+3] = 0xFFFFFFFF;
            data[i+4] = Float.floatToRawIntBits(u);
            data[i+5] = Float.floatToRawIntBits(v);
            data[i+6] = packNormal(face);
        }

        private int packNormal(EnumFacing face) {
            int x = ((int)(face.getXOffset() * 127)) & 0xFF;
            int y = ((int)(face.getYOffset() * 127)) & 0xFF;
            int z = ((int)(face.getZOffset() * 127)) & 0xFF;
            return x | (y << 8) | (z << 16);
        }

        @Override public boolean isAmbientOcclusion()  { return true; }
        @Override public boolean isGui3d()              { return true; }
        @Override public boolean isBuiltInRenderer()    { return false; }
        @Override public TextureAtlasSprite getParticleTexture() { return sprite; }
        @Override public ItemOverrideList getOverrides()         { return ItemOverrideList.NONE; }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }
    }
}
