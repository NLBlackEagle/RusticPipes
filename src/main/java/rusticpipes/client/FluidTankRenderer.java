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
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.opengl.GL11;
import rusticpipes.multiblock.TankMultiblock;
import rusticpipes.tileentity.TileEntityFluidTank;

@SideOnly(Side.CLIENT)
public class FluidTankRenderer extends TileEntitySpecialRenderer<TileEntityFluidTank> {

    @Override
    public void render(TileEntityFluidTank te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {

        // Only render from the controller (or single block)
        if (!te.isController() && te.isPartOfMultiblock()) return;

        FluidStack fluid = te.getFluid();
        float fill = (fluid != null && fluid.amount > 0) ? te.getFillFraction() : 0f;

        // Get fluid texture (may be null if tank is empty)
        TextureAtlasSprite sprite = null;
        if (fluid != null && fluid.getFluid() != null) {
            sprite = Minecraft.getMinecraft().getTextureMapBlocks()
                    .getAtlasSprite(fluid.getFluid().getStill(fluid).toString());
        }

        // Determine the interior bounds of the tank
        // For a single block: small interior (0.125 – 0.875)
        // For a multiblock: span the entire interior hollow
        BlockPos pos = te.getPos();
        double minX, minY, minZ, maxX, maxY, maxZ;

        TileEntityFluidTank ctrl = te; // already ensured we're controller
        int capacity = ctrl.getCapacity();

        // We need the multiblock bounds — read from controllerPos and scan for extent
        // For simplicity use the world to find the bounding box
        // by scanning for connected tank blocks
        if (te.isPartOfMultiblock()) {
            // Compute bounds from the structure (re-validate is cheap for rendering)
            TankMultiblock.Structure structure = TankMultiblock.validate(getWorld(), pos);
            if (structure == null) return;

            net.minecraft.util.math.BlockPos sMin = structure.min;
            net.minecraft.util.math.BlockPos sMax = structure.max;

            // Interior: one block inside each wall
            int innerMinX = sMin.getX() + 1;
            int innerMinZ = sMin.getZ() + 1;
            int innerMaxX = sMax.getX();
            int innerMaxZ = sMax.getZ();
            int innerMinY = sMin.getY() + 1;
            int innerMaxY = sMax.getY();

            if (structure.baseSize <= 2) {
                // Fully solid — render inside the full block footprint
                innerMinX = sMin.getX();
                innerMinZ = sMin.getZ();
                innerMaxX = sMax.getX() + 1;
                innerMaxZ = sMax.getZ() + 1;
                innerMinY = sMin.getY();
                innerMaxY = sMax.getY() + 1;
            }

            minX = (innerMinX - pos.getX()) + 0.01;
            minZ = (innerMinZ - pos.getZ()) + 0.01;
            maxX = (innerMaxX - pos.getX()) - 0.01;
            maxZ = (innerMaxZ - pos.getZ()) - 0.01;
            minY = (innerMinY - pos.getY()) + 0.01;
            maxY = minY + (innerMaxY - innerMinY - 0.02) * fill;
        } else {
            // Single block
            minX = 0.18; maxX = 0.82;
            minZ = 0.18; maxZ = 0.82;
            minY = 0.04;
            maxY = minY + (0.68 * fill);
        }

        // Get tank textures for inner walls
        TextureAtlasSprite wallSprite = Minecraft.getMinecraft()
                .getTextureMapBlocks()
                .getAtlasSprite("rusticpipes:blocks/fluid_tank/fluid_tank_inner_viewport");
        if (wallSprite == null) wallSprite = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
        TextureAtlasSprite solidSprite = Minecraft.getMinecraft()
                .getTextureMapBlocks()
                .getAtlasSprite("rusticpipes:blocks/fluid_tank/fluid_tank_solid");
        if (solidSprite == null) solidSprite = wallSprite;

        // Render
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();

        // ---- Inner walls — always rendered ----
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buf = tessellator.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        float wx1 = (float)minX, wx2 = (float)maxX;
        float wy1 = (float)minY, wy2 = (float)(minY + (maxX - minX)); // full height
        float wz1 = (float)minZ, wz2 = (float)maxZ;
        float wTop = (float)(te.isPartOfMultiblock() ? (minY + (maxX - minX)) : 0.99);

        // Per-face sprite: use solid if adjacent block is opaque, else inner_viewport
        net.minecraft.world.World tankWorld = te.getWorld();
        BlockPos tPos = te.getPos();
        TextureAtlasSprite sprN = tankWorld.getBlockState(tPos.north()).isOpaqueCube() ? solidSprite : wallSprite;
        TextureAtlasSprite sprS = tankWorld.getBlockState(tPos.south()).isOpaqueCube() ? solidSprite : wallSprite;
        TextureAtlasSprite sprE = tankWorld.getBlockState(tPos.east()).isOpaqueCube()  ? solidSprite : wallSprite;
        TextureAtlasSprite sprW = tankWorld.getBlockState(tPos.west()).isOpaqueCube()  ? solidSprite : wallSprite;



        // Bottom and top — solid texture
        float su0 = solidSprite.getMinU(), su1 = solidSprite.getMaxU();
        float sv0 = solidSprite.getMinV(), sv1 = solidSprite.getMaxV();
        putQuad(buf, wx1,wy1,wz1, wx1,wy1,wz2, wx2,wy1,wz2, wx2,wy1,wz1, su0,sv0,su1,sv1, 1f,1f,1f,1f);
putQuad(buf, wx2,wy1,wz1, wx2,wy1,wz2, wx1,wy1,wz2, wx1,wy1,wz1, su0,sv0,su1,sv1, 1f,1f,1f,1f);
        putQuad(buf, wx1,wTop,wz1, wx2,wTop,wz1, wx2,wTop,wz2, wx1,wTop,wz2, su0,sv0,su1,sv1, 1f,1f,1f,1f);
        putQuad(buf, wx1,wy1,wz1, wx2,wy1,wz1, wx2,wTop,wz1, wx1,wTop,wz1, sprN.getMinU(),sprN.getMinV(),sprN.getMaxU(),sprN.getMaxV(), 1f,1f,1f,1f);
        putQuad(buf, wx2,wy1,wz2, wx1,wy1,wz2, wx1,wTop,wz2, wx2,wTop,wz2, sprS.getMinU(),sprS.getMinV(),sprS.getMaxU(),sprS.getMaxV(), 1f,1f,1f,1f);
        putQuad(buf, wx1,wy1,wz2, wx1,wy1,wz1, wx1,wTop,wz1, wx1,wTop,wz2, sprW.getMinU(),sprW.getMinV(),sprW.getMaxU(),sprW.getMaxV(), 1f,1f,1f,1f);
        putQuad(buf, wx2,wy1,wz1, wx2,wy1,wz2, wx2,wTop,wz2, wx2,wTop,wz1, sprE.getMinU(),sprE.getMinV(),sprE.getMaxU(),sprE.getMaxV(), 1f,1f,1f,1f);
        tessellator.draw();

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        // Force full brightness for fluid rendering
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240f, 240f);

        // ---- Fluid ----
        if (fluid == null || fluid.amount <= 0) {
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.disableBlend();
            GlStateManager.popMatrix();
            return;
        }

        int color = fluid.getFluid().getColor(fluid);
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = (color & 0xFF)         / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0f) a = 0.8f; // default alpha if fluid has no alpha channel

