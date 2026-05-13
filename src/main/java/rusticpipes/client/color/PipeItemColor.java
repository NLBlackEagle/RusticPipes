package rusticpipes.client.color;

import net.minecraft.client.renderer.color.IItemColor;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import rusticpipes.block.BlockItemPipe;

public class PipeItemColor implements IItemColor {

    public static final PipeItemColor INSTANCE = new PipeItemColor();

    @Override
    public int colorMultiplier(ItemStack stack, int tintIndex) {
        if (tintIndex != 0) return 0xFFFFFF;
        if (!(stack.getItem() instanceof ItemBlock)) return 0xFFFFFF;
        ItemBlock ib = (ItemBlock) stack.getItem();
        if (!(ib.getBlock() instanceof BlockItemPipe)) return 0xFFFFFF;
        return TintHelper.attenuate(((BlockItemPipe) ib.getBlock()).pipeColor.tintColor, 0.4f);
    }
}
