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
        boolean hollow = (sz >= 2) && (sMax.getY() - sMin.getY() >= 1);
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

        // Render interior walls for hollow tanks
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
     * Renders interior walls as spanning quads.
     * Interior cavity = entire tank volume (from min to max inclusive).
     */
    private void renderInteriorWalls(BufferBuilder buf, BlockPos ctrl, TankMultiblock.Structure st) {
        BlockPos min = st.min, max = st.max;
        int baseSize = st.baseSize;
        int height = max.getY() - min.getY() + 1;
        
        // Interior cavity dimensions
        int wallWidth = baseSize;    // Width of interior cavity
        int wallHeight = height;     // Height of interior cavity
        
        // Only render if there's interior space
        if (wallWidth < 2 || wallHeight < 1) return;
        
        // Get textures
        String sidePath = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_side_" + wallWidth + "x" + wallHeight;
        String topPath = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_top_" + wallWidth + "x" + wallWidth;
        String botPath = "rusticpipes:blocks/fluid_tank/fluid_tank_inner_bottom_" + wallWidth + "x" + wallWidth;
        
        TextureAtlasSprite sideSpr = spr(sidePath);
        TextureAtlasSprite topSpr = spr(topPath);
        TextureAtlasSprite botSpr = spr(botPath);
        
        System.out.println("=== INTERIOR WALLS ===");
        System.out.println("baseSize=" + baseSize + ", height=" + height);
        System.out.println("Side: " + sidePath + " (found=" + !sideSpr.getIconName().contains("missingno") + ")");
        System.out.println("Top: " + topPath + " (found=" + !topSpr.getIconName().contains("missingno") + ")");
        
        // Interior cavity bounds: entire tank volume (relative to controller block at origin)
        // Inset slightly to avoid z-fighting with block faces
        float intMinX = 0.01f;
        float intMaxX = wallWidth - 0.01f;
        float intMinZ = 0.01f;
        float intMaxZ = wallWidth - 0.01f;
        float intMinY = 0.01f;
        float intMaxY = wallHeight - 0.01f;
        
        // Render 4 spanning side walls
        // North wall (at minZ, facing +Z inward) - mirrored
        putQMirrorX(buf, intMaxX, intMinY, intMinZ, intMinX, intMinY, intMinZ, 
                    intMinX, intMaxY, intMinZ, intMaxX, intMaxY, intMinZ, sideSpr);
        
        // South wall (at maxZ, facing -Z inward) - mirrored
        putQMirrorX(buf, intMinX, intMinY, intMaxZ, intMaxX, intMinY, intMaxZ,
                    intMaxX, intMaxY, intMaxZ, intMinX, intMaxY, intMaxZ, sideSpr);
        
        // West wall (at minX, facing +X inward)
        putQ(buf, intMinX, intMinY, intMaxZ, intMinX, intMinY, intMinZ,
             intMinX, intMaxY, intMinZ, intMinX, intMaxY, intMaxZ, sideSpr);
        
        // East wall (at maxX, facing -X inward)
        putQ(buf, intMaxX, intMinY, intMinZ, intMaxX, intMinY, intMaxZ,
             intMaxX, intMaxY, intMaxZ, intMaxX, intMaxY, intMinZ, sideSpr);
        
        // Ceiling (at maxY, facing -Y downward)
        putQ(buf, intMinX, intMaxY, intMinZ, intMaxX, intMaxY, intMinZ,
             intMaxX, intMaxY, intMaxZ, intMinX, intMaxY, intMaxZ, topSpr);
        
        // Floor (at minY, facing +Y upward)
        putQ(buf, intMinX, intMinY, intMaxZ, intMaxX, intMinY, intMaxZ,
             intMaxX, intMinY, intMinZ, intMinX, intMinY, intMinZ, botSpr);
        
        System.out.println("=== 6 QUADS RENDERED ===");
    }

    private TextureAtlasSprite spr(String name) {
        TextureAtlasSprite s = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);
        if (s == null) {
            System.out.println("WARNING: Sprite not found: " + name);
            s = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
        }
        return s;
    }

    private void putQ(BufferBuilder buf,
                      float x1, float y1, float z1, float x2, float y2, float z2,
                      float x3, float y3, float z3, float x4, float y4, float z4,
                      TextureAtlasSprite s) {
        float u0=s.getMinU(), v0=s.getMinV(), u1=s.getMaxU(), v1=s.getMaxV();
        buf.pos(x1,y1,z1).tex(u0,v1).color(1f,1f,1f,1f).endVertex();
        buf.pos(x2,y2,z2).tex(u1,v1).color(1f,1f,1f,1f).endVertex();
        buf.pos(x3,y3,z3).tex(u1,v0).color(1f,1f,1f,1f).endVertex();
        buf.pos(x4,y4,z4).tex(u0,v0).color(1f,1f,1f,1f).endVertex();
    }
    
    private void putQMirrorX(BufferBuilder buf,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             TextureAtlasSprite s) {
        float u0=s.getMinU(), v0=s.getMinV(), u1=s.getMaxU(), v1=s.getMaxV();
        buf.pos(x1,y1,z1).tex(u1,v1).color(1f,1f,1f,1f).endVertex();  // Swapped U
        buf.pos(x2,y2,z2).tex(u0,v1).color(1f,1f,1f,1f).endVertex();  // Swapped U
        buf.pos(x3,y3,z3).tex(u0,v0).color(1f,1f,1f,1f).endVertex();  // Swapped U
        buf.pos(x4,y4,z4).tex(u1,v0).color(1f,1f,1f,1f).endVertex();  // Swapped U
    }
}
