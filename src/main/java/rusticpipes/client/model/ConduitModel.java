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

    public static final IUnlistedProperty<BlockPos> BLOCK_POS    = unlisted("conduit_block_pos", BlockPos.class);
    public static final IUnlistedProperty<Integer>  CONDUIT_TIER = unlisted("conduit_tier",      Integer.class);
    public static final IUnlistedProperty<Integer>  CON_NORTH    = unlisted("conduit_con_north", Integer.class);
    public static final IUnlistedProperty<Integer>  CON_SOUTH    = unlisted("conduit_con_south", Integer.class);
    public static final IUnlistedProperty<Integer>  CON_EAST     = unlisted("conduit_con_east",  Integer.class);
    public static final IUnlistedProperty<Integer>  CON_WEST     = unlisted("conduit_con_west",  Integer.class);
    public static final IUnlistedProperty<Integer>  CON_UP       = unlisted("conduit_con_up",    Integer.class);
    public static final IUnlistedProperty<Integer>  CON_DOWN     = unlisted("conduit_con_down",  Integer.class);
    public static final IUnlistedProperty<Boolean>  POWERED      = unlisted("conduit_powered",   Boolean.class);

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
            case NORTH: return CON_NORTH; case SOUTH: return CON_SOUTH;
            case EAST:  return CON_EAST;  case WEST:  return CON_WEST;
            case UP:    return CON_UP;    default:    return CON_DOWN;
        }
    }

    // -----------------------------------------------------------------------
    // Textures
    // -----------------------------------------------------------------------

    private static final ResourceLocation BODY       = new ResourceLocation("rusticpipes:blocks/conduit/conduit_body");
    private static final ResourceLocation CAP        = new ResourceLocation("rusticpipes:blocks/conduit/conduit_cap");
    private static final ResourceLocation SLOW       = new ResourceLocation("rusticpipes:blocks/motors/conduit_buffer_slow_on");
    private static final ResourceLocation NORMAL     = new ResourceLocation("rusticpipes:blocks/motors/conduit_buffer_normal_on");
    private static final ResourceLocation FAST       = new ResourceLocation("rusticpipes:blocks/motors/conduit_buffer_fast_on");
    private static final ResourceLocation TURBO      = new ResourceLocation("rusticpipes:blocks/motors/conduit_buffer_turbo_on");
    private static final ResourceLocation HYPER      = new ResourceLocation("rusticpipes:blocks/motors/conduit_buffer_hyper_on");
    private static final ResourceLocation ULTRA      = new ResourceLocation("rusticpipes:blocks/motors/conduit_buffer_ultra_on");
    // Connector collar textures
    private static final ResourceLocation CON_FACE   = new ResourceLocation("rusticpipes:blocks/conduit/conduit_connector_face");
    private static final ResourceLocation CON_UP_T   = new ResourceLocation("rusticpipes:blocks/conduit/conduit_connector_up");
    private static final ResourceLocation CON_DOWN_T = new ResourceLocation("rusticpipes:blocks/conduit/conduit_connector_down");
    private static final ResourceLocation CON_ONE    = new ResourceLocation("rusticpipes:blocks/conduit/conduit_connector_one");
    private static final ResourceLocation CON_ONE_OFF  = new ResourceLocation("rusticpipes:blocks/conduit/conduit_connector_one_off");
    private static final ResourceLocation CON_TWO      = new ResourceLocation("rusticpipes:blocks/conduit/conduit_connector_two");
    private static final ResourceLocation CON_TWO_OFF  = new ResourceLocation("rusticpipes:blocks/conduit/conduit_connector_two_off");
    private static final ResourceLocation CON_UP_T_OFF  = new ResourceLocation("rusticpipes:blocks/conduit/conduit_connector_up_off");
    private static final ResourceLocation CON_DOWN_T_OFF = new ResourceLocation("rusticpipes:blocks/conduit/conduit_connector_down_off");

    private static TextureAtlasSprite getOrFallback(
            Function<ResourceLocation, TextureAtlasSprite> getter,
            ResourceLocation loc, ResourceLocation fallback) {
        TextureAtlasSprite s = getter.apply(loc);
        return s != null ? s : getter.apply(fallback);
    }

    @Override
    public Collection<ResourceLocation> getTextures() {
        return Arrays.asList(BODY, CAP, SLOW, NORMAL, FAST, TURBO, HYPER, ULTRA,
                CON_FACE, CON_UP_T, CON_DOWN_T, CON_ONE, CON_ONE_OFF,
                CON_TWO, CON_TWO_OFF, CON_UP_T_OFF, CON_DOWN_T_OFF);
    }

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> getter) {
        TextureAtlasSprite bodyTex = getter.apply(BODY);
        return new ConduitBakedModel(
                getter.apply(BODY),            getter.apply(CAP),
                getter.apply(SLOW),            getter.apply(NORMAL),
                getter.apply(FAST),            getter.apply(TURBO),
                getter.apply(HYPER),           getter.apply(ULTRA),
                getOrFallback(getter, CON_FACE,   BODY),
                getOrFallback(getter, CON_UP_T,   BODY),
                getOrFallback(getter, CON_DOWN_T, BODY),
                getOrFallback(getter, CON_ONE,      BODY),
                getOrFallback(getter, CON_ONE_OFF,  BODY),
                getOrFallback(getter, CON_TWO,      BODY),
                getOrFallback(getter, CON_TWO_OFF,  BODY),
                getOrFallback(getter, CON_UP_T_OFF,  BODY),
                getOrFallback(getter, CON_DOWN_T_OFF, BODY));
    }

    @Override public IModelState getDefaultState() { return TRSRTransformation.identity(); }
    @Override public Collection<ResourceLocation> getDependencies() { return Collections.emptyList(); }

    // -----------------------------------------------------------------------
    // Baked model
    // -----------------------------------------------------------------------

    public static class ConduitBakedModel implements IBakedModel {

        private final TextureAtlasSprite body, cap;
        private final TextureAtlasSprite tierSlow, tierNormal, tierFast, tierTurbo, tierHyper, tierUltra;
        private final TextureAtlasSprite conFace, conUp, conDown, conUpOff, conDownOff;
        private final TextureAtlasSprite conOne, conOneOff, conTwo, conTwoOff;

        // Core dimensions
        private static final float CORE_MIN = 6f / 16f, CORE_MAX = 10f / 16f;
        // Collar dimensions — 2px wider each side than core
        private static final float COL_MIN  = 4f / 16f, COL_MAX  = 12f / 16f;
        // Collar depth — same as current arm width
        private static final float COL_D    = 4f / 16f;

        private static final int CON_NONE = 0, CON_CONDUIT = 1, CON_FE_SOURCE = 2;
        // CON_PIPE_NET removed — pipes connect via motors now

        public ConduitBakedModel(TextureAtlasSprite body, TextureAtlasSprite cap,
                                 TextureAtlasSprite slow,  TextureAtlasSprite normal,
                                 TextureAtlasSprite fast,  TextureAtlasSprite turbo,
                                 TextureAtlasSprite hyper, TextureAtlasSprite ultra,
                                 TextureAtlasSprite conFace,    TextureAtlasSprite conUp,
                                 TextureAtlasSprite conDown,    TextureAtlasSprite conOne,
                                 TextureAtlasSprite conOneOff,  TextureAtlasSprite conTwo,
                                 TextureAtlasSprite conTwoOff,  TextureAtlasSprite conUpOff,
                                 TextureAtlasSprite conDownOff
                                 ) {
            this.body = body; this.cap = cap;
            this.tierSlow = slow; this.tierNormal = normal;
            this.tierFast = fast; this.tierTurbo = turbo;
            this.tierHyper = hyper; this.tierUltra = ultra;
            this.conFace = conFace;
            this.conUp = conUp;     this.conUpOff   = conUpOff;
            this.conDown = conDown; this.conDownOff = conDownOff;
            this.conOne = conOne;   this.conOneOff  = conOneOff;
            this.conTwo = conTwo;   this.conTwoOff  = conTwoOff;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state,
                                        @Nullable EnumFacing side, long rand) {
            if (side != null) return Collections.emptyList();
            List<BakedQuad> quads = new ArrayList<>();

            if (state == null) {
                // Inventory render
                addCube(quads, CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, body);
                addArm(quads, EnumFacing.EAST, body);
                addCap(quads, EnumFacing.EAST, CON_CONDUIT, true);
                addArm(quads, EnumFacing.WEST, body);
                addCap(quads, EnumFacing.WEST, CON_CONDUIT, true);
                return quads;
            }

            int north = 0, south = 0, east = 0, west = 0, up = 0, down = 0;
            IExtendedBlockState ext = null;
            if (state instanceof IExtendedBlockState) {
                ext = (IExtendedBlockState) state;
                Integer n = ext.getValue(CON_NORTH);
                if (n != null) north = n;
                Integer s = ext.getValue(CON_SOUTH);
                if (s != null) south = s;
                Integer e = ext.getValue(CON_EAST);
                if (e != null) east = e;
                Integer w = ext.getValue(CON_WEST);
                if (w != null) west = w;
                Integer u = ext.getValue(CON_UP);
                if (u != null) up = u;
                Integer d = ext.getValue(CON_DOWN);
                if (d != null) down = d;
            }

            addCube(quads, CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, body);
            Boolean poweredVal = ext.getValue(POWERED);
            boolean powered = poweredVal != null && poweredVal;
            if (north > 0) {
                addArm(quads, EnumFacing.NORTH, body);
                addCap(quads, EnumFacing.NORTH, north, powered);
            }
            if (south > 0) {
                addArm(quads, EnumFacing.SOUTH, body);
                addCap(quads, EnumFacing.SOUTH, south, powered);
            }
            if (east > 0) {
                addArm(quads, EnumFacing.EAST, body);
                addCap(quads, EnumFacing.EAST, east, powered);
            }
            if (west > 0) {
                addArm(quads, EnumFacing.WEST, body);
                addCap(quads, EnumFacing.WEST, west, powered);
            }
            if (up > 0) {
                addArm(quads, EnumFacing.UP, body);
                addCap(quads, EnumFacing.UP, up, powered);
            }
            if (down > 0) {
                addArm(quads, EnumFacing.DOWN, body);
                addCap(quads, EnumFacing.DOWN, down, powered);
            }
            return quads;
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

        private void addCap(List<BakedQuad> q, EnumFacing dir, int conType, boolean powered) {
            if (conType == CON_FE_SOURCE) {
                addCollar(q, dir, powered);
                return;
            }
            // CON_CONDUIT — plain cap
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

        /**
         * Collar for CON_FE_SOURCE connections.
         * Wider than the arm (COL_MIN to COL_MAX), depth COL_D.
         * Textures: conFace = outward face, conUp = top, conDown = bottom,
         *           conOne = one pair of sides, conTwo = other pair.
         */
        private void addCollar(List<BakedQuad> q, EnumFacing dir, boolean powered) {
            TextureAtlasSprite cFace  = conFace;
            TextureAtlasSprite cUp    = powered ? conUp    : conUpOff;
            TextureAtlasSprite cDown  = powered ? conDown  : conDownOff;
            TextureAtlasSprite cOne   = powered ? conOne   : conOneOff;
            TextureAtlasSprite cTwo   = powered ? conTwo   : conTwoOff;
            switch (dir) {
                case DOWN: {
                    // Collar extends from y=0 inward to y=COL_D
                    float x1=COL_MIN, z1=COL_MIN, x2=COL_MAX, z2=COL_MAX;
                    float y1=0f, y2=COL_D;
                    // outward face (DOWN = y=0)
                    addQuad(q,EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, cFace);
                    // inner face (UP = y=COL_D)
                    addQuad(q,EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, cDown);
                    // sides
                    addQuad(q,EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, cOne);
                    addQuad(q,EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, cOne);
                    addQuad(q,EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, cTwo);
                    addQuad(q,EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, cTwo);
                    break;
                }
                case UP: {
                    float x1=COL_MIN, z1=COL_MIN, x2=COL_MAX, z2=COL_MAX;
                    float y1=1f-COL_D, y2=1f;
                    addQuad(q,EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, cFace);
                    addQuad(q,EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, cUp);
                    addQuad(q,EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, cOne);
                    addQuad(q,EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, cOne);
                    addQuad(q,EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, cTwo);
                    addQuad(q,EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, cTwo);
                    break;
                }
                case NORTH: {
                    float x1=COL_MIN, y1=COL_MIN, x2=COL_MAX, y2=COL_MAX;
                    float z1=0f, z2=COL_D;
                    addQuad(q,EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, cFace);
                    addQuad(q,EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, cDown);
                    addQuad(q,EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, cUp);
                    addQuad(q,EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, cDown);
                    addQuad(q,EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, cOne);
                    addQuad(q,EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, cTwo);
                    break;
                }
                case SOUTH: {
                    float x1=COL_MIN, y1=COL_MIN, x2=COL_MAX, y2=COL_MAX;
                    float z1=1f-COL_D, z2=1f;
                    addQuad(q,EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, cFace);
                    addQuad(q,EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, cDown);
                    addQuad(q,EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, cUp);
                    addQuad(q,EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, cDown);
                    addQuad(q,EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, cOne);
                    addQuad(q,EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, cTwo);
                    break;
                }
                case WEST: {
                    float y1=COL_MIN, z1=COL_MIN, y2=COL_MAX, z2=COL_MAX;
                    float x1=0f, x2=COL_D;
                    addQuad(q,EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, cFace);
                    addQuad(q,EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, cDown);
                    addQuad(q,EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, cUp);
                    addQuad(q,EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, cDown);
                    addQuad(q,EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, cOne);
                    addQuad(q,EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, cTwo);
                    break;
                }
                case EAST: {
                    float y1=COL_MIN, z1=COL_MIN, y2=COL_MAX, z2=COL_MAX;
                    float x1=1f-COL_D, x2=1f;
                    addQuad(q,EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, cFace);
                    addQuad(q,EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, cDown);
                    addQuad(q,EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, cUp);
                    addQuad(q,EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, cDown);
                    addQuad(q,EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, cOne);
                    addQuad(q,EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, cTwo);
                    break;
                }
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

        @Override
        public org.apache.commons.lang3.tuple.Pair<? extends net.minecraft.client.renderer.block.model.IBakedModel, javax.vecmath.Matrix4f>
                handlePerspective(ItemCameraTransforms.TransformType type) {
            return org.apache.commons.lang3.tuple.Pair.of(this, conduitTransform(type).getMatrix());
        }

        private static TRSRTransformation conduitTransform(ItemCameraTransforms.TransformType type) {
            javax.vecmath.Vector3f t = new javax.vecmath.Vector3f(0, 0, 0);
            javax.vecmath.Vector3f r = new javax.vecmath.Vector3f(0, 0, 0);
            float s;
            switch (type) {
                case FIRST_PERSON_RIGHT_HAND: r = new javax.vecmath.Vector3f(0, 45, 0);   s = 0.40f; break;
                case FIRST_PERSON_LEFT_HAND:  r = new javax.vecmath.Vector3f(0, 225, 0);  s = 0.40f; break;
                case THIRD_PERSON_RIGHT_HAND:
                case THIRD_PERSON_LEFT_HAND:
                    r = new javax.vecmath.Vector3f(75, 45, 0);
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
    }
}
