package rusticpipes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;
import rusticpipes.multiblock.TankMultiblock;
import rusticpipes.tileentity.TileEntityFluidTankMultiblock;

@SideOnly(Side.CLIENT)
public class FluidTankMultiblockRenderer extends TileEntitySpecialRenderer<TileEntityFluidTankMultiblock> {

    @Override
    public void render(TileEntityFluidTankMultiblock te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {

        // Skip non-controller members of a formed multiblock
        if (te.isPartOfMultiblock() && !te.isController()) return;

        BlockPos pos = te.getPos();
        TankMultiblock.Structure st = TankMultiblock.validateMultiblock(getWorld(), pos);
        if (st == null) return;
        // If TE not yet synced (just placed), check this block would be the controller
        if (!st.controller.equals(pos)) return;

        FluidStack fluid = te.getFluid();
        // Clamp fill to [0, 1] to prevent rendering glitches during structure transitions
        float fill = (fluid != null && fluid.amount > 0) ? Math.max(0f, Math.min(1f, te.getFillFraction())) : 0f;

        TextureAtlasSprite fluidSprite = null;
        if (fluid != null && fluid.getFluid() != null)
            fluidSprite = spr(fluid.getFluid().getStill(fluid).toString());

        BlockPos sMin = st.min, sMax = st.max;
        int sz = st.baseSize;

        // Fluid always fills the full tank volume (tank is solid, no hollow frame)
        boolean hollow = sz >= 1;
        double minX = sMin.getX() - pos.getX() + 0.01;
        double minZ = sMin.getZ() - pos.getZ() + 0.01;
        double maxX = sMax.getX() - pos.getX() + 1.0 - 0.01;
        double maxZ = sMax.getZ() - pos.getZ() + 1.0 - 0.01;
        double minY = sMin.getY() - pos.getY() + 0.01;
        double wallH = (sMax.getY() - sMin.getY() + 1) - 0.12; // extra headroom to prevent clipping through roof

        double maxY = Math.min(minY + wallH * fill, minY + wallH - 0.01); // clamp to ceiling

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        int combinedLight = getWorld().getCombinedLight(sMax.up(), 0);
        int lightU = (combinedLight >> 4) & 0xF;   // block light 0-15
        int lightV = (combinedLight >> 20) & 0xF; // sky light 0-15
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU * 16f, lightV * 16f);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        // Render interior walls for all multiblocks
        if (hollow) {
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            renderInteriorWalls(buf, pos, st);
            tess.draw();
        }

        // Fluid
        GlStateManager.color(1f, 1f, 1f, 1f);

        if (fluid == null || fluid.amount <= 0 || fluidSprite == null) {
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
            return;
        }

        int fc = fluid.getFluid().getColor(fluid);
        float r = ((fc >> 16) & 0xFF) / 255f;
        float g = ((fc >> 8)  & 0xFF) / 255f;
        float b = (fc & 0xFF)         / 255f;
        float a = ((fc >> 24) & 0xFF) / 255f;
        if (a == 0f) a = 0.8f;

        double fx1 = minX + 0.02, fx2 = maxX - 0.02;
        double fz1 = minZ + 0.02, fz2 = maxZ - 0.02;
        double fy1 = minY + 0.01;
        float u1 = fluidSprite.getMinU(), u2 = fluidSprite.getMaxU();
        float v1 = fluidSprite.getMinV(), v2 = fluidSprite.getMaxV();

        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        buf.pos(fx1,maxY,fz1).tex(u1,v1).color(r,g,b,a).endVertex();
        buf.pos(fx1,maxY,fz2).tex(u1,v2).color(r,g,b,a).endVertex();
        buf.pos(fx2,maxY,fz2).tex(u2,v2).color(r,g,b,a).endVertex();
        buf.pos(fx2,maxY,fz1).tex(u2,v1).color(r,g,b,a).endVertex();

        buf.pos(fx2,fy1,fz1).tex(u2,v2).color(r,g,b,a).endVertex();
        buf.pos(fx1,fy1,fz1).tex(u1,v2).color(r,g,b,a).endVertex();
        buf.pos(fx1,maxY,fz1).tex(u1,v1).color(r,g,b,a).endVertex();
        buf.pos(fx2,maxY,fz1).tex(u2,v1).color(r,g,b,a).endVertex();

        buf.pos(fx1,fy1,fz2).tex(u1,v2).color(r,g,b,a).endVertex();
        buf.pos(fx2,fy1,fz2).tex(u2,v2).color(r,g,b,a).endVertex();
        buf.pos(fx2,maxY,fz2).tex(u2,v1).color(r,g,b,a).endVertex();
        buf.pos(fx1,maxY,fz2).tex(u1,v1).color(r,g,b,a).endVertex();

        buf.pos(fx1,fy1,fz1).tex(u1,v2).color(r,g,b,a).endVertex();
        buf.pos(fx1,fy1,fz2).tex(u2,v2).color(r,g,b,a).endVertex();
        buf.pos(fx1,maxY,fz2).tex(u2,v1).color(r,g,b,a).endVertex();
        buf.pos(fx1,maxY,fz1).tex(u1,v1).color(r,g,b,a).endVertex();

        buf.pos(fx2,fy1,fz2).tex(u1,v2).color(r,g,b,a).endVertex();
        buf.pos(fx2,fy1,fz1).tex(u2,v2).color(r,g,b,a).endVertex();
        buf.pos(fx2,maxY,fz1).tex(u2,v1).color(r,g,b,a).endVertex();
        buf.pos(fx2,maxY,fz2).tex(u1,v1).color(r,g,b,a).endVertex();
        tess.draw();

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    /**
     * Renders interior walls as 6 spanning quads.
     * Textures are padded to square power-of-2, so both U and V ratios are applied.
     */
    private void renderInteriorWalls(BufferBuilder buf, BlockPos ctrl, TankMultiblock.Structure st) {
        BlockPos min = st.min, max = st.max;
        int baseSize = st.baseSize;
        int height = max.getY() - min.getY() + 1;

        int wallWidth = baseSize;
        int wallHeight = height;

        if (wallWidth < 1 || wallHeight < 1) return;

        String sidePath, topPath, botPath;
        if (wallWidth == 1 && wallHeight == 1) {
            // 1x1x1 — use single inner viewport texture
            sidePath = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_viewport";
            topPath  = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_top_1x1";
            botPath  = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_bottom_1x1";
        } else if (wallWidth == 1) {
            // 1x1x2-10 — use height-specific inner side textures
            sidePath = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_side_1x" + wallHeight;
            topPath  = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_top_1x1";
            botPath  = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_bottom_1x1";
        } else {
            sidePath = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_side_" + wallWidth + "x" + wallHeight;
            topPath  = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_top_"  + wallWidth + "x" + wallWidth;
            botPath  = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_bottom_" + wallWidth + "x" + wallWidth;
        }

        TextureAtlasSprite sideSpr = spr(sidePath);
        TextureAtlasSprite topSpr  = spr(topPath);
        TextureAtlasSprite botSpr  = spr(botPath);

        // Textures are padded to square power-of-2.
        // Compute U and V ratios: only use the fraction that contains real content.
        // For 1x1x1 the inner viewport is used at full UV
        // For 1x1x2-10 the side texture is WxH so needs proper ratio calculation
        float sideURatio, sideVRatio, topURatio, topVRatio;
        if (wallWidth == 1 && wallHeight == 1) {
            sideURatio = 1.0f; sideVRatio = 1.0f;
            topURatio  = 1.0f; topVRatio  = 1.0f;
        } else if (wallWidth == 1) {
            // fluid_tank_inner_side_1xH: 16px wide x (H*16)px tall — needs padding
            int sideOrigW = 16;
            int sideOrigH = wallHeight * 16;
            int sideSize  = nextPow2(Math.max(sideOrigW, sideOrigH));
            sideURatio = (float) sideOrigW / sideSize;
            sideVRatio = (float) sideOrigH / sideSize;
            topURatio  = 1.0f; topVRatio = 1.0f;
        } else {
            // Side texture: original is wallWidth*16 wide x wallHeight*16 tall
            int sideOrigW = wallWidth  * 16;
            int sideOrigH = wallHeight * 16;
            int sideSize  = nextPow2(Math.max(sideOrigW, sideOrigH));
            sideURatio = (float) sideOrigW / sideSize;
            sideVRatio = (float) sideOrigH / sideSize;

            // Top/bottom texture: original is wallWidth*16 x wallWidth*16 (square)
            int topOrigW = wallWidth * 16;
            int topSize  = nextPow2(topOrigW);
            topURatio = (float) topOrigW / topSize;
            topVRatio = topURatio;
        }

        // Interior cavity bounds (entire tank volume, inset slightly to avoid z-fighting)
        float x0 = 0.01f, x1 = wallWidth  - 0.01f;
        float z0 = 0.01f, z1 = wallWidth  - 0.01f;
        float y0 = 0.01f, y1 = wallHeight - 0.01f;

        // North wall (minZ, facing +Z) — mirrored U
        putQuad(buf, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0, sideSpr, true,  sideURatio, sideVRatio);
        // South wall (maxZ, facing -Z) — mirrored U
        putQuad(buf, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, sideSpr, true,  sideURatio, sideVRatio);
        // West wall  (minX, facing +X)
        putQuad(buf, x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, sideSpr, false, sideURatio, sideVRatio);
        // East wall  (maxX, facing -X)
        putQuad(buf, x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, sideSpr, false, sideURatio, sideVRatio);
        // Ceiling and floor — enable culling so they only show from inside, not through solid top/bottom
        GlStateManager.enableCull();
        putQuad(buf, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, topSpr, false, topURatio, topVRatio);
        putQuad(buf, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, botSpr, false, topURatio, topVRatio);
        GlStateManager.disableCull();
    }

    private static int nextPow2(int n) {
        int p = 1;
        while (p < n) p <<= 1;
        return p;
    }

    private TextureAtlasSprite spr(String name) {
        TextureAtlasSprite s = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);
        return s != null ? s : Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
    }

