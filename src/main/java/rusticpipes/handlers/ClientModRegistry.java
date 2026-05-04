package rusticpipes.handlers;

import rusticpipes.handlers.ModRegistry;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import rusticpipes.RusticPipes;

@Mod.EventBusSubscriber(modid = RusticPipes.MODID, value = Side.CLIENT)
public class ClientModRegistry {

    @SubscribeEvent
    public static void modelRegisterEvent(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(
                Item.getItemFromBlock(ModRegistry.ITEM_PIPE),
                0,
                new ModelResourceLocation(ModRegistry.ITEM_PIPE.getRegistryName(), "inventory")
        );
    }
}