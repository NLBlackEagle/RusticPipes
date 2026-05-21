package rusticpipes.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;

/**
 * Sent server → client whenever a conduit network's power state crosses the
 * empty/non-empty boundary (i.e. lastTier changes).  One packet per member
 * conduit block so each block can update its own entry in ConduitClientState
 * and trigger a re-render via notifyBlockUpdate.
 */
public class PacketConduitPower implements IMessage {

    private long packedPos;
    private boolean powered;

    /** Required by Forge. */
    public PacketConduitPower() {}

    public PacketConduitPower(BlockPos pos, boolean powered) {
        this.packedPos = pos.toLong();
        this.powered   = powered;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(packedPos);
        buf.writeBoolean(powered);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        packedPos = buf.readLong();
        powered   = buf.readBoolean();
    }

    // -----------------------------------------------------------------------
    // Handler — runs on the client thread via addScheduledTask
    // -----------------------------------------------------------------------

    public static class Handler implements IMessageHandler<PacketConduitPower, IMessage> {

        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(PacketConduitPower msg, MessageContext ctx) {
            BlockPos pos = BlockPos.fromLong(msg.packedPos);
            Minecraft.getMinecraft().addScheduledTask(() -> {
                ConduitClientState.set(pos, msg.powered);
                // notifyBlockUpdate flag 8 = re-render only, no neighbour updates
                net.minecraft.world.World world = Minecraft.getMinecraft().world;
                if (world != null) {
                    net.minecraft.block.state.IBlockState bs = world.getBlockState(pos);
                    world.markBlockRangeForRenderUpdate(pos, pos);
                }
            });
            return null;
        }
    }
}
