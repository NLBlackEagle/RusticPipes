package rusticpipes.client.color;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.color.IBlockColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import rusticpipes.block.BlockFluidPipe;
import rusticpipes.block.BlockItemPipe;

import javax.annotation.Nullable;

public class PipeBlockColor implements IBlockColor {

    public static final PipeBlockColor INSTANCE = new PipeBlockColor();

    @Override
    public int colorMultiplier(IBlockState state, @Nullable IBlockAccess worldIn,
                               @Nullable BlockPos pos, int tintIndex) {
        if (tintIndex != 0) return 0xFFFFFF;
        if (state.getBlock() instanceof BlockItemPipe)
            return TintHelper.attenuate(((BlockItemPipe) state.getBlock()).pipeColor.tintColor, 0.4f);
        if (state.getBlock() instanceof BlockFluidPipe)
            return TintHelper.attenuate(((BlockFluidPipe) state.getBlock()).pipeColor.tintColor, 0.4f);
        return 0xFFFFFF;
    }
}
