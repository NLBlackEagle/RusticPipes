package rusticpipes.client;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Ensures all inner tank textures are registered with the texture atlas.
 */
@Mod.EventBusSubscriber(modid = "rusticpipes", value = Side.CLIENT)
@SideOnly(Side.CLIENT)
public class TextureStitchHandler {
    
    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        if (!event.getMap().getBasePath().equals("textures/atlases/blocks")) return;
        
        // Register inner side textures (2x1-4x10)
        for (int base = 2; base <= 4; base++) {
            for (int height = 1; height <= 10; height++) {
                event.getMap().registerSprite(new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_inner_side_" + base + "x" + height));
            }
        }
        
        // Register inner top/bottom textures (2x2, 3x3, 4x4)
        for (int base = 2; base <= 4; base++) {
            event.getMap().registerSprite(new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_inner_top_" + base + "x" + base));
            event.getMap().registerSprite(new ResourceLocation("rusticpipes:blocks/fluid_tank/fluid_tank_inner_bottom_" + base + "x" + base));
        }
    }
}
