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
import net.minecraftforge.common.property.IExtendedBlockState;
import rusticpipes.block.BlockFluidTankMultiblock;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

/**
 * Custom model for the fluid tank block.
 * Splits rendering: SOLID pass renders opaque pixels, CUTOUT_MIPPED renders transparent ones.
 * Picks the correct viewport texture based on VIEWPORT_ROW (SINGLE/BOTTOM/CENTER/TOP).
 */
public class FluidTankModel implements IModel {

    public static final FluidTankModel INSTANCE = new FluidTankModel();

    private static final ResourceLocation TEX_VIEWPORT        = new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_viewport");
    private static final ResourceLocation TEX_VIEWPORT_BOTTOM = new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_viewport_bottom");
    private static final ResourceLocation TEX_VIEWPORT_CENTER = new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_viewport_center");
    private static final ResourceLocation TEX_VIEWPORT_TOP    = new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_viewport_top");
    private static final ResourceLocation TEX_SOLID           = new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_solid");
    private static final ResourceLocation TEX_INNER_VIEWPORT  = new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_inner_viewport");

    @Override
    public Collection<ResourceLocation> getTextures() {
        return java.util.Arrays.asList(
                TEX_VIEWPORT, TEX_VIEWPORT_BOTTOM, TEX_VIEWPORT_CENTER, TEX_VIEWPORT_TOP,
                TEX_SOLID, TEX_INNER_VIEWPORT);
    }

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> getter) {
        return new BakedTankModel(
                getter.apply(TEX_VIEWPORT),
                getter.apply(TEX_VIEWPORT_BOTTOM),
                getter.apply(TEX_VIEWPORT_CENTER),
                getter.apply(TEX_VIEWPORT_TOP),
                getter.apply(TEX_SOLID));
    }

    @Override public IModelState getDefaultState() { return TRSRTransformation.identity(); }
    @Override public Collection<ResourceLocation> getDependencies() { return Collections.emptyList(); }

    public static final class BakedTankModel implements IBakedModel {

        private final TextureAtlasSprite viewport;
        private final TextureAtlasSprite viewportBottom;
        private final TextureAtlasSprite viewportCenter;
        private final TextureAtlasSprite viewportTop;
        private final TextureAtlasSprite solid;
        private static final int NO_TINT = -1;

        BakedTankModel(TextureAtlasSprite viewport,
                       TextureAtlasSprite viewportBottom,
                       TextureAtlasSprite viewportCenter,
                       TextureAtlasSprite viewportTop,
                       TextureAtlasSprite solid) {
            this.viewport       = viewport;
            this.viewportBottom = viewportBottom;
            this.viewportCenter = viewportCenter;
            this.viewportTop    = viewportTop;
            this.solid          = solid;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            if (side != null) return Collections.emptyList();

            BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
            boolean isInventory = state == null;
            boolean isSolid  = isInventory || layer == BlockRenderLayer.SOLID;
            boolean isCutout = isInventory || layer == BlockRenderLayer.CUTOUT_MIPPED;
            if (!isSolid && !isCutout) return Collections.emptyList();

            // Pick viewport texture based on row
            TextureAtlasSprite vpTex = viewport; // default: SINGLE
            if (state instanceof IExtendedBlockState) {
                BlockFluidTankMultiblock.ViewportRow row =
                        ((IExtendedBlockState) state).getValue(BlockFluidTankMultiblock.VIEWPORT_ROW);
                if (row != null) {
                    switch (row) {
                        case BOTTOM: vpTex = viewportBottom; break;
                        case CENTER: vpTex = viewportCenter; break;
                        case TOP:    vpTex = viewportTop;    break;
                        default:     vpTex = viewport;       break;
                    }
                }
            }

            List<BakedQuad> quads = new ArrayList<>();

            // Top and bottom — solid texture
            if (isSolid) {
                float su0 = solid.getMinU(), sv0 = solid.getMinV();
                float su1 = solid.getMaxU(), sv1 = solid.getMaxV();
                addQuad(quads, EnumFacing.DOWN, 0,0,0, 1,0,0, 1,0,1, 0,0,1, su0,sv0,su1,sv1, solid);
                addQuad(quads, EnumFacing.UP,   0,1,1, 1,1,1, 1,1,0, 0,1,0, su0,sv0,su1,sv1, solid);
            }

            // Side faces — viewport texture (chosen by row)
            if (isCutout) {
                float vu0 = vpTex.getMinU(), vv0 = vpTex.getMinV();
                float vu1 = vpTex.getMaxU(), vv1 = vpTex.getMaxV();
                addQuad(quads, EnumFacing.NORTH, 1,0,0, 0,0,0, 0,1,0, 1,1,0, vu0,vv0,vu1,vv1, vpTex);
                addQuad(quads, EnumFacing.SOUTH, 0,0,1, 1,0,1, 1,1,1, 0,1,1, vu0,vv0,vu1,vv1, vpTex);
                addQuad(quads, EnumFacing.WEST,  0,0,0, 0,0,1, 0,1,1, 0,1,0, vu0,vv0,vu1,vv1, vpTex);
                addQuad(quads, EnumFacing.EAST,  1,0,1, 1,0,0, 1,1,0, 1,1,1, vu0,vv0,vu1,vv1, vpTex);
            }

            return quads;
        }

        private void addQuad(List<BakedQuad> quads, EnumFacing face,
                float x1, float y1, float z1, float x2, float y2, float z2,
                float x3, float y3, float z3, float x4, float y4, float z4,
                float u0, float v0, float u1, float v1,
                TextureAtlasSprite sprite) {
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
        @Override public TextureAtlasSprite getParticleTexture() { return solid; }
        @Override public ItemOverrideList getOverrides()         { return ItemOverrideList.NONE; }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }
    }
}
