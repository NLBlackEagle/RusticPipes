package rusticpipes.client.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;
import rusticpipes.block.PipeColor;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

/**
 * Fluid pipe model — fully self-contained, does NOT extend PipeModel.
 *
 * Uses PipeModel's static property instances (CON_NORTH etc.) directly
 * so they match what BlockFluidPipe registers in its ExtendedBlockState.
 *
 * Adds FLUID_COLOR as an extra unlisted property (registered in
 * BlockFluidPipe.createBlockState) to drive viewport rendering.
 * Every 3rd body sprite (30%) uses pipe_water_01 — a texture with a
 * transparent viewport hole. When fluid is flowing, a colored quad is
 * rendered just inside the pipe core, visible through that hole.
 */
public class FluidPipeModel implements IModel {

    public static final FluidPipeModel INSTANCE = new FluidPipeModel();

    // -----------------------------------------------------------------------
    // FLUID_COLOR — extra unlisted property, only on fluid pipes
    // -----------------------------------------------------------------------

    public static final IUnlistedProperty<Integer> FLUID_COLOR = new IUnlistedProperty<Integer>() {
        @Override public String getName()                   { return "fluid_color"; }
        @Override public Class<Integer> getType()           { return Integer.class; }
        @Override public boolean isValid(Integer value)     { return true; }
        @Override public String valueToString(Integer value){ return value.toString(); }
    };

    // -----------------------------------------------------------------------
    // Texture resource locations (fluid_pipes folder)
    // -----------------------------------------------------------------------

    private static final String ROOT = "rusticpipes:blocks/fluid_pipes/";

    private static final ResourceLocation PIPE_CAP                 = rl("pipe_cap");
    private static final ResourceLocation PIPE_FLANGE              = rl("pipe_flange");
    private static final ResourceLocation PIPE_FLANGE_OUTER        = rl("pipe_flange_outer");
    private static final ResourceLocation PIPE_FLANGE_INNER        = rl("pipe_flange_inner");
    private static final ResourceLocation PIPE_FLANGE_OUTER_OUTPUT = rl("pipe_flange_outer_output");
    private static final ResourceLocation PIPE_FLANGE_INNER_OUTPUT = rl("pipe_flange_inner_output");
    private static final ResourceLocation PIPE_FLANGE_OUTER_INPUT  = rl("pipe_flange_outer_input");
    private static final ResourceLocation PIPE_FLANGE_INNER_INPUT  = rl("pipe_flange_inner_input");
    private static final ResourceLocation PIPE_CAP_OUTPUT_EAST     = rl("pipe_cap_output_east");
    private static final ResourceLocation PIPE_CAP_OUTPUT_WEST     = rl("pipe_cap_output_west");
    private static final ResourceLocation PIPE_CAP_OUTPUT_NORTH    = rl("pipe_cap_output_north");
    private static final ResourceLocation PIPE_CAP_OUTPUT_SOUTH    = rl("pipe_cap_output_south");
    private static final ResourceLocation PIPE_CAP_INPUT_EAST      = rl("pipe_cap_input_east");
    private static final ResourceLocation PIPE_CAP_INPUT_WEST      = rl("pipe_cap_input_west");
    private static final ResourceLocation PIPE_CAP_INPUT_NORTH     = rl("pipe_cap_input_north");
    private static final ResourceLocation PIPE_CAP_INPUT_SOUTH     = rl("pipe_cap_input_south");
    private static final ResourceLocation PIPE_WATER               = rl("pipe_water_01");
    private static final ResourceLocation PIPE_BODY_INNER          = rl("pipe_body_inner");
    private static final ResourceLocation PIPE_VP_INNER            = rl("pipe_viewport_inner");

    private static ResourceLocation rl(String name) { return new ResourceLocation(ROOT + name); }

    // -----------------------------------------------------------------------
    // IModel
    // -----------------------------------------------------------------------

