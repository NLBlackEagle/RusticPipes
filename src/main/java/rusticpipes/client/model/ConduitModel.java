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
import rusticpipes.network.PipeNetwork;

import javax.annotation.Nullable;
import java.util.*;
import java.util.Arrays;
import java.util.function.Function;

public class ConduitModel implements IModel {

    public static final ConduitModel INSTANCE = new ConduitModel();

    // -----------------------------------------------------------------------
    // Unlisted properties
    // -----------------------------------------------------------------------

    public static final IUnlistedProperty<BlockPos> BLOCK_POS = new IUnlistedProperty<BlockPos>() {
        @Override public String getName() { return "conduit_block_pos"; }
        @Override public Class<BlockPos> getType() { return BlockPos.class; }
        @Override public boolean isValid(BlockPos value) { return true; }
        @Override public String valueToString(BlockPos value) { return value.toString(); }
    };

    public static final IUnlistedProperty<PipeNetwork.SpeedTier> CONDUIT_TIER =
            new IUnlistedProperty<PipeNetwork.SpeedTier>() {
                @Override public String getName() { return "conduit_tier"; }
                @Override public Class<PipeNetwork.SpeedTier> getType() { return PipeNetwork.SpeedTier.class; }
                @Override public boolean isValid(PipeNetwork.SpeedTier value) { return true; }
                @Override public String valueToString(PipeNetwork.SpeedTier value) { return value.name(); }
            };

    public static final IUnlistedProperty<Integer> CON_NORTH = conProp("conduit_con_north");
    public static final IUnlistedProperty<Integer> CON_SOUTH = conProp("conduit_con_south");
    public static final IUnlistedProperty<Integer> CON_EAST  = conProp("conduit_con_east");
    public static final IUnlistedProperty<Integer> CON_WEST  = conProp("conduit_con_west");
    public static final IUnlistedProperty<Integer> CON_UP    = conProp("conduit_con_up");
    public static final IUnlistedProperty<Integer> CON_DOWN  = conProp("conduit_con_down");

    private static IUnlistedProperty<Integer> conProp(String name) {
        return new IUnlistedProperty<Integer>() {
            @Override public String getName() { return name; }
            @Override public Class<Integer> getType() { return Integer.class; }
            @Override public boolean isValid(Integer value) { return value >= 0 && value <= 3; }
            @Override public String valueToString(Integer value) { return value.toString(); }
        };
    }

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
    // Textures
    // -----------------------------------------------------------------------

    // Body textures (randomised like pipes, but cable-look)
    private static final ResourceLocation BODY   = new ResourceLocation("rusticpipes:blocks/conduit_body");
    private static final ResourceLocation CAP    = new ResourceLocation("rusticpipes:blocks/conduit_cap");
    private static final ResourceLocation SLOW   = new ResourceLocation("rusticpipes:blocks/conduit_slow");
    private static final ResourceLocation NORMAL = new ResourceLocation("rusticpipes:blocks/conduit_normal");
    private static final ResourceLocation FAST   = new ResourceLocation("rusticpipes:blocks/conduit_fast");
    private static final ResourceLocation TURBO  = new ResourceLocation("rusticpipes:blocks/conduit_turbo");

