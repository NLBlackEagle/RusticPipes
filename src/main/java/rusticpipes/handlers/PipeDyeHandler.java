package rusticpipes.handlers;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemDye;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketAnimation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import rusticpipes.RusticPipes;
import rusticpipes.block.BlockFluidTankMultiblock;
import rusticpipes.block.BlockItemPipe;
import rusticpipes.block.PipeColor;
import rusticpipes.tileentity.TileEntityFluidTankMultiblock;
import rusticpipes.tileentity.TileEntityItemPipe;

/**
 * Handles shift+right-click pipe dyeing via {@link PlayerInteractEvent.RightClickBlock}.
 *
 * We cannot use {@code onBlockActivated} for this because Minecraft suppresses
 * block activation when the player is sneaking and holding an item that has its
 * own {@code onItemUse} (which {@code ItemDye} does). The Forge interact event
 * fires before that suppression, so we intercept it here instead.
 */
@Mod.EventBusSubscriber(modid = RusticPipes.MODID)
public class PipeDyeHandler {

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // Server side only
        if (event.getSide() == Side.CLIENT) return;
        if (!ForgeConfigHandler.recipes.enableInWorldDyeing) return;

        EntityPlayer player = event.getEntityPlayer();
        if (!player.isSneaking()) return;

        ItemStack held = event.getItemStack();
        if (held.isEmpty() || !(held.getItem() instanceof ItemDye)) return;

        World world = event.getWorld();
        BlockPos pos = event.getPos();
        Block block = world.getBlockState(pos).getBlock();
        if (!(block instanceof BlockItemPipe)) return;

        BlockItemPipe pipe = (BlockItemPipe) block;

        // ItemDye metadata is inverted vs EnumDyeColor ordinal — byDyeDamage handles this
        EnumDyeColor dyeColor = EnumDyeColor.byDyeDamage(held.getMetadata());
        PipeColor targetColor = PipeColor.fromDye(dyeColor);

        if (targetColor == pipe.pipeColor) return; // already this color, do nothing

        // Preserve face modes from the existing TE
        TileEntity oldTe = world.getTileEntity(pos);
        NBTTagCompound savedData = null;
        if (oldTe instanceof TileEntityItemPipe) {
            savedData = oldTe.writeToNBT(new NBTTagCompound());
        }

        // Swap block to the new color
        world.setBlockState(pos, ModRegistry.getPipe(targetColor).getDefaultState(), 3);

        // Restore face modes on the new TE
        if (savedData != null) {
            TileEntity newTe = world.getTileEntity(pos);
            if (newTe instanceof TileEntityItemPipe) {
                savedData.setInteger("x", pos.getX());
                savedData.setInteger("y", pos.getY());
                savedData.setInteger("z", pos.getZ());
                newTe.readFromNBT(savedData);
            }
        }

        // Consume one dye unless in creative
        if (!player.isCreative()) {
            held.shrink(1);
        }

        if (RusticPipes.DEBUG) {
            player.sendMessage(new TextComponentTranslation(
                    "rusticpipes.message.pipe.dyed", targetColor.displayName));
        }

        // Broadcast arm swing to all nearby clients
        if (player instanceof EntityPlayerMP && world instanceof WorldServer) {
            int animType = event.getHand() == net.minecraft.util.EnumHand.MAIN_HAND ? 0 : 3;
            SPacketAnimation packet = new SPacketAnimation(player, animType);
            ((EntityPlayerMP) player).connection.sendPacket(packet);
            ((WorldServer) world).getEntityTracker().sendToTracking(player, packet);
        }

        // Cancel the event so the dye's own onItemUse doesn't also fire
        event.setCanceled(true);
    }
}
