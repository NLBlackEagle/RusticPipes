package rusticpipes.client.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import rusticpipes.block.PipeColor;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

public class PipeModel implements IModel {

    public static final PipeModel INSTANCE = new PipeModel();

    // -----------------------------------------------------------------------
    // Unlisted properties — passed through extended block state at render time
    // -----------------------------------------------------------------------

    public static final IUnlistedProperty<BlockPos> BLOCK_POS = new IUnlistedProperty<BlockPos>() {
        @Override public String getName() { return "block_pos"; }
        @Override public Class<BlockPos> getType() { return BlockPos.class; }
        @Override public boolean isValid(BlockPos value) { return true; }
        @Override public String valueToString(BlockPos value) { return value.toString(); }
    };

    public static final IUnlistedProperty<PipeColor> PIPE_COLOR = new IUnlistedProperty<PipeColor>() {
        @Override public String getName() { return "pipe_color"; }
        @Override public Class<PipeColor> getType() { return PipeColor.class; }
        @Override public boolean isValid(PipeColor value) { return true; }
        @Override public String valueToString(PipeColor value) { return value.name(); }
    };

    // One unlisted Integer property per face — replaces the listed PropertyInteger fields
    // that were causing the combinatorial explosion of blockstate permutations.
    public static final IUnlistedProperty<Integer> CON_NORTH = conProp("con_north");
    public static final IUnlistedProperty<Integer> CON_SOUTH = conProp("con_south");
    public static final IUnlistedProperty<Integer> CON_EAST  = conProp("con_east");
    public static final IUnlistedProperty<Integer> CON_WEST  = conProp("con_west");
    public static final IUnlistedProperty<Integer> CON_UP    = conProp("con_up");
    public static final IUnlistedProperty<Integer> CON_DOWN  = conProp("con_down");

    private static IUnlistedProperty<Integer> conProp(String name) {
        return new IUnlistedProperty<Integer>() {
            @Override public String getName() { return name; }
            @Override public Class<Integer> getType() { return Integer.class; }
            @Override public boolean isValid(Integer value) { return value >= 0 && value <= 3; }
            @Override public String valueToString(Integer value) { return value.toString(); }
        };
    }

    /** Returns the connection unlisted property for the given face. */
    public static IUnlistedProperty<Integer> getConProperty(EnumFacing face) {
        switch (face) {
            case NORTH: return CON_NORTH;
            case SOUTH: return CON_SOUTH;
            case EAST:  return CON_EAST;
            case WEST:  return CON_WEST;
            case UP:    return CON_UP;
            case DOWN:  return CON_DOWN;
            default:    return CON_NORTH;
        }
    }

    // -----------------------------------------------------------------------
    // Texture resource locations
    // -----------------------------------------------------------------------

