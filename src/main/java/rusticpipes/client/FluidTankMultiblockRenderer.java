package rusticpipes.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
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

        if (!te.isController() && te.isPartOfMultiblock()) return;
        if (!te.isPartOfMultiblock()) return;

        BlockPos pos = te.getPos();
        TankMultiblock.Structure st = TankMultiblock.validateMultiblock(getWorld(), pos);
        if (st == null) return;

        FluidStack fluid = te.getFluid();
        float fill = (fluid != null && fluid.amount > 0) ? te.getFillFraction() : 0f;

        TextureAtlasSprite fluidSprite = null;
        if (fluid != null && fluid.getFluid() != null)
            fluidSprite = spr(fluid.getFluid().getStill(fluid).toString());

        BlockPos sMin = st.min, sMax = st.max;
        int sz = st.baseSize;

        double minX, minY, minZ, maxX, maxZ, wallH;
        // All multiblocks (even 1-tall) have an interior cavity
        boolean hollow = sz >= 2;
        if (!hollow) {
            minX = sMin.getX() - pos.getX() + 0.01;
            minZ = sMin.getZ() - pos.getZ() + 0.01;
            maxX = sMax.getX() - pos.getX() + 1.0 - 0.01;
            maxZ = sMax.getZ() - pos.getZ() + 1.0 - 0.01;
            minY = sMin.getY() - pos.getY() + 0.01;
            wallH = (sMax.getY() - sMin.getY() + 1) - 0.02;
        } else {
            minX = sMin.getX() - pos.getX() + 1.0 + 0.01;
            minZ = sMin.getZ() - pos.getZ() + 1.0 + 0.01;
            maxX = sMax.getX() - pos.getX() - 0.01;
            maxZ = sMax.getZ() - pos.getZ() - 0.01;
            minY = sMin.getY() - pos.getY() + 1.0 + 0.01;
            wallH = (sMax.getY() - sMin.getY() - 1) - 0.02;
        }

        double maxY = minY + wallH * fill;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();

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
     * Texture UV ratios are adjusted for padded power-of-2 textures.
     */
    private void renderInteriorWalls(BufferBuilder buf, BlockPos ctrl, TankMultiblock.Structure st) {
        BlockPos min = st.min, max = st.max;
        int baseSize = st.baseSize;
        int height = max.getY() - min.getY() + 1;

        int wallWidth = baseSize;
        int wallHeight = height;

        if (wallWidth < 2 || wallHeight < 1) return;

        String sidePath = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_side_" + wallWidth + "x" + wallHeight;
        String topPath  = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_top_"  + wallWidth + "x" + wallWidth;
        String botPath  = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_bottom_" + wallWidth + "x" + wallWidth;

        TextureAtlasSprite sideSpr = spr(sidePath);
        TextureAtlasSprite topSpr  = spr(topPath);
        TextureAtlasSprite botSpr  = spr(botPath);

        // Calculate UV ratio for padded textures
        // Original texture height (pixels) = wallHeight * 16
        // Padded to next power-of-2, so we only use vRatio of the V range
        float sideVRatio = vRatio(wallHeight);
        float topVRatio  = vRatio(wallWidth);  // top/bottom textures: W x W, padded if needed

        // Interior cavity bounds (entire tank volume, inset slightly)
        float x0 = 0.01f, x1 = wallWidth  - 0.01f;
        float z0 = 0.01f, z1 = wallWidth  - 0.01f;
        float y0 = 0.01f, y1 = wallHeight - 0.01f;

        // North wall (minZ, facing +Z) — mirrored
        putQv(buf, x1,y0,z0, x0,y0,z0, x0,y1,z0, x1,y1,z0, sideSpr, true,  sideVRatio);
        // South wall (maxZ, facing -Z) — mirrored
        putQv(buf, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1, sideSpr, true,  sideVRatio);
        // West wall  (minX, facing +X)
        putQv(buf, x0,y0,z1, x0,y0,z0, x0,y1,z0, x0,y1,z1, sideSpr, false, sideVRatio);
        // East wall  (maxX, facing -X)
        putQv(buf, x1,y0,z0, x1,y0,z1, x1,y1,z1, x1,y1,z0, sideSpr, false, sideVRatio);
        // Ceiling (maxY, facing -Y)
        putQv(buf, x0,y1,z0, x1,y1,z0, x1,y1,z1, x0,y1,z1, topSpr,  false, topVRatio);
        // Floor (minY, facing +Y)
        putQv(buf, x0,y0,z1, x1,y0,z1, x1,y0,z0, x0,y0,z0, botSpr,  false, topVRatio);
    }

    /**
     * Returns the fraction of V range to use for a texture with given block-height dimension.
     * Textures are padded to next power-of-2, so we only use the real portion.
     */
    private static float vRatio(int blockDim) {
        int pixels = blockDim * 16;
        int padded = nextPow2(pixels);
        return (float) pixels / padded;
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
     * Renders a quad with UV ratio applied to the V axis.
     * @param mirrorU if true, swaps U coordinates (for north/south walls viewed from inside)
     * @param vRatio  fraction of the sprite's V range to use (for padded textures)
     */
    private void putQv(BufferBuilder buf,
                       float x1, float y1, float z1, float x2, float y2, float z2,
                       float x3, float y3, float z3, float x4, float y4, float z4,
                       TextureAtlasSprite s, boolean mirrorU, float vRatio) {
        float uMin = s.getMinU(), uMax = s.getMaxU();
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
