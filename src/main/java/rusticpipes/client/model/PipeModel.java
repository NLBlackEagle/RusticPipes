package rusticpipes.client.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import rusticpipes.block.BlockItemPipe;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

public class PipeModel implements IModel {

    public static final PipeModel INSTANCE = new PipeModel();

    private static final ResourceLocation PIPE_BODY      = new ResourceLocation("rusticpipes:blocks/pipe_body");
    private static final ResourceLocation PIPE_CAP       = new ResourceLocation("rusticpipes:blocks/pipe_cap");
    private static final ResourceLocation PIPE_IND_RIGHT = new ResourceLocation("rusticpipes:blocks/pipe_indicator_right");
    private static final ResourceLocation PIPE_IND_LEFT  = new ResourceLocation("rusticpipes:blocks/pipe_indicator_left");

    @Override
    public Collection<ResourceLocation> getTextures() {
        return Arrays.asList(PIPE_BODY, PIPE_CAP, PIPE_IND_RIGHT, PIPE_IND_LEFT);
    }

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        return new PipeBakedModel(
                bakedTextureGetter.apply(PIPE_BODY),
                bakedTextureGetter.apply(PIPE_CAP),
                bakedTextureGetter.apply(PIPE_IND_RIGHT),
                bakedTextureGetter.apply(PIPE_IND_LEFT));
    }

    @Override public IModelState getDefaultState() { return TRSRTransformation.identity(); }
    @Override public Collection<ResourceLocation> getDependencies() { return Collections.emptyList(); }

    public static class PipeBakedModel implements IBakedModel {

        private final TextureAtlasSprite body, cap, indRight, indLeft;

        private static final float CORE_MIN = 4f / 16f;
        private static final float CORE_MAX = 12f / 16f;
        private static final float CAP_MIN  = 3f / 16f;
        private static final float CAP_MAX  = 13f / 16f;
        private static final float CAP_W    = 2f / 16f;

        public PipeBakedModel(TextureAtlasSprite body, TextureAtlasSprite cap,
                              TextureAtlasSprite indRight, TextureAtlasSprite indLeft) {
            this.body     = body;
            this.cap      = cap;
            this.indRight = indRight;
            this.indLeft  = indLeft;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            if (side != null) return Collections.emptyList();

            List<BakedQuad> quads = new ArrayList<>();

            if (state == null) {
                addCube(quads, CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, body);
                for (EnumFacing f : EnumFacing.VALUES) {
                    addArm(quads, f);
                    addFlange(quads, f);
                }
                return quads;
            }

            EnumFacing facing     = state.getValue(BlockItemPipe.FACING);
            boolean    isEndpoint = state.getValue(BlockItemPipe.ENDPOINT);
            boolean    connNorth  = state.getValue(BlockItemPipe.NORTH);
            boolean    connSouth  = state.getValue(BlockItemPipe.SOUTH);
            boolean    connEast   = state.getValue(BlockItemPipe.EAST);
            boolean    connWest   = state.getValue(BlockItemPipe.WEST);
            boolean    connUp     = state.getValue(BlockItemPipe.UP);
            boolean    connDown   = state.getValue(BlockItemPipe.DOWN);

            // Core
            addCube(quads, CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, body);

            // Connected faces: arm + flange at block face
            // Unconnected faces: nothing — just the plain core face
            if (connNorth) { addArm(quads, EnumFacing.NORTH); addFlange(quads, EnumFacing.NORTH); }
            if (connSouth) { addArm(quads, EnumFacing.SOUTH); addFlange(quads, EnumFacing.SOUTH); }
            if (connEast)  { addArm(quads, EnumFacing.EAST);  addFlange(quads, EnumFacing.EAST);  }
            if (connWest)  { addArm(quads, EnumFacing.WEST);  addFlange(quads, EnumFacing.WEST);  }
            if (connUp)    { addArm(quads, EnumFacing.UP);    addFlange(quads, EnumFacing.UP);    }
            if (connDown)  { addArm(quads, EnumFacing.DOWN);  addFlange(quads, EnumFacing.DOWN);  }

            // Indicator panels
            if (isEndpoint) {
                TextureAtlasSprite ind = getIndicator(facing);
                for (EnumFacing face : EnumFacing.VALUES) {
                    if (face.getAxis() != facing.getAxis()) {
                        addIndicatorPanel(quads, face, ind);
                    }
                }
            }

            return quads;
        }

        private TextureAtlasSprite getIndicator(EnumFacing facing) {
            switch (facing) {
                case EAST: case SOUTH: case UP: return indRight;
                default: return indLeft;
            }
        }

        // Arm: tube from core to block face
        private void addArm(List<BakedQuad> quads, EnumFacing dir) {
            switch (dir) {
                case DOWN:  addCube(quads, CORE_MIN, 0,        CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX, body); break;
                case UP:    addCube(quads, CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX, 1,        CORE_MAX, body); break;
                case NORTH: addCube(quads, CORE_MIN, CORE_MIN, 0,        CORE_MAX, CORE_MAX, CORE_MIN, body); break;
                case SOUTH: addCube(quads, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, 1,        body); break;
                case WEST:  addCube(quads, 0,        CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, body); break;
                case EAST:  addCube(quads, CORE_MAX, CORE_MIN, CORE_MIN, 1,        CORE_MAX, CORE_MAX, body); break;
            }
        }

        // Flange: wider collar at block face on connected faces only
        private void addFlange(List<BakedQuad> quads, EnumFacing dir) {
            switch (dir) {
                case DOWN:  addCube(quads, CAP_MIN, 0,        CAP_MIN, CAP_MAX, CAP_W,    CAP_MAX, cap); break;
                case UP:    addCube(quads, CAP_MIN, 1-CAP_W,  CAP_MIN, CAP_MAX, 1,        CAP_MAX, cap); break;
                case NORTH: addCube(quads, CAP_MIN, CAP_MIN,  0,       CAP_MAX, CAP_MAX,  CAP_W,   cap); break;
                case SOUTH: addCube(quads, CAP_MIN, CAP_MIN,  1-CAP_W, CAP_MAX, CAP_MAX,  1,       cap); break;
                case WEST:  addCube(quads, 0,       CAP_MIN,  CAP_MIN, CAP_W,   CAP_MAX,  CAP_MAX, cap); break;
                case EAST:  addCube(quads, 1-CAP_W, CAP_MIN,  CAP_MIN, 1,       CAP_MAX,  CAP_MAX, cap); break;
            }
        }

        private void addIndicatorPanel(List<BakedQuad> quads, EnumFacing face, TextureAtlasSprite sprite) {
            float off = 0.002f;
            switch (face) {
                case UP:    addQuad(quads, face, CORE_MIN,CORE_MAX+off,CORE_MIN, CORE_MAX,CORE_MAX+off,CORE_MIN, CORE_MAX,CORE_MAX+off,CORE_MAX, CORE_MIN,CORE_MAX+off,CORE_MAX, sprite); break;
                case DOWN:  addQuad(quads, face, CORE_MIN,CORE_MIN-off,CORE_MAX, CORE_MAX,CORE_MIN-off,CORE_MAX, CORE_MAX,CORE_MIN-off,CORE_MIN, CORE_MIN,CORE_MIN-off,CORE_MIN, sprite); break;
                case NORTH: addQuad(quads, face, CORE_MAX,CORE_MIN,CORE_MIN-off, CORE_MIN,CORE_MIN,CORE_MIN-off, CORE_MIN,CORE_MAX,CORE_MIN-off, CORE_MAX,CORE_MAX,CORE_MIN-off, sprite); break;
                case SOUTH: addQuad(quads, face, CORE_MIN,CORE_MIN,CORE_MAX+off, CORE_MAX,CORE_MIN,CORE_MAX+off, CORE_MAX,CORE_MAX,CORE_MAX+off, CORE_MIN,CORE_MAX,CORE_MAX+off, sprite); break;
                case WEST:  addQuad(quads, face, CORE_MIN-off,CORE_MIN,CORE_MAX, CORE_MIN-off,CORE_MIN,CORE_MIN, CORE_MIN-off,CORE_MAX,CORE_MIN, CORE_MIN-off,CORE_MAX,CORE_MAX, sprite); break;
                case EAST:  addQuad(quads, face, CORE_MAX+off,CORE_MIN,CORE_MIN, CORE_MAX+off,CORE_MIN,CORE_MAX, CORE_MAX+off,CORE_MAX,CORE_MAX, CORE_MAX+off,CORE_MAX,CORE_MIN, sprite); break;
            }
        }

        private void addCube(List<BakedQuad> quads, float x1, float y1, float z1,
                             float x2, float y2, float z2, TextureAtlasSprite s) {
            addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, s);
            addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, s);
            addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, s);
            addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, s);
            addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, s);
            addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, s);
        }

        private void addQuad(List<BakedQuad> quads, EnumFacing face,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             TextureAtlasSprite sprite) {
            int[] data = new int[28];
            float u0 = sprite.getMinU(), v0 = sprite.getMinV();
            float u1 = sprite.getMaxU(), v1 = sprite.getMaxV();
            putVertex(data, 0,  x1,y1,z1, u0,v1, face);
            putVertex(data, 7,  x2,y2,z2, u1,v1, face);
            putVertex(data, 14, x3,y3,z3, u1,v0, face);
            putVertex(data, 21, x4,y4,z4, u0,v0, face);
            quads.add(new BakedQuad(data, -1, face, sprite, true, DefaultVertexFormats.ITEM));
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

        @Override public boolean isAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean isBuiltInRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleTexture() { return body; }
        @Override public ItemOverrideList getOverrides() { return ItemOverrideList.NONE; }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }
    }
}