        // Fluid bounds — inset from inner walls to avoid z-fighting
        double fMinX = minX + 0.02, fMaxX = maxX - 0.02;
        double fMinZ = minZ + 0.02, fMaxZ = maxZ - 0.02;
        double fMinY = minY + 0.01;

        float u1 = sprite.getMinU(), u2 = sprite.getMaxU();
        float v1 = sprite.getMinV(), v2 = sprite.getMaxV();

        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        // Top face
        buf.pos(fMinX, maxY, fMinZ).tex(u1, v1).color(r, g, b, a).endVertex();
        buf.pos(fMinX, maxY, fMaxZ).tex(u1, v2).color(r, g, b, a).endVertex();
        buf.pos(fMaxX, maxY, fMaxZ).tex(u2, v2).color(r, g, b, a).endVertex();
        buf.pos(fMaxX, maxY, fMinZ).tex(u2, v1).color(r, g, b, a).endVertex();

        // Bottom face
        buf.pos(fMinX, fMinY, fMaxZ).tex(u1, v2).color(r, g, b, a).endVertex();
        buf.pos(fMinX, fMinY, fMinZ).tex(u1, v1).color(r, g, b, a).endVertex();
        buf.pos(fMaxX, fMinY, fMinZ).tex(u2, v1).color(r, g, b, a).endVertex();
        buf.pos(fMaxX, fMinY, fMaxZ).tex(u2, v2).color(r, g, b, a).endVertex();

        // North face
        buf.pos(fMaxX, fMinY, fMinZ).tex(u2, v2).color(r, g, b, a).endVertex();
        buf.pos(fMinX, fMinY, fMinZ).tex(u1, v2).color(r, g, b, a).endVertex();
        buf.pos(fMinX, maxY,  fMinZ).tex(u1, v1).color(r, g, b, a).endVertex();
        buf.pos(fMaxX, maxY,  fMinZ).tex(u2, v1).color(r, g, b, a).endVertex();

        // South face
        buf.pos(fMinX, fMinY, fMaxZ).tex(u1, v2).color(r, g, b, a).endVertex();
        buf.pos(fMaxX, fMinY, fMaxZ).tex(u2, v2).color(r, g, b, a).endVertex();
        buf.pos(fMaxX, maxY,  fMaxZ).tex(u2, v1).color(r, g, b, a).endVertex();
        buf.pos(fMinX, maxY,  fMaxZ).tex(u1, v1).color(r, g, b, a).endVertex();

        // West face
        buf.pos(fMinX, fMinY, fMinZ).tex(u1, v2).color(r, g, b, a).endVertex();
        buf.pos(fMinX, fMinY, fMaxZ).tex(u2, v2).color(r, g, b, a).endVertex();
        buf.pos(fMinX, maxY,  fMaxZ).tex(u2, v1).color(r, g, b, a).endVertex();
        buf.pos(fMinX, maxY,  fMinZ).tex(u1, v1).color(r, g, b, a).endVertex();

        // East face
        buf.pos(fMaxX, fMinY, fMaxZ).tex(u1, v2).color(r, g, b, a).endVertex();
        buf.pos(fMaxX, fMinY, fMinZ).tex(u2, v2).color(r, g, b, a).endVertex();
        buf.pos(fMaxX, maxY,  fMinZ).tex(u2, v1).color(r, g, b, a).endVertex();
        buf.pos(fMaxX, maxY,  fMaxZ).tex(u1, v1).color(r, g, b, a).endVertex();

        tessellator.draw();

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
