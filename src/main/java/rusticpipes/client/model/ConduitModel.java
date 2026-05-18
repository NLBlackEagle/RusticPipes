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
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.common.property.IUnlistedProperty;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Function;

public class ConduitModel implements IModel {

    public static final ConduitModel INSTANCE = new ConduitModel();

    // -----------------------------------------------------------------------
    // Unlisted properties
    // -----------------------------------------------------------------------

    public static final IUnlistedProperty<BlockPos> BLOCK_POS = unlisted("conduit_block_pos", BlockPos.class);

    /** Tier 0-3 — computed from buffer fill in BlockConduit.getExtendedState */
    public static final IUnlistedProperty<Integer> CONDUIT_TIER = unlisted("conduit_tier", Integer.class);

    public static final IUnlistedProperty<Integer> CON_NORTH = unlisted("conduit_con_north", Integer.class);
    public static final IUnlistedProperty<Integer> CON_SOUTH = unlisted("conduit_con_south", Integer.class);
    public static final IUnlistedProperty<Integer> CON_EAST  = unlisted("conduit_con_east",  Integer.class);
    public static final IUnlistedProperty<Integer> CON_WEST  = unlisted("conduit_con_west",  Integer.class);
    public static final IUnlistedProperty<Integer> CON_UP    = unlisted("conduit_con_up",    Integer.class);
    public static final IUnlistedProperty<Integer> CON_DOWN  = unlisted("conduit_con_down",  Integer.class);

    private static <T> IUnlistedProperty<T> unlisted(String name, Class<T> type) {
        return new IUnlistedProperty<T>() {
            @Override public String getName() { return name; }
            @Override public Class<T> getType() { return type; }
            @Override public boolean isValid(T value) { return true; }
            @Override public String valueToString(T value) { return value == null ? "null" : value.toString(); }
        };
    }

    public static IUnlistedProperty<Integer> getConProperty(EnumFacing face) {
        switch (face) {
            case NORTH: return CON_NORTH;
            case SOUTH: return CON_SOUTH;
            case EAST:  return CON_EAST;
            case WEST:  return CON_WEST;
            case UP:    return CON_UP;
            default:    return CON_DOWN;
        }
    }

    // -----------------------------------------------------------------------
    // Textures
    // -----------------------------------------------------------------------

    private static final ResourceLocation BODY  = new ResourceLocation("rusticpipes:blocks/conduit_body");
    private static final ResourceLocation CAP   = new ResourceLocation("rusticpipes:blocks/conduit_cap");
    private static final ResourceLocation SLOW  = new ResourceLocation("rusticpipes:blocks/conduit_slow");
    private static final ResourceLocation NORMAL= new ResourceLocation("rusticpipes:blocks/conduit_normal");
    private static final ResourceLocation FAST  = new ResourceLocation("rusticpipes:blocks/conduit_fast");
    private static final ResourceLocation TURBO = new ResourceLocation("rusticpipes:blocks/conduit_turbo");
    private static final ResourceLocation HYPER = new ResourceLocation("rusticpipes:blocks/conduit_hyper");
    private static final ResourceLocation ULTRA = new ResourceLocation("rusticpipes:blocks/conduit_ultra");