    /**
     * Renders a quad with UV ratios applied to both axes.
     * Textures are padded to square power-of-2; ratios clip UVs to real content.
     * @param mirrorU  if true, swaps U (for north/south walls viewed from inside)
     * @param uRatio   fraction of U range containing real texture content
     * @param vRatio   fraction of V range containing real texture content
     */
    private void putQuad(BufferBuilder buf,
                         float x1, float y1, float z1, float x2, float y2, float z2,
                         float x3, float y3, float z3, float x4, float y4, float z4,
                         TextureAtlasSprite s, boolean mirrorU, float uRatio, float vRatio) {
        float uMin = s.getMinU();
        float uMax = s.getMinU() + (s.getMaxU() - s.getMinU()) * uRatio;
        float vMin = s.getMinV();
        float vMax = s.getMinV() + (s.getMaxV() - s.getMinV()) * vRatio;

        float uA = mirrorU ? uMax : uMin;
        float uB = mirrorU ? uMin : uMax;

        buf.pos(x1,y1,z1).tex(uA, vMax).color(1f,1f,1f,1f).endVertex();
        buf.pos(x2,y2,z2).tex(uB, vMax).color(1f,1f,1f,1f).endVertex();
        buf.pos(x3,y3,z3).tex(uB, vMin).color(1f,1f,1f,1f).endVertex();
        buf.pos(x4,y4,z4).tex(uA, vMin).color(1f,1f,1f,1f).endVertex();
    }
}
