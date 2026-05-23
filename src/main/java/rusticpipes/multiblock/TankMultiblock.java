package rusticpipes.multiblock;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import rusticpipes.block.BlockFluidTank;
import rusticpipes.tileentity.TileEntityFluidTank;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Set;

/**
 * Validates and describes a fluid tank multiblock structure.
 *
 * Rules:
 *  - Base footprint: 1×1, 2×2, 3×3, or 4×4 (inner hollow for sizes ≥ 3×3)
 *  - Height: 1–7 blocks
 *  - Structure is a hollow rectangular prism — walls, floor, ceiling are solid;
 *    interior is air for sizes ≥ 3×3 (1×1 and 2×2 are always solid)
 *  - All blocks must be BlockFluidTank
 *
 * Roles assigned per block position:
 *  SINGLE  — 1×1×1 structure (acts as a pipe with buffer)
 *  BOTTOM  — floor layer (output connections allowed on sides)
 *  TOP     — ceiling layer (input connections on top or sides)
 *  WALL    — non-corner, non-edge wall block
 *  CORNER  — corner block (both horizontal axes are at min/max)
 *  EDGE    — edge block (one horizontal axis is at min/max, the other is not)
 */
public class TankMultiblock {

    public enum Role { SINGLE, BOTTOM, TOP, WALL, CORNER, EDGE }

    /** Describes a validated multiblock. */
    public static final class Structure {
        /** Min corner of the bounding box. */
        public final BlockPos min;
        /** Max corner of the bounding box. */
        public final BlockPos max;
        /** Total number of member blocks. */
        public final int blockCount;
        /** Footprint size (1–4). */
        public final int baseSize;
        /** Height in blocks (1–7). */
        public final int height;
        /** The controller pos — always the min corner. */
        public final BlockPos controller;

        public Structure(BlockPos min, BlockPos max, int blockCount, int baseSize, int height) {
            this.min = min;
            this.max = max;
            this.blockCount = blockCount;
            this.baseSize = baseSize;
            this.height = height;
            this.controller = min;
        }

        /** Returns the role of a given position within this structure. */
        public Role roleOf(BlockPos pos) {
            if (blockCount == 1) return Role.SINGLE;

            boolean isBottom = pos.getY() == min.getY();
            boolean isTop    = pos.getY() == max.getY();
            boolean isMinX   = pos.getX() == min.getX();
            boolean isMaxX   = pos.getX() == max.getX();
            boolean isMinZ   = pos.getZ() == min.getZ();
            boolean isMaxZ   = pos.getZ() == max.getZ();
            boolean isEdgeX  = isMinX || isMaxX;
            boolean isEdgeZ  = isMinZ || isMaxZ;

            if (isBottom) {
                if (isEdgeX && isEdgeZ) return Role.CORNER;
                return Role.BOTTOM;
            }
            if (isTop) {
                if (isEdgeX && isEdgeZ) return Role.CORNER;
                return Role.TOP;
            }
            // Middle layers — only wall blocks exist (interior is hollow for base >= 3)
            if (isEdgeX && isEdgeZ) return Role.CORNER;
            if (isEdgeX || isEdgeZ) return Role.EDGE;
            return Role.WALL;
        }