    @Override
    public Collection<ResourceLocation> getTextures() {
        return Arrays.asList(BODY, CAP, SLOW, NORMAL, FAST, TURBO, HYPER, ULTRA);
    }

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> getter) {
        return new ConduitBakedModel(
                getter.apply(BODY),  getter.apply(CAP),
                getter.apply(SLOW),  getter.apply(NORMAL),
                getter.apply(FAST),  getter.apply(TURBO),
                getter.apply(HYPER), getter.apply(ULTRA));
    }

    @Override public IModelState getDefaultState() { return TRSRTransformation.identity(); }
    @Override public Collection<ResourceLocation> getDependencies() { return Collections.emptyList(); }

    // -----------------------------------------------------------------------
    // Baked model
    // -----------------------------------------------------------------------

    public static class ConduitBakedModel implements IBakedModel {

        private final TextureAtlasSprite body, cap;
        private final TextureAtlasSprite tierSlow, tierNormal, tierFast, tierTurbo, tierHyper, tierUltra;

        private static final float CORE_MIN = 6f / 16f, CORE_MAX = 10f / 16f;
        private static final float FL_MIN = 3f / 16f,   FL_MAX = 13f / 16f;
        private static final float FL_W   = 2f / 16f,   FL_OFF = 2.5f / 16f;

        private static final int CON_NONE = 0, CON_CONDUIT = 1, CON_FE_SOURCE = 2, CON_PIPE_NET = 3;

        public ConduitBakedModel(TextureAtlasSprite body, TextureAtlasSprite cap,
                                 TextureAtlasSprite slow, TextureAtlasSprite normal,
                                 TextureAtlasSprite fast,  TextureAtlasSprite turbo,
                                 TextureAtlasSprite hyper, TextureAtlasSprite ultra) {
            this.body = body; this.cap = cap;
            this.tierSlow = slow; this.tierNormal = normal;
            this.tierFast = fast; this.tierTurbo = turbo;
            this.tierHyper = hyper; this.tierUltra = ultra;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state,
                                        @Nullable EnumFacing side, long rand) {
            if (side != null) return Collections.emptyList();
            List<BakedQuad> quads = new ArrayList<>();

            if (state == null) {
                addCube(quads, CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, body);
                addArm(quads, EnumFacing.EAST, body); addArm(quads, EnumFacing.WEST, body);
                addCap(quads, EnumFacing.EAST, CON_CONDUIT, 0);
                addCap(quads, EnumFacing.WEST, CON_CONDUIT, 0);
                return quads;
            }

            int tier = 0;
            int north = 0, south = 0, east = 0, west = 0, up = 0, down = 0;
            if (state instanceof IExtendedBlockState) {
                IExtendedBlockState ext = (IExtendedBlockState) state;
                Integer t = ext.getValue(CONDUIT_TIER); if (t != null) tier  = t;
                Integer n = ext.getValue(CON_NORTH);    if (n != null) north = n;
                Integer s = ext.getValue(CON_SOUTH);    if (s != null) south = s;
                Integer e = ext.getValue(CON_EAST);     if (e != null) east  = e;
                Integer w = ext.getValue(CON_WEST);     if (w != null) west  = w;
                Integer u = ext.getValue(CON_UP);       if (u != null) up    = u;
                Integer d = ext.getValue(CON_DOWN);     if (d != null) down  = d;
            }

            addCube(quads, CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, body);
            if (north > 0) { addArm(quads, EnumFacing.NORTH, body); addCap(quads, EnumFacing.NORTH, north, tier); }
            if (south > 0) { addArm(quads, EnumFacing.SOUTH, body); addCap(quads, EnumFacing.SOUTH, south, tier); }
            if (east  > 0) { addArm(quads, EnumFacing.EAST,  body); addCap(quads, EnumFacing.EAST,  east,  tier); }
            if (west  > 0) { addArm(quads, EnumFacing.WEST,  body); addCap(quads, EnumFacing.WEST,  west,  tier); }
            if (up    > 0) { addArm(quads, EnumFacing.UP,    body); addCap(quads, EnumFacing.UP,    up,    tier); }
            if (down  > 0) { addArm(quads, EnumFacing.DOWN,  body); addCap(quads, EnumFacing.DOWN,  down,  tier); }
            return quads;
        }

        private TextureAtlasSprite tierTex(int tier) {
            switch (tier) {
                case 3: return tierTurbo;
                case 2: return tierFast;
                case 1: return tierNormal;
                default: return tierSlow;
            }
        }

        private void addArm(List<BakedQuad> q, EnumFacing dir, TextureAtlasSprite b) {
            switch (dir) {
                case DOWN:  addCube(q, CORE_MIN,0,       CORE_MIN,CORE_MAX,CORE_MIN,CORE_MAX,b); break;
                case UP:    addCube(q, CORE_MIN,CORE_MAX,CORE_MIN,CORE_MAX,1,       CORE_MAX,b); break;
                case NORTH: addCube(q, CORE_MIN,CORE_MIN,0,       CORE_MAX,CORE_MAX,CORE_MIN,b); break;
                case SOUTH: addCube(q, CORE_MIN,CORE_MIN,CORE_MAX,CORE_MAX,CORE_MAX,1,       b); break;
                case WEST:  addCube(q, 0,       CORE_MIN,CORE_MIN,CORE_MIN,CORE_MAX,CORE_MAX,b); break;
                case EAST:  addCube(q, CORE_MAX,CORE_MIN,CORE_MIN,1,       CORE_MAX,CORE_MAX,b); break;
            }
        }

        private void addCap(List<BakedQuad> q, EnumFacing dir, int conType, int tier) {
            if (conType == CON_PIPE_NET) { addFlange(q, dir, tier); return; }
            float s = CORE_MIN, e = CORE_MAX;
            switch (dir) {
                case DOWN:  addQuad(q,EnumFacing.DOWN,  s,0,s,e,0,s,e,0,e,s,0,e,cap); break;
                case UP:    addQuad(q,EnumFacing.UP,    s,1,e,e,1,e,e,1,s,s,1,s,cap); break;
                case NORTH: addQuad(q,EnumFacing.NORTH, e,s,0,s,s,0,s,e,0,e,e,0,cap); break;
                case SOUTH: addQuad(q,EnumFacing.SOUTH, s,s,1,e,s,1,e,e,1,s,e,1,cap); break;
                case WEST:  addQuad(q,EnumFacing.WEST,  0,s,s,0,s,e,0,e,e,0,e,s,cap); break;
                case EAST:  addQuad(q,EnumFacing.EAST,  1,s,e,1,s,s,1,e,s,1,e,e,cap); break;
            }
        }

        private void addFlange(List<BakedQuad> q, EnumFacing dir, int tier) {
            TextureAtlasSprite s = tierTex(tier), f = cap;
            switch (dir) {
                case DOWN: { float x1=FL_MIN,y1=-0.001f,z1=FL_MIN,x2=FL_MAX,y2=FL_OFF+FL_W,z2=FL_MAX;
                    addQuad(q,EnumFacing.NORTH,x2,y1,z1,x1,y1,z1,x1,y2,z1,x2,y2,z1,s);
                    addQuad(q,EnumFacing.SOUTH,x1,y1,z2,x2,y1,z2,x2,y2,z2,x1,y2,z2,s);
                    addQuad(q,EnumFacing.WEST, x1,y1,z1,x1,y1,z2,x1,y2,z2,x1,y2,z1,s);
                    addQuad(q,EnumFacing.EAST, x2,y1,z2,x2,y1,z1,x2,y2,z1,x2,y2,z2,s);
                    addQuad(q,EnumFacing.DOWN, x1,y1,z1,x2,y1,z1,x2,y1,z2,x1,y1,z2,f);
                    addQuad(q,EnumFacing.UP,   x1,y2,z2,x2,y2,z2,x2,y2,z1,x1,y2,z1,f); break; }
                case UP: { float x1=FL_MIN,y1=1-FL_OFF-FL_W,z1=FL_MIN,x2=FL_MAX,y2=1.001f,z2=FL_MAX;
                    addQuad(q,EnumFacing.NORTH,x2,y1,z1,x1,y1,z1,x1,y2,z1,x2,y2,z1,s);
                    addQuad(q,EnumFacing.SOUTH,x1,y1,z2,x2,y1,z2,x2,y2,z2,x1,y2,z2,s);
                    addQuad(q,EnumFacing.WEST, x1,y1,z1,x1,y1,z2,x1,y2,z2,x1,y2,z1,s);
                    addQuad(q,EnumFacing.EAST, x2,y1,z2,x2,y1,z1,x2,y2,z1,x2,y2,z2,s);
                    addQuad(q,EnumFacing.DOWN, x1,y1,z1,x2,y1,z1,x2,y1,z2,x1,y1,z2,f);
                    addQuad(q,EnumFacing.UP,   x1,y2,z2,x2,y2,z2,x2,y2,z1,x1,y2,z1,f); break; }
                case NORTH: { float x1=FL_MIN,y1=FL_MIN,z1=-0.001f,x2=FL_MAX,y2=FL_MAX,z2=FL_OFF+FL_W;
                    addQuad(q,EnumFacing.WEST, x1,y1,z1,x1,y1,z2,x1,y2,z2,x1,y2,z1,s);
                    addQuad(q,EnumFacing.EAST, x2,y1,z2,x2,y1,z1,x2,y2,z1,x2,y2,z2,s);
                    addQuad(q,EnumFacing.DOWN, x1,y1,z1,x2,y1,z1,x2,y1,z2,x1,y1,z2,s);
                    addQuad(q,EnumFacing.UP,   x1,y2,z2,x2,y2,z2,x2,y2,z1,x1,y2,z1,s);
                    addQuad(q,EnumFacing.NORTH,x2,y1,z1,x1,y1,z1,x1,y2,z1,x2,y2,z1,f);
                    addQuad(q,EnumFacing.SOUTH,x1,y1,z2,x2,y1,z2,x2,y2,z2,x1,y2,z2,f); break; }
                case SOUTH: { float x1=FL_MIN,y1=FL_MIN,z1=1-FL_OFF-FL_W,x2=FL_MAX,y2=FL_MAX,z2=1.001f;
                    addQuad(q,EnumFacing.WEST, x1,y1,z1,x1,y1,z2,x1,y2,z2,x1,y2,z1,s);
                    addQuad(q,EnumFacing.EAST, x2,y1,z2,x2,y1,z1,x2,y2,z1,x2,y2,z2,s);
                    addQuad(q,EnumFacing.DOWN, x1,y1,z1,x2,y1,z1,x2,y1,z2,x1,y1,z2,s);
                    addQuad(q,EnumFacing.UP,   x1,y2,z2,x2,y2,z2,x2,y2,z1,x1,y2,z1,s);
                    addQuad(q,EnumFacing.NORTH,x2,y1,z1,x1,y1,z1,x1,y2,z1,x2,y2,z1,f);
                    addQuad(q,EnumFacing.SOUTH,x1,y1,z2,x2,y1,z2,x2,y2,z2,x1,y2,z2,f); break; }
                case WEST: { float x1=-0.001f,y1=FL_MIN,z1=FL_MIN,x2=FL_OFF+FL_W,y2=FL_MAX,z2=FL_MAX;
                    addQuad(q,EnumFacing.NORTH,x2,y1,z1,x1,y1,z1,x1,y2,z1,x2,y2,z1,s);
                    addQuad(q,EnumFacing.SOUTH,x1,y1,z2,x2,y1,z2,x2,y2,z2,x1,y2,z2,s);
                    addQuad(q,EnumFacing.DOWN, x1,y1,z1,x2,y1,z1,x2,y1,z2,x1,y1,z2,s);
                    addQuad(q,EnumFacing.UP,   x1,y2,z2,x2,y2,z2,x2,y2,z1,x1,y2,z1,s);
                    addQuad(q,EnumFacing.WEST, x1,y1,z1,x1,y1,z2,x1,y2,z2,x1,y2,z1,f);
                    addQuad(q,EnumFacing.EAST, x2,y1,z2,x2,y1,z1,x2,y2,z1,x2,y2,z2,f); break; }
                case EAST: { float x1=1-FL_OFF-FL_W,y1=FL_MIN,z1=FL_MIN,x2=1.001f,y2=FL_MAX,z2=FL_MAX;
                    addQuad(q,EnumFacing.NORTH,x2,y1,z1,x1,y1,z1,x1,y2,z1,x2,y2,z1,s);
                    addQuad(q,EnumFacing.SOUTH,x1,y1,z2,x2,y1,z2,x2,y2,z2,x1,y2,z2,s);
                    addQuad(q,EnumFacing.DOWN, x1,y1,z1,x2,y1,z1,x2,y1,z2,x1,y1,z2,s);
                    addQuad(q,EnumFacing.UP,   x1,y2,z2,x2,y2,z2,x2,y2,z1,x1,y2,z1,s);
                    addQuad(q,EnumFacing.WEST, x1,y1,z1,x1,y1,z2,x1,y2,z2,x1,y2,z1,f);
                    addQuad(q,EnumFacing.EAST, x2,y1,z2,x2,y1,z1,x2,y2,z1,x2,y2,z2,f); break; }
            }
        }

        private void addCube(List<BakedQuad> q, float x1,float y1,float z1,
                             float x2,float y2,float z2, TextureAtlasSprite s) {
            addQuad(q,EnumFacing.DOWN,  x1,y1,z1,x2,y1,z1,x2,y1,z2,x1,y1,z2,s);
            addQuad(q,EnumFacing.UP,    x1,y2,z2,x2,y2,z2,x2,y2,z1,x1,y2,z1,s);
            addQuad(q,EnumFacing.NORTH, x2,y1,z1,x1,y1,z1,x1,y2,z1,x2,y2,z1,s);
            addQuad(q,EnumFacing.SOUTH, x1,y1,z2,x2,y1,z2,x2,y2,z2,x1,y2,z2,s);
            addQuad(q,EnumFacing.WEST,  x1,y1,z1,x1,y1,z2,x1,y2,z2,x1,y2,z1,s);
            addQuad(q,EnumFacing.EAST,  x2,y1,z2,x2,y1,z1,x2,y2,z1,x2,y2,z2,s);
        }

        private void addQuad(List<BakedQuad> q, EnumFacing face,
                             float x1,float y1,float z1,float x2,float y2,float z2,
                             float x3,float y3,float z3,float x4,float y4,float z4,
                             TextureAtlasSprite sp) {
            int[] data = new int[28];
            putVertex(data, 0,  x1,y1,z1, sp.getMinU(),sp.getMaxV(), face);
            putVertex(data, 7,  x2,y2,z2, sp.getMaxU(),sp.getMaxV(), face);
            putVertex(data, 14, x3,y3,z3, sp.getMaxU(),sp.getMinV(), face);
            putVertex(data, 21, x4,y4,z4, sp.getMinU(),sp.getMinV(), face);
            q.add(new BakedQuad(data, -1, face, sp, true, DefaultVertexFormats.ITEM));
        }

        private void putVertex(int[] d, int i, float x,float y,float z,
                               float u,float v, EnumFacing face) {
            d[i]=Float.floatToRawIntBits(x); d[i+1]=Float.floatToRawIntBits(y);
            d[i+2]=Float.floatToRawIntBits(z); d[i+3]=0xFFFFFFFF;
            d[i+4]=Float.floatToRawIntBits(u); d[i+5]=Float.floatToRawIntBits(v);
            int nx=((int)(face.getXOffset()*127))&0xFF;
            int ny=((int)(face.getYOffset()*127))&0xFF;
            int nz=((int)(face.getZOffset()*127))&0xFF;
            d[i+6]=nx|(ny<<8)|(nz<<16);
        }

        @Override public boolean isAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean isBuiltInRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleTexture() { return body; }
        @Override public ItemOverrideList getOverrides() { return ItemOverrideList.NONE; }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }
    }
}
