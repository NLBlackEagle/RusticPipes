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
import rusticpipes.tileentity.TileEntityFluidTank;

@SideOnly(Side.CLIENT)
public class FluidTankRenderer extends TileEntitySpecialRenderer<TileEntityFluidTank> {

    @Override
    public void render(TileEntityFluidTank te, double x, double y, double z,
                       float partialTicks, int destroyStage, float alpha) {

        // Only render from the controller, and only if we have fluid
        if (!te.isController() && te.isPartOfMultiblock()) return;

        FluidStack fluid = te.getFluid();
        if (fluid == null || fluid.amount <= 0) return;

        float fill = te.getFillFraction();
        if (fill <= 0.001f) return;

        // Get fluid texture
        TextureAtlasSprite sprite = Minecraft.getMinecraft()
                .getTextureMapBlocks()
                .getAtlasSprite(fluid.getFluid().getStill(fluid).toString());
        if (sprite == null) return;

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
            minX = 0.125; maxX = 0.875;
            minZ = 0.125; maxZ = 0.875;
            minY = 0.125;
            maxY = minY + (0.75 * fill);
        }

        // Render
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);

        Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();

        int color = fluid.getFluid().getColor(fluid);
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8)  & 0xFF) / 255f;
        float b = (color & 0xFF)         / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;
        if (a == 0f) a = 0.8f; // default alpha if fluid has no alpha channel

        float u1 = sprite.getMinU(), u2 = sprite.getMaxU();
        float v1 = sprite.getMinV(), v2 = sprite.getMaxV();

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buf = tessellator.getBuffer();
        buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR);

        // Top face
        buf.pos(minX, maxY, minZ).tex(u1, v1).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, maxZ).tex(u1, v2).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, maxZ).tex(u2, v2).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, minZ).tex(u2, v1).color(r, g, b, a).endVertex();

        // Bottom face
        buf.pos(minX, minY, maxZ).tex(u1, v2).color(r, g, b, a).endVertex();
        buf.pos(minX, minY, minZ).tex(u1, v1).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, minZ).tex(u2, v1).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, maxZ).tex(u2, v2).color(r, g, b, a).endVertex();

        // North face
        buf.pos(maxX, minY, minZ).tex(u2, v2).color(r, g, b, a).endVertex();
        buf.pos(minX, minY, minZ).tex(u1, v2).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, minZ).tex(u1, v1).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, minZ).tex(u2, v1).color(r, g, b, a).endVertex();

        // South face
        buf.pos(minX, minY, maxZ).tex(u1, v2).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, maxZ).tex(u2, v2).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, maxZ).tex(u2, v1).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, maxZ).tex(u1, v1).color(r, g, b, a).endVertex();

        // West face
        buf.pos(minX, minY, minZ).tex(u1, v2).color(r, g, b, a).endVertex();
        buf.pos(minX, minY, maxZ).tex(u2, v2).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, maxZ).tex(u2, v1).color(r, g, b, a).endVertex();
        buf.pos(minX, maxY, minZ).tex(u1, v1).color(r, g, b, a).endVertex();

        // East face
        buf.pos(maxX, minY, maxZ).tex(u1, v2).color(r, g, b, a).endVertex();
        buf.pos(maxX, minY, minZ).tex(u2, v2).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, minZ).tex(u2, v1).color(r, g, b, a).endVertex();
        buf.pos(maxX, maxY, maxZ).tex(u1, v1).color(r, g, b, a).endVertex();

        tessellator.draw();

        GlStateManager.enableCull();
        GlStateManager.enableLighting();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }
}
