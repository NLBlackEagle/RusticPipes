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

    private static final ResourceLocation PIPE_BODY             = new ResourceLocation("rusticpipes:blocks/pipe_body");
    private static final ResourceLocation PIPE_CAP    = new ResourceLocation("rusticpipes:blocks/pipe_cap");
    private static final ResourceLocation PIPE_FLANGE = new ResourceLocation("rusticpipes:blocks/pipe_flange");
    private static final ResourceLocation PIPE_CAP_OUTPUT_EAST  = new ResourceLocation("rusticpipes:blocks/pipe_cap_output_east");
    private static final ResourceLocation PIPE_CAP_OUTPUT_WEST  = new ResourceLocation("rusticpipes:blocks/pipe_cap_output_west");
    private static final ResourceLocation PIPE_CAP_OUTPUT_NORTH = new ResourceLocation("rusticpipes:blocks/pipe_cap_output_north");
    private static final ResourceLocation PIPE_CAP_OUTPUT_SOUTH = new ResourceLocation("rusticpipes:blocks/pipe_cap_output_south");
    private static final ResourceLocation PIPE_CAP_INPUT_EAST   = new ResourceLocation("rusticpipes:blocks/pipe_cap_input_east");
    private static final ResourceLocation PIPE_CAP_INPUT_WEST   = new ResourceLocation("rusticpipes:blocks/pipe_cap_input_west");
    private static final ResourceLocation PIPE_CAP_INPUT_NORTH  = new ResourceLocation("rusticpipes:blocks/pipe_cap_input_north");
    private static final ResourceLocation PIPE_CAP_INPUT_SOUTH  = new ResourceLocation("rusticpipes:blocks/pipe_cap_input_south");

    @Override
    public Collection<ResourceLocation> getTextures() {
        return Arrays.asList(PIPE_BODY, PIPE_CAP, PIPE_FLANGE,
                PIPE_CAP_OUTPUT_EAST, PIPE_CAP_OUTPUT_WEST, PIPE_CAP_OUTPUT_NORTH, PIPE_CAP_OUTPUT_SOUTH,
                PIPE_CAP_INPUT_EAST,  PIPE_CAP_INPUT_WEST,  PIPE_CAP_INPUT_NORTH,  PIPE_CAP_INPUT_SOUTH);
    }

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        return new PipeBakedModel(
                bakedTextureGetter.apply(PIPE_BODY),
                bakedTextureGetter.apply(PIPE_CAP),
                bakedTextureGetter.apply(PIPE_FLANGE),
                bakedTextureGetter.apply(PIPE_CAP_OUTPUT_EAST),
                bakedTextureGetter.apply(PIPE_CAP_OUTPUT_WEST),
                bakedTextureGetter.apply(PIPE_CAP_OUTPUT_NORTH),
                bakedTextureGetter.apply(PIPE_CAP_OUTPUT_SOUTH),
                bakedTextureGetter.apply(PIPE_CAP_INPUT_EAST),
                bakedTextureGetter.apply(PIPE_CAP_INPUT_WEST),
                bakedTextureGetter.apply(PIPE_CAP_INPUT_NORTH),
                bakedTextureGetter.apply(PIPE_CAP_INPUT_SOUTH));
    }

    @Override public IModelState getDefaultState() { return TRSRTransformation.identity(); }
    @Override public Collection<ResourceLocation> getDependencies() { return Collections.emptyList(); }

    public static class PipeBakedModel implements IBakedModel {

        private final TextureAtlasSprite body, cap, flange;
        private final TextureAtlasSprite outEast, outWest, outNorth, outSouth;
        private final TextureAtlasSprite inEast,  inWest,  inNorth,  inSouth;

        private static final float CORE_MIN = 4f / 16f;
        private static final float CORE_MAX = 12f / 16f;
        private static final float CAP_MIN  = 3f / 16f;
        private static final float CAP_MAX  = 13f / 16f;
        private static final float CAP_W    = 2f / 16f;

        // Face state values
        private static final int NOT_CONNECTED  = 0;
        private static final int PIPE           = 1;
        private static final int INV_OUTPUT     = 2;
        private static final int INV_INPUT      = 3;

        public PipeBakedModel(TextureAtlasSprite body, TextureAtlasSprite cap,
                              TextureAtlasSprite flange,
                              TextureAtlasSprite outEast, TextureAtlasSprite outWest,
                              TextureAtlasSprite outNorth, TextureAtlasSprite outSouth,
                              TextureAtlasSprite inEast, TextureAtlasSprite inWest,
                              TextureAtlasSprite inNorth, TextureAtlasSprite inSouth) {
            this.body     = body;
            this.cap      = cap;
            this.flange   = flange;
            this.outEast  = outEast;
            this.outWest  = outWest;
            this.outNorth = outNorth;
            this.outSouth = outSouth;
            this.inEast   = inEast;
            this.inWest   = inWest;
            this.inNorth  = inNorth;
            this.inSouth  = inSouth;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            if (side != null) return Collections.emptyList();

            List<BakedQuad> quads = new ArrayList<>();

            if (state == null) {
                addCube(quads, CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, body);
                for (EnumFacing f : EnumFacing.VALUES) {
                    addArm(quads, f);
                    addFlange(quads, f, INV_OUTPUT);
                }
                return quads;
            }

            int north = state.getValue(BlockItemPipe.NORTH);
            int south = state.getValue(BlockItemPipe.SOUTH);
            int east  = state.getValue(BlockItemPipe.EAST);
            int west  = state.getValue(BlockItemPipe.WEST);
            int up    = state.getValue(BlockItemPipe.UP);
            int down  = state.getValue(BlockItemPipe.DOWN);

            addCube(quads, CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, body);

            if (north > 0) { addArm(quads, EnumFacing.NORTH); addFlange(quads, EnumFacing.NORTH, north); }
            if (south > 0) { addArm(quads, EnumFacing.SOUTH); addFlange(quads, EnumFacing.SOUTH, south); }
            if (east  > 0) { addArm(quads, EnumFacing.EAST);  addFlange(quads, EnumFacing.EAST,  east);  }
            if (west  > 0) { addArm(quads, EnumFacing.WEST);  addFlange(quads, EnumFacing.WEST,  west);  }
            if (up    > 0) { addArm(quads, EnumFacing.UP);    addFlange(quads, EnumFacing.UP,    up);    }
            if (down  > 0) { addArm(quads, EnumFacing.DOWN);  addFlange(quads, EnumFacing.DOWN,  down);  }

            return quads;
        }

        private TextureAtlasSprite getArrowTex(EnumFacing armDir, EnumFacing sideFace, boolean input) {
            EnumFacing texDir;
            switch (armDir) {
                case EAST:
                    switch (sideFace) {
                        case NORTH: texDir = EnumFacing.WEST;  break;
                        case SOUTH: texDir = EnumFacing.EAST;  break;
                        case UP:    texDir = EnumFacing.EAST;  break;
                        case DOWN:  texDir = EnumFacing.EAST;  break;
                        default:    texDir = EnumFacing.EAST;  break;
                    }
                    break;
                case WEST:
                    switch (sideFace) {
                        case NORTH: texDir = EnumFacing.EAST;  break;
                        case SOUTH: texDir = EnumFacing.WEST;  break;
                        case UP:    texDir = EnumFacing.WEST;  break;
                        case DOWN:  texDir = EnumFacing.WEST;  break;
                        default:    texDir = EnumFacing.WEST;  break;
                    }
                    break;
                case NORTH:
                    switch (sideFace) {
                        case EAST:  texDir = EnumFacing.EAST;  break;
                        case WEST:  texDir = EnumFacing.WEST;  break;
                        case UP:    texDir = EnumFacing.NORTH; break;
                        case DOWN:  texDir = EnumFacing.SOUTH; break;
                        default:    texDir = EnumFacing.NORTH; break;
                    }
                    break;
                case SOUTH:
                    switch (sideFace) {
                        case EAST:  texDir = EnumFacing.WEST;  break;
                        case WEST:  texDir = EnumFacing.EAST;  break;
                        case UP:    texDir = EnumFacing.SOUTH; break;
                        case DOWN:  texDir = EnumFacing.NORTH; break;
                        default:    texDir = EnumFacing.SOUTH; break;
                    }
                    break;
                case UP:
                    switch (sideFace) {
                        case NORTH: texDir = EnumFacing.SOUTH; break;
                        case SOUTH: texDir = EnumFacing.NORTH; break;
                        case EAST:  texDir = EnumFacing.NORTH; break;
                        case WEST:  texDir = EnumFacing.NORTH; break;
                        default:    texDir = EnumFacing.NORTH; break;
                    }
                    break;
                case DOWN:
                    switch (sideFace) {
                        case NORTH: texDir = EnumFacing.SOUTH; break;
                        case SOUTH: texDir = EnumFacing.NORTH; break;
                        case EAST:  texDir = EnumFacing.NORTH; break;
                        case WEST:  texDir = EnumFacing.NORTH; break;
                        default:    texDir = EnumFacing.NORTH; break;
                    }
                    break;
                default: texDir = EnumFacing.EAST; break;
            }

            if (input) {
                switch (texDir) {
                    case EAST:  return inEast;
                    case WEST:  return inWest;
                    case NORTH: return inNorth;
                    case SOUTH: return inSouth;
                    default:    return inEast;
                }
            } else {
                switch (texDir) {
                    case EAST:  return outEast;
                    case WEST:  return outWest;
                    case NORTH: return outNorth;
                    case SOUTH: return outSouth;
                    default:    return outEast;
                }
            }
        }

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

        // faceState: 1=pipe, 2=inv output, 3=inv input
        private void addFlange(List<BakedQuad> quads, EnumFacing dir, int faceState) {
            boolean isInventory = faceState == INV_OUTPUT || faceState == INV_INPUT;
            boolean isInput     = faceState == INV_INPUT;

            switch (dir) {
                case DOWN: {
                    float x1=CAP_MIN, y1=0, z1=CAP_MIN, x2=CAP_MAX, y2=CAP_W, z2=CAP_MAX;
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.NORTH, isInput) : flange);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.SOUTH, isInput) : flange);
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.WEST,  isInput) : flange);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.EAST,  isInput) : flange);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, flange);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, flange);
                    break;
                }
                case UP: {
                    float x1=CAP_MIN, y1=1-CAP_W, z1=CAP_MIN, x2=CAP_MAX, y2=1, z2=CAP_MAX;
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.NORTH, isInput) : flange);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.SOUTH, isInput) : flange);
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.WEST,  isInput) : flange);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.EAST,  isInput) : flange);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, flange);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, flange);
                    break;
                }
                case NORTH: {
                    float x1=CAP_MIN, y1=CAP_MIN, z1=0, x2=CAP_MAX, y2=CAP_MAX, z2=CAP_W;
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.WEST,  isInput) : flange);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.EAST,  isInput) : flange);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, isInventory ? getArrowTex(dir, EnumFacing.DOWN,  isInput) : flange);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.UP,    isInput) : flange);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, flange);
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, flange);
                    break;
                }
                case SOUTH: {
                    float x1=CAP_MIN, y1=CAP_MIN, z1=1-CAP_W, x2=CAP_MAX, y2=CAP_MAX, z2=1;
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.WEST,  isInput) : flange);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.EAST,  isInput) : flange);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, isInventory ? getArrowTex(dir, EnumFacing.DOWN,  isInput) : flange);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.UP,    isInput) : flange);
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, flange);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, flange);
                    break;
                }
                case WEST: {
                    float x1=0, y1=CAP_MIN, z1=CAP_MIN, x2=CAP_W, y2=CAP_MAX, z2=CAP_MAX;
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.NORTH, isInput) : flange);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.SOUTH, isInput) : flange);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, isInventory ? getArrowTex(dir, EnumFacing.DOWN,  isInput) : flange);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.UP,    isInput) : flange);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, flange);
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, flange);
                    break;
                }
                case EAST: {
                    float x1=1-CAP_W, y1=CAP_MIN, z1=CAP_MIN, x2=1, y2=CAP_MAX, z2=CAP_MAX;
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.NORTH, isInput) : flange);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.SOUTH, isInput) : flange);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, isInventory ? getArrowTex(dir, EnumFacing.DOWN,  isInput) : flange);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.UP,    isInput) : flange);
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, flange);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, flange);
                    break;
                }
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
