package rusticpipes.client;

import net.minecraft.block.state.IBlockState;
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
import rusticpipes.block.BlockFluidTankMultiblock;
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

        TextureAtlasSprite vpBotSpr      = spr("rusticpipes:blocks/fluid_tank/fluid_tank_viewport_bottom");
        TextureAtlasSprite vpCtrSpr      = spr("rusticpipes:blocks/fluid_tank/fluid_tank_viewport_center");
        TextureAtlasSprite vpTopSpr      = spr("rusticpipes:blocks/fluid_tank/fluid_tank_viewport_top");
        TextureAtlasSprite innerBotSpr   = spr("rusticpipes:blocks/fluid_tank/fluid_tank_inner_viewport_bottom");
        TextureAtlasSprite innerCtrSpr   = spr("rusticpipes:blocks/fluid_tank/fluid_tank_inner_viewport_center");
        TextureAtlasSprite innerTopSpr   = spr("rusticpipes:blocks/fluid_tank/fluid_tank_inner_viewport_top");

        BlockPos sMin = st.min, sMax = st.max;
        int sz = st.baseSize;

        double minX, minY, minZ, maxX, maxZ, wallH;
        boolean hollow = (sz >= 3) && (sMax.getY() - sMin.getY() >= 2);
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
        int totalH = sMax.getY() - sMin.getY() + 1;

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        // Render inner viewport faces for every viewport block in the structure
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);
        GlStateManager.color(1f, 1f, 1f, 1f);
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        for (BlockPos p : st.allPositions()) {
            IBlockState state = getWorld().getBlockState(p);
            if (!(state.getBlock() instanceof BlockFluidTankMultiblock)) continue;
            BlockFluidTankMultiblock.ViewportFace face = state.getValue(BlockFluidTankMultiblock.VIEWPORT);
            if (face == BlockFluidTankMultiblock.ViewportFace.NONE) continue;

            renderInnerFace(buf, pos, p, sMin.getY(), sMax.getY(), totalH, face, innerBotSpr, innerCtrSpr, innerTopSpr);
        }

        tess.draw();

        // Fluid
        GlStateManager.color(1f, 1f, 1f, 1f);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

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
     * Renders the inner viewport face for a single block, just inside its exterior face.
     * Uses inner_viewport texture, positioned epsilon inside the block surface.
     */
    private void renderInnerFace(BufferBuilder buf, BlockPos ctrl, BlockPos p,
                                  int minY, int maxY, int totalH,
                                  BlockFluidTankMultiblock.ViewportFace face,
                                  TextureAtlasSprite innerBot, TextureAtlasSprite innerCtr,
                                  TextureAtlasSprite innerTop) {
        float eps = 0.002f;
        int by = p.getY();
        TextureAtlasSprite s;
        if (totalH == 1 || by == minY) { s = innerBot; }
        else if (by == maxY)           { s = innerTop; }
        else                           { s = innerCtr; }

        float lx1 = p.getX() - ctrl.getX();
        float lx2 = lx1 + 1f;
        float lz1 = p.getZ() - ctrl.getZ();
        float lz2 = lz1 + 1f;
        float ly1 = by - ctrl.getY();
        float ly2 = by - ctrl.getY() + 1f;

        String faceName = face.getName();
        if      (faceName.startsWith("north")) putQ(buf, lx2,ly1,lz1+eps, lx1,ly1,lz1+eps, lx1,ly2,lz1+eps, lx2,ly2,lz1+eps, s);
        else if (faceName.startsWith("south")) putQ(buf, lx1,ly1,lz2-eps, lx2,ly1,lz2-eps, lx2,ly2,lz2-eps, lx1,ly2,lz2-eps, s);
        else if (faceName.startsWith("west"))  putQ(buf, lx1+eps,ly1,lz2, lx1+eps,ly1,lz1, lx1+eps,ly2,lz1, lx1+eps,ly2,lz2, s);
        else if (faceName.startsWith("east"))  putQ(buf, lx2-eps,ly1,lz1, lx2-eps,ly1,lz2, lx2-eps,ly2,lz2, lx2-eps,ly2,lz1, s);
    }

    private TextureAtlasSprite spr(String name) {
        TextureAtlasSprite s = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);
        return s != null ? s : Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
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
}
