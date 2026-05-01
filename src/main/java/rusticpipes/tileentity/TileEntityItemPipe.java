package rusticpipes.tileentity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

public class TileEntityItemPipe extends TileEntity {

    public boolean isConnected(EnumFacing face) {
        return false; // placeholder
    }

    public void refreshConnections() {
        // placeholder
    }

    public void onRemoved() {
        // placeholder
    }
}