        public Set<BlockPos> allPositions() {
            Set<BlockPos> positions = new HashSet<>();
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int y = min.getY(); y <= max.getY(); y++) {
                    for (int z = min.getZ(); z <= max.getZ(); z++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (isStructureMember(p)) positions.add(p);
                    }
                }
            }
            return positions;
        }

        /** Returns true if this position is part of the hollow shell (not interior air). */
        public boolean isStructureMember(BlockPos pos) {
            if (baseSize <= 2) return true; // 1×1 and 2×2 are fully solid
            boolean isBottom = pos.getY() == min.getY();
            boolean isTop    = pos.getY() == max.getY();
            boolean isMinX   = pos.getX() == min.getX();
            boolean isMaxX   = pos.getX() == max.getX();
            boolean isMinZ   = pos.getZ() == min.getZ();
            boolean isMaxZ   = pos.getZ() == max.getZ();
            // Must be on at least one face of the bounding box
            return isBottom || isTop || isMinX || isMaxX || isMinZ || isMaxZ;
        }
    }

    /**
     * Attempts to validate a multiblock structure starting from {@code origin}.
     * Scans outward to find the bounding box, then validates all expected positions.
     * Returns null if no valid structure is found.
     */
    @Nullable
    public static Structure validate(World world, BlockPos origin) {
        // Expand to find the full bounding box by flood-filling connected tank blocks
        BlockPos min = origin;
        BlockPos max = origin;

        Set<BlockPos> visited = new HashSet<>();
        java.util.Queue<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(origin);
        visited.add(origin);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            min = new BlockPos(
                    Math.min(min.getX(), current.getX()),
                    Math.min(min.getY(), current.getY()),
                    Math.min(min.getZ(), current.getZ()));
            max = new BlockPos(
                    Math.max(max.getX(), current.getX()),
                    Math.max(max.getY(), current.getY()),
                    Math.max(max.getZ(), current.getZ()));

            for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.VALUES) {
                BlockPos np = current.offset(face);
                if (!visited.contains(np) && world.getBlockState(np).getBlock() instanceof BlockFluidTank) {
                    visited.add(np);
                    queue.add(np);
                }
            }
        }

        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        // Validate dimensions
        if (sizeX != sizeZ) return null;           // must be square footprint
        if (sizeX < 1 || sizeX > 4) return null;  // 1×1 to 4×4
        if (sizeY < 1 || sizeY > 7) return null;  // 1 to 7 high

        int baseSize = sizeX;

        // Build the expected structure and verify all positions are tank blocks
        Structure candidate = new Structure(min, max, 0, baseSize, sizeY);
        Set<BlockPos> expected = candidate.allPositions();
        int blockCount = expected.size();

        for (BlockPos p : expected) {
            if (!(world.getBlockState(p).getBlock() instanceof BlockFluidTank)) return null;
        }

        // Also verify interior is air (for base >= 3)
        if (baseSize >= 3) {
            for (int x = min.getX() + 1; x <= max.getX() - 1; x++) {
                for (int y = min.getY() + 1; y <= max.getY() - 1; y++) {
                    for (int z = min.getZ() + 1; z <= max.getZ() - 1; z++) {
                        if (!world.isAirBlock(new BlockPos(x, y, z))) return null;
                    }
                }
            }
        }

        return new Structure(min, max, blockCount, baseSize, sizeY);
    }

    /**
     * Applies a validated structure to the world — updates all member TEs with
     * their role, controller pos, and total capacity.
     */
    public static void apply(World world, Structure structure) {
        int capacity = structure.blockCount * rusticpipes.handlers.ForgeConfigHandler.fluid.capacityPerTankBlock;

        for (BlockPos p : structure.allPositions()) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(p);
            if (!(te instanceof TileEntityFluidTank)) continue;
            TileEntityFluidTank tank = (TileEntityFluidTank) te;
            tank.onMultiblockFormed(structure.controller, structure.roleOf(p), capacity);
        }
    }

    /**
     * Invalidates all members of the structure the given position belongs to.
     * Called when any member block is broken.
     */
    public static void invalidate(World world, BlockPos pos) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityFluidTank)) return;
        TileEntityFluidTank tank = (TileEntityFluidTank) te;
        BlockPos controller = tank.getControllerPos();
        if (controller == null) return;

        // Collect all members that point to the same controller
        // We search a limited area around the controller (max 4×7×4)
        BlockPos min = controller.add(-4, 0, -4);
        BlockPos max = controller.add(4, 7, 4);
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    net.minecraft.tileentity.TileEntity member = world.getTileEntity(p);
                    if (!(member instanceof TileEntityFluidTank)) continue;
                    TileEntityFluidTank memberTank = (TileEntityFluidTank) member;
                    if (controller.equals(memberTank.getControllerPos())) {
                        memberTank.onMultiblockInvalidated();
                    }
                }
            }
        }
    }
}