    @Override
    public Collection<ResourceLocation> getTextures() {
        return Arrays.asList(BODY, CAP, SLOW, NORMAL, FAST, TURBO);
    }

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format,
                            Function<ResourceLocation, TextureAtlasSprite> getter) {
        return new ConduitBakedModel(
                getter.apply(BODY),
                getter.apply(CAP),
                getter.apply(SLOW),
                getter.apply(NORMAL),
                getter.apply(FAST),
                getter.apply(TURBO));
    }

    @Override public IModelState getDefaultState() { return TRSRTransformation.identity(); }
    @Override public Collection<ResourceLocation> getDependencies() { return Collections.emptyList(); }

    // -----------------------------------------------------------------------
    // Baked model
    // -----------------------------------------------------------------------

    public static class ConduitBakedModel implements IBakedModel {

        private final TextureAtlasSprite body;
        private final TextureAtlasSprite cap;
        private final TextureAtlasSprite tierSlow, tierNormal, tierFast, tierTurbo;

        // Half the width of a pipe — skinny cable, no flanges
        private static final float CORE_MIN = 6f / 16f;
        private static final float CORE_MAX = 10f / 16f;

        private static final int CON_NONE      = 0;
        private static final int CON_CONDUIT   = 1;
        private static final int CON_FE_SOURCE = 2;
        private static final int CON_PIPE_NET  = 3;

        public ConduitBakedModel(TextureAtlasSprite body, TextureAtlasSprite cap,
                                 TextureAtlasSprite tierSlow,   TextureAtlasSprite tierNormal,
                                 TextureAtlasSprite tierFast,   TextureAtlasSprite tierTurbo) {
            this.body       = body;
            this.cap        = cap;
            this.tierSlow   = tierSlow;
            this.tierNormal = tierNormal;
            this.tierFast   = tierFast;
            this.tierTurbo  = tierTurbo;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state,
                                        @Nullable EnumFacing side, long rand) {
            if (side != null) return Collections.emptyList();
            List<BakedQuad> quads = new ArrayList<>();

            // Inventory render
            if (state == null) {
                addCube(quads, CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, body);
                addArm(quads, EnumFacing.EAST, body);
                addArm(quads, EnumFacing.WEST, body);
                addCap(quads, EnumFacing.EAST, CON_CONDUIT, PipeNetwork.SpeedTier.SLOW);
                addCap(quads, EnumFacing.WEST, CON_CONDUIT, PipeNetwork.SpeedTier.SLOW);
                return quads;
            }

            // In-world render — read from extended state
            int north = 0, south = 0, east = 0, west = 0, up = 0, down = 0;
            BlockPos pos = null;
            PipeNetwork.SpeedTier tier = PipeNetwork.SpeedTier.SLOW;

            if (state instanceof IExtendedBlockState) {
                IExtendedBlockState ext = (IExtendedBlockState) state;
                Integer n = ext.getValue(CON_NORTH); if (n != null) north = n;
                Integer s = ext.getValue(CON_SOUTH); if (s != null) south = s;
                Integer e = ext.getValue(CON_EAST);  if (e != null) east  = e;
                Integer w = ext.getValue(CON_WEST);  if (w != null) west  = w;
                Integer u = ext.getValue(CON_UP);    if (u != null) up    = u;
                Integer d = ext.getValue(CON_DOWN);  if (d != null) down  = d;
                pos  = ext.getValue(BLOCK_POS);
                PipeNetwork.SpeedTier t = ext.getValue(CONDUIT_TIER);
                if (t != null) tier = t;
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

        private TextureAtlasSprite capTexFor(int conType, PipeNetwork.SpeedTier tier) {
            if (conType == CON_PIPE_NET) {
                switch (tier) {
                    case NORMAL: return tierNormal;
                    case FAST:   return tierFast;
                    case TURBO:  return tierTurbo;
                    default:     return tierSlow;
                }
            }
            // CON_CONDUIT, CON_FE_SOURCE and CON_NONE all use the plain cap
            return cap;
        }

        private void addArm(List<BakedQuad> quads, EnumFacing dir, TextureAtlasSprite b) {
            switch (dir) {
                case DOWN:  addCube(quads, CORE_MIN, 0,        CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX, b); break;
                case UP:    addCube(quads, CORE_MIN, CORE_MAX, CORE_MIN, CORE_MAX, 1,        CORE_MAX, b); break;
                case NORTH: addCube(quads, CORE_MIN, CORE_MIN, 0,        CORE_MAX, CORE_MAX, CORE_MIN, b); break;
                case SOUTH: addCube(quads, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX, 1,        b); break;
                case WEST:  addCube(quads, 0,        CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, b); break;
                case EAST:  addCube(quads, CORE_MAX, CORE_MIN, CORE_MIN, 1,        CORE_MAX, CORE_MAX, b); break;
            }
        }

        private void addCap(List<BakedQuad> quads, EnumFacing dir, int conType,
                            PipeNetwork.SpeedTier tier) {
            // No flanges — just stamp the tier texture on the arm's end face
            TextureAtlasSprite outer = capTexFor(conType, tier);
            float s = CORE_MIN, e = CORE_MAX;
            switch (dir) {
                case DOWN:  addQuad(quads, EnumFacing.DOWN,  s,0,s, e,0,s, e,0,e, s,0,e, outer); return;
                case UP:    addQuad(quads, EnumFacing.UP,    s,1,e, e,1,e, e,1,s, s,1,s, outer); return;
                case NORTH: addQuad(quads, EnumFacing.NORTH, e,s,0, s,s,0, s,e,0, e,e,0, outer); return;
                case SOUTH: addQuad(quads, EnumFacing.SOUTH, s,s,1, e,s,1, e,e,1, s,e,1, outer); return;
                case WEST:  addQuad(quads, EnumFacing.WEST,  0,s,s, 0,s,e, 0,e,e, 0,e,s, outer); return;
                case EAST:  addQuad(quads, EnumFacing.EAST,  1,s,e, 1,s,s, 1,e,s, 1,e,e, outer); return;
            }
        }

        // ---- Cube helpers ----
        private void addCube(List<BakedQuad> q, float x1, float y1, float z1,
                             float x2, float y2, float z2, TextureAtlasSprite s) {
            addCube(q, x1, y1, z1, x2, y2, z2, s, s, s, s, s, s);
        }
        private void addCube(List<BakedQuad> q, float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             TextureAtlasSprite sD, TextureAtlasSprite sU,
                             TextureAtlasSprite sN, TextureAtlasSprite sS,
                             TextureAtlasSprite sW, TextureAtlasSprite sE) {
            addQuad(q, EnumFacing.DOWN,  x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, sD);
            addQuad(q, EnumFacing.UP,    x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, sU);
            addQuad(q, EnumFacing.NORTH, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, sN);
            addQuad(q, EnumFacing.SOUTH, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, sS);
            addQuad(q, EnumFacing.WEST,  x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, sW);
            addQuad(q, EnumFacing.EAST,  x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, sE);
        }

        private void addQuad(List<BakedQuad> quads, EnumFacing face,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
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