    @Override
    public Collection<ResourceLocation> getTextures() {
        List<ResourceLocation> textures = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            textures.add(new ResourceLocation(ROOT + "pipe_body_" + String.format("%02d", i)));
        }
        textures.add(PIPE_CAP);
        textures.add(PIPE_FLANGE);
        textures.add(PIPE_FLANGE_OUTER);        textures.add(PIPE_FLANGE_INNER);
        textures.add(PIPE_FLANGE_OUTER_OUTPUT); textures.add(PIPE_FLANGE_INNER_OUTPUT);
        textures.add(PIPE_FLANGE_OUTER_INPUT);  textures.add(PIPE_FLANGE_INNER_INPUT);
        textures.add(PIPE_CAP_OUTPUT_EAST);     textures.add(PIPE_CAP_OUTPUT_WEST);
        textures.add(PIPE_CAP_OUTPUT_NORTH);    textures.add(PIPE_CAP_OUTPUT_SOUTH);
        textures.add(PIPE_CAP_INPUT_EAST);      textures.add(PIPE_CAP_INPUT_WEST);
        textures.add(PIPE_CAP_INPUT_NORTH);     textures.add(PIPE_CAP_INPUT_SOUTH);
        textures.add(PIPE_WATER);
        textures.add(PIPE_BODY_INNER);
        textures.add(PIPE_VP_INNER);
        return textures;
    }

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> getter) {
        TextureAtlasSprite[] body = new TextureAtlasSprite[20];
        TextureAtlasSprite water = getter.apply(PIPE_WATER);
        for (int i = 0; i < 20; i++) {
            // All slots are solid — waterSprite is used separately via vp* flags
            body[i] = getter.apply(new ResourceLocation(ROOT + "pipe_body_" + String.format("%02d", i + 1)));
        }
        return new FluidPipeBakedModel(
                body, water,
                getter.apply(PIPE_CAP),
                getter.apply(PIPE_FLANGE),
                getter.apply(PIPE_FLANGE_OUTER),        getter.apply(PIPE_FLANGE_INNER),
                getter.apply(PIPE_FLANGE_OUTER_OUTPUT), getter.apply(PIPE_FLANGE_INNER_OUTPUT),
                getter.apply(PIPE_FLANGE_OUTER_INPUT),  getter.apply(PIPE_FLANGE_INNER_INPUT),
                getter.apply(PIPE_CAP_OUTPUT_EAST),     getter.apply(PIPE_CAP_OUTPUT_WEST),
                getter.apply(PIPE_CAP_OUTPUT_NORTH),    getter.apply(PIPE_CAP_OUTPUT_SOUTH),
                getter.apply(PIPE_CAP_INPUT_EAST),      getter.apply(PIPE_CAP_INPUT_WEST),
                getter.apply(PIPE_CAP_INPUT_NORTH),     getter.apply(PIPE_CAP_INPUT_SOUTH));
    }

    @Override public IModelState getDefaultState()              { return TRSRTransformation.identity(); }
    @Override public Collection<ResourceLocation> getDependencies() { return Collections.emptyList(); }

    // -----------------------------------------------------------------------
    // Baked model — fully standalone, no inheritance from PipeModel
    // -----------------------------------------------------------------------

    public static final class FluidPipeBakedModel implements IBakedModel {

        // Uses PipeModel's property INSTANCES — same objects BlockFluidPipe registers
        private static final IUnlistedProperty<BlockPos>  PROP_POS   = PipeModel.BLOCK_POS;
        private static final IUnlistedProperty<PipeColor> PROP_COLOR = PipeModel.PIPE_COLOR;
        private static final IUnlistedProperty<Integer>   PROP_N     = PipeModel.CON_NORTH;
        private static final IUnlistedProperty<Integer>   PROP_S     = PipeModel.CON_SOUTH;
        private static final IUnlistedProperty<Integer>   PROP_E     = PipeModel.CON_EAST;
        private static final IUnlistedProperty<Integer>   PROP_W     = PipeModel.CON_WEST;
        private static final IUnlistedProperty<Integer>   PROP_U     = PipeModel.CON_UP;
        private static final IUnlistedProperty<Integer>   PROP_D     = PipeModel.CON_DOWN;

        private static final float CORE_MIN = 4f / 16f;
        private static final float CORE_MAX = 12f / 16f;
        private static final float CAP_MIN  = 3f / 16f;
        private static final float CAP_MAX  = 13f / 16f;
        private static final float CAP_W    = 2f / 16f;

        private static final int NO_TINT        = -1;
        private static final int BODY_TINT      = 0;
        private static final int CON_NONE       = 0;
        private static final int CON_PIPE       = 1;
        private static final int CON_INV_OUTPUT = 2;
        private static final int CON_INV_INPUT  = 3;

        private final TextureAtlasSprite[] bodySprites; // 20 slots, every 3rd = water viewport
        private final TextureAtlasSprite waterSprite;
        private final TextureAtlasSprite cap, flange;
        private final TextureAtlasSprite flangeOuter, flangeInner;
        private final TextureAtlasSprite flangeOuterOut, flangeInnerOut;
        private final TextureAtlasSprite flangeOuterIn,  flangeInnerIn;
        private final TextureAtlasSprite outE, outW, outN, outS;
        private final TextureAtlasSprite inE,  inW,  inN,  inS;

        FluidPipeBakedModel(TextureAtlasSprite[] bodySprites, TextureAtlasSprite waterSprite,
                TextureAtlasSprite cap, TextureAtlasSprite flange,
                TextureAtlasSprite flangeOuter,   TextureAtlasSprite flangeInner,
                TextureAtlasSprite flangeOuterOut, TextureAtlasSprite flangeInnerOut,
                TextureAtlasSprite flangeOuterIn,  TextureAtlasSprite flangeInnerIn,
                TextureAtlasSprite outE, TextureAtlasSprite outW,
                TextureAtlasSprite outN, TextureAtlasSprite outS,
                TextureAtlasSprite inE,  TextureAtlasSprite inW,
                TextureAtlasSprite inN,  TextureAtlasSprite inS) {
            this.bodySprites   = bodySprites;
            this.waterSprite   = waterSprite;
            this.cap           = cap;
            this.flange        = flange;
            this.flangeOuter   = flangeOuter;    this.flangeInner   = flangeInner;
            this.flangeOuterOut = flangeOuterOut; this.flangeInnerOut = flangeInnerOut;
            this.flangeOuterIn  = flangeOuterIn;  this.flangeInnerIn  = flangeInnerIn;
            this.outE = outE; this.outW = outW; this.outN = outN; this.outS = outS;
            this.inE  = inE;  this.inW  = inW;  this.inN  = inN;  this.inS  = inS;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            if (side != null) return Collections.emptyList();
            List<BakedQuad> quads = new ArrayList<>();

            // ---- Inventory rendering ----
            if (state == null) {
                TextureAtlasSprite body = bodySprites[0];
                final float S = 0.8f, C = 0.5f, D = (1f - S) / 2f;
                final float sCORE_MIN = C - (C - CORE_MIN) * S;
                final float sCORE_MAX = C + (CORE_MAX - C) * S;
                final float sCAP_MIN  = C - (C - CAP_MIN)  * S;
                final float sCAP_MAX  = C + (CAP_MAX - C)  * S;
                final float sCAP_W    = CAP_W * S;
                final float EPS = 0.001f;
                addCube(quads, sCORE_MIN, sCORE_MIN, sCORE_MIN, sCORE_MAX, sCORE_MAX, sCORE_MAX,
                        body, body, waterSprite, waterSprite, waterSprite, waterSprite, BODY_TINT);
                addCube(quads, sCORE_MAX, sCORE_MIN, sCORE_MIN, 1f-D-EPS,  sCORE_MAX, sCORE_MAX, body, BODY_TINT);
                addCube(quads, D+EPS,     sCORE_MIN, sCORE_MIN, sCORE_MIN, sCORE_MAX, sCORE_MAX, body, BODY_TINT);
                float ex1=1f-D-sCAP_W, ey1=sCAP_MIN, ez1=sCAP_MIN, ex2=1f-D+0.001f, ey2=sCAP_MAX, ez2=sCAP_MAX;
                addQuad(quads, EnumFacing.NORTH, ex2,ey1,ez1, ex1,ey1,ez1, ex1,ey2,ez1, ex2,ey2,ez1, flange, NO_TINT);
                addQuad(quads, EnumFacing.SOUTH, ex1,ey1,ez2, ex2,ey1,ez2, ex2,ey2,ez2, ex1,ey2,ez2, flange, NO_TINT);
                addQuad(quads, EnumFacing.DOWN,  ex1,ey1,ez1, ex2,ey1,ez1, ex2,ey1,ez2, ex1,ey1,ez2, flange, NO_TINT);
                addQuad(quads, EnumFacing.UP,    ex1,ey2,ez2, ex2,ey2,ez2, ex2,ey2,ez1, ex1,ey2,ez1, flange, NO_TINT);
                addQuad(quads, EnumFacing.WEST,  ex1,ey1,ez1, ex1,ey1,ez2, ex1,ey2,ez2, ex1,ey2,ez1, flangeInner, NO_TINT);
                addQuad(quads, EnumFacing.EAST,  ex2,ey1,ez2, ex2,ey1,ez1, ex2,ey2,ez1, ex2,ey2,ez2, flangeOuter, NO_TINT);
                float wx1=D-0.001f, wy1=sCAP_MIN, wz1=sCAP_MIN, wx2=D+sCAP_W, wy2=sCAP_MAX, wz2=sCAP_MAX;
                addQuad(quads, EnumFacing.NORTH, wx2,wy1,wz1, wx1,wy1,wz1, wx1,wy2,wz1, wx2,wy2,wz1, flange, NO_TINT);
                addQuad(quads, EnumFacing.SOUTH, wx1,wy1,wz2, wx2,wy1,wz2, wx2,wy2,wz2, wx1,wy2,wz2, flange, NO_TINT);
                addQuad(quads, EnumFacing.DOWN,  wx1,wy1,wz1, wx2,wy1,wz1, wx2,wy1,wz2, wx1,wy1,wz2, flange, NO_TINT);
                addQuad(quads, EnumFacing.UP,    wx1,wy2,wz2, wx2,wy2,wz2, wx2,wy2,wz1, wx1,wy2,wz1, flange, NO_TINT);
                addQuad(quads, EnumFacing.EAST,  wx2,wy1,wz2, wx2,wy1,wz1, wx2,wy2,wz1, wx2,wy2,wz2, flangeInner, NO_TINT);
                addQuad(quads, EnumFacing.WEST,  wx1,wy1,wz1, wx1,wy1,wz2, wx1,wy2,wz2, wx1,wy2,wz1, flangeOuter, NO_TINT);
                return quads;
            }

            // ---- In-world rendering ----
            int north = 0, south = 0, east = 0, west = 0, up = 0, down = 0;
            int fluidColor = 0;
            BlockPos pos = null;

            if (state instanceof IExtendedBlockState) {
                IExtendedBlockState ext = (IExtendedBlockState) state;
                Integer n = ext.getValue(PROP_N); if (n != null) north = n;
                Integer s = ext.getValue(PROP_S); if (s != null) south = s;
                Integer e = ext.getValue(PROP_E); if (e != null) east  = e;
                Integer w = ext.getValue(PROP_W); if (w != null) west  = w;
                Integer u = ext.getValue(PROP_U); if (u != null) up    = u;
                Integer d = ext.getValue(PROP_D); if (d != null) down  = d;
                pos = ext.getValue(PROP_POS);
                Integer fc = ext.getValue(FluidPipeModel.FLUID_COLOR);
                if (fc != null) fluidColor = fc;
            }

            int texBase = pos != null
                    ? Math.abs((pos.getX() * 73856093) ^ (pos.getY() * 19349663) ^ (pos.getZ() * 83492791))
                    : 0;

            // Per-face viewport: 50% of N/S/E/W faces show pipe_water_01, but only
            // when that face isn't connected to a neighbor — connected faces get an
            // arm instead and must never be eligible for a viewport.
            // Top/bottom are never viewports.
            int idxN = (texBase + 3)  % 20;
            int idxS = (texBase + 7)  % 20;
            int idxE = (texBase + 11) % 20;
            int idxW = (texBase + 13) % 20;
            int idxU = (texBase + 17) % 20;
            int idxD = (texBase + 19) % 20;

            boolean vpN = north == 0 && idxN % 10 < 5;
            boolean vpS = south == 0 && idxS % 10 < 5;
            boolean vpE = east  == 0 && idxE % 10 < 5;
            boolean vpW = west  == 0 && idxW % 10 < 5;

            // Core face sprites — viewport faces use waterSprite, arms always solid
            TextureAtlasSprite bodyN = vpN ? waterSprite : bodySprites[idxN];
            TextureAtlasSprite bodyS = vpS ? waterSprite : bodySprites[idxS];
            TextureAtlasSprite bodyE = vpE ? waterSprite : bodySprites[idxE];
            TextureAtlasSprite bodyW = vpW ? waterSprite : bodySprites[idxW];
            TextureAtlasSprite bodyU = bodySprites[idxU];
            TextureAtlasSprite bodyD = bodySprites[idxD];
            // Arm sprites — always solid, bodySprites never contains waterSprite
            TextureAtlasSprite solidN = bodySprites[idxN];
            TextureAtlasSprite solidS = bodySprites[idxS];
            TextureAtlasSprite solidE = bodySprites[idxE];
            TextureAtlasSprite solidW = bodySprites[idxW];

            BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
            boolean isCutout = layer == BlockRenderLayer.CUTOUT_MIPPED;
            boolean isSolid  = layer == null || layer == BlockRenderLayer.SOLID;

            if (isSolid) {
                // SOLID pass — skip viewport faces AND faces hidden by connection arms
                TextureAtlasSprite sN = vpN ? null : bodySprites[idxN];
                TextureAtlasSprite sS = vpS ? null : bodySprites[idxS];
                TextureAtlasSprite sE = vpE ? null : bodySprites[idxE];
                TextureAtlasSprite sW = vpW ? null : bodySprites[idxW];
                if (sN != null && north == 0) addQuad(quads, EnumFacing.NORTH, CORE_MAX,CORE_MIN,CORE_MIN, CORE_MIN,CORE_MIN,CORE_MIN, CORE_MIN,CORE_MAX,CORE_MIN, CORE_MAX,CORE_MAX,CORE_MIN, sN, BODY_TINT);
                if (sS != null && south == 0) addQuad(quads, EnumFacing.SOUTH, CORE_MIN,CORE_MIN,CORE_MAX, CORE_MAX,CORE_MIN,CORE_MAX, CORE_MAX,CORE_MAX,CORE_MAX, CORE_MIN,CORE_MAX,CORE_MAX, sS, BODY_TINT);
                if (sE != null && east  == 0) addQuad(quads, EnumFacing.EAST,  CORE_MAX,CORE_MIN,CORE_MAX, CORE_MAX,CORE_MIN,CORE_MIN, CORE_MAX,CORE_MAX,CORE_MIN, CORE_MAX,CORE_MAX,CORE_MAX, sE, BODY_TINT);
                if (sW != null && west  == 0) addQuad(quads, EnumFacing.WEST,  CORE_MIN,CORE_MIN,CORE_MIN, CORE_MIN,CORE_MIN,CORE_MAX, CORE_MIN,CORE_MAX,CORE_MAX, CORE_MIN,CORE_MAX,CORE_MIN, sW, BODY_TINT);
                if (up   == 0) addQuad(quads, EnumFacing.UP,   CORE_MIN,CORE_MAX,CORE_MAX, CORE_MAX,CORE_MAX,CORE_MAX, CORE_MAX,CORE_MAX,CORE_MIN, CORE_MIN,CORE_MAX,CORE_MIN, bodyU, BODY_TINT);
                if (down == 0) addQuad(quads, EnumFacing.DOWN, CORE_MIN,CORE_MIN,CORE_MIN, CORE_MAX,CORE_MIN,CORE_MIN, CORE_MAX,CORE_MIN,CORE_MAX, CORE_MIN,CORE_MIN,CORE_MAX, bodyD, BODY_TINT);
            }

            if (isCutout) {
                // CUTOUT_MIPPED pass — viewport faces only (skip if hidden by connection arm)
                if (vpN && north == 0) addQuad(quads, EnumFacing.NORTH, CORE_MAX,CORE_MIN,CORE_MIN, CORE_MIN,CORE_MIN,CORE_MIN, CORE_MIN,CORE_MAX,CORE_MIN, CORE_MAX,CORE_MAX,CORE_MIN, waterSprite, BODY_TINT);
                if (vpS && south == 0) addQuad(quads, EnumFacing.SOUTH, CORE_MIN,CORE_MIN,CORE_MAX, CORE_MAX,CORE_MIN,CORE_MAX, CORE_MAX,CORE_MAX,CORE_MAX, CORE_MIN,CORE_MAX,CORE_MAX, waterSprite, BODY_TINT);
                if (vpE && east  == 0) addQuad(quads, EnumFacing.EAST,  CORE_MAX,CORE_MIN,CORE_MAX, CORE_MAX,CORE_MIN,CORE_MIN, CORE_MAX,CORE_MAX,CORE_MIN, CORE_MAX,CORE_MAX,CORE_MAX, waterSprite, BODY_TINT);
                if (vpW && west  == 0) addQuad(quads, EnumFacing.WEST,  CORE_MIN,CORE_MIN,CORE_MIN, CORE_MIN,CORE_MIN,CORE_MAX, CORE_MIN,CORE_MAX,CORE_MAX, CORE_MIN,CORE_MAX,CORE_MIN, waterSprite, BODY_TINT);
                // Fluid color quad just inside viewport faces when fluid flowing
                if (fluidColor != 0) {
                    int packedColor = 0xFF000000 | (fluidColor & 0x00FFFFFF);
                    float fi = CORE_MIN + 0.005f, fa = CORE_MAX - 0.005f;
                    if (vpN && north == 0) addFluidQuad(quads, EnumFacing.NORTH, fi, fa, waterSprite, packedColor);
                    if (vpS && south == 0) addFluidQuad(quads, EnumFacing.SOUTH, fi, fa, waterSprite, packedColor);
                    if (vpE && east  == 0) addFluidQuad(quads, EnumFacing.EAST,  fi, fa, waterSprite, packedColor);
                    if (vpW && west  == 0) addFluidQuad(quads, EnumFacing.WEST,  fi, fa, waterSprite, packedColor);
                }
            }

            if (!isSolid) return quads;
            // Arms always use solid sprites — never the viewport texture
            if (north > 0) { addArm(quads, EnumFacing.NORTH, solidN); addFlange(quads, EnumFacing.NORTH, north); }
            if (south > 0) { addArm(quads, EnumFacing.SOUTH, solidS); addFlange(quads, EnumFacing.SOUTH, south); }
            if (east  > 0) { addArm(quads, EnumFacing.EAST,  solidE); addFlange(quads, EnumFacing.EAST,  east); }
            if (west  > 0) { addArm(quads, EnumFacing.WEST,  solidW); addFlange(quads, EnumFacing.WEST,  west); }
            if (up    > 0) { addArm(quads, EnumFacing.UP,    bodyU);  addFlange(quads, EnumFacing.UP,    up); }
            if (down  > 0) { addArm(quads, EnumFacing.DOWN,  bodyD);  addFlange(quads, EnumFacing.DOWN,  down); }

            return quads;
        }

        // -----------------------------------------------------------------------
        // Fluid quad — colored quad just inside the pipe core on a viewport face
        // -----------------------------------------------------------------------

        private void addFluidQuad(List<BakedQuad> quads, EnumFacing face,
                                  float fi, float fa, TextureAtlasSprite s, int color) {
            switch (face) {
                case NORTH: addColoredQuad(quads, face, fa,fi,fi+0.001f, fi,fi,fi+0.001f, fi,fa,fi+0.001f, fa,fa,fi+0.001f, s, color); break;
                case SOUTH: addColoredQuad(quads, face, fi,fi,fa-0.001f, fa,fi,fa-0.001f, fa,fa,fa-0.001f, fi,fa,fa-0.001f, s, color); break;
                case EAST:  addColoredQuad(quads, face, fa-0.001f,fi,fa, fa-0.001f,fi,fi, fa-0.001f,fa,fi, fa-0.001f,fa,fa, s, color); break;
                case WEST:  addColoredQuad(quads, face, fi+0.001f,fi,fi, fi+0.001f,fi,fa, fi+0.001f,fa,fa, fi+0.001f,fa,fi, s, color); break;
                case UP:    addColoredQuad(quads, face, fi,fa-0.001f,fa, fa,fa-0.001f,fa, fa,fa-0.001f,fi, fi,fa-0.001f,fi, s, color); break;
                case DOWN:  addColoredQuad(quads, face, fi,fi+0.001f,fi, fa,fi+0.001f,fi, fa,fi+0.001f,fa, fi,fi+0.001f,fa, s, color); break;
            }
        }

        private void addColoredQuad(List<BakedQuad> quads, EnumFacing face,
                float x1, float y1, float z1, float x2, float y2, float z2,
                float x3, float y3, float z3, float x4, float y4, float z4,
                TextureAtlasSprite s, int color) {
            int[] data = new int[28];
            float u0 = s.getMinU(), v0 = s.getMinV(), u1 = s.getMaxU(), v1 = s.getMaxV();
            putVertex(data, 0,  x1,y1,z1, u0,v1, face, color);
            putVertex(data, 7,  x2,y2,z2, u1,v1, face, color);
            putVertex(data, 14, x3,y3,z3, u1,v0, face, color);
            putVertex(data, 21, x4,y4,z4, u0,v0, face, color);
            quads.add(new BakedQuad(data, NO_TINT, face, s, true, DefaultVertexFormats.ITEM));
        }

        private void putVertex(int[] data, int i, float x, float y, float z,
                               float u, float v, EnumFacing face, int color) {
            data[i]   = Float.floatToRawIntBits(x);
            data[i+1] = Float.floatToRawIntBits(y);
            data[i+2] = Float.floatToRawIntBits(z);
            data[i+3] = color;
            data[i+4] = Float.floatToRawIntBits(u);
            data[i+5] = Float.floatToRawIntBits(v);
            data[i+6] = packNormal(face);
        }

        // -----------------------------------------------------------------------
        // Geometry helpers — duplicated from PipeModel (no inheritance)
        // -----------------------------------------------------------------------

        private void addArm(List<BakedQuad> quads, EnumFacing dir, TextureAtlasSprite body) {
            // Skip the inner face (junction with core) — it is always hidden inside the core geometry
            float x1, y1, z1, x2, y2, z2;
            EnumFacing skip; // the face to omit
            switch (dir) {
                case DOWN:  x1=CORE_MIN; y1=0.001f;   z1=CORE_MIN; x2=CORE_MAX; y2=CORE_MIN;  z2=CORE_MAX; skip=EnumFacing.UP;    break;
                case UP:    x1=CORE_MIN; y1=CORE_MAX; z1=CORE_MIN; x2=CORE_MAX; y2=0.999f;    z2=CORE_MAX; skip=EnumFacing.DOWN;  break;
                case NORTH: x1=CORE_MIN; y1=CORE_MIN; z1=0.001f;   x2=CORE_MAX; y2=CORE_MAX;  z2=CORE_MIN; skip=EnumFacing.SOUTH; break;
                case SOUTH: x1=CORE_MIN; y1=CORE_MIN; z1=CORE_MAX; x2=CORE_MAX; y2=CORE_MAX;  z2=0.999f;   skip=EnumFacing.NORTH; break;
                case WEST:  x1=0.001f;   y1=CORE_MIN; z1=CORE_MIN; x2=CORE_MIN; y2=CORE_MAX;  z2=CORE_MAX; skip=EnumFacing.EAST;  break;
                case EAST:  x1=CORE_MAX; y1=CORE_MIN; z1=CORE_MIN; x2=0.999f;   y2=CORE_MAX;  z2=CORE_MAX; skip=EnumFacing.WEST;  break;
                default: return;
            }
            addCubeSkip(quads, x1, y1, z1, x2, y2, z2, body, BODY_TINT, skip);
        }

        /** Returns the display transform for pipe items in each perspective context. */
        private static TRSRTransformation pipeTransform(ItemCameraTransforms.TransformType type) {
            javax.vecmath.Vector3f t = new javax.vecmath.Vector3f(0, 0, 0);
            javax.vecmath.Vector3f r = new javax.vecmath.Vector3f(0, 0, 0);
            float s;
            switch (type) {
                case FIRST_PERSON_RIGHT_HAND: r = new javax.vecmath.Vector3f(0, 45, 0);   s = 0.40f; break;
                case FIRST_PERSON_LEFT_HAND:  r = new javax.vecmath.Vector3f(0, 225, 0);  s = 0.40f; break;
                case THIRD_PERSON_RIGHT_HAND:
                case THIRD_PERSON_LEFT_HAND:  r = new javax.vecmath.Vector3f(75, 45, 0);
                    t = new javax.vecmath.Vector3f(0, 0.15625f, 0); s = 0.375f; break;
                case GUI:                     r = new javax.vecmath.Vector3f(30, 225, 0); s = 0.625f; break;
                case GROUND:                  t = new javax.vecmath.Vector3f(0, 0.1875f, 0); s = 0.25f; break;
                default:                      s = 0.5f; break;
            }
            return new TRSRTransformation(
                t,
                TRSRTransformation.quatFromXYZDegrees(r),
                new javax.vecmath.Vector3f(s, s, s),
                null
            );
        }

        private void addCubeSkip(List<BakedQuad> quads, float x1, float y1, float z1,
                                  float x2, float y2, float z2,
                                  TextureAtlasSprite s, int tint, EnumFacing skip) {
            if (skip != EnumFacing.DOWN)  addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, s, tint);
            if (skip != EnumFacing.UP)    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, s, tint);
            if (skip != EnumFacing.NORTH) addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, s, tint);
            if (skip != EnumFacing.SOUTH) addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, s, tint);
            if (skip != EnumFacing.WEST)  addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, s, tint);
            if (skip != EnumFacing.EAST)  addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, s, tint);
        }

        private void addFlange(List<BakedQuad> quads, EnumFacing dir, int faceState) {
            boolean isInv   = faceState == CON_INV_OUTPUT || faceState == CON_INV_INPUT;
            boolean isInput = faceState == CON_INV_INPUT;
            TextureAtlasSprite outer = isInv ? (isInput ? flangeOuterIn  : flangeOuterOut) : flangeOuter;
            TextureAtlasSprite inner = isInv ? (isInput ? flangeInnerIn  : flangeInnerOut) : flangeInner;
            switch (dir) {
                case DOWN: {
                    float x1=CAP_MIN,y1=-0.001f,z1=CAP_MIN,x2=CAP_MAX,y2=CAP_W,z2=CAP_MAX;
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, isInv ? getArrow(dir,EnumFacing.NORTH,isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, isInv ? getArrow(dir,EnumFacing.SOUTH,isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, isInv ? getArrow(dir,EnumFacing.WEST, isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, isInv ? getArrow(dir,EnumFacing.EAST, isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, inner, NO_TINT);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, outer, NO_TINT);
                    break;
                }
                case UP: {
                    float x1=CAP_MIN,y1=1-CAP_W,z1=CAP_MIN,x2=CAP_MAX,y2=1+0.001f,z2=CAP_MAX;
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, isInv ? getArrow(dir,EnumFacing.NORTH,isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, isInv ? getArrow(dir,EnumFacing.SOUTH,isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, isInv ? getArrow(dir,EnumFacing.WEST, isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, isInv ? getArrow(dir,EnumFacing.EAST, isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, inner, NO_TINT);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, outer, NO_TINT);
                    break;
                }
                case NORTH: {
                    float x1=CAP_MIN,y1=CAP_MIN,z1=-0.001f,x2=CAP_MAX,y2=CAP_MAX,z2=CAP_W;
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, isInv ? getArrow(dir,EnumFacing.WEST, isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, isInv ? getArrow(dir,EnumFacing.EAST, isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, isInv ? getArrow(dir,EnumFacing.DOWN, isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, isInv ? getArrow(dir,EnumFacing.UP,   isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, inner, NO_TINT);
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, outer, NO_TINT);
                    break;
                }
                case SOUTH: {
                    float x1=CAP_MIN,y1=CAP_MIN,z1=1-CAP_W,x2=CAP_MAX,y2=CAP_MAX,z2=1+0.001f;
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, isInv ? getArrow(dir,EnumFacing.WEST, isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, isInv ? getArrow(dir,EnumFacing.EAST, isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, isInv ? getArrow(dir,EnumFacing.DOWN, isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, isInv ? getArrow(dir,EnumFacing.UP,   isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, inner, NO_TINT);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, outer, NO_TINT);
                    break;
                }
                case WEST: {
                    float x1=-0.001f,y1=CAP_MIN,z1=CAP_MIN,x2=CAP_W,y2=CAP_MAX,z2=CAP_MAX;
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, isInv ? getArrow(dir,EnumFacing.NORTH,isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, isInv ? getArrow(dir,EnumFacing.SOUTH,isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, isInv ? getArrow(dir,EnumFacing.DOWN, isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, isInv ? getArrow(dir,EnumFacing.UP,   isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, inner, NO_TINT);
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, outer, NO_TINT);
                    break;
                }
                case EAST: {
                    float x1=1-CAP_W,y1=CAP_MIN,z1=CAP_MIN,x2=1+0.001f,y2=CAP_MAX,z2=CAP_MAX;
                    addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, isInv ? getArrow(dir,EnumFacing.NORTH,isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, isInv ? getArrow(dir,EnumFacing.SOUTH,isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, isInv ? getArrow(dir,EnumFacing.DOWN, isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, isInv ? getArrow(dir,EnumFacing.UP,   isInput):flange, NO_TINT);
                    addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, inner, NO_TINT);
                    addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, outer, NO_TINT);
                    break;
                }
            }
        }

        private TextureAtlasSprite getArrow(EnumFacing arm, EnumFacing side, boolean input) {
            EnumFacing tex;
            switch (arm) {
                case EAST:  tex = (side == EnumFacing.NORTH) ? EnumFacing.WEST  : EnumFacing.EAST;  break;
                case WEST:  tex = (side == EnumFacing.NORTH) ? EnumFacing.EAST  : EnumFacing.WEST;  break;
                case NORTH: switch (side) { case EAST: tex=EnumFacing.EAST; break; case WEST: tex=EnumFacing.WEST; break; case UP: tex=EnumFacing.NORTH; break; default: tex=EnumFacing.SOUTH; } break;
                case SOUTH: switch (side) { case EAST: tex=EnumFacing.WEST; break; case WEST: tex=EnumFacing.EAST; break; case UP: tex=EnumFacing.SOUTH; break; default: tex=EnumFacing.NORTH; } break;
                case UP:
                    // All side faces: NORTH → outN = upward (✓ OUTPUT), inN = downward (✓ INPUT)
                    tex = EnumFacing.NORTH;
                    break;
                case DOWN:
                    // All side faces: SOUTH → outS = downward (✓ OUTPUT), inS = upward (✓ INPUT)
                    tex = EnumFacing.SOUTH;
                    break;
                default:    tex = EnumFacing.EAST;
            }
            if (input) { switch (tex) { case EAST: return inE; case WEST: return inW; case NORTH: return inN; default: return inS; } }
            else       { switch (tex) { case EAST: return outE; case WEST: return outW; case NORTH: return outN; default: return outS; } }
        }

        private void addCube(List<BakedQuad> quads, float x1, float y1, float z1,
                             float x2, float y2, float z2, TextureAtlasSprite s, int tint) {
            addCube(quads, x1,y1,z1, x2,y2,z2, s,s,s,s,s,s, tint);
        }

        private void addCube(List<BakedQuad> quads, float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             TextureAtlasSprite sD, TextureAtlasSprite sU,
                             TextureAtlasSprite sN, TextureAtlasSprite sS,
                             TextureAtlasSprite sW, TextureAtlasSprite sE, int tint) {
            addQuad(quads, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, sD, tint);
            addQuad(quads, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, sU, tint);
            addQuad(quads, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, sN, tint);
            addQuad(quads, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, sS, tint);
            addQuad(quads, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, sW, tint);
            addQuad(quads, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, sE, tint);
        }

        private void addQuad(List<BakedQuad> quads, EnumFacing face,
                float x1,float y1,float z1, float x2,float y2,float z2,
                float x3,float y3,float z3, float x4,float y4,float z4,
                TextureAtlasSprite s, int tint) {
            int[] data = new int[28];
            float u0=s.getMinU(), v0=s.getMinV(), u1=s.getMaxU(), v1=s.getMaxV();
            putVertex(data, 0,  x1,y1,z1, u0,v1, face, 0xFFFFFFFF);
            putVertex(data, 7,  x2,y2,z2, u1,v1, face, 0xFFFFFFFF);
            putVertex(data, 14, x3,y3,z3, u1,v0, face, 0xFFFFFFFF);
            putVertex(data, 21, x4,y4,z4, u0,v0, face, 0xFFFFFFFF);
            quads.add(new BakedQuad(data, tint, face, s, true, DefaultVertexFormats.ITEM));
        }

        private int packNormal(EnumFacing face) {
            int x = ((int)(face.getXOffset() * 127)) & 0xFF;
            int y = ((int)(face.getYOffset() * 127)) & 0xFF;
            int z = ((int)(face.getZOffset() * 127)) & 0xFF;
            return x | (y << 8) | (z << 16);
        }

        @Override public boolean isAmbientOcclusion()  { return false; }
        @Override public boolean isGui3d()              { return true; }
        @Override public boolean isBuiltInRenderer()    { return false; }
        @Override public TextureAtlasSprite getParticleTexture() { return bodySprites[0]; }
        @Override public ItemOverrideList getOverrides()         { return ItemOverrideList.NONE; }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }

        @Override
        public org.apache.commons.lang3.tuple.Pair<? extends net.minecraft.client.renderer.block.model.IBakedModel, javax.vecmath.Matrix4f>
                handlePerspective(ItemCameraTransforms.TransformType type) {
            return org.apache.commons.lang3.tuple.Pair.of(this, pipeTransform(type).getMatrix());
        }
    }
}