    private static final ResourceLocation PIPE_CAP          = new ResourceLocation("rusticpipes:blocks/pipe_cap");
    private static final ResourceLocation PIPE_FLANGE        = new ResourceLocation("rusticpipes:blocks/pipe_flange");
    private static final ResourceLocation PIPE_FLANGE_OUTER         = new ResourceLocation("rusticpipes:blocks/pipe_flange_outer");
    private static final ResourceLocation PIPE_FLANGE_INNER         = new ResourceLocation("rusticpipes:blocks/pipe_flange_inner");
    private static final ResourceLocation PIPE_FLANGE_OUTER_OUTPUT  = new ResourceLocation("rusticpipes:blocks/pipe_flange_outer_output");
    private static final ResourceLocation PIPE_FLANGE_INNER_OUTPUT  = new ResourceLocation("rusticpipes:blocks/pipe_flange_inner_output");
    private static final ResourceLocation PIPE_FLANGE_OUTER_INPUT   = new ResourceLocation("rusticpipes:blocks/pipe_flange_outer_input");
    private static final ResourceLocation PIPE_FLANGE_INNER_INPUT   = new ResourceLocation("rusticpipes:blocks/pipe_flange_inner_input");
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
        List<ResourceLocation> textures = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            textures.add(new ResourceLocation("rusticpipes:blocks/pipe_body_" + String.format("%02d", i)));
        }
        textures.add(PIPE_CAP);
        textures.add(PIPE_FLANGE);
        textures.add(PIPE_FLANGE_OUTER);        textures.add(PIPE_FLANGE_INNER);
        textures.add(PIPE_FLANGE_OUTER_OUTPUT); textures.add(PIPE_FLANGE_INNER_OUTPUT);
        textures.add(PIPE_FLANGE_OUTER_INPUT);  textures.add(PIPE_FLANGE_INNER_INPUT);
        textures.add(PIPE_CAP_OUTPUT_EAST);  textures.add(PIPE_CAP_OUTPUT_WEST);
        textures.add(PIPE_CAP_OUTPUT_NORTH); textures.add(PIPE_CAP_OUTPUT_SOUTH);
        textures.add(PIPE_CAP_INPUT_EAST);   textures.add(PIPE_CAP_INPUT_WEST);
        textures.add(PIPE_CAP_INPUT_NORTH);  textures.add(PIPE_CAP_INPUT_SOUTH);
        return textures;
    }

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        TextureAtlasSprite[] bodySprites = new TextureAtlasSprite[20];
        for (int i = 0; i < 20; i++) {
            bodySprites[i] = bakedTextureGetter.apply(
                    new ResourceLocation("rusticpipes:blocks/pipe_body_" + String.format("%02d", i + 1)));
        }
        return new PipeBakedModel(
                bodySprites,
                bakedTextureGetter.apply(PIPE_CAP),
                bakedTextureGetter.apply(PIPE_FLANGE),
                bakedTextureGetter.apply(PIPE_FLANGE_OUTER),
                bakedTextureGetter.apply(PIPE_FLANGE_INNER),
                bakedTextureGetter.apply(PIPE_FLANGE_OUTER_OUTPUT),
                bakedTextureGetter.apply(PIPE_FLANGE_INNER_OUTPUT),
                bakedTextureGetter.apply(PIPE_FLANGE_OUTER_INPUT),
                bakedTextureGetter.apply(PIPE_FLANGE_INNER_INPUT),
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

    // -----------------------------------------------------------------------
    // Baked model
    // -----------------------------------------------------------------------

    public static class PipeBakedModel implements IBakedModel {

        private final TextureAtlasSprite[] bodySprites;
        private final TextureAtlasSprite cap, flange;
        private final TextureAtlasSprite flangeOuter, flangeInner;
        private final TextureAtlasSprite flangeOuterOutput, flangeInnerOutput;
        private final TextureAtlasSprite flangeOuterInput,  flangeInnerInput;
        private final TextureAtlasSprite outEast, outWest, outNorth, outSouth;
        private final TextureAtlasSprite inEast,  inWest,  inNorth,  inSouth;

        private static final float CORE_MIN = 4f / 16f;
        private static final float CORE_MAX = 12f / 16f;
        private static final float CAP_MIN  = 3f / 16f;
        private static final float CAP_MAX  = 13f / 16f;
        private static final float CAP_W    = 2f / 16f;

        static final int BODY_TINT_INDEX = 0;
        static final int NO_TINT = -1;

        private static final int CON_NONE       = 0;
        private static final int CON_PIPE       = 1;
        private static final int CON_INV_OUTPUT = 2;
        private static final int CON_INV_INPUT  = 3;

        public PipeBakedModel(TextureAtlasSprite[] bodySprites, TextureAtlasSprite cap,
                              TextureAtlasSprite flange,
                              TextureAtlasSprite flangeOuter,        TextureAtlasSprite flangeInner,
                              TextureAtlasSprite flangeOuterOutput,  TextureAtlasSprite flangeInnerOutput,
                              TextureAtlasSprite flangeOuterInput,   TextureAtlasSprite flangeInnerInput,
                              TextureAtlasSprite outEast,  TextureAtlasSprite outWest,
                              TextureAtlasSprite outNorth, TextureAtlasSprite outSouth,
                              TextureAtlasSprite inEast,   TextureAtlasSprite inWest,
                              TextureAtlasSprite inNorth,  TextureAtlasSprite inSouth) {
            this.bodySprites = bodySprites;
            this.cap      = cap;
            this.flange   = flange;
            this.flangeOuter        = flangeOuter;
            this.flangeInner        = flangeInner;
            this.flangeOuterOutput  = flangeOuterOutput;
            this.flangeInnerOutput  = flangeInnerOutput;
            this.flangeOuterInput   = flangeOuterInput;
            this.flangeInnerInput   = flangeInnerInput;
            this.outEast  = outEast;  this.outWest  = outWest;
            this.outNorth = outNorth; this.outSouth = outSouth;
            this.inEast   = inEast;   this.inWest   = inWest;
            this.inNorth  = inNorth;  this.inSouth  = inSouth;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            if (side != null) return Collections.emptyList();

            List<BakedQuad> quads = new ArrayList<>();

            // ---- Inventory / item rendering (state == null) ----
            if (state == null) {
                TextureAtlasSprite body = bodySprites[0];
                addCube(quads, CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, body, BODY_TINT_INDEX);
                for (EnumFacing f : EnumFacing.VALUES) {
                    addArm(quads, f, body, BODY_TINT_INDEX);
                    addFlange(quads, f, CON_INV_OUTPUT, flange);
                }
                return quads;
            }

            // ---- In-world rendering — read connection values from extended state ----
            // Default to 0 (not connected) if extended state isn't available
            int north = 0, south = 0, east = 0, west = 0, up = 0, down = 0;
            BlockPos pos = null;

            if (state instanceof IExtendedBlockState) {
                IExtendedBlockState ext = (IExtendedBlockState) state;
                Integer n = ext.getValue(CON_NORTH); if (n != null) north = n;
                Integer s = ext.getValue(CON_SOUTH); if (s != null) south = s;
                Integer e = ext.getValue(CON_EAST);  if (e != null) east  = e;
                Integer w = ext.getValue(CON_WEST);  if (w != null) west  = w;
                Integer u = ext.getValue(CON_UP);    if (u != null) up    = u;
                Integer d = ext.getValue(CON_DOWN);  if (d != null) down  = d;
                pos = ext.getValue(BLOCK_POS);
            }

            int texBase = pos != null
                    ? Math.abs((pos.getX() * 73856093) ^ (pos.getY() * 19349663) ^ (pos.getZ() * 83492791))
                    : 0;

            TextureAtlasSprite bodyNorth = bodySprites[(texBase + 3)  % 20];
            TextureAtlasSprite bodySouth = bodySprites[(texBase + 7)  % 20];
            TextureAtlasSprite bodyEast  = bodySprites[(texBase + 11) % 20];
            TextureAtlasSprite bodyWest  = bodySprites[(texBase + 13) % 20];
            TextureAtlasSprite bodyUp    = bodySprites[(texBase + 17) % 20];
            TextureAtlasSprite bodyDown  = bodySprites[(texBase + 19) % 20];

            addCube(quads,
                    CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX,
                    bodyDown, bodyUp, bodyNorth, bodySouth, bodyWest, bodyEast,
                    BODY_TINT_INDEX);

            if (north > 0) { addArm(quads, EnumFacing.NORTH, bodyNorth, BODY_TINT_INDEX); addFlange(quads, EnumFacing.NORTH, north, flange); }
            if (south > 0) { addArm(quads, EnumFacing.SOUTH, bodySouth, BODY_TINT_INDEX); addFlange(quads, EnumFacing.SOUTH, south, flange); }
            if (east  > 0) { addArm(quads, EnumFacing.EAST,  bodyEast,  BODY_TINT_INDEX); addFlange(quads, EnumFacing.EAST,  east,  flange); }
            if (west  > 0) { addArm(quads, EnumFacing.WEST,  bodyWest,  BODY_TINT_INDEX); addFlange(quads, EnumFacing.WEST,  west,  flange); }
            if (up    > 0) { addArm(quads, EnumFacing.UP,    bodyUp,    BODY_TINT_INDEX); addFlange(quads, EnumFacing.UP,    up,    flange); }
            if (down  > 0) { addArm(quads, EnumFacing.DOWN,  bodyDown,  BODY_TINT_INDEX); addFlange(quads, EnumFacing.DOWN,  down,  flange); }

            return quads;
        }

        // ----------------------------------------------------------------
        // Arrow texture helpers
        // ----------------------------------------------------------------

        private TextureAtlasSprite getArrowTex(EnumFacing armDir, EnumFacing sideFace, boolean input) {
            EnumFacing texDir;
            switch (armDir) {
                case EAST:
                    switch (sideFace) {
                        case NORTH: texDir = EnumFacing.WEST;  break;
                        case SOUTH: texDir = EnumFacing.EAST;  break;
                        default:    texDir = EnumFacing.EAST;  break;
                    } break;
                case WEST:
                    switch (sideFace) {
                        case NORTH: texDir = EnumFacing.EAST;  break;
                        case SOUTH: texDir = EnumFacing.WEST;  break;
                        default:    texDir = EnumFacing.WEST;  break;
                    } break;
                case NORTH:
                    switch (sideFace) {
                        case EAST:  texDir = EnumFacing.EAST;  break;
                        case WEST:  texDir = EnumFacing.WEST;  break;
                        case UP:    texDir = EnumFacing.NORTH; break;
                        case DOWN:  texDir = EnumFacing.SOUTH; break;
                        default:    texDir = EnumFacing.NORTH; break;
                    } break;
                case SOUTH:
                    switch (sideFace) {
                        case EAST:  texDir = EnumFacing.WEST;  break;
                        case WEST:  texDir = EnumFacing.EAST;  break;
                        case UP:    texDir = EnumFacing.SOUTH; break;
                        case DOWN:  texDir = EnumFacing.NORTH; break;
                        default:    texDir = EnumFacing.SOUTH; break;
                    } break;
                case UP:
                    switch (sideFace) {
                        case NORTH: texDir = EnumFacing.SOUTH; break;
                        case SOUTH: texDir = EnumFacing.NORTH; break;
                        default:    texDir = EnumFacing.NORTH; break;
                    } break;
                case DOWN:
                    switch (sideFace) {
                        case NORTH: texDir = EnumFacing.SOUTH; break;
                        case SOUTH: texDir = EnumFacing.NORTH; break;
                        default:    texDir = EnumFacing.NORTH; break;
                    } break;
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

        // ----------------------------------------------------------------
        // Geometry helpers
        // ----------------------------------------------------------------

        private void addArm(List<BakedQuad> quads, EnumFacing dir,
                            TextureAtlasSprite body, int tint) {
            switch (dir) {
                case DOWN:  addCube(quads, CORE_MIN, 0,        CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX, body, tint); break;
                case UP:    addCube(quads, CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX, 1,        CORE_MAX, body, tint); break;
                case NORTH: addCube(quads, CORE_MIN, CORE_MIN, 0,        CORE_MAX, CORE_MAX, CORE_MIN, body, tint); break;
                case SOUTH: addCube(quads, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, 1,        body, tint); break;
                case WEST:  addCube(quads, 0,        CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, body, tint); break;
                case EAST:  addCube(quads, CORE_MAX, CORE_MIN, CORE_MIN, 1,        CORE_MAX, CORE_MAX, body, tint); break;
            }
        }

        private void addFlange(List<BakedQuad> quads, EnumFacing dir, int faceState, TextureAtlasSprite flange) {
            boolean isInventory = faceState == CON_INV_OUTPUT || faceState == CON_INV_INPUT;
            boolean isInput     = faceState == CON_INV_INPUT;
            // Pick outer/inner face textures based on connection type
            TextureAtlasSprite outer = isInventory ? (isInput ? flangeOuterInput  : flangeOuterOutput) : flangeOuter;
            TextureAtlasSprite inner = isInventory ? (isInput ? flangeInnerInput  : flangeInnerOutput) : flangeInner;

            switch (dir) {
                case DOWN: {
                    float x1=CAP_MIN, y1=0, z1=CAP_MIN, x2=CAP_MAX, y2=CAP_W, z2=CAP_MAX;
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.NORTH, isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.SOUTH, isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.WEST,  isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.EAST,  isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, inner, NO_TINT);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, outer, NO_TINT);
                    break;
                }
                case UP: {
                    float x1=CAP_MIN, y1=1-CAP_W, z1=CAP_MIN, x2=CAP_MAX, y2=1, z2=CAP_MAX;
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.NORTH, isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.SOUTH, isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.WEST,  isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.EAST,  isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, inner, NO_TINT);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, outer, NO_TINT);
                    break;
                }
                case NORTH: {
                    float x1=CAP_MIN, y1=CAP_MIN, z1=0, x2=CAP_MAX, y2=CAP_MAX, z2=CAP_W;
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.WEST,  isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.EAST,  isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, isInventory ? getArrowTex(dir, EnumFacing.DOWN,  isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.UP,    isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, inner, NO_TINT);
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, outer, NO_TINT);
                    break;
                }
                case SOUTH: {
                    float x1=CAP_MIN, y1=CAP_MIN, z1=1-CAP_W, x2=CAP_MAX, y2=CAP_MAX, z2=1;
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.WEST,  isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.EAST,  isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, isInventory ? getArrowTex(dir, EnumFacing.DOWN,  isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.UP,    isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, inner, NO_TINT);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, outer, NO_TINT);
                    break;
                }
                case WEST: {
                    float x1=0, y1=CAP_MIN, z1=CAP_MIN, x2=CAP_W, y2=CAP_MAX, z2=CAP_MAX;
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.NORTH, isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.SOUTH, isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, isInventory ? getArrowTex(dir, EnumFacing.DOWN,  isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.UP,    isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, inner, NO_TINT);
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, outer, NO_TINT);
                    break;
                }
                case EAST: {
                    float x1=1-CAP_W, y1=CAP_MIN, z1=CAP_MIN, x2=1, y2=CAP_MAX, z2=CAP_MAX;
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.NORTH, isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, isInventory ? getArrowTex(dir, EnumFacing.SOUTH, isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, isInventory ? getArrowTex(dir, EnumFacing.DOWN,  isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, isInventory ? getArrowTex(dir, EnumFacing.UP,    isInput) : flange, NO_TINT);
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, inner, NO_TINT);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, outer, NO_TINT);
                    break;
                }
            }
        }

        private void addCube(List<BakedQuad> quads, float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             TextureAtlasSprite s, int tint) {
            addCube(quads, x1, y1, z1, x2, y2, z2, s, s, s, s, s, s, tint);
        }

        private void addCube(List<BakedQuad> quads, float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             TextureAtlasSprite sDown,  TextureAtlasSprite sUp,
                             TextureAtlasSprite sNorth, TextureAtlasSprite sSouth,
                             TextureAtlasSprite sWest,  TextureAtlasSprite sEast,
                             int tint) {
            addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, sDown,  tint);
            addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, sUp,    tint);
            addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, sNorth, tint);
            addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, sSouth, tint);
            addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, sWest,  tint);
            addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, sEast,  tint);
        }

        private void addQuad(List<BakedQuad> quads, EnumFacing face,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             TextureAtlasSprite sprite, int tint) {
            int[] data = new int[28];
            float u0 = sprite.getMinU(), v0 = sprite.getMinV();
            float u1 = sprite.getMaxU(), v1 = sprite.getMaxV();
            putVertex(data, 0,  x1,y1,z1, u0,v1, face);
            putVertex(data, 7,  x2,y2,z2, u1,v1, face);
            putVertex(data, 14, x3,y3,z3, u1,v0, face);
            putVertex(data, 21, x4,y4,z4, u0,v0, face);
            quads.add(new BakedQuad(data, tint, face, sprite, true, DefaultVertexFormats.ITEM));
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
        @Override public TextureAtlasSprite getParticleTexture() { return bodySprites[0]; }
        @Override public ItemOverrideList getOverrides() { return ItemOverrideList.NONE; }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }
    }
}
