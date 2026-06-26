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
import rusticpipes.tileentity.TileEntityFluidPipe;

@SideOnly(Side.CLIENT)
public class FluidPipeRenderer extends TileEntitySpecialRenderer<TileEntityFluidPipe> {

    private static final float BORE_MIN  = 4f / 16f + 0.002f;  // inner walls, as close as possible without z-fighting
    private static final float BORE_MAX  = 12f / 16f - 0.002f;
    private static final float FLUID_MIN = 4f / 16f + 0.01f;   // fluid, just inside inner walls
    private static final float FLUID_MAX = 12f / 16f - 0.01f;

    @Override
    public void render(TileEntityFluidPipe te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {
        BlockPos pos = te.getPos();

        // Check if any face is a viewport — same logic as FluidPipeModel
        int texBase = Math.abs((pos.getX() * 73856093) ^ (pos.getY() * 19349663) ^ (pos.getZ() * 83492791));
        boolean vpN = ((texBase + 3)  % 20) % 10 < 3;
        boolean vpS = ((texBase + 7)  % 20) % 10 < 3;
        boolean vpE = ((texBase + 11) % 20) % 10 < 3;
        boolean vpW = ((texBase + 13) % 20) % 10 < 3;
        if (!vpN && !vpS && !vpE && !vpW) return; // no viewport faces

        // Fluid state
        FluidStack buffer = te.getBuffer();
        float fillFraction = (buffer != null && buffer.amount > 0)
                ? (float) buffer.amount / te.getBufferCapacity()
                : 0f;

        // Fluid color
        int fluidColor = te.getFluidColor();
        float fr = 0.2f, fg = 0.6f, fb = 1.0f, fa2 = 0.85f; // default blue
        if (fluidColor != 0) {
            fr = ((fluidColor >> 16) & 0xFF) / 255f;
            fg = ((fluidColor >> 8)  & 0xFF) / 255f;
            fb = (fluidColor & 0xFF)         / 255f;
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        GlStateManager.disableLighting();
        int combinedLight = getWorld().getCombinedLight(te.getPos().up(), 0);
        int lightU = (combinedLight >> 4) & 0xF;   // block light 0-15
        int lightV = (combinedLight >> 20) & 0xF; // sky light 0-15
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightU * 16f, lightV * 16f);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        float bMin = BORE_MIN, bMax = BORE_MAX;

        // ---- Inner pipe walls ----
        TextureAtlasSprite bodyInner = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite("rusticpipes:blocks/fluid_pipes/pipe_body_inner");
        TextureAtlasSprite vpInner = Minecraft.getMinecraft().getTextureMapBlocks()
                .getAtlasSprite("rusticpipes:blocks/fluid_pipes/pipe_viewport_inner");
        if (bodyInner == null) bodyInner = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
        if (vpInner == null)   vpInner   = bodyInner;

        // Per-face: viewport face gets vpInner, solid face gets bodyInner
        // Top/bottom always use bodyInner
        TextureAtlasSprite sprN = vpN ? vpInner : bodyInner;
        TextureAtlasSprite sprS = vpS ? vpInner : bodyInner;
        TextureAtlasSprite sprE = vpE ? vpInner : bodyInner;
        TextureAtlasSprite sprW = vpW ? vpInner : bodyInner;

        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
        // North inner wall (faces inward toward +Z)
        putQuad(buf, bMin,bMin,bMin, bMax,bMin,bMin, bMax,bMax,bMin, bMin,bMax,bMin,
                sprN.getMinU(),sprN.getMinV(),sprN.getMaxU(),sprN.getMaxV(), 1f,1f,1f,1f);
        // South inner wall
        putQuad(buf, bMax,bMin,bMax, bMin,bMin,bMax, bMin,bMax,bMax, bMax,bMax,bMax,
                sprS.getMinU(),sprS.getMinV(),sprS.getMaxU(),sprS.getMaxV(), 1f,1f,1f,1f);
        // West inner wall
        putQuad(buf, bMin,bMin,bMax, bMin,bMin,bMin, bMin,bMax,bMin, bMin,bMax,bMax,
                sprW.getMinU(),sprW.getMinV(),sprW.getMaxU(),sprW.getMaxV(), 1f,1f,1f,1f);
        // East inner wall
        putQuad(buf, bMax,bMin,bMin, bMax,bMin,bMax, bMax,bMax,bMax, bMax,bMax,bMin,
                sprE.getMinU(),sprE.getMinV(),sprE.getMaxU(),sprE.getMaxV(), 1f,1f,1f,1f);
        // Bottom inner wall
        putQuad(buf, bMin,bMin,bMax, bMax,bMin,bMax, bMax,bMin,bMin, bMin,bMin,bMin,
                bodyInner.getMinU(),bodyInner.getMinV(),bodyInner.getMaxU(),bodyInner.getMaxV(), 1f,1f,1f,1f);
        // Top inner wall
        putQuad(buf, bMin,bMax,bMin, bMax,bMax,bMin, bMax,bMax,bMax, bMin,bMax,bMax,
                bodyInner.getMinU(),bodyInner.getMinV(),bodyInner.getMaxU(),bodyInner.getMaxV(), 1f,1f,1f,1f);
        tess.draw();

        // ---- Fluid level ----
        if (fillFraction > 0.01f && buffer != null && buffer.getFluid() != null) {
            float fMin = FLUID_MIN, fMax = FLUID_MAX;
            TextureAtlasSprite fluidSprite = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite(buffer.getFluid().getStill(buffer).toString());
            if (fluidSprite == null) fluidSprite = Minecraft.getMinecraft()
                    .getTextureMapBlocks().getMissingSprite();

            float fluidTop = fMin + (fMax - fMin) * fillFraction;
            float fu0 = fluidSprite.getMinU(), fv0 = fluidSprite.getMinV();
            float fu1 = fluidSprite.getMaxU(), fv1 = fluidSprite.getMaxV();

            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            putQuad(buf, fMin,fluidTop,fMax, fMax,fluidTop,fMax, fMax,fluidTop,fMin, fMin,fluidTop,fMin, fu0,fv0,fu1,fv1, fr,fg,fb,fa2);
            putQuad(buf, fMax,fMin,fMin, fMin,fMin,fMin, fMin,fluidTop,fMin, fMax,fluidTop,fMin, fu0,fv0,fu1,fv1, fr*0.8f,fg*0.8f,fb*0.8f,fa2);
            putQuad(buf, fMin,fMin,fMax, fMax,fMin,fMax, fMax,fluidTop,fMax, fMin,fluidTop,fMax, fu0,fv0,fu1,fv1, fr*0.8f,fg*0.8f,fb*0.8f,fa2);
            putQuad(buf, fMin,fMin,fMin, fMin,fMin,fMax, fMin,fluidTop,fMax, fMin,fluidTop,fMin, fu0,fv0,fu1,fv1, fr*0.7f,fg*0.7f,fb*0.7f,fa2);
            putQuad(buf, fMax,fMin,fMax, fMax,fMin,fMin, fMax,fluidTop,fMin, fMax,fluidTop,fMax, fu0,fv0,fu1,fv1, fr*0.7f,fg*0.7f,fb*0.7f,fa2);
            tess.draw();
        }

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void putQuad(BufferBuilder buf,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         float x3, float y3, float z3,
                         float x4, float y4, float z4,
                         float u0, float v0, float u1, float v1,
                         float r, float g, float b, float a) {
        buf.pos(x1,y1,z1).tex(u0,v1).color(r,g,b,a).endVertex();
        buf.pos(x2,y2,z2).tex(u1,v1).color(r,g,b,a).endVertex();
        buf.pos(x3,y3,z3).tex(u1,v0).color(r,g,b,a).endVertex();
        buf.pos(x4,y4,z4).tex(u0,v0).color(r,g,b,a).endVertex();
    }
}
