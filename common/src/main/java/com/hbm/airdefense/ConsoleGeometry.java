package com.hbm.airdefense;

import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Where elements sit on the console blocks. The modelling space of tools/blender/console.py is used throughout:
 * x right, y away from the operator (front at -y), z up, x and y centred on the block, z from its floor, in blocks.
 * The desk stands 1.5 blocks tall (working height for a 1.8 block player); the wall panels fill their block.
 * A face is a rectangle of grid px (1/16 block) with its own right / up / out axes; elements are modelled in that
 * frame (X right, Y up, Z out), so one element fits every face, upright, sloped or level.
 */
public final class ConsoleGeometry {

    public enum Kind { DESK, PANEL, TILT }

    /**
     * w x h grid px; elements keep mx / my px off the sides (the dark seam texels along block and plate edges),
     * so they go between minX..maxX and minY..maxY.
     */
    public record Face(Vector3f origin, Vector3f right, Vector3f up, Vector3f out, int w, int h, int mx, int my, boolean level) {
        public int minX() {
            return mx;
        }

        public int maxX() {
            return w - mx;
        }

        public int minY() {
            return my;
        }

        public int maxY() {
            return h - my;
        }
    }

    private static float p(float px) {
        return px / 16 - 0.5f;
    }

    private static Face face(float ox, float oy, float oz, Vector3f up, Vector3f out, int w, int h, int mx, int my, boolean level) {
        up.normalize();
        out.normalize();
        return new Face(new Vector3f(ox, oy, oz), new Vector3f(up).cross(out), up, out, w, h, mx, my, level);
    }

    private static final List<Face> DESK = List.of(
            face(-0.5f, p(3), 15 / 16f, new Vector3f(0, 8, 4.5f), new Vector3f(0, -4.5f, 8), 16, 9, 1, 0, false), // sloped plate
            face(-0.5f, p(11), 19.5f / 16, new Vector3f(0, 0, 1), new Vector3f(0, -1, 0), 16, 4, 1, 0, false),    // raised back strip
            face(-0.5f, p(11), 1.5f, new Vector3f(0, 1, 0), new Vector3f(0, 0, 1), 16, 5, 1, 0, true));            // its level top
    private static final List<Face> PANEL = List.of(
            face(-0.5f, p(1), 0, new Vector3f(0, 0, 1), new Vector3f(0, -1, 0), 16, 16, 1, 1, false));
    private static final List<Face> TILT = List.of(
            face(-0.5f, p(5), 0, new Vector3f(0, -3.75f, 15), new Vector3f(0, -15, -3.75f), 16, 15, 1, 1, false));

    public static List<Face> faces(Kind kind) {
        return switch (kind) {
            case DESK -> DESK;
            case PANEL -> PANEL;
            case TILT -> TILT;
        };
    }

    /** Modelling space -> Minecraft block space (y up, front at +z before the facing turn). */
    private static final Matrix4f TO_MC = new Matrix4f(1, 0, 0, 0, 0, 0, -1, 0, 0, 1, 0, 0, 0, 0, 0, 1);

    /** Block space of a console facing `facing` (its front, where the operator stands): the meshes are made facing south. */
    public static Matrix4f block(Direction facing) {
        return new Matrix4f().translate(0.5f, 0, 0.5f).rotateY((float) Math.toRadians(-facing.toYRot()));
    }

    /** Element space (blocks, X right / Y up / Z out of the face) at grid px (x, y) of a face -> block space. */
    public static Matrix4f element(Direction facing, Face f, float x, float y) {
        Matrix4f face = new Matrix4f(f.right().x, f.right().y, f.right().z, 0, f.up().x, f.up().y, f.up().z, 0,
                f.out().x, f.out().y, f.out().z, 0, f.origin().x, f.origin().y, f.origin().z, 1);
        return block(facing).mul(TO_MC).mul(face).translate(x / 16, y / 16, 0);
    }

    // Collision / outline: kept just behind the faces, so a look at a face reaches it before the block.
    // The desk is 1.5 blocks tall and collides up there like a fence.
    private static final Map<Direction, VoxelShape> DESK_SHAPE = Shapes.rotateHorizontal(north(
            0, 2, 0, 16, 16, 15, 0, 9, 15, 16, 16, 18, 0, 11, 18, 16, 16, 24));
    private static final Map<Direction, VoxelShape> PANEL_SHAPE = Shapes.rotateHorizontal(north(0, 1, 0, 16, 16, 16));
    private static final Map<Direction, VoxelShape> TILT_SHAPE = Shapes.rotateHorizontal(north(
            0, 5, 0, 16, 16, 4, 0, 4, 4, 16, 16, 8, 0, 3, 8, 16, 16, 12, 0, 2, 12, 16, 16, 15, 0, 0, 15, 16, 16, 16));

    /** Boxes in modelling px (x0 y0 z0 x1 y1 z1 ...) as a shape for a console facing north. */
    private static VoxelShape north(int... b) {
        VoxelShape s = Shapes.empty();
        for (int i = 0; i < b.length; i += 6) {
            s = Shapes.or(s, Block.box(16 - b[i + 3], b[i + 2], b[i + 1], 16 - b[i], b[i + 5], b[i + 4]));
        }
        return s;
    }

    /** A run turning 90 degrees in this block: inner (around the operator) or outer, turned to `facing`. */
    public record Corner(boolean inner, Direction facing) {}

    /**
     * Like vanilla stairs: a console between two runs of its kind on perpendicular sides becomes a corner.
     * An arm is a neighbour of the same kind whose front runs across the side it touches. The corner meshes are
     * made for facing south: inner joins the runs west (front south) and south (front west), outer joins
     * west (front south) and north (front east).
     */
    public static @Nullable Corner corner(BlockGetter level, BlockPos pos, Kind kind) {
        for (Direction a : Direction.Plane.HORIZONTAL) {
            Direction fa = front(level, pos, a, kind);
            if (fa == null) continue;
            Direction b = a.getClockWise(), fb = front(level, pos, b, kind);
            if (fa == b && fb == a) return new Corner(true, a);
            b = a.getCounterClockWise();
            fb = front(level, pos, b, kind);
            if (fa == b.getOpposite() && fb == a.getOpposite()) return new Corner(false, a.getOpposite());
        }
        return null;
    }

    /** Front of the neighbour on side `side` when it is an arm (same kind, running across that side). */
    private static @Nullable Direction front(BlockGetter level, BlockPos pos, Direction side, Kind kind) {
        BlockState s = level.getBlockState(pos.relative(side));
        if (!(s.getBlock() instanceof BlockConsole b) || b.kind != kind) return null;
        Direction f = s.getValue(BlockConsole.FACING);
        return f.getAxis() == side.getAxis() ? null : f;
    }

    /** Collision of a corner: both runs' shapes over each other. */
    public static VoxelShape cornerShape(Kind kind, Corner c) {
        Direction other = c.inner() ? c.facing().getClockWise() : c.facing().getCounterClockWise();
        return Shapes.or(shape(kind, c.facing()), shape(kind, other));
    }

    public static VoxelShape shape(Kind kind, Direction facing) {
        return (switch (kind) {
            case DESK -> DESK_SHAPE;
            case PANEL -> PANEL_SHAPE;
            case TILT -> TILT_SHAPE;
        }).get(facing);
    }
}
