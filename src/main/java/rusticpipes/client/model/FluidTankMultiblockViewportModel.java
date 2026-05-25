package rusticpipes.client.model;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.property.IExtendedBlockState;
import rusticpipes.block.BlockFluidTankMultiblock;

import javax.annotation.Nullable;
import java.util.*;

/**
 * Single baked model used for ALL viewport states of the multiblock tank.
 *
 * Direction (N/S/E/W) comes from the block's listed VIEWPORT property (block meta).
 * Row (SINGLE/BOTTOM/CENTER/TOP) comes from the unlisted VIEWPORT_ROW property,
 * which is populated from the TE in Block.getExtendedState() — no meta bits used.
 *
 * Render layer split:
 *   SOLID        — the 5 non-viewport faces
 *   CUTOUT_MIPPED — the viewport face
 */
public class FluidTankMultiblockViewportModel implements IBakedModel {

    // Sprites indexed by ViewportRow ordinal: NONE, SINGLE, BOTTOM, CENTER, TOP
    private final TextureAtlasSprite[] rowSprites;
    private final TextureAtlasSprite   spriteSolid;

    public FluidTankMultiblockViewportModel(
            TextureAtlasSprite spriteSingle,   // fluid_tank_viewport
            TextureAtlasSprite spriteBottom,   // fluid_tank_viewport_bottom
            TextureAtlasSprite spriteCenter,   // fluid_tank_viewport_center
            TextureAtlasSprite spriteTop,      // fluid_tank_viewport_top
            TextureAtlasSprite spriteSolid) {
        this.spriteSolid = spriteSolid;
        // Indexed by ViewportRow.ordinal(): NONE=0, SINGLE=1, BOTTOM=2, CENTER=3, TOP=4
        this.rowSprites = new TextureAtlasSprite[]{
            spriteSolid,   // NONE  — fallback, shouldn't normally be used
            spriteSingle,  // SINGLE
            spriteBottom,  // BOTTOM
            spriteCenter,  // CENTER
            spriteTop      // TOP
        };
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
        if (side != null) return Collections.emptyList();

        BlockRenderLayer layer = MinecraftForgeClient.getRenderLayer();
        boolean isInventory = (layer == null);
        boolean isSolid  = isInventory || layer == BlockRenderLayer.SOLID;
        boolean isCutout = isInventory || layer == BlockRenderLayer.CUTOUT_MIPPED;

        // Read direction from listed property
        EnumFacing vpFace = EnumFacing.NORTH; // fallback
        if (state != null) {
            BlockFluidTankMultiblock.ViewportFace vp =
                    state.getValue(BlockFluidTankMultiblock.VIEWPORT);
            switch (vp) {
                case NORTH: vpFace = EnumFacing.NORTH; break;
                case SOUTH: vpFace = EnumFacing.SOUTH; break;
                case EAST:  vpFace = EnumFacing.EAST;  break;
                case WEST:  vpFace = EnumFacing.WEST;  break;
                default:    break;
            }
        }

        // Read row from unlisted (TE-driven) extended state
        BlockFluidTankMultiblock.ViewportRow row = BlockFluidTankMultiblock.ViewportRow.BOTTOM;
        if (state instanceof IExtendedBlockState) {
            BlockFluidTankMultiblock.ViewportRow r =
                    ((IExtendedBlockState) state).getValue(BlockFluidTankMultiblock.VIEWPORT_ROW);
            if (r != null) row = r;
        }

        TextureAtlasSprite vpSprite = rowSprites[row.ordinal()];

        List<BakedQuad> quads = new ArrayList<>();

        if (isSolid) {
            for (EnumFacing f : EnumFacing.VALUES) {
                if (f != vpFace) quads.add(buildFace(f, spriteSolid, false));
            }
        }
        if (isCutout) {
            quads.add(buildFace(vpFace, vpSprite, false));
        }

        return quads;
    }

    private BakedQuad buildFace(EnumFacing face, TextureAtlasSprite sprite, boolean inset) {
        final float D = 1f / 16f;
        float[] x = new float[4], y = new float[4], z = new float[4];
        switch (face) {
            case DOWN:  { float p=inset?D:0;    x=f(0,1,1,0); y=c(p); z=f(1,1,0,0); break; }
            case UP:    { float p=inset?1-D:1;  x=f(0,0,1,1); y=c(p); z=f(0,1,1,0); break; }
            case NORTH: { float p=inset?D:0;    x=f(1,0,0,1); y=f(0,0,1,1); z=c(p); break; }
            case SOUTH: { float p=inset?1-D:1;  x=f(0,1,1,0); y=f(0,0,1,1); z=c(p); break; }
            case WEST:  { float p=inset?D:0;    x=c(p); y=f(0,0,1,1); z=f(0,1,1,0); break; }
            default:    { float p=inset?1-D:1;  x=c(p); y=f(0,0,1,1); z=f(1,0,0,1); break; }
        }
        float u0=sprite.getMinU(), u1=sprite.getMaxU(), v0=sprite.getMinV(), v1=sprite.getMaxV();
        float[] us={u0,u1,u1,u0}, vs={v1,v1,v0,v0};
        int[] data = new int[28];
        for (int i = 0; i < 4; i++) {
            data[i*7]   = Float.floatToRawIntBits(x[i]);
            data[i*7+1] = Float.floatToRawIntBits(y[i]);
            data[i*7+2] = Float.floatToRawIntBits(z[i]);
            data[i*7+3] = 0xFFFFFFFF;
            data[i*7+4] = Float.floatToRawIntBits(us[i]);
            data[i*7+5] = Float.floatToRawIntBits(vs[i]);
            data[i*7+6] = packNormal(face);
        }
        return new BakedQuad(data, -1, face, sprite, true, DefaultVertexFormats.ITEM);
    }

    private static float[] f(float a, float b, float c, float d) { return new float[]{a,b,c,d}; }
    private static float[] c(float v) { return new float[]{v,v,v,v}; }
    private static int packNormal(EnumFacing face) {
        return (((int)(face.getXOffset()*127))&0xFF)
             | ((((int)(face.getYOffset()*127))&0xFF)<<8)
             | ((((int)(face.getZOffset()*127))&0xFF)<<16);
    }

    @Override public boolean isAmbientOcclusion()  { return true; }
    @Override public boolean isGui3d()             { return true; }
    @Override public boolean isBuiltInRenderer()   { return false; }
    @Override public TextureAtlasSprite getParticleTexture() { return spriteSolid; }
    @Override public ItemOverrideList getOverrides()         { return ItemOverrideList.NONE; }
    @Override public ItemCameraTransforms getItemCameraTransforms() { return ItemCameraTransforms.DEFAULT; }
}
