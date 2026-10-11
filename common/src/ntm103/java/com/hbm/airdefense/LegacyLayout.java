package com.hbm.airdefense;

import com.hbm.blocks.multiblock.MultiblockHandlerXR;
import com.hbm.blocks.multiblock.MultiblockLayout;
import com.hbm.blocks.multiblock.MultiblockMaskTable;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * NTM 1.0.3 no longer works a multiblock's cells out from its block: it reads a layout baked by NTM's
 * datagen, which addon blocks do not get. This builds that layout at runtime from the NTM 1.0.2-style
 * description (dimensions, offset, port cells, passive cells), the same way NTM's baker does: checked
 * byte for byte against NTM's own baked assembly machine and large radar (see docs/port-notes.md).
 */
final class LegacyLayout {

    interface Cells {
        /** mask: open faces in the block's local frame; roles: BlockMultiblockCore.ROLE_* (15 = all); kind: 0 = plain. */
        void cell(BlockPos pos, int mask, int roles, int kind);

        default void cell(BlockPos pos, int mask) {
            cell(pos, mask, 15, 0);
        }
    }

    interface Passives {
        void passive(BlockPos pos, int sides, int domains);
    }

    interface Shape {
        /** Port cells beyond the plain dimension box, world positions around the core for this facing. */
        void cells(BlockPos core, Direction facing, Cells out);

        void passives(BlockPos core, Direction facing, Passives out);
    }

    private LegacyLayout() {}

    /**
     * @param perFacing one mask table per facing: for ports that keep a world position whatever way the
     *                  block faces. NTM's baker takes a single table (facing SOUTH) and turns it.
     */
    static MultiblockLayout bake(int caps, int[] dims, int offset, boolean perFacing, Shape shape) {
        MultiblockMaskTable[] tables = new MultiblockMaskTable[4];
        long[][] offsets = new long[4][];
        long[][] clearance = new long[4][];
        IntArrayList ports = new IntArrayList();
        for (int i = 0; i < 4; i++) {
            Direction f = Direction.from2DDataValue(i);
            BlockPos core = BlockPos.ZERO;
            Map<BlockPos, int[]> cells = new LinkedHashMap<>(); // pos -> {mask, roles, kind}
            MultiblockHandlerXR.visitBox(core, dims, f, (pos, dir) -> cells.putIfAbsent(pos.immutable(), new int[]{0, 15, 0}));
            Map<BlockPos, Integer> ported = new LinkedHashMap<>(); // port cells in visiting order, for the placement preview
            shape.cells(core, f, (pos, mask, roles, kind) -> {
                cells.put(pos.immutable(), new int[]{mask, roles, kind});
                if (mask != 0) ported.put(pos.immutable(), mask);
            });
            MultiblockMaskTable.Builder b = MultiblockMaskTable.builder();
            LongArrayList off = new LongArrayList();
            b.cell(0, 0, 0, 0, 15); // the core
            for (Map.Entry<BlockPos, int[]> e : cells.entrySet()) {
                BlockPos p = e.getKey();
                if (p.equals(core)) continue;
                off.add(p.asLong());
                int[] c = e.getValue();
                b.cell(lx(p, f), ly(p, f), lz(p, f), c[0], c[1]);
                if (c[2] != 0) b.kind(lx(p, f), ly(p, f), lz(p, f), c[2]);
            }
            int[] domains = {0};
            shape.passives(core, f, (p, sides, d) -> {
                domains[0] |= d;
                if (!p.equals(core)) b.passive(lx(p, f), ly(p, f), lz(p, f), sides, d);
            });
            b.passive(0, 0, 0, 63, domains[0]);
            if (i == 0) {
                for (Map.Entry<BlockPos, Integer> e : ported.entrySet())
                    for (int bit = 5; bit >= 0; bit--)
                        if ((e.getValue() & 1 << bit) != 0) ports.addAll(IntArrayList.of(lx(e.getKey(), f), ly(e.getKey(), f), lz(e.getKey(), f), bit));
            }
            tables[i] = perFacing || i == 0 ? b.build() : tables[0];
            offsets[i] = off.toLongArray();
            clearance[i] = MultiblockLayout.NO_CELLS;
        }
        int[] preview = new int[9];
        System.arraycopy(dims, 0, preview, 0, 6);
        return new MultiblockLayout(caps, tables, offsets, clearance, placement(dims.clone(), offset, preview, ports.toIntArray()),
                MultiblockLayout.CellGeometry.NONE);
    }

    /** NTM 1.1.0 adds preview colours and port arrows to the placement; 1.0.3 has neither. */
    private static MultiblockLayout.Placement placement(int[] dims, int offset, int[] preview, int[] ports) {
        try {
            return MultiblockLayout.Placement.class
                    .getConstructor(int[].class, int.class, int.class, int[].class, double[].class, int[].class, int[].class)
                    .newInstance(dims, offset, 0, preview, new double[0], new int[0], ports);
        } catch (NoSuchMethodException e) {
            return new MultiblockLayout.Placement(dims, offset, 0, preview, new double[0]);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static int lx(BlockPos p, Direction f) {
        return MultiblockMaskTable.localX(p.getX(), p.getY(), p.getZ(), f);
    }

    private static int ly(BlockPos p, Direction f) {
        return MultiblockMaskTable.localY(p.getX(), p.getY(), p.getZ(), f);
    }

    private static int lz(BlockPos p, Direction f) {
        return MultiblockMaskTable.localZ(p.getX(), p.getY(), p.getZ(), f);
    }
}
