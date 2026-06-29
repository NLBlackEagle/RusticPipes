package rusticpipes.multiblock;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import rusticpipes.block.BlockFluidTank;
import rusticpipes.block.BlockFluidTankMultiblock;
import rusticpipes.tileentity.TileEntityFluidTankMultiblock;

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
    /** @deprecated Use applyMultiblock() for BlockFluidTankMultiblock. */
    public static void apply(World world, Structure structure) {
        // Single-block tanks no longer support multiblock — this is a no-op.
    }

    /**
     * Invalidates all members of the structure the given position belongs to.
     * Called when any member block is broken.
     */
    /** @deprecated Use invalidateMultiblock() for BlockFluidTankMultiblock. */
    public static void invalidate(World world, BlockPos pos) {
        // Single-block tanks no longer support multiblock — this is a no-op.
    }
    // -----------------------------------------------------------------------
    // Multiblock-block variants (BlockFluidTankMultiblock)
    // -----------------------------------------------------------------------

    /**
     * Validates a multiblock structure made of BlockFluidTankMultiblock blocks.
     * Footprints: 2x2, 3x3, 4x4. Height: 1-10.
     */
    @Nullable
    public static Structure validateMultiblock(World world, BlockPos origin) {
        BlockPos min = origin, max = origin;
        Set<BlockPos> visited = new HashSet<>();
        java.util.Queue<BlockPos> queue = new java.util.ArrayDeque<>();
        queue.add(origin); visited.add(origin);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            min = new BlockPos(Math.min(min.getX(), current.getX()),
                               Math.min(min.getY(), current.getY()),
                               Math.min(min.getZ(), current.getZ()));
            max = new BlockPos(Math.max(max.getX(), current.getX()),
                               Math.max(max.getY(), current.getY()),
                               Math.max(max.getZ(), current.getZ()));
            for (net.minecraft.util.EnumFacing face : net.minecraft.util.EnumFacing.VALUES) {
                BlockPos np = current.offset(face);
                if (!visited.contains(np)
                        && world.getBlockState(np).getBlock() instanceof BlockFluidTankMultiblock) {
                    visited.add(np); queue.add(np);
                }
            }
        }

        int sizeX = max.getX() - min.getX() + 1;
        int sizeY = max.getY() - min.getY() + 1;
        int sizeZ = max.getZ() - min.getZ() + 1;

        if (sizeX != sizeZ) return null;
        if (sizeX < 1 || sizeX > 4) return null;  // 1x1 to 4x4
        if (sizeY < 1 || sizeY > 10) return null; // 1 to 10 high

        int baseSize = sizeX;
        Structure candidate = new Structure(min, max, 0, baseSize, sizeY);
        Set<BlockPos> expected = candidate.allPositions();
        int blockCount = expected.size();

        for (BlockPos p : expected) {
            if (!(world.getBlockState(p).getBlock() instanceof BlockFluidTankMultiblock))
                return null;
        }

        // All structures are fully solid — no interior air check needed

        return new Structure(min, max, blockCount, baseSize, sizeY);
    }

    /** Applies a validated multiblock structure to TileEntityFluidTankMultiblock TEs. */
    public static void applyMultiblock(World world, Structure structure) {
        int capacity = structure.blockCount
                * rusticpipes.handlers.ForgeConfigHandler.fluid.capacityPerTankBlock;

        // Preserve fluid across revalidation — scan all positions for a TE that holds fluid
        // (getFluid() fails after invalidate since controllerPos is null, so use getRawFluid())
        net.minecraftforge.fluids.FluidStack preserved = null;
        for (BlockPos p : structure.allPositions()) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(p);
            if (!(te instanceof TileEntityFluidTankMultiblock)) continue;
            net.minecraftforge.fluids.FluidStack raw = ((TileEntityFluidTankMultiblock) te).getRawFluid();
            if (raw != null && raw.amount > 0) {
                if (preserved == null || raw.amount > preserved.amount) preserved = raw.copy();
                ((TileEntityFluidTankMultiblock) te).setRawFluid(null); // clear from old member
            }
        }
        // Trim to new capacity and assign to new controller
        net.minecraft.tileentity.TileEntity ctrlTe = world.getTileEntity(structure.controller);
        if (ctrlTe instanceof TileEntityFluidTankMultiblock && preserved != null) {
            if (preserved.amount > capacity) preserved.amount = capacity;
            ((TileEntityFluidTankMultiblock) ctrlTe).setRawFluid(preserved);
        }

        for (BlockPos p : structure.allPositions()) {
            net.minecraft.tileentity.TileEntity te = world.getTileEntity(p);
            if (!(te instanceof TileEntityFluidTankMultiblock)) continue;
            rusticpipes.block.BlockFluidTankMultiblock.ViewportFace face =
                    viewportFaceFor(p, structure);
            rusticpipes.block.BlockFluidTankMultiblock.ViewportRow row =
                    viewportRowFor(p, structure);
            net.minecraft.util.EnumFacing sideFace = sideFaceFor(p, structure, face);
            ((TileEntityFluidTankMultiblock) te).onMultiblockFormed(
                    structure.controller, structure.roleOf(p), capacity, structure.baseSize, row, sideFace);
            IBlockState current = world.getBlockState(p);
            world.setBlockState(p, current.withProperty(
                    rusticpipes.block.BlockFluidTankMultiblock.VIEWPORT, face), 2);
        }
    }

    /** Computes the viewport row for a block in the structure. */
    private static rusticpipes.block.BlockFluidTankMultiblock.ViewportRow viewportRowFor(
            BlockPos p, Structure structure) {
        rusticpipes.block.BlockFluidTankMultiblock.ViewportFace face = viewportFaceFor(p, structure);
        if (face == rusticpipes.block.BlockFluidTankMultiblock.ViewportFace.NONE)
            return rusticpipes.block.BlockFluidTankMultiblock.ViewportRow.NONE;

        int totalH = structure.max.getY() - structure.min.getY() + 1;
        boolean isBottom = p.getY() == structure.min.getY();
        boolean isTop    = p.getY() == structure.max.getY();

        if (totalH == 1)   return rusticpipes.block.BlockFluidTankMultiblock.ViewportRow.SINGLE;
        if (isBottom)      return rusticpipes.block.BlockFluidTankMultiblock.ViewportRow.BOTTOM;
        if (isTop)         return rusticpipes.block.BlockFluidTankMultiblock.ViewportRow.TOP;
        return rusticpipes.block.BlockFluidTankMultiblock.ViewportRow.CENTER;
    }

    /**
     * Determines which face of this block should show the viewport texture.
     * For a 2x2 base:
     *   min-X/min-Z → WEST
     *   max-X/min-Z → NORTH
     *   min-X/max-Z → SOUTH
     *   max-X/max-Z → EAST
     * Only wall blocks (not top/bottom) on the exterior get a viewport face.
     */
    /**
     * For 2x2 corner viewport blocks, returns the extra exterior perpendicular face.
     * That is the 90-degree-CCW rotation of the viewport direction.
     * For 3x3+ or blocks with no viewport, returns null.
     */
    private static net.minecraft.util.EnumFacing sideFaceFor(
            BlockPos p, Structure structure,
            rusticpipes.block.BlockFluidTankMultiblock.ViewportFace face) {
        if (face == rusticpipes.block.BlockFluidTankMultiblock.ViewportFace.NONE) return null;
        if (structure.baseSize != 2) return null;

        // For 2x2, each corner block has a second exterior face perpendicular to the viewport.
        // It's the CCW rotation of the viewport direction:
        // NORTH→WEST, WEST→SOUTH, SOUTH→EAST, EAST→NORTH
        switch (face) {
            case NORTH: return net.minecraft.util.EnumFacing.WEST;
            case WEST:  return net.minecraft.util.EnumFacing.SOUTH;
            case SOUTH: return net.minecraft.util.EnumFacing.EAST;
            case EAST:  return net.minecraft.util.EnumFacing.NORTH;
            default:    return null;
        }
    }

    private static rusticpipes.block.BlockFluidTankMultiblock.ViewportFace viewportFaceFor(
            BlockPos p, Structure structure) {
        // 1x1 base — show all 4 viewports via SINGLE state
        if (structure.baseSize == 1) {
            return rusticpipes.block.BlockFluidTankMultiblock.ViewportFace.SINGLE;
        }
        boolean isBottom = p.getY() == structure.min.getY();
        boolean isTop    = p.getY() == structure.max.getY();
        boolean isMinX   = p.getX() == structure.min.getX();
        boolean isMaxX   = p.getX() == structure.max.getX();
        boolean isMinZ   = p.getZ() == structure.min.getZ();
        boolean isMaxZ   = p.getZ() == structure.max.getZ();

        // Only wall blocks on the exterior get a viewport face (not top/bottom layer corners)
        String dir = null;

        if (structure.baseSize == 2) {
            if      (isMaxX && isMaxZ) dir = "south";
            else if (isMinX && isMinZ) dir = "north";
            else if (isMaxX && isMinZ) dir = "east";
            else if (isMinX && isMaxZ) dir = "west";
        } else {
            // Corners are solid for 3x3+
            if ((isMinX && isMinZ) || (isMaxX && isMinZ)
             || (isMinX && isMaxZ) || (isMaxX && isMaxZ))
                return rusticpipes.block.BlockFluidTankMultiblock.ViewportFace.NONE;

            if      (isMaxZ && p.getX() == structure.max.getX() - 1) dir = "south";
            else if (isMinZ && p.getX() == structure.min.getX() + 1) dir = "north";
            else if (isMaxX && p.getZ() == structure.min.getZ() + 1) dir = "east";
            else if (isMinX && p.getZ() == structure.max.getZ() - 1) dir = "west";
        }

        if (dir == null) return rusticpipes.block.BlockFluidTankMultiblock.ViewportFace.NONE;

        for (rusticpipes.block.BlockFluidTankMultiblock.ViewportFace f :
                rusticpipes.block.BlockFluidTankMultiblock.ViewportFace.values()) {
            if (f.getName().equals(dir)) return f;
        }
        return rusticpipes.block.BlockFluidTankMultiblock.ViewportFace.NONE;
    }


    /** Invalidates all TileEntityFluidTankMultiblock members sharing the same controller. */
    public static void invalidateMultiblock(World world, BlockPos pos) {
        net.minecraft.tileentity.TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileEntityFluidTankMultiblock)) return;
        BlockPos controller = ((TileEntityFluidTankMultiblock) te).getControllerPos();
        if (controller == null) return;
        for (int x = controller.getX() - 4; x <= controller.getX() + 4; x++)
            for (int y = controller.getY(); y <= controller.getY() + 10; y++)
                for (int z = controller.getZ() - 4; z <= controller.getZ() + 4; z++) {
                    BlockPos mp = new BlockPos(x, y, z);
                    net.minecraft.tileentity.TileEntity m = world.getTileEntity(mp);
                    if (m instanceof TileEntityFluidTankMultiblock) {
                        TileEntityFluidTankMultiblock mt = (TileEntityFluidTankMultiblock) m;
                        if (controller.equals(mt.getControllerPos())) {
                            mt.invalidate();
                            IBlockState current = world.getBlockState(mp);
                            if (current.getBlock() instanceof rusticpipes.block.BlockFluidTankMultiblock) {
                                world.setBlockState(mp, current.withProperty(
                                        rusticpipes.block.BlockFluidTankMultiblock.VIEWPORT,
                                        rusticpipes.block.BlockFluidTankMultiblock.ViewportFace.NONE), 2);
                            }
                        }
                    }
                }
    }
}
