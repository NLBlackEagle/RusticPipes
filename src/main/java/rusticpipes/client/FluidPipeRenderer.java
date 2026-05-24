package rusticpipes.client;

import net.minecraft.block.state.IBlockState;
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
import rusticpipes.tileentity.TileEntityFluidPipe;

@SideOnly(Side.CLIENT)
public class FluidPipeRenderer extends TileEntitySpecialRenderer<TileEntityFluidPipe> {

    // Inner bore dimensions — inset enough from pipe core (4/16) to avoid z-fighting
    private static final float BORE_MIN = 6f / 16f;
    private static final float BORE_MAX = 10f / 16f;

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

        // Get the body sprite this pipe uses on its north face (most visible)
        int idxN = (texBase + 3) % 20;
        String spriteName = "rusticpipes:blocks/fluid_pipes/pipe_body_" + String.format("%02d", idxN + 1);
        TextureAtlasSprite bodySprite = Minecraft.getMinecraft()
                .getTextureMapBlocks().getAtlasSprite(spriteName);
        if (bodySprite == null) return;

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
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableCull();

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();

        // ---- Inner pipe walls ----
        float u0 = bodySprite.getMinU(), v0 = bodySprite.getMinV();
        float u1 = bodySprite.getMaxU(), v1 = bodySprite.getMaxV();
        float bMin = BORE_MIN, bMax = BORE_MAX;

        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        // North inner wall
        putQuad(buf, bMax,bMin,bMin, bMin,bMin,bMin, bMin,bMax,bMin, bMax,bMax,bMin, u0,v0,u1,v1, 0.6f,0.6f,0.6f,1f);
        // South inner wall
        putQuad(buf, bMin,bMin,bMax, bMax,bMin,bMax, bMax,bMax,bMax, bMin,bMax,bMax, u0,v0,u1,v1, 0.6f,0.6f,0.6f,1f);
        // West inner wall
        putQuad(buf, bMin,bMin,bMin, bMin,bMin,bMax, bMin,bMax,bMax, bMin,bMax,bMin, u0,v0,u1,v1, 0.5f,0.5f,0.5f,1f);
        // East inner wall
        putQuad(buf, bMax,bMin,bMax, bMax,bMin,bMin, bMax,bMax,bMin, bMax,bMax,bMax, u0,v0,u1,v1, 0.5f,0.5f,0.5f,1f);
        // Bottom inner wall
        putQuad(buf, bMin,bMin,bMin, bMax,bMin,bMin, bMax,bMin,bMax, bMin,bMin,bMax, u0,v0,u1,v1, 0.4f,0.4f,0.4f,1f);
        // Top inner wall
        putQuad(buf, bMin,bMax,bMax, bMax,bMax,bMax, bMax,bMax,bMin, bMin,bMax,bMin, u0,v0,u1,v1, 0.8f,0.8f,0.8f,1f);

        tess.draw();

        // ---- Fluid level ----
        if (fillFraction > 0.01f) {
            TextureAtlasSprite fluidSprite = (buffer != null && buffer.getFluid() != null)
                    ? Minecraft.getMinecraft().getTextureMapBlocks()
                        .getAtlasSprite(buffer.getFluid().getStill(buffer).toString())
                    : bodySprite;

            float fluidTop = bMin + (bMax - bMin) * fillFraction;
            float fu0 = fluidSprite.getMinU(), fv0 = fluidSprite.getMinV();
            float fu1 = fluidSprite.getMaxU(), fv1 = fluidSprite.getMaxV();

            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);
            // Top surface of fluid
            putQuad(buf, bMin,fluidTop,bMax, bMax,fluidTop,bMax, bMax,fluidTop,bMin, bMin,fluidTop,bMin, fu0,fv0,fu1,fv1, fr,fg,fb,fa2);
            // North face of fluid
            putQuad(buf, bMax,bMin,bMin, bMin,bMin,bMin, bMin,fluidTop,bMin, bMax,fluidTop,bMin, fu0,fv0,fu1,fv1, fr*0.8f,fg*0.8f,fb*0.8f,fa2);
            // South face of fluid
            putQuad(buf, bMin,bMin,bMax, bMax,bMin,bMax, bMax,fluidTop,bMax, bMin,fluidTop,bMax, fu0,fv0,fu1,fv1, fr*0.8f,fg*0.8f,fb*0.8f,fa2);
            // West face of fluid
            putQuad(buf, bMin,bMin,bMin, bMin,bMin,bMax, bMin,fluidTop,bMax, bMin,fluidTop,bMin, fu0,fv0,fu1,fv1, fr*0.7f,fg*0.7f,fb*0.7f,fa2);
            // East face of fluid
            putQuad(buf, bMax,bMin,bMax, bMax,bMin,bMin, bMax,fluidTop,bMin, bMax,fluidTop,bMax, fu0,fv0,fu1,fv1, fr*0.7f,fg*0.7f,fb*0.7f,fa2);
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